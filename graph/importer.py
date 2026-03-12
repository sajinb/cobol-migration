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
from typing import Dict, List

from tools.neo4j_tools import Neo4jTools
from tools.file_tools import FileTools
from graph.schema import apply_schema

logger = logging.getLogger(__name__)


class MapaCsvImporter:
    """Ingest MAPA CSV output into the Neo4j graph."""

    def __init__(self, neo4j: Neo4jTools, cobol_source_dir: str):
        self._neo4j = neo4j
        self._source_dir = Path(cobol_source_dir)

    def run(self, csv_path: str) -> Dict[str, int]:
        """
        Full ingestion run.
        Returns a dict with counts: programs, paragraphs, relationships.
        """
        apply_schema(self._neo4j)

        records = FileTools.parse_mapa_csv(csv_path)
        if not records:
            logger.warning("No records to import from: %s", csv_path)
            return {"programs": 0, "paragraphs": 0, "relationships": 0}

        # Track unique program names so the final count reflects every Program
        # node written — including those implicitly created by CALL handling.
        seen_programs: set = set()
        counts = {"programs": 0, "paragraphs": 0, "relationships": 0,
                  "copybooks": 0, "calls": 0, "datasets": 0}

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
        # Create Program nodes                                                #
        # ------------------------------------------------------------------ #
        for row in records.get("PGM", []):
            if len(row) < 4:
                continue
            file_uuid = row[2]
            pgm_name  = row[3].upper()
            file_path = file_map.get(file_uuid, "")

            # Prefer a file found in the local source dir; fall back to the
            # absolute path MAPA recorded (useful for audit / cross-reference).
            cobol_file = self._find_cobol_file(pgm_name, mapa_file_path=file_path)
            resolved_path = str(cobol_file) if cobol_file else file_path

            self._neo4j.upsert_program(pgm_name, resolved_path)
            seen_programs.add(pgm_name)
            logger.debug("Program: %s  (%s)", pgm_name, resolved_path)

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
        # relationships = COPIES edges + CALLS edges (DD creates DataItem nodes,
        # not relationship edges, so they are tracked separately as "datasets")
        counts["relationships"] = counts["copybooks"] + counts["calls"]

        logger.info(
            "Import complete — programs: %d  copybooks: %d  calls: %d  datasets: %d",
            counts["programs"],
            counts["copybooks"],
            counts["calls"],
            counts["datasets"],
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
