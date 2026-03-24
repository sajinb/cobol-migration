"""
MAPA Runner
===========
Wraps the CallTree.jar invocation so the pipeline can generate result.csv
automatically from raw COBOL source files — no manual step required.

MAPA (github.com/cschneid-the-elder/mapa) is an open-source static analysis
tool for COBOL portfolios. CallTree.jar scans COBOL programs and produces a
CSV containing program structure, CALL chains, CICS/SQL references, and data
definitions.

GitHub : https://github.com/cschneid-the-elder/mapa
JAR    : https://github.com/cschneid-the-elder/mapa/raw/refs/heads/master/cobol/CallTree.jar

Actual CLI invocation:
    java -jar CallTree.jar -freeForm -fileList <file-listing-cobol-paths> -out result.csv [-copy <copybook-dir>]

Dependency JARs (must be co-located with CallTree.jar — the manifest Class-Path references them by name):
    antlr-4.13.2-complete.jar  — ANTLR4 runtime used by the COBOL grammar
    commons-cli-1.4.jar        — Apache Commons CLI for argument parsing

This module:
  1. Optionally auto-downloads CallTree.jar from GitHub if not present locally.
  2. Writes a temporary file-list of all .cbl/.cob files found in cobol_dir.
  3. Invokes CallTree.jar via subprocess with the correct flags.
  4. Validates that the output CSV was produced and is non-empty.
  5. Returns a structured result dict for LangGraph agent consumption.
"""

import logging
import shutil
import subprocess
import tempfile
import urllib.request
import urllib.error
from pathlib import Path
from typing import Dict, List, Optional

logger = logging.getLogger(__name__)

# Direct download URLs — raw GitHub content (not the blob page)
_MAPA_BASE_URL = "https://github.com/cschneid-the-elder/mapa/raw/refs/heads/master/cobol"

MAPA_DEFAULT_JAR_URL = f"{_MAPA_BASE_URL}/CallTree.jar"
MAPA_DEFAULT_JAR_NAME = "CallTree.jar"

# These two JARs must live in the same directory as CallTree.jar.
# The manifest Class-Path references them by name (relative paths).
MAPA_DEPENDENCY_JARS: List[Dict] = [
    {
        "name": "antlr-4.13.2-complete.jar",
        "url": f"{_MAPA_BASE_URL}/antlr-4.13.2-complete.jar",
    },
    {
        "name": "commons-cli-1.4.jar",
        "url": f"{_MAPA_BASE_URL}/commons-cli-1.4.jar",
    },
]


class MapaRunner:
    """
    Runs MAPA's CallTree.jar against a directory of COBOL source files and
    produces a result.csv that feeds the Neo4j ingestion pipeline.

    Usage::

        runner = MapaRunner(jar_path="./CallTree.jar")
        result = runner.run(
            cobol_dir="./cobol_samples",
            output_csv="./cobol_samples/result.csv",
            copybook_dir="./copybooks",   # optional
        )
        if result["success"]:
            print(f"Generated: {result['csv_path']}")
        else:
            print(f"MAPA failed: {result['error']}")
    """

    def __init__(
        self,
        jar_path: str = f"./{MAPA_DEFAULT_JAR_NAME}",
        jar_url: Optional[str] = None,
        java_executable: str = "java",
        jvm_opts: str = "-Xmx2g",
        auto_download: bool = True,
    ):
        self.jar_path = Path(jar_path)
        self.jar_url = jar_url or MAPA_DEFAULT_JAR_URL
        self.java_executable = java_executable
        self.jvm_opts = jvm_opts
        self.auto_download = auto_download

    # ------------------------------------------------------------------ #
    #  Public API                                                          #
    # ------------------------------------------------------------------ #

    def run(
        self,
        cobol_dir: str,
        output_csv: str,
        copybook_dir: Optional[str] = None,
        extra_args: Optional[list] = None,
    ) -> Dict:
        """
        Run CallTree.jar against ``cobol_dir`` and write ``output_csv``.

        Parameters
        ----------
        cobol_dir    : directory scanned recursively for .cbl/.cob files
        output_csv   : path for the generated CSV (-out flag)
        copybook_dir : optional directory containing copybooks (-copy flag)
        extra_args   : additional raw CLI flags passed verbatim

        Returns a dict::

            {
                "success"   : bool,
                "csv_path"  : str,    # populated on success
                "returncode": int,
                "stdout"    : str,
                "stderr"    : str,
                "row_count" : int,    # populated on success
                "error"     : str,    # populated on failure
            }
        """
        cobol_path = Path(cobol_dir)
        csv_path = Path(output_csv)

        # 1. Ensure JAR + co-located dependency JARs are available
        jar_result = self._ensure_jar()
        if not jar_result["success"]:
            return {**jar_result, "csv_path": "", "returncode": -1, "stdout": "", "stderr": ""}

        dep_result = self._ensure_dependencies()
        if not dep_result["success"]:
            return {**dep_result, "csv_path": "", "returncode": -1, "stdout": "", "stderr": ""}

        # 2. Collect COBOL source files
        if not cobol_path.is_dir():
            return self._error(f"COBOL source directory not found: {cobol_path}")

        cobol_files = (
            list(cobol_path.glob("**/*.cbl"))
            + list(cobol_path.glob("**/*.cob"))
            + list(cobol_path.glob("**/*.CBL"))
            + list(cobol_path.glob("**/*.COB"))
        )
        # Deduplicate (case-insensitive glob may overlap on case-sensitive FS)
        cobol_files = list({f.resolve(): f for f in cobol_files}.values())

        if not cobol_files:
            return self._error(f"No .cbl/.cob files found in: {cobol_path}")

        logger.info("Running CallTree.jar on %d COBOL file(s) in %s", len(cobol_files), cobol_path)

        # copy_deps accumulates COPY member names extracted during preprocessing;
        # populated only when the retry path is taken (original MAPA run fails).
        copy_deps: Dict[str, List[str]] = {}

        # 3. Ensure output directory exists
        csv_path.parent.mkdir(parents=True, exist_ok=True)

        # 4. Write a temp file-list and build the command.
        #    CallTree.jar uses -fileList <path-to-file> where each line is a COBOL file path.
        #    IMPORTANT: Use POSIX (forward-slash) paths inside the filelist even on Windows.
        #    Java's internal file reader uses the JVM's path handling which accepts forward
        #    slashes on all platforms, but backslashes inside a text file read by the JAR
        #    may not be resolved correctly on non-Windows builds of the tool.
        with tempfile.NamedTemporaryFile(
            mode="w", suffix=".txt", delete=False, prefix="mapa_filelist_", encoding="utf-8"
        ) as flist:
            posix_paths = "\n".join(f.resolve().as_posix() for f in cobol_files)
            flist.write(posix_paths)
            flist_path = flist.name

        logger.debug("Filelist contents:\n%s", posix_paths)

        # Normalise copybook dir: create uppercase-named symlinks in a temp dir
        # so MAPA can find members regardless of on-disk capitalisation.
        resolved_copybook_dir = (
            self._normalise_copybook_dir(copybook_dir) if copybook_dir else None
        )

        try:
            cmd = self._build_command(flist_path, csv_path, resolved_copybook_dir, extra_args or [])
            logger.info("MAPA command: %s", " ".join(str(c) for c in cmd))

            # 5. Execute
            try:
                proc = subprocess.run(
                    cmd,
                    capture_output=True,
                    text=True,
                    encoding="utf-8",
                    errors="replace",
                    timeout=600,  # 10-minute timeout for large portfolios
                )
            except FileNotFoundError:
                return self._error(
                    f"Java executable not found: '{self.java_executable}'. "
                    "Please install JDK 11+ and ensure 'java' is on your PATH."
                )
            except subprocess.TimeoutExpired:
                return self._error("CallTree.jar timed out after 600 seconds.")
            except Exception as exc:
                return self._error(f"Subprocess error: {exc}")

            # Always log stdout/stderr so errors from the JAR are visible
            if proc.stdout.strip():
                logger.info("CallTree.jar stdout:\n%s", proc.stdout.strip())
            if proc.stderr.strip():
                logger.warning("CallTree.jar stderr:\n%s", proc.stderr.strip())

            if proc.returncode != 0:
                # ---------------------------------------------------------- #
                # Root cause: a bug in some CallTree.jar builds where         #
                # CopyStatement.apply() performs substring(0, indexOf(x)-1)  #
                # and indexOf returns 0, giving substring(0,-1) → crash.     #
                #                                                             #
                # Fix: preprocess the COBOL sources into a temp directory,   #
                # replacing every COPY statement with CONTINUE. (a valid     #
                # COBOL no-op). MAPA never constructs a CopyStatement, so    #
                # apply() is never called.  CALL/PERFORM chains, paragraph   #
                # structure and data items are still fully captured.  COPY   #
                # dependencies are extracted separately from the original    #
                # sources and returned alongside the CSV path so the         #
                # ingestion agent can add them to Neo4j directly.            #
                # ---------------------------------------------------------- #
                preprocessed_dir = Path(tempfile.mkdtemp(prefix="mapa_preprocessed_"))
                preprocessed_flist = None
                try:
                    logger.warning(
                        "CallTree.jar failed (rc=%d) with original sources — "
                        "retrying with COPY-neutralised preprocessed copies to "
                        "bypass CopyStatement.apply() crash.",
                        proc.returncode,
                    )
                    preprocessed_files, copy_deps = self._preprocess_copy_statements(  # noqa: F841
                        cobol_files, preprocessed_dir
                    )
                    if copy_deps:
                        logger.info(
                            "Extracted %d COPY dependencies from original sources: %s",
                            sum(len(v) for v in copy_deps.values()),
                            copy_deps,
                        )

                    # Write a new file-list for the preprocessed copies
                    with tempfile.NamedTemporaryFile(
                        mode="w",
                        suffix=".txt",
                        delete=False,
                        prefix="mapa_filelist_pre_",
                        encoding="utf-8",
                    ) as pflist:
                        pflist.write(
                            "\n".join(
                                Path(p).resolve().as_posix() for p in preprocessed_files
                            )
                        )
                        preprocessed_flist = pflist.name

                    cmd_pre = self._build_command(
                        preprocessed_flist, csv_path, resolved_copybook_dir, extra_args or []
                    )
                    logger.info(
                        "MAPA retry command (preprocessed): %s",
                        " ".join(str(c) for c in cmd_pre),
                    )
                    try:
                        proc = subprocess.run(
                            cmd_pre,
                            capture_output=True,
                            text=True,
                            encoding="utf-8",
                            errors="replace",
                            timeout=600,
                        )
                    except Exception as exc:
                        return self._error(f"Subprocess error on preprocessed retry: {exc}")

                    if proc.stdout.strip():
                        logger.info(
                            "CallTree.jar retry stdout:\n%s", proc.stdout.strip()
                        )
                    if proc.stderr.strip():
                        logger.warning(
                            "CallTree.jar retry stderr:\n%s", proc.stderr.strip()
                        )
                finally:
                    if preprocessed_flist:
                        Path(preprocessed_flist).unlink(missing_ok=True)
                    shutil.rmtree(preprocessed_dir, ignore_errors=True)

                if proc.returncode != 0:
                    return {
                        "success": False,
                        "csv_path": "",
                        "returncode": proc.returncode,
                        "stdout": proc.stdout,
                        "stderr": proc.stderr,
                        "row_count": 0,
                        "error": (
                            f"CallTree.jar exited with code {proc.returncode}. "
                            f"stderr: {proc.stderr[:500]}"
                        ),
                    }

            # 6. Validate output
            if not csv_path.exists():
                return self._error(
                    f"CallTree.jar succeeded (rc=0) but {csv_path} was not created. "
                    "Check that -out flag is supported by your JAR version."
                )

            row_count = self._count_csv_rows(csv_path)
            logger.info("CallTree.jar complete — %d rows in %s", row_count, csv_path)

            if row_count == 0:
                # Log first 500 bytes of the file to diagnose silent empty-output issues
                try:
                    raw = csv_path.read_bytes()
                    logger.warning(
                        "result.csv is empty (0 rows). File size: %d bytes. "
                        "First 500 bytes: %r",
                        len(raw), raw[:500],
                    )
                except Exception:
                    pass

            return {
                "success": True,
                "csv_path": str(csv_path),
                "returncode": proc.returncode,
                "stdout": proc.stdout,
                "stderr": proc.stderr,
                "row_count": row_count,
                "error": "",
                # Populated when the COPY-neutralisation retry was taken.
                # Maps program_name → [copybook_member, ...] so the caller
                # can write COPIES edges to Neo4j independently of MAPA's CSV.
                "copy_deps": copy_deps,
            }
        finally:
            # Clean up the temp file-list
            Path(flist_path).unlink(missing_ok=True)

    # ------------------------------------------------------------------ #
    #  JAR management                                                      #
    # ------------------------------------------------------------------ #

    def _ensure_jar(self) -> Dict:
        """Return success if JAR exists; try to download it if not."""
        if self.jar_path.exists():
            logger.debug("CallTree.jar found at %s", self.jar_path)
            return {"success": True, "error": ""}

        if not self.auto_download:
            return self._error(
                f"MAPA JAR not found at {self.jar_path}. "
                f"Set MAPA_JAR_PATH or place {MAPA_DEFAULT_JAR_NAME} in the project root. "
                "Download from: " + self.jar_url
            )

        logger.info("CallTree.jar not found — downloading from %s", self.jar_url)
        return self._download_jar()

    def _download_jar(self) -> Dict:
        """Download CallTree.jar from the GitHub raw URL."""
        self.jar_path.parent.mkdir(parents=True, exist_ok=True)
        try:
            logger.info("Downloading %s → %s", self.jar_url, self.jar_path)
            urllib.request.urlretrieve(self.jar_url, str(self.jar_path))

            if not self.jar_path.exists() or self.jar_path.stat().st_size < 1024:
                return self._error(
                    f"Downloaded file at {self.jar_path} is empty or invalid."
                )

            logger.info(
                "CallTree.jar downloaded successfully (%d bytes)", self.jar_path.stat().st_size
            )
            return {"success": True, "error": ""}

        except urllib.error.URLError as exc:
            return self._error(
                f"Failed to download CallTree.jar from {self.jar_url}: {exc}. "
                "Download manually from "
                "https://github.com/cschneid-the-elder/mapa/raw/refs/heads/master/cobol/CallTree.jar "
                "and set MAPA_JAR_PATH in your .env file."
            )

    def _ensure_dependencies(self) -> Dict:
        """
        Ensure antlr-4.13.2-complete.jar and commons-cli-1.4.jar are present
        in the same directory as CallTree.jar.

        CallTree.jar's MANIFEST.MF contains::

            Class-Path: antlr-4.13.2-complete.jar commons-cli-1.4.jar

        Java resolves these as paths relative to the JAR itself, so they must
        be co-located — adding them to -cp has no effect when -jar is used.
        """
        jar_dir = self.jar_path.parent
        jar_dir.mkdir(parents=True, exist_ok=True)

        for dep in MAPA_DEPENDENCY_JARS:
            dep_path = jar_dir / dep["name"]
            if dep_path.exists():
                logger.debug("Dependency found: %s", dep_path)
                continue

            if not self.auto_download:
                return self._error(
                    f"Required dependency not found: {dep_path}. "
                    f"Download from {dep['url']} and place it next to CallTree.jar."
                )

            logger.info("Downloading dependency %s → %s", dep["name"], dep_path)
            try:
                urllib.request.urlretrieve(dep["url"], str(dep_path))
            except urllib.error.URLError as exc:
                return self._error(
                    f"Failed to download {dep['name']}: {exc}. "
                    f"Download manually from {dep['url']} and place it next to CallTree.jar."
                )

            if not dep_path.exists() or dep_path.stat().st_size < 1024:
                return self._error(
                    f"Downloaded {dep['name']} appears empty or corrupt at {dep_path}."
                )

            logger.info(
                "%s downloaded successfully (%d bytes)", dep["name"], dep_path.stat().st_size
            )

        return {"success": True, "error": ""}

    # ------------------------------------------------------------------ #
    #  COBOL preprocessor                                                  #
    # ------------------------------------------------------------------ #

    @staticmethod
    def _preprocess_copy_statements(
        cobol_files: List[Path],
        dest_dir: Path,
    ):
        """
        Write copies of *cobol_files* into *dest_dir* with every COPY statement
        replaced by ``CONTINUE.``

        This neutralises a bug in certain CallTree.jar builds where
        ``CopyStatement.apply()`` performs ``substring(0, indexOf(x) - 1)``
        and crashes with StringIndexOutOfBoundsException when ``indexOf``
        returns 0 (i.e. the target token is at the very start of the string
        MAPA happens to be processing).

        Returns
        -------
        preprocessed_files : list[str]
            Absolute paths of the files written to *dest_dir*.
        copy_deps : dict[str, list[str]]
            Mapping of ``program_name → [copybook_member, ...]`` extracted
            from the original sources so the caller can add COPIES edges to
            Neo4j without relying on MAPA's (now-suppressed) expansion.
        """
        import re

        # Matches: optional leading whitespace, COPY, whitespace, member name
        # (anything up to the statement-terminating period), optional trailing
        # whitespace/comment.  Handles single-line COPY statements only; for
        # multi-line REPLACING clauses the member is still captured correctly
        # because it always appears on the COPY line itself.
        _COPY_RE = re.compile(
            r"^(?P<indent>\s*)COPY\s+(?P<member>[A-Za-z0-9$#@_-]+)(?P<rest>[^.]*)\.",
            re.IGNORECASE | re.MULTILINE,
        )

        preprocessed_files: List[str] = []
        copy_deps: Dict[str, List[str]] = {}

        for src_path in cobol_files:
            try:
                source = src_path.read_text(encoding="utf-8", errors="replace")
            except OSError as exc:
                logger.warning("Could not read %s for preprocessing: %s", src_path, exc)
                continue

            program_name = src_path.stem.upper()
            members_found: List[str] = []

            def _replace(m: "re.Match") -> str:  # type: ignore[type-arg]
                member = m.group("member").upper()
                members_found.append(member)
                indent = m.group("indent")
                logger.debug(
                    "Neutralising COPY %s in %s → CONTINUE.", member, src_path.name
                )
                return f"{indent}CONTINUE."

            processed = _COPY_RE.sub(_replace, source)

            if members_found:
                copy_deps[program_name] = members_found

            dest_file = dest_dir / src_path.name
            try:
                dest_file.write_text(processed, encoding="utf-8")
                preprocessed_files.append(str(dest_file))
            except OSError as exc:
                logger.warning(
                    "Could not write preprocessed %s: %s", dest_file, exc
                )

        return preprocessed_files, copy_deps

    # ------------------------------------------------------------------ #
    #  Command builder                                                     #
    # ------------------------------------------------------------------ #

    def _build_command(
        self,
        flist_path: str,
        output_csv: Path,
        copybook_dir: Optional[str],
        extra_args: list,
    ) -> list:
        """
        Build the subprocess command list for CallTree.jar.

        CallTree.jar flags used:
          -freeForm         Treat COBOL source as free-form (required; without it
                             the parser applies fixed-form column rules and may
                             skip entire programs → 0 rows in output)
          -fileList <path>   File containing one COBOL source path per line
          -out <path>        Output CSV path
          -copy <dir>        Copybook directory (optional, single path)
          -logLevel INFO     Emit parse errors/warnings so failures are visible
        """
        cmd = [self.java_executable]
        if self.jvm_opts:
            cmd.extend(self.jvm_opts.split())

        # Use POSIX paths for -out and -copy as well (forward slashes work on all platforms)
        cmd += [
            "-jar", str(self.jar_path),
            "-freeForm",                          # must be present — see docstring above
            "-fileList", flist_path,
            "-out", Path(output_csv).as_posix(),
            "-logLevel", "INFO",
        ]

        if copybook_dir:
            cmd += ["-copy", Path(copybook_dir).as_posix()]

        cmd.extend(extra_args)
        return cmd

    # ------------------------------------------------------------------ #
    #  Helpers                                                             #
    # ------------------------------------------------------------------ #

    @staticmethod
    def _error(msg: str) -> Dict:
        logger.error(msg)
        return {
            "success": False,
            "csv_path": "",
            "returncode": -1,
            "stdout": "",
            "stderr": "",
            "row_count": 0,
            "error": msg,
        }

    @staticmethod
    def _count_csv_rows(csv_path: Path) -> int:
        try:
            with csv_path.open() as f:
                return sum(1 for line in f if line.strip())
        except Exception:
            return -1

    def _normalise_copybook_dir(self, copybook_dir: str) -> str:
        """
        MAPA's JAR resolves a COPY member (e.g. ``COPY EMPREC``) by looking for
        a file whose name is **exactly the member name with no extension** in the
        -copy directory.  From the MAPA readme "Real Example":

            "These have a file extension for some reason and I need to remove it
             because the COBOL code that uses them does not make reference to the
             extension."
            find -name "*.CPY" -exec sh -c 'mv "$1" "${1%.CPY}"' _ {} \\;

        So ``EMPREC.CPY`` on disk will never be matched — MAPA looks for ``EMPREC``.

        This method creates a temporary directory containing extension-less
        symlinks (uppercase stem) for every copybook file found, so the JAR
        resolves members regardless of how the files are named on disk.

        The temp dir lives under /tmp and is recreated fresh on every run.
        """
        src = Path(copybook_dir)
        if not src.is_dir():
            return copybook_dir  # nothing to do

        tmp = Path(tempfile.mkdtemp(prefix="mapa_copybooks_"))
        copybook_exts = {".cpy", ".copy"}  # lowercase comparison
        linked = 0
        for f in src.iterdir():
            if not f.is_file():
                continue
            if f.suffix.lower() not in copybook_exts:
                continue
            # Extension-less, uppercase link — what MAPA actually looks for
            ext_less = tmp / f.stem.upper()
            if not ext_less.exists():
                ext_less.symlink_to(f.resolve())
                linked += 1

        logger.debug(
            "Copybook normalisation: %d extension-less symlinks created in %s → %s",
            linked, src, tmp,
        )
        return str(tmp)
