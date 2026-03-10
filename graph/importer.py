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

        counts = {"programs": 0, "paragraphs": 0, "relationships": 0}

        # 1st pass: create Program and Paragraph nodes (with source code)
        for row in rows:
            program = row.get("program", "").upper()
            paragraph = row.get("paragraph", "").upper()
            if not program or not paragraph:
                continue

            start_line = int(row.get("start_line", 0) or 0)
            end_line = int(row.get("end_line", 0) or 0)

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
            for copybook in FileTools.split_list_field(row.get("copies", "")):
                self._neo4j.upsert_copybook(copybook.upper(), program)
                counts["relationships"] += 1

        # 2nd pass: create relationships (requires all nodes to exist first)
        for row in rows:
            program = row.get("program", "").upper()
            paragraph = row.get("paragraph", "").upper()
            if not program or not paragraph:
                continue

            # PERFORMS relationships
            for target in FileTools.split_list_field(row.get("performs", "")):
                self._neo4j.add_perform_relationship(paragraph, target.upper(), program)
                counts["relationships"] += 1

            # CALLS relationships (inter-program)
            for called in FileTools.split_list_field(row.get("calls", "")):
                self._neo4j.add_call_relationship(program, called.upper())
                counts["relationships"] += 1

            # Data READ relationships
            for item in FileTools.split_list_field(row.get("data_reads", "")):
                self._neo4j.upsert_data_item(item.upper(), program)
                self._neo4j.add_reads_relationship(paragraph, item.upper(), program)
                counts["relationships"] += 1

            # Data WRITE relationships
            for item in FileTools.split_list_field(row.get("data_writes", "")):
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
