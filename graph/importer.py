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
from typing import Dict, List, Set

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

        Pass 1 — MAPA CSV: Program, Copybook, CALLS, DD (dataset) nodes.
        Pass 2 — CobolParser: Paragraph nodes, DataItems, PERFORMS / READS / WRITES.

        Returns counts: programs, paragraphs, data_items, copybooks, calls,
        datasets, relationships.
        """
        apply_schema(self._neo4j)

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
        return counts

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
