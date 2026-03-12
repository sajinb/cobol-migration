"""
MAPA CSV → Neo4j importer.

Reads the MAPA result.csv (produced by running the MAPA JAR against your
COBOL source portfolio) and populates the Neo4j graph with Program,
Paragraph, DataItem, Copybook nodes and all their relationships.

Expected CSV columns (all lowercase, stripped):
  program, paragraph, start_line, end_line,
  performs, calls, copies, data_reads, data_writes

Multi-value fields are semicolon-separated: e.g. "PARA-A;PARA-B"
"""

import logging
from pathlib import Path
from typing import List, Dict

from tools.neo4j_tools import Neo4jTools
from tools.file_tools import FileTools
from graph.schema import apply_schema

logger = logging.getLogger(__name__)


class MapaCsvImporter:
    """Ingest MAPA CSV output into the Neo4j graph."""

    def __init__(self, neo4j: Neo4jTools, cobol_source_dir: str):
        self._neo4j = neo4j
        self._source_dir = Path(cobol_source_dir)
        self._file_tools = FileTools()

    def run(self, csv_path: str) -> Dict[str, int]:
        """
        Full ingestion run.
        Returns a dict with counts: programs, paragraphs, relationships.
        """
        # Ensure schema (constraints + indexes) is in place
        apply_schema(self._neo4j)

        rows = FileTools.parse_mapa_csv(csv_path)
        if not rows:
            logger.warning("No rows to import from: %s", csv_path)
            return {"programs": 0, "paragraphs": 0, "relationships": 0}

        # Log actual column names so mismatches are immediately visible.
        logger.info("CSV columns detected: %s", list(rows[0].keys()))

        # MAPA / CallTree.jar uses different column names depending on version.
        # Build a normalised alias map so we can handle both variants.
        col = _build_column_map(rows[0])
        logger.info("Column mapping resolved: %s", col)

        counts = {"programs": 0, "paragraphs": 0, "relationships": 0}

        # 1st pass: create Program and Paragraph nodes (with source code)
        for row in rows:
            program = row.get(col["program"], "").upper()
            paragraph = row.get(col["paragraph"], "").upper()
            if not program or not paragraph:
                logger.debug("Skipping row — missing program/paragraph: %s", row)
                continue

            start_line = int(row.get(col["start_line"], 0) or 0)
            end_line = int(row.get(col["end_line"], 0) or 0)

            # Locate the COBOL source file
            cobol_file = self._find_cobol_file(program)

            # Upsert Program node
            self._neo4j.upsert_program(program, str(cobol_file) if cobol_file else "")
            counts["programs"] += 1

            # Extract raw source and upsert Paragraph node
            source_code = ""
            if cobol_file and start_line and end_line:
                source_code = FileTools.extract_paragraph(
                    str(cobol_file), start_line, end_line
                )
            self._neo4j.upsert_paragraph(program, paragraph, start_line, end_line, source_code)
            counts["paragraphs"] += 1

            # Copybooks
            for copybook in FileTools.split_list_field(row.get(col["copies"], "")):
                self._neo4j.upsert_copybook(copybook.upper(), program)
                counts["relationships"] += 1

        # 2nd pass: create relationships (requires all nodes to exist first)
        for row in rows:
            program = row.get(col["program"], "").upper()
            paragraph = row.get(col["paragraph"], "").upper()
            if not program or not paragraph:
                continue

            # PERFORMS relationships
            for target in FileTools.split_list_field(row.get(col["performs"], "")):
                self._neo4j.add_perform_relationship(paragraph, target.upper(), program)
                counts["relationships"] += 1

            # CALLS relationships (inter-program)
            for called in FileTools.split_list_field(row.get(col["calls"], "")):
                self._neo4j.add_call_relationship(program, called.upper())
                counts["relationships"] += 1

            # Data READ relationships
            for item in FileTools.split_list_field(row.get(col["data_reads"], "")):
                self._neo4j.upsert_data_item(item.upper(), program)
                self._neo4j.add_reads_relationship(paragraph, item.upper(), program)
                counts["relationships"] += 1

            # Data WRITE relationships
            for item in FileTools.split_list_field(row.get(col["data_writes"], "")):
                self._neo4j.upsert_data_item(item.upper(), program)
                self._neo4j.add_writes_relationship(paragraph, item.upper(), program)
                counts["relationships"] += 1

        logger.info(
            "Import complete — programs: %d, paragraphs: %d, relationships: %d",
            counts["programs"],
            counts["paragraphs"],
            counts["relationships"],
        )
        return counts

    # ------------------------------------------------------------------ #
    #  Internal helpers                                                    #
    # ------------------------------------------------------------------ #

    # ------------------------------------------------------------------ #
    #  Column-name resolution                                             #
    # ------------------------------------------------------------------ #

    def _find_cobol_file(self, program_name: str):
        """
        Look for a COBOL source file matching the program name.
        Tries common extensions: .cbl, .cob, .cobol (case-insensitive).
        """
        extensions = [".cbl", ".cob", ".cobol", ".CBL", ".COB", ".COBOL"]
        for ext in extensions:
            candidate = self._source_dir / f"{program_name}{ext}"
            if candidate.exists():
                return candidate
            # Case-insensitive fallback
            candidate_lower = self._source_dir / f"{program_name.lower()}{ext.lower()}"
            if candidate_lower.exists():
                return candidate_lower
        logger.debug("No source file found for program: %s", program_name)
        return None


# ------------------------------------------------------------------ #
#  Column-name alias resolution                                       #
# ------------------------------------------------------------------ #

# MAPA / CallTree.jar has changed column names across versions.
# Each entry is a logical field → list of candidate column names in
# priority order (all must already be lowercased / stripped).
_COLUMN_ALIASES: Dict[str, List[str]] = {
    "program":    ["program", "programname", "program-name", "program_name", "pgmname", "pgm"],
    "paragraph":  ["paragraph", "paragraphname", "paragraph-name", "para", "section", "proc"],
    "start_line": ["start_line", "startline", "start-line", "from", "fromline", "beginline", "begin_line"],
    "end_line":   ["end_line", "endline", "end-line", "to", "toline", "endofpara", "end_of_para"],
    "performs":   ["performs", "perform", "calledparagraphs", "called_paragraphs", "performedby"],
    "calls":      ["calls", "call", "calledprograms", "called_programs", "extcalls", "ext_calls"],
    "copies":     ["copies", "copy", "copybooks", "copybook", "includes", "include"],
    "data_reads": ["data_reads", "datareads", "reads", "read", "readitems", "readsdata"],
    "data_writes":["data_writes", "datawrites", "writes", "write", "writeitems", "writesdata"],
}


def _build_column_map(first_row: Dict[str, str]) -> Dict[str, str]:
    """
    Given the first parsed row (keys already lowercased), return a dict
    that maps each logical field name to the actual column key present in
    the CSV.  Falls back to the canonical name if nothing matches, so the
    caller always gets a string key (resulting in an empty-string value).
    """
    available = set(first_row.keys())
    result: Dict[str, str] = {}
    for field, candidates in _COLUMN_ALIASES.items():
        matched = next((c for c in candidates if c in available), candidates[0])
        result[field] = matched
        if matched not in available:
            logger.warning(
                "Column '%s' not found in CSV. Tried: %s. Available: %s",
                field, candidates, sorted(available),
            )
    return result
