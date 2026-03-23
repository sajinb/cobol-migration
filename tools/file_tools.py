"""
File tools for reading COBOL source files and MAPA CSV output.
"""

import csv
import logging
import re
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
    def parse_mapa_csv(csv_path: str) -> Dict[str, List[List[str]]]:
        """
        Parse MAPA's result.csv which has *no header row*.

        Each row is self-describing: the first column is a record-type tag.
        Returns a dict mapping each tag to the list of raw (stripped) rows
        of that type.

        Known tags produced by CallTree.jar:
          FILE  — source file metadata
          PGM   — program definition
          COPY  — COPY (copybook) statement
          CALL  — CALL statement (inter-program call)
          DD    — file/dataset declaration (SELECT … ASSIGN)
        """
        path = Path(csv_path)
        if not path.exists():
            logger.warning("MAPA CSV not found: %s", csv_path)
            return {}

        records: Dict[str, List[List[str]]] = {}
        with path.open("r", newline="", errors="replace") as fh:
            reader = csv.reader(fh)
            for raw_row in reader:
                if not raw_row or not raw_row[0].strip():
                    continue
                rec_type = raw_row[0].strip().upper()
                cleaned = [col.strip() for col in raw_row]
                records.setdefault(rec_type, []).append(cleaned)

        total = sum(len(v) for v in records.values())
        logger.info(
            "Parsed %d rows from MAPA CSV: %s  (record types: %s)",
            total, csv_path, list(records.keys()),
        )
        return records

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
    def parse_method_fragment(code: str) -> Dict:
        """
        Parse LLM output that uses the structured fragment format:
            // ===IMPORTS===
            // ===FIELDS===
            // ===METHOD===

        Falls back to extracting imports + body from a legacy complete-class
        response when markers are absent.

        Returns a dict with keys 'imports' (List[str]), 'fields' (List[str]),
        'method' (str).
        """
        # Strip markdown fences if present
        code = code.replace("```java", "").replace("```", "")

        imports: List[str] = []
        fields: List[str] = []
        method_lines: List[str] = []
        section = None
        in_companion = False   # True while inside a ===COMPANION_FILE=== block

        for line in code.splitlines():
            stripped = line.strip()
            if stripped == "// ===IMPORTS===":
                section = "imports"
                in_companion = False
            elif stripped == "// ===FIELDS===":
                section = "fields"
                in_companion = False
            elif stripped == "// ===METHOD===":
                section = "method"
                in_companion = False
            elif stripped.startswith("// ===COMPANION_FILE:") and stripped.endswith("==="):
                # Entering a companion block — stop feeding lines into method_lines
                in_companion = True
            elif stripped == "// ===END_COMPANION===":
                in_companion = False
            elif in_companion:
                pass  # companion body handled by the second pass below
            elif section == "imports" and stripped.startswith("import "):
                imports.append(stripped)
            elif section == "fields" and stripped:
                fields.append(stripped)
            elif section == "method":
                method_lines.append(line)

        # Fallback: no markers — extract from a complete class response
        if not method_lines:
            for line in code.splitlines():
                if line.strip().startswith("import "):
                    imports.append(line.strip())
            body = re.sub(
                r'^.*?public\s+class\s+\w+[^{]*\{', '', code,
                count=1, flags=re.DOTALL,
            )
            body = body.rstrip()
            if body.endswith("}"):
                body = body[:-1]
            method_lines = body.splitlines()

        # Extract companion files (===COMPANION_FILE: Name.java=== … ===END_COMPANION===)
        companion_files: Dict[str, str] = {}
        comp_name: Optional[str] = None
        comp_lines: List[str] = []
        for line in code.splitlines():
            stripped = line.strip()
            if stripped.startswith("// ===COMPANION_FILE:") and stripped.endswith("==="):
                comp_name  = stripped[len("// ===COMPANION_FILE:"):].rstrip("=").strip()
                comp_lines = []
            elif stripped == "// ===END_COMPANION===" and comp_name:
                companion_files[comp_name] = "\n".join(comp_lines).strip()
                comp_name  = None
                comp_lines = []
            elif comp_name is not None:
                comp_lines.append(line)

        return {
            "imports":         list(dict.fromkeys(i for i in imports if i)),
            "fields":          list(dict.fromkeys(f for f in fields if f)),
            "method":          "\n".join(method_lines).strip(),
            "companion_files": companion_files,
        }

    @staticmethod
    def assemble_service_class(program: str, fragments: List[Dict]) -> str:
        """
        Combine per-paragraph method fragments into one @Service class.

        Each entry in *fragments* must have a 'generated_code' key containing
        the raw LLM output (structured or legacy format).

        Returns the full Java source text for <ProgramCamelCase>Service.java.
        """
        package_name = program.lower().replace("-", "")
        class_name   = _to_camel_case(program) + "Service"

        base_imports = {
            "import lombok.extern.slf4j.Slf4j;",
            "import org.springframework.beans.factory.annotation.Autowired;",
            "import org.springframework.stereotype.Service;",
            "import org.springframework.transaction.annotation.Transactional;",
        }
        all_imports: Dict[str, None] = {i: None for i in base_imports}
        all_fields:  Dict[str, str]  = {}   # var_name → full declaration line
        methods: List[str] = []

        for frag in fragments:
            parsed = FileTools.parse_method_fragment(frag.get("generated_code", ""))
            for imp in parsed["imports"]:
                all_imports[imp] = None
            for field in parsed["fields"]:
                # Key by last token before the semicolon (the variable name)
                tokens = field.rstrip(";").split()
                var_name = tokens[-1] if tokens else field
                all_fields[var_name] = field
            if parsed["method"]:
                methods.append(parsed["method"])

        lines: List[str] = [
            f"package com.migration.{package_name};",
            "",
        ]
        lines += sorted(all_imports.keys())
        lines += [
            "",
            "@Slf4j",
            "@Service",
            f"public class {class_name} {{",
            "",
        ]
        for field_decl in all_fields.values():
            lines.append(f"    {field_decl}")
        if all_fields:
            lines.append("")
        for method in methods:
            for mline in method.splitlines():
                lines.append(f"    {mline}" if mline.strip() else "")
            lines.append("")
        lines.append("}")

        return "\n".join(lines)

    @staticmethod
    def write_service_class(output_dir: str, program: str, java_class: str) -> str:
        """
        Write an assembled service class to *output_dir*/<ProgramCamelCase>Service.java.
        Returns the path of the written file.
        """
        class_name = _to_camel_case(program) + "Service"
        out_path   = Path(output_dir) / f"{class_name}.java"
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(java_class, encoding="utf-8")
        logger.info("Service class written: %s", out_path)
        return str(out_path)

    @staticmethod
    def collect_companion_files(fragments: List[Dict]) -> Dict[str, str]:
        """
        Scan all method fragments and collect every unique companion file
        (JPA entities, repository interfaces, DTOs) emitted by the LLM.

        Returns a dict mapping filename → file content.
        Last-write wins for duplicate filenames (same entity defined twice).
        """
        result: Dict[str, str] = {}
        for frag in fragments:
            parsed = FileTools.parse_method_fragment(frag.get("generated_code", ""))
            result.update(parsed.get("companion_files", {}))
        return result

    @staticmethod
    def write_companion_files(output_dir: str, companion_files: Dict[str, str]) -> List[str]:
        """
        Write each companion file to *output_dir*.
        Returns the list of paths written.
        """
        root = Path(output_dir)
        root.mkdir(parents=True, exist_ok=True)
        written: List[str] = []
        for filename, content in companion_files.items():
            out_path = root / filename
            out_path.write_text(content, encoding="utf-8")
            logger.info("Companion file written: %s", out_path)
            written.append(str(out_path))
        return written

    @staticmethod
    def write_java_output(
        output_dir: str, program: str, paragraph: str, java_code: str
    ) -> str:
        """
        Write a single paragraph's raw LLM output to the output directory.
        Retained for debug / legacy use — prefer write_service_class for
        the assembled single-class output.
        Returns the path of the written file.
        """
        root = Path(output_dir) / program
        root.mkdir(parents=True, exist_ok=True)
        class_name = _to_camel_case(paragraph)
        out_path = root / f"{class_name}.java"
        out_path.write_text(java_code, encoding="utf-8")
        logger.info("Java output written: %s", out_path)
        return str(out_path)


def _to_camel_case(cobol_name: str) -> str:
    """Convert COBOL-STYLE-NAMES to CamelCase."""
    parts = cobol_name.replace("-", "_").split("_")
    return "".join(p.capitalize() for p in parts if p)
