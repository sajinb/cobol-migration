"""
MAPA Runner
===========
Wraps the MAPA JAR invocation so the pipeline can generate result.csv
automatically from raw COBOL source files — no manual step required.

MAPA (Mainframe Application Portfolio Analyser) is an open-source static
analysis tool built on ProLeap's COBOL parser. It produces a CSV that maps
programs → paragraphs → data items → call/perform/copy relationships.

GitHub: https://github.com/mapa-devs/mapa  (JAR in Releases)

Typical invocation:
    java -jar mapa.jar --input <cobol-dir> --output <result.csv>

This module:
  1. Optionally auto-downloads the JAR from GitHub if not present locally.
  2. Invokes the JAR via subprocess with configurable JVM options.
  3. Validates that the output CSV was produced and is non-empty.
  4. Returns a structured result dict for LangGraph agent consumption.
"""

import logging
import subprocess
import urllib.request
import urllib.error
from pathlib import Path
from typing import Dict, Optional

logger = logging.getLogger(__name__)

# Default GitHub release URL for MAPA JAR
# Update this to the actual release URL once the open-source repo is confirmed.
MAPA_DEFAULT_JAR_URL = (
    "https://github.com/mapa-devs/mapa/releases/latest/download/mapa.jar"
)

# Fallback: ProLeap-based portfolio analyser
PROLEAP_ANALYSER_URL = (
    "https://github.com/uwol/proleap-cobol-parser/releases/latest/download/proleap-cobol-parser.jar"
)


class MapaRunner:
    """
    Runs the MAPA JAR against a directory of COBOL source files and
    produces a result.csv that feeds the Neo4j ingestion pipeline.

    Usage::

        runner = MapaRunner(jar_path="./mapa.jar")
        result = runner.run(
            cobol_dir="./cobol_samples",
            output_csv="./cobol_samples/result.csv",
        )
        if result["success"]:
            print(f"Generated: {result['csv_path']}")
        else:
            print(f"MAPA failed: {result['error']}")
    """

    def __init__(
        self,
        jar_path: str = "./mapa.jar",
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
        extra_args: Optional[list] = None,
    ) -> Dict:
        """
        Run MAPA against ``cobol_dir`` and write ``output_csv``.

        Returns a dict::

            {
                "success": bool,
                "csv_path": str,          # populated on success
                "returncode": int,
                "stdout": str,
                "stderr": str,
                "error": str,             # populated on failure
            }
        """
        cobol_path = Path(cobol_dir)
        csv_path = Path(output_csv)

        # 1. Ensure JAR is available
        jar_result = self._ensure_jar()
        if not jar_result["success"]:
            return {**jar_result, "csv_path": "", "returncode": -1, "stdout": "", "stderr": ""}

        # 2. Validate COBOL directory
        if not cobol_path.is_dir():
            return self._error(f"COBOL source directory not found: {cobol_path}")

        cobol_files = list(cobol_path.glob("**/*.cbl")) + list(cobol_path.glob("**/*.cob"))
        if not cobol_files:
            return self._error(f"No .cbl/.cob files found in: {cobol_path}")

        logger.info(
            "Running MAPA on %d COBOL file(s) in %s", len(cobol_files), cobol_path
        )

        # 3. Ensure output directory exists
        csv_path.parent.mkdir(parents=True, exist_ok=True)

        # 4. Build the command
        cmd = self._build_command(cobol_path, csv_path, extra_args or [])
        logger.debug("MAPA command: %s", " ".join(cmd))

        # 5. Execute
        try:
            proc = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=600,  # 10-minute timeout for large portfolios
            )
        except FileNotFoundError:
            return self._error(
                f"Java executable not found: '{self.java_executable}'. "
                "Please install JDK 11+ and ensure 'java' is on your PATH."
            )
        except subprocess.TimeoutExpired:
            return self._error("MAPA JAR timed out after 600 seconds.")
        except Exception as exc:
            return self._error(f"Subprocess error: {exc}")

        if proc.returncode != 0:
            return {
                "success": False,
                "csv_path": "",
                "returncode": proc.returncode,
                "stdout": proc.stdout,
                "stderr": proc.stderr,
                "error": f"MAPA exited with code {proc.returncode}. stderr: {proc.stderr[:500]}",
            }

        # 6. Validate output
        if not csv_path.exists():
            return self._error(
                f"MAPA succeeded (rc=0) but {csv_path} was not created. "
                "Check MAPA version and --output flag compatibility."
            )

        row_count = self._count_csv_rows(csv_path)
        logger.info("MAPA complete — %d rows in %s", row_count, csv_path)

        return {
            "success": True,
            "csv_path": str(csv_path),
            "returncode": proc.returncode,
            "stdout": proc.stdout,
            "stderr": proc.stderr,
            "row_count": row_count,
            "error": "",
        }

    # ------------------------------------------------------------------ #
    #  JAR management                                                      #
    # ------------------------------------------------------------------ #

    def _ensure_jar(self) -> Dict:
        """Return success if JAR exists; try to download it if not."""
        if self.jar_path.exists():
            logger.debug("MAPA JAR found at %s", self.jar_path)
            return {"success": True, "error": ""}

        if not self.auto_download:
            return self._error(
                f"MAPA JAR not found at {self.jar_path}. "
                "Set MAPA_JAR_PATH or place mapa.jar in the project root. "
                "Download from: " + self.jar_url
            )

        logger.info("MAPA JAR not found — attempting auto-download from %s", self.jar_url)
        return self._download_jar()

    def _download_jar(self) -> Dict:
        """Download the MAPA JAR from GitHub releases."""
        self.jar_path.parent.mkdir(parents=True, exist_ok=True)
        try:
            logger.info("Downloading %s → %s", self.jar_url, self.jar_path)
            urllib.request.urlretrieve(self.jar_url, str(self.jar_path))

            if not self.jar_path.exists() or self.jar_path.stat().st_size < 1024:
                return self._error(
                    f"Downloaded file at {self.jar_path} is empty or invalid."
                )

            logger.info("MAPA JAR downloaded successfully (%d bytes)", self.jar_path.stat().st_size)
            return {"success": True, "error": ""}

        except urllib.error.URLError as exc:
            return self._error(
                f"Failed to download MAPA JAR from {self.jar_url}: {exc}. "
                "Download manually and set MAPA_JAR_PATH in your .env file."
            )

    # ------------------------------------------------------------------ #
    #  Command builder                                                     #
    # ------------------------------------------------------------------ #

    def _build_command(
        self, cobol_dir: Path, output_csv: Path, extra_args: list
    ) -> list:
        """
        Build the subprocess command list.

        MAPA CLI flags (typical):
          --input  <dir>    COBOL source directory (scanned recursively)
          --output <path>   Output CSV file path
          --recursive       Recurse into subdirectories (enabled by default)

        Adjust flags here if your MAPA build uses different argument names.
        """
        cmd = [
            self.java_executable,
        ]
        if self.jvm_opts:
            cmd.extend(self.jvm_opts.split())

        cmd += [
            "-jar", str(self.jar_path),
            "--input", str(cobol_dir),
            "--output", str(output_csv),
            "--recursive",
        ]
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
            "error": msg,
        }

    @staticmethod
    def _count_csv_rows(csv_path: Path) -> int:
        try:
            with csv_path.open() as f:
                return sum(1 for _ in f) - 1  # subtract header row
        except Exception:
            return -1
