"""
MAPA CSV → Neo4j importer.

MAPA's result.csv has NO header row.  Each row is self-describing:
the first column is a record-type tag.  Known tags (CallTree.jar):

  FILE  col[1]=uuid  col[2]=file_path        col[3]=timestamp
  COPY  col[1]=uuid  col[2]=file_uuid         col[3]=copybook_name
  PGM   col[1]=uuid  col[2]=file_uuid         col[3]=program_name  col[4..]=metrics
  CALL  col[1]=uuid  col[2]=pgm_uuid          col[3]=calling_pgm   col[4]=call_type  col[5]=called_pgm
  DD    col[1]=uuid  col[2]=pgm_uuid          col[3]=file_path     col[4]=dd_name    col[5..]=io_counts

UUID linkage:
  FILE.uuid  ← referenced by COPY.col[2] and PGM.col[2]
  PGM.uuid   ← referenced by CALL.col[2] and DD.col[2]
"""

import logging
from pathlib import Path
from typing import Dict, List, Optional, Set

from tools.neo4j_tools import Neo4jTools
from tools.file_tools import FileTools
from tools.cobol_parser import CobolParser
from graph.schema import apply_schema

logger = logging.getLogger(__name__)


class MapaCsvImporter:
    """Ingest MAPA CSV output into the Neo4j graph."""

    def __init__(self, neo4j: Neo4jTools, cobol_source_dir: str):
        self._neo4j = neo4j
        self._source_dir = Path(cobol_source_dir)
        self._cobol_parser = CobolParser()

    def run(self, csv_path: str) -> Dict[str, int]:
        """
        Full ingestion run.

        Detects the CSV format automatically:
          - Custom format (header row: program,paragraph,...) → _run_custom_csv
          - MAPA native format (no header, first col = record-type tag) → standard path

        Pass 1 — Program nodes from CSV.
        Pass 2 — CobolParser: Paragraph nodes, DataItems, PERFORMS / READS / WRITES.

        Returns counts: programs, paragraphs, data_items, copybooks, calls,
        datasets, relationships.
        """
        apply_schema(self._neo4j)

        if self._is_custom_csv(csv_path):
            logger.info("Detected custom CSV format — using simplified ingestion path.")
            return self._run_custom_csv(csv_path)

        records = FileTools.parse_mapa_csv(csv_path)
        if not records:
            logger.warning("No records to import from: %s", csv_path)
            return {
                "programs": 0, "paragraphs": 0, "data_items": 0,
                "copybooks": 0, "calls": 0, "datasets": 0, "relationships": 0,
            }

        # Track unique program names so the final count reflects every Program
        # node written — including those implicitly created by CALL handling.
        seen_programs: Set[str] = set()
        counts: Dict[str, int] = {
            "programs": 0, "paragraphs": 0, "data_items": 0,
            "copybooks": 0, "calls": 0, "datasets": 0, "relationships": 0,
        }

        # ------------------------------------------------------------------ #
        # Build UUID lookup tables                                            #
        # ------------------------------------------------------------------ #

        # file_uuid → absolute file path (from FILE records)
        # FILE: col[1]=uuid, col[2]=file_path
        file_map: Dict[str, str] = {
            row[1]: row[2]
            for row in records.get("FILE", [])
            if len(row) >= 3
        }

        # file_uuid → list of program names (one .cbl can have >1 PGM entry)
        # PGM: col[1]=pgm_uuid, col[2]=file_uuid, col[3]=program_name
        file_to_pgms: Dict[str, List[str]] = {}
        pgm_uuid_to_name: Dict[str, str] = {}  # pgm_uuid → program_name
        for row in records.get("PGM", []):
            if len(row) < 4:
                continue
            pgm_uuid, file_uuid, pgm_name = row[1], row[2], row[3].upper()
            pgm_uuid_to_name[pgm_uuid] = pgm_name
            file_to_pgms.setdefault(file_uuid, []).append(pgm_name)

        # ------------------------------------------------------------------ #
        # Pass 1 — Create Program nodes from MAPA PGM records                #
        # ------------------------------------------------------------------ #
        program_files: Dict[str, str] = {}  # pgm_name → resolved source path

        for row in records.get("PGM", []):
            if len(row) < 4:
                continue
            file_uuid = row[2]
            pgm_name  = row[3].upper()
            file_path = file_map.get(file_uuid, "")

            # Prefer a file found in the local source dir; fall back to the
            # absolute path MAPA recorded (useful for audit / cross-reference).
            cobol_file    = self._find_cobol_file(pgm_name, mapa_file_path=file_path)
            resolved_path = str(cobol_file) if cobol_file else file_path

            self._neo4j.upsert_program(pgm_name, resolved_path)
            seen_programs.add(pgm_name)
            program_files[pgm_name] = resolved_path
            logger.debug("Program: %s  (%s)", pgm_name, resolved_path)

        # ------------------------------------------------------------------ #
        # Pass 2 — CobolParser: paragraphs, data items, source-level edges   #
        # ------------------------------------------------------------------ #
        src = self._run_source_parse_pass(program_files)
        counts["paragraphs"]   += src["paragraphs"]
        counts["data_items"]   += src["data_items"]
        counts["relationships"] += src["relationships"]

        # ------------------------------------------------------------------ #
        # Create Copybook nodes + COPIES relationships                        #
        # COPY: col[2]=file_uuid, col[3]=copybook_name                        #
        # ------------------------------------------------------------------ #
        for row in records.get("COPY", []):
            if len(row) < 4:
                continue
            file_uuid     = row[2]
            copybook_name = row[3].upper()
            for pgm_name in file_to_pgms.get(file_uuid, []):
                self._neo4j.upsert_copybook(copybook_name, pgm_name)
                counts["copybooks"] += 1
                counts["relationships"] += 1
                logger.debug("COPIES: %s → %s", pgm_name, copybook_name)

        # ------------------------------------------------------------------ #
        # Create CALLS relationships                                          #
        # CALL: col[3]=calling_pgm, col[4]=call_type, col[5]=called_pgm      #
        # ------------------------------------------------------------------ #
        for row in records.get("CALL", []):
            if len(row) < 6:
                continue
            calling_pgm = row[3].upper()
            called_pgm  = row[5].upper()
            # Ensure both program nodes exist (called program may not have
            # a PGM row if it lives in a separate un-analysed file)
            self._neo4j.upsert_program(called_pgm, "")
            seen_programs.add(called_pgm)
            self._neo4j.add_call_relationship(calling_pgm, called_pgm)
            counts["calls"] += 1
            counts["relationships"] += 1
            logger.debug("CALLS: %s → %s  (%s)", calling_pgm, called_pgm, row[4])

        # ------------------------------------------------------------------ #
        # Create File/Dataset nodes from DD records                          #
        # DD: col[2]=pgm_uuid, col[3]=file_path, col[4]=dd_name             #
        # col[5]=?, col[6]=reads?, col[7]=writes?                            #
        # ------------------------------------------------------------------ #
        for row in records.get("DD", []):
            if len(row) < 5:
                continue
            pgm_uuid  = row[2]
            pgm_name  = pgm_uuid_to_name.get(pgm_uuid, "")
            dd_name   = row[4].upper()
            if pgm_name and dd_name:
                # Store the dataset as a DataItem so the existing schema applies
                self._neo4j.upsert_data_item(dd_name, pgm_name, pic_type="FILE", level=0)
                counts["datasets"] += 1
                logger.debug("DD: %s.%s  (%s)", pgm_name, dd_name, row[3])

        counts["programs"] = len(seen_programs)
        # Add MAPA-level edges (COPIES + CALLS) to relationship total.
        counts["relationships"] += counts["copybooks"] + counts["calls"]

        logger.info(
            "Import complete — programs=%d  paragraphs=%d  data_items=%d  "
            "copybooks=%d  calls=%d  datasets=%d  relationships=%d",
            counts["programs"],
            counts["paragraphs"],
            counts["data_items"],
            counts["copybooks"],
            counts["calls"],
            counts["datasets"],
            counts["relationships"],
        )
        # Also return the exact program names that were written so callers can
        # scope the next pipeline stages to only the freshly-ingested programs.
        counts["program_names"] = sorted(seen_programs)
        return counts

    # ------------------------------------------------------------------ #
    #  Copybook ingestion (COPY-neutralised retry path)                   #
    # ------------------------------------------------------------------ #

    def ingest_copy_deps(
        self,
        copy_deps: Dict[str, List[str]],
        copybook_dir: str = "",
    ) -> Dict[str, int]:
        """
        Create Copybook nodes + COPIES relationships from *copy_deps* and
        optionally parse each .cpy file for its data items.

        *copy_deps* has the shape produced by
        ``MapaRunner._preprocess_copy_statements``:
            {"EMPPROG": ["EMPREC"], ...}

        Copybook files are looked up (case-insensitively) in *copybook_dir*,
        falling back to the importer's source directory.  When a .cpy file is
        found its data items are added to Neo4j linked to the Copybook node.

        Returns counts of copybooks and data_items written.
        """
        from tools.cobol_parser import CobolParser

        counts = {"copybooks": 0, "data_items": 0, "relationships": 0}
        if not copy_deps:
            return counts

        parser = CobolParser()
        search_dirs: List[Path] = []
        if copybook_dir and Path(copybook_dir).is_dir():
            search_dirs.append(Path(copybook_dir))
        if self._source_dir and self._source_dir.is_dir():
            if self._source_dir not in search_dirs:
                search_dirs.append(self._source_dir)

        for program_name, members in copy_deps.items():
            for member in members:
                # Create Copybook node + COPIES edge
                self._neo4j.upsert_copybook(member, program_name)
                counts["copybooks"] += 1
                counts["relationships"] += 1
                logger.debug("COPIES (from copy_deps): %s → %s", program_name, member)

                # Try to find and parse the .cpy file
                cpy_file = self._find_copybook_file(member, search_dirs)
                if not cpy_file:
                    continue

                try:
                    result = parser.parse(str(cpy_file))
                    for item in result.data_items:
                        # Link data items to the Program (standard schema) so
                        # Graph RAG queries that traverse HAS_DATA_ITEM from
                        # the Program node pick them up.
                        self._neo4j.upsert_data_item(
                            item.name, program_name,
                            pic_type=item.pic_type, level=item.level,
                        )
                        counts["data_items"] += 1
                    logger.info(
                        "Parsed copybook %s — %d data items added for program %s",
                        cpy_file.name, len(result.data_items), program_name,
                    )
                except Exception as exc:
                    logger.warning(
                        "Could not parse copybook %s: %s", cpy_file, exc
                    )

        counts["relationships"] += counts["data_items"]
        return counts

    @staticmethod
    def _find_copybook_file(member: str, search_dirs: List[Path]) -> Optional[Path]:
        """Return the first .cpy file matching *member* (case-insensitive)."""
        for d in search_dirs:
            for ext in (".cpy", ".CPY", ".copy", ".COPY"):
                candidate = d / f"{member}{ext}"
                if candidate.exists():
                    return candidate
                # Also try with program-name casing variants
                for name_variant in (member.lower(), member.upper()):
                    candidate = d / f"{name_variant}{ext}"
                    if candidate.exists():
                        return candidate
        return None

    # ------------------------------------------------------------------ #
    #  Pass 2: COBOL source parser                                         #
    # ------------------------------------------------------------------ #

    def _run_source_parse_pass(self, program_files: Dict[str, str]) -> Dict[str, int]:
        """
        For each Program with a resolvable source file, parse the .cbl with
        CobolParser and enrich the graph.

        All Neo4j writes for a single program are collected then sent in one
        batched HTTP request instead of one request per write — this eliminates
        the N+1 round-trip bottleneck that caused the pipeline to stall on
        programs with many paragraphs / data items.
        """
        extra: Dict[str, int] = {"paragraphs": 0, "data_items": 0, "relationships": 0}

        # ── Cypher templates (kept here for locality) ──────────────────
        _UPSERT_PARA = """
        MERGE (p:Paragraph {name: $name, program: $program})
        ON CREATE SET p.status  = 'pending', p.created = timestamp()
        SET p.start_line  = $start_line,
            p.end_line    = $end_line,
            p.source_code = $source_code,
            p.updated     = timestamp()
        WITH p
        MATCH (prog:Program {name: $program})
        MERGE (prog)-[:HAS_PARAGRAPH]->(p)
        """

        _SET_PARA_STATUS = """
        MATCH (p:Paragraph {name: $name, program: $program})
        SET p.status     = $status,
            p.complexity = $complexity,
            p.intent     = $intent,
            p.updated    = timestamp()
        """

        _UPSERT_DATA_ITEM = """
        MERGE (d:DataItem {name: $name, program: $program})
        SET d.pic_type = $pic_type, d.level = $level
        WITH d
        MATCH (prog:Program {name: $program})
        MERGE (prog)-[:HAS_DATA_ITEM]->(d)
        """

        _ADD_PERFORMS = """
        MATCH (a:Paragraph {name: $from_para, program: $program})
        MATCH (b:Paragraph {name: $to_para,   program: $program})
        MERGE (a)-[:PERFORMS]->(b)
        """

        _ADD_CALL = """
        MERGE (a:Program {name: $from_program})
        MERGE (b:Program {name: $to_program})
        MERGE (a)-[:CALLS]->(b)
        """

        _ADD_READS = """
        MATCH (p:Paragraph {name: $para,      program: $program})
        MATCH (d:DataItem  {name: $data_item, program: $program})
        MERGE (p)-[:READS]->(d)
        """

        _ADD_WRITES = """
        MATCH (p:Paragraph {name: $para,      program: $program})
        MATCH (d:DataItem  {name: $data_item, program: $program})
        MERGE (p)-[:WRITES]->(d)
        """

        _UPSERT_COPYBOOK = """
        MERGE (c:Copybook {name: $name})
        WITH c
        MATCH (prog:Program {name: $program})
        MERGE (prog)-[:COPIES]->(c)
        """

        for pgm_name, file_path in program_files.items():
            if not file_path:
                continue

            parse_result = self._cobol_parser.parse(file_path)
            if not parse_result.paragraphs and not parse_result.data_items:
                logger.debug("Parser found nothing for %s (%s)", pgm_name, file_path)
                continue

            para_names: Set[str] = {p.name for p in parse_result.paragraphs}

            # Collect every write for this program into a single list,
            # then flush them all in one (or a few) HTTP requests.
            batch: List[tuple] = []

            # ── Paragraph nodes ────────────────────────────────────────
            for para in parse_result.paragraphs:
                batch.append((_UPSERT_PARA, {
                    "program": pgm_name,
                    "name": para.name,
                    "start_line": para.start_line,
                    "end_line": para.end_line,
                    "source_code": para.source_code,
                }))
                extra["paragraphs"] += 1

                if para.is_section_entry and para.performs:
                    batch.append((_SET_PARA_STATUS, {
                        "name": para.name,
                        "program": pgm_name,
                        "status": "analysed",
                        "complexity": "LOW",
                        "intent": (
                            f"COBOL SECTION entry point — delegates to "
                            f"{para.performs[0]} (section body)"
                        ),
                    }))

                # PERFORMS (intra-program only)
                for target in para.performs:
                    if target in para_names:
                        batch.append((_ADD_PERFORMS, {
                            "from_para": para.name,
                            "to_para": target,
                            "program": pgm_name,
                        }))
                        extra["relationships"] += 1

                # CALLS to external programs
                for called in para.calls:
                    batch.append((_ADD_CALL, {
                        "from_program": pgm_name,
                        "to_program": called,
                    }))
                    extra["relationships"] += 1

                # DataItem READS — upsert item then link
                for item_name in para.reads:
                    batch.append((_UPSERT_DATA_ITEM, {
                        "name": item_name, "program": pgm_name,
                        "pic_type": "", "level": 0,
                    }))
                    batch.append((_ADD_READS, {
                        "para": para.name, "data_item": item_name, "program": pgm_name,
                    }))
                    extra["relationships"] += 1

                # DataItem WRITES — upsert item then link
                for item_name in para.writes:
                    batch.append((_UPSERT_DATA_ITEM, {
                        "name": item_name, "program": pgm_name,
                        "pic_type": "", "level": 0,
                    }))
                    batch.append((_ADD_WRITES, {
                        "para": para.name, "data_item": item_name, "program": pgm_name,
                    }))
                    extra["relationships"] += 1

            # ── COPY members → Copybook nodes + COPIES edges ───────────
            # CobolParser extracts COPY <member> statements from the .cbl source.
            # This path fires when MAPA CSV has no COPY records (e.g. custom CSV
            # or programs not included in MAPA's call-tree scan).
            for member in parse_result.copies:
                batch.append((_UPSERT_COPYBOOK, {
                    "name": member,
                    "program": pgm_name,
                }))
                extra["relationships"] += 1
                logger.debug("COPIES (source-parse): %s → %s", pgm_name, member)

            # ── WORKING-STORAGE items (program-level DataItem nodes) ───
            for di in parse_result.data_items:
                batch.append((_UPSERT_DATA_ITEM, {
                    "name": di.name, "program": pgm_name,
                    "pic_type": di.pic, "level": di.level,
                }))
                extra["data_items"] += 1

            # ── Single batched flush for this program ──────────────────
            logger.debug(
                "Flushing %d Neo4j writes for program %s", len(batch), pgm_name
            )
            self._neo4j.write_batch(batch)

        logger.info(
            "Source-parse pass — paragraphs=%d  data_items=%d  relationships=%d",
            extra["paragraphs"],
            extra["data_items"],
            extra["relationships"],
        )
        return extra

    # ------------------------------------------------------------------ #
    #  Custom CSV format (simplified, header-based)                        #
    # ------------------------------------------------------------------ #

    def _is_custom_csv(self, csv_path: str) -> bool:
        """Return True if the CSV has a header row starting with 'program'."""
        import csv as _csv
        path = Path(csv_path)
        if not path.exists():
            return False
        with path.open("r", newline="", errors="replace") as fh:
            first = next(_csv.reader(fh), None)
        return bool(first and first[0].strip().lower() == "program")

    def _run_custom_csv(self, csv_path: str) -> Dict[str, int]:
        """
        Ingest the simplified custom CSV format:
          program, paragraph, start_line, end_line, performs, calls,
          copies, data_reads, data_writes

        Pass 1 — create Program nodes from unique program names in the CSV.
        Pass 2 — CobolParser enriches each program from its .cbl source file.
        """
        import csv as _csv

        counts: Dict[str, int] = {
            "programs": 0, "paragraphs": 0, "data_items": 0,
            "copybooks": 0, "calls": 0, "datasets": 0, "relationships": 0,
        }

        program_files: Dict[str, str] = {}
        seen_programs: Set[str] = set()

        path = Path(csv_path)
        with path.open("r", newline="", errors="replace") as fh:
            reader = _csv.DictReader(fh)
            for row in reader:
                pgm_name = row.get("program", "").strip().upper()
                if not pgm_name:
                    continue
                if pgm_name not in seen_programs:
                    cobol_file = self._find_cobol_file(pgm_name)
                    resolved   = str(cobol_file) if cobol_file else ""
                    self._neo4j.upsert_program(pgm_name, resolved)
                    seen_programs.add(pgm_name)
                    program_files[pgm_name] = resolved
                    logger.debug("Program (custom CSV): %s  (%s)", pgm_name, resolved)

        counts["programs"] = len(seen_programs)

        # Pass 2 — source-level enrichment via CobolParser
        src = self._run_source_parse_pass(program_files)
        counts["paragraphs"]    += src["paragraphs"]
        counts["data_items"]    += src["data_items"]
        counts["relationships"] += src["relationships"]

        logger.info(
            "Custom CSV import complete — programs=%d  paragraphs=%d  "
            "data_items=%d  relationships=%d",
            counts["programs"], counts["paragraphs"],
            counts["data_items"], counts["relationships"],
        )
        return counts

    # ------------------------------------------------------------------ #
    #  Internal helpers                                                    #
    # ------------------------------------------------------------------ #

    def _find_cobol_file(self, program_name: str, mapa_file_path: str = ""):
        """
        Look for a COBOL source file under the configured source directory.

        Search order:
          1. <source_dir>/<program_name><ext>  (e.g. MEGADEMO.cbl)
          2. <source_dir>/<program_name.lower><ext>
          3. <source_dir>/<basename of MAPA-recorded path>  (e.g. Sample.cbl)
             — handles cases where the filename differs from the program name

        Returns a Path on success, None if not found.
        """
        extensions = [".cbl", ".cob", ".cobol", ".CBL", ".COB", ".COBOL"]
        for ext in extensions:
            for stem in (program_name, program_name.lower()):
                candidate = self._source_dir / f"{stem}{ext}"
                if candidate.exists():
                    return candidate

        # Fallback: look for the exact filename that MAPA recorded
        if mapa_file_path:
            mapa_basename = Path(mapa_file_path).name
            candidate = self._source_dir / mapa_basename
            if candidate.exists():
                return candidate
            # Also try the lowercase version
            candidate_lower = self._source_dir / mapa_basename.lower()
            if candidate_lower.exists():
                return candidate_lower

        logger.debug(
            "No source file found for program '%s' (mapa_path=%s)",
            program_name, mapa_file_path or "n/a",
        )
        return None
