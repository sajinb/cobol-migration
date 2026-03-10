"""
File tools for reading COBOL source files and MAPA CSV output.
"""

import csv
import logging
from pathlib import Path
from typing import Dict, Iterator, List, Optional

logger = logging.getLogger(__name__)


class FileTools:
    """Utilities for working with COBOL source files and MAPA CSV output."""

    # ------------------------------------------------------------------ #
    #  COBOL source extraction                                             #
    # ------------------------------------------------------------------ #

    @staticmethod
    def extract_paragraph(file_path: str, start_line: int, end_line: int) -> str:
        """
        Slice a range of lines from a COBOL file (1-based line numbers).
        Returns the raw COBOL text for a paragraph.
        """
        path = Path(file_path)
        if not path.exists():
            logger.warning("COBOL file not found: %s", file_path)
            return ""
        with path.open("r", errors="replace") as fh:
            lines = fh.readlines()
        # CSV line numbers are 1-based
        return "".join(lines[start_line - 1 : end_line])

    @staticmethod
    def read_cobol_file(file_path: str) -> str:
        """Read the entire content of a COBOL source file."""
        path = Path(file_path)
        if not path.exists():
            logger.warning("COBOL file not found: %s", file_path)
            return ""
        return path.read_text(errors="replace")

    @staticmethod
    def list_cobol_files(directory: str) -> List[str]:
        """Return all .cbl / .cob / .cobol files under a directory."""
        root = Path(directory)
        extensions = {".cbl", ".cob", ".cobol", ".CBL", ".COB", ".COBOL"}
        return [
            str(p)
            for p in root.rglob("*")
            if p.is_file() and p.suffix in extensions
        ]

    # ------------------------------------------------------------------ #
    #  MAPA CSV parsing                                                    #
    # ------------------------------------------------------------------ #

    @staticmethod
    def parse_mapa_csv(csv_path: str) -> List[Dict]:
        """
        Parse the MAPA result.csv and return a list of row dicts.

        Expected columns (MAPA output):
          program, paragraph, start_line, end_line,
          performs, calls, copies, data_reads, data_writes

        Column names are normalised to lowercase / stripped.
        """
        path = Path(csv_path)
        if not path.exists():
            logger.warning("MAPA CSV not found: %s", csv_path)
            return []

        rows: List[Dict] = []
        with path.open("r", newline="", errors="replace") as fh:
            reader = csv.DictReader(fh)
            for row in reader:
                # Normalise keys
                normalised = {k.strip().lower(): v.strip() for k, v in row.items()}
                rows.append(normalised)

        logger.info("Parsed %d rows from MAPA CSV: %s", len(rows), csv_path)
        return rows

    @staticmethod
    def split_list_field(value: str, delimiter: str = ";") -> List[str]:
        """
        Helper to split a CSV field that contains multiple values separated
        by a delimiter (e.g. 'PARA-A;PARA-B').
        Returns an empty list for blank values.
        """
        if not value or value.strip() == "":
            return []
        return [v.strip() for v in value.split(delimiter) if v.strip()]

    # ------------------------------------------------------------------ #
    #  Output writing                                                      #
    # ------------------------------------------------------------------ #

    @staticmethod
    def write_java_output(
        output_dir: str, program: str, paragraph: str, java_code: str
    ) -> str:
        """
        Write generated Java code to the output directory.
        Returns the path of the written file.
        """
        root = Path(output_dir) / program
        root.mkdir(parents=True, exist_ok=True)
        # Convert COBOL naming (hyphens) to Java file naming (CamelCase)
        class_name = _to_camel_case(paragraph)
        out_path = root / f"{class_name}.java"
        out_path.write_text(java_code)
        logger.info("Java output written: %s", out_path)
        return str(out_path)


def _to_camel_case(cobol_name: str) -> str:
    """Convert COBOL-STYLE-NAMES to CamelCase."""
    parts = cobol_name.replace("-", "_").split("_")
    return "".join(p.capitalize() for p in parts if p)
