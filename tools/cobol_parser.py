"""
COBOL source parser — extracts paragraph structure, PERFORM/CALL relationships,
and WORKING-STORAGE data items directly from .cbl / .cob source files.

Supports both IBM mainframe **fixed-format** and **free-format** COBOL:

  Fixed-format layout (most mainframe COBOL):
    cols  1-6  : sequence number (ignored)
    col   7    : indicator  (* or / = comment, - = continuation, space = normal)
    cols  8-11 : Area A  (division/section/paragraph headers, 01/77 level items)
    cols 12-72 : Area B  (statements, subordinate data items)

  Free-format layout:
    col   7    : NOT an indicator — treated as content
    Comments   : lines starting with *> or //

Format is auto-detected per file from the first non-blank line.
"""

import logging
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Tuple

logger = logging.getLogger(__name__)


# ──────────────────────────────────────────────────────────────────────────────
# Data-classes
# ──────────────────────────────────────────────────────────────────────────────

@dataclass
class ParagraphInfo:
    """One COBOL paragraph — maps to a single Java method in the target."""
    name: str
    start_line: int
    end_line: int
    source_lines: List[str] = field(default_factory=list)
    performs: List[str] = field(default_factory=list)  # PERFORMed paragraph names
    calls: List[str] = field(default_factory=list)     # CALLed external programs
    reads: List[str] = field(default_factory=list)     # DataItems read (MOVE … FROM)
    writes: List[str] = field(default_factory=list)    # DataItems written (MOVE … TO / COMPUTE)
    section: str = ""                # COBOL SECTION this paragraph belongs to
    is_section_entry: bool = False   # True for synthetic section-wrapper paragraphs

    @property
    def source_code(self) -> str:
        return "".join(self.source_lines)


@dataclass
class DataItemInfo:
    """One entry from WORKING-STORAGE / LINKAGE SECTION."""
    name: str
    level: int
    pic: str = ""


@dataclass
class CobolParseResult:
    """Full parse output for one COBOL source file."""
    program_name: str = ""
    source_path: str = ""
    paragraphs: List[ParagraphInfo] = field(default_factory=list)
    data_items: List[DataItemInfo] = field(default_factory=list)


# ──────────────────────────────────────────────────────────────────────────────
# Module-level constants
# ──────────────────────────────────────────────────────────────────────────────

# COBOL keywords that can legally follow PERFORM — NOT paragraph names.
_PERFORM_KEYWORDS = frozenset({
    "UNTIL", "VARYING", "TIMES", "WITH", "TEST", "BEFORE", "AFTER",
    "THROUGH", "THRU", "INLINE", "END-PERFORM",
})

# Figurative constants used in MOVE/COMPUTE — not DataItem names.
_FIGURATIVE = frozenset({
    "SPACES", "SPACE", "ZEROS", "ZERO", "ZEROES",
    "HIGH-VALUES", "HIGH-VALUE", "LOW-VALUES", "LOW-VALUE",
    "QUOTES", "QUOTE", "ALL", "NULL", "NULLS", "TRUE", "FALSE",
})


# ──────────────────────────────────────────────────────────────────────────────
# Parser
# ──────────────────────────────────────────────────────────────────────────────

class CobolParser:
    """
    Line-based COBOL parser.

    Usage::

        result = CobolParser().parse("path/to/MEGADEMO.cbl")
        for p in result.paragraphs:
            print(p.name, p.start_line, "→", p.end_line)
            print("  performs:", p.performs)
            print("  calls   :", p.calls)
    """

    # ── Compiled patterns ────────────────────────────────────────────── #

    # Paragraph header: a COBOL name followed ONLY by a period (no keywords after it).
    # The regex is applied against the stripped statement text.
    _PARA_HEADER = re.compile(r'^([A-Z0-9][A-Z0-9-]*)\s*\.\s*$', re.IGNORECASE)

    # PROCEDURE DIVISION section header: NAME SECTION.
    # Must be matched separately because _PARA_HEADER only matches "NAME." forms.
    _SECT_HEADER = re.compile(r'^([A-Z0-9][A-Z0-9-]+)\s+SECTION\s*\.\s*$', re.IGNORECASE)

    # Exclude lines that look like paragraph headers but are not.
    _DIV_OR_SECT = re.compile(
        r'\b(?:DIVISION|SECTION|DECLARATIVES|END\s+DECLARATIVES)\b',
        re.IGNORECASE,
    )

    # Division / section boundary markers (searched against stripped body).
    _IDENTIFICATION_DIV = re.compile(r'\bIDENTIFICATION\s+DIVISION\b', re.IGNORECASE)
    _ENVIRONMENT_DIV    = re.compile(r'\bENVIRONMENT\s+DIVISION\b',    re.IGNORECASE)
    _DATA_DIV           = re.compile(r'\bDATA\s+DIVISION\b',           re.IGNORECASE)
    _PROCEDURE_DIV      = re.compile(r'\bPROCEDURE\s+DIVISION\b',      re.IGNORECASE)
    _WORKING_STORAGE    = re.compile(r'\bWORKING-STORAGE\s+SECTION\b', re.IGNORECASE)
    _LINKAGE_SECTION    = re.compile(r'\bLINKAGE\s+SECTION\b',         re.IGNORECASE)
    _LOCAL_STORAGE      = re.compile(r'\bLOCAL-STORAGE\s+SECTION\b',   re.IGNORECASE)
    _FILE_SECTION       = re.compile(r'\bFILE\s+SECTION\b',            re.IGNORECASE)

    # PROGRAM-ID.  <name>
    _PROGRAM_ID = re.compile(
        r'\bPROGRAM-ID\s*\.\s*([A-Z0-9][A-Z0-9-]*)', re.IGNORECASE
    )

    # Data item: level  name  [PIC[TURE] [IS] clause]
    _DATA_ITEM = re.compile(
        r'^(\d{1,2})\s+([A-Z0-9][A-Z0-9-]*)'
        r'(?:\s+PIC(?:TURE)?\s+(?:IS\s+)?([^\s.,]+))?',
        re.IGNORECASE,
    )

    # PERFORM <name> [THROUGH/THRU <name>] [UNTIL/VARYING/TIMES …]
    _PERFORM_THRU = re.compile(
        r'\bPERFORM\s+([A-Z0-9][A-Z0-9-]*)\s+(?:THROUGH|THRU)\s+([A-Z0-9][A-Z0-9-]*)',
        re.IGNORECASE,
    )
    _PERFORM = re.compile(r'\bPERFORM\s+([A-Z0-9][A-Z0-9-]*)', re.IGNORECASE)

    # CALL 'literal'  or  CALL identifier
    _CALL_LITERAL = re.compile(r'\bCALL\s+["\']([^"\']+)["\']', re.IGNORECASE)

    # MOVE src TO dest  (single-target form only)
    _MOVE_TO = re.compile(
        r'\bMOVE\s+([A-Z0-9][A-Z0-9-]*)\s+TO\s+([A-Z0-9][A-Z0-9-]*)',
        re.IGNORECASE,
    )

    # ── Public API ────────────────────────────────────────────────────── #

    def parse(self, cobol_path: str) -> CobolParseResult:
        """Parse *cobol_path* and return a :class:`CobolParseResult`."""
        path = Path(cobol_path)
        if not path.exists():
            logger.warning("COBOL file not found: %s", cobol_path)
            return CobolParseResult(source_path=str(path))

        raw_lines = path.read_text(errors="replace").splitlines(keepends=True)
        fixed = self._is_fixed_format(raw_lines)

        result = self._parse_lines(raw_lines, fixed_format=fixed)
        result.source_path = str(path)

        logger.info(
            "Parsed %-30s  format=%-5s  program=%-12s  paragraphs=%d  data_items=%d",
            path.name,
            "fixed" if fixed else "free",
            result.program_name or "(unknown)",
            len(result.paragraphs),
            len(result.data_items),
        )
        return result

    # ── Format detection ─────────────────────────────────────────────── #

    @staticmethod
    def _is_fixed_format(lines: List[str]) -> bool:
        """
        Heuristic: fixed-format if the majority of non-blank, non-trivial
        lines have col 7 (index 6) as a space, *, /, or - indicator AND
        the line is at least 7 characters wide.
        """
        votes_fixed = votes_free = 0
        for line in lines[:50]:
            stripped = line.rstrip("\n").rstrip("\r")
            if not stripped.strip():
                continue
            if len(stripped) >= 7:
                col7 = stripped[6]
                if col7 in (" ", "*", "/", "-", "D", "d"):
                    votes_fixed += 1
                else:
                    votes_free += 1
            else:
                votes_free += 1
        return votes_fixed >= votes_free

    # ── Line body extraction ─────────────────────────────────────────── #

    @staticmethod
    def _is_fixed_comment(line: str) -> bool:
        return len(line) > 6 and line[6] in ("*", "/")

    @staticmethod
    def _fixed_body(line: str) -> str:
        """Extract Area A+B (cols 8-72, index 7-71) for fixed-format lines."""
        return line[7:72] if len(line) > 7 else ""

    @staticmethod
    def _free_body(line: str) -> str:
        """Return content for free-format lines (strip leading whitespace only)."""
        return line.strip()

    @staticmethod
    def _is_free_comment(body: str) -> bool:
        return body.startswith("*>") or body.startswith("//")

    # ── Core parse loop ──────────────────────────────────────────────── #

    def _parse_lines(self, lines: List[str], fixed_format: bool) -> CobolParseResult:
        result = CobolParseResult()

        in_data_div        = False
        in_working_storage = False
        in_procedure_div   = False
        current_para: Optional[ParagraphInfo] = None
        current_section: str = ""   # tracks the current PROCEDURE DIVISION section name

        def close_para(end_line: int) -> None:
            nonlocal current_para
            if current_para:
                current_para.end_line = end_line
                result.paragraphs.append(current_para)
                current_para = None

        for lineno, raw_line in enumerate(lines, start=1):
            line = raw_line.rstrip("\n").rstrip("\r")

            # ── Blank lines ────────────────────────────────────────────
            if not line.strip():
                if current_para:
                    current_para.source_lines.append(raw_line)
                continue

            # ── Comment detection & body extraction ────────────────────
            if fixed_format:
                if self._is_fixed_comment(line):
                    if current_para:
                        current_para.source_lines.append(raw_line)
                    continue
                body = self._fixed_body(line)
                # If Area A+B is empty, the useful content may be in col 1-6
                # (rare, but handle gracefully by falling back to full strip).
                stmt = body.strip() or line.strip()
            else:
                stmt = self._free_body(line)
                if self._is_free_comment(stmt):
                    if current_para:
                        current_para.source_lines.append(raw_line)
                    continue

            if not stmt:
                if current_para:
                    current_para.source_lines.append(raw_line)
                continue

            # ── Division / section boundary detection ──────────────────
            if self._IDENTIFICATION_DIV.search(stmt):
                close_para(lineno - 1)
                in_data_div = in_working_storage = in_procedure_div = False
                continue

            if self._ENVIRONMENT_DIV.search(stmt):
                close_para(lineno - 1)
                in_data_div = in_working_storage = in_procedure_div = False
                continue

            if self._DATA_DIV.search(stmt):
                close_para(lineno - 1)
                in_data_div = True
                in_working_storage = False
                in_procedure_div = False
                continue

            if self._PROCEDURE_DIV.search(stmt):
                close_para(lineno - 1)
                in_procedure_div = True
                in_data_div = False
                in_working_storage = False
                continue

            if in_data_div:
                if self._WORKING_STORAGE.search(stmt):
                    in_working_storage = True
                    continue
                if (self._LINKAGE_SECTION.search(stmt)
                        or self._LOCAL_STORAGE.search(stmt)
                        or self._FILE_SECTION.search(stmt)):
                    in_working_storage = False
                    continue

            # ── PROGRAM-ID (IDENTIFICATION DIVISION) ───────────────────
            if not in_procedure_div and not result.program_name:
                m = self._PROGRAM_ID.search(stmt)
                if m:
                    result.program_name = m.group(1).upper()

            # ── DATA DIVISION: collect WORKING-STORAGE items ───────────
            if in_working_storage:
                m = self._DATA_ITEM.match(stmt)
                if m:
                    level = int(m.group(1))
                    name  = m.group(2).upper()
                    pic   = (m.group(3) or "").upper()
                    # Skip FILLER, condition-names (88), and rename items (66)
                    if name != "FILLER" and level not in (66, 88):
                        result.data_items.append(
                            DataItemInfo(name=name, level=level, pic=pic)
                        )
                continue

            # ── PROCEDURE DIVISION: paragraph headers & statements ──────
            if not in_procedure_div:
                continue

            # PROCEDURE DIVISION section header? (e.g. "INIT-FILES-AND-DATA SECTION.")
            # Must be checked before _PARA_HEADER because _PARA_HEADER only matches
            # "NAME." forms and would miss "NAME SECTION." entirely.
            sec_m = self._SECT_HEADER.match(stmt)
            if sec_m:
                close_para(lineno - 1)
                current_section = sec_m.group(1).upper()
                continue

            # Paragraph header? (a COBOL name followed only by a period)
            m = self._PARA_HEADER.match(stmt)
            if m and not self._DIV_OR_SECT.search(stmt):
                close_para(lineno - 1)
                current_para = ParagraphInfo(
                    name=m.group(1).upper(),
                    start_line=lineno,
                    end_line=lineno,
                    source_lines=[raw_line],
                    section=current_section,
                )
                continue

            # Accumulate raw source and extract relationships
            if current_para:
                current_para.source_lines.append(raw_line)
                self._extract_stmt(stmt, current_para)

        close_para(len(lines))
        self._create_section_entries(result)
        return result

    # ── Section-wrapper synthesis ─────────────────────────────────────── #

    @staticmethod
    def _create_section_entries(result: CobolParseResult) -> None:
        """
        Create synthetic section-entry ParagraphInfos for each PROCEDURE DIVISION
        SECTION encountered during parsing.

        In COBOL, ``PERFORM SECTION-NAME`` executes every paragraph in the named
        section sequentially through EXIT SECTION.  Without a matching Paragraph
        node the graph would contain a dangling PERFORMS reference and the
        Migration Agent would generate a call to a non-existent Java method.

        The synthetic paragraph:
        - Has ``name = SECTION-NAME`` (e.g. ``INIT-FILES-AND-DATA``)
        - Has ``performs = [all paragraphs in section, in order]``
        - Has a synthetic COBOL source showing only the PERFORM delegation chain
          (prevents the LLM from inlining section-body logic instead of delegating)
        - Has ``is_section_entry = True`` to allow the importer to pre-classify it
        """
        from collections import OrderedDict

        # Preserve parse order: group paragraphs by section
        sections: "OrderedDict[str, List[ParagraphInfo]]" = OrderedDict()
        for para in result.paragraphs:
            if para.section:
                sections.setdefault(para.section, []).append(para)

        for section_name, paras in sections.items():
            if not paras:
                continue
            wrapper = ParagraphInfo(
                name=section_name,
                start_line=paras[0].start_line,
                end_line=paras[-1].end_line,
                section="",
                is_section_entry=True,
            )
            # All paragraphs in section, in source order — they execute sequentially.
            wrapper.performs = [p.name for p in paras]

            # Synthetic COBOL source: shows ONLY the delegation chain.
            # This is critical — if we leave source_lines empty, the LLM receives
            # child paragraph COBOL source via the 'performs' context and INLINES
            # that logic into this method instead of calling the child method.
            # With an explicit PERFORM chain here, the LLM sees only delegation.
            perform_stmts = "".join(
                f"    PERFORM {p.name}.\n" for p in paras
            )
            wrapper.source_lines = [
                f"{section_name} SECTION.\n",
                f"*> Section entry — delegates to constituent paragraphs in order.\n",
                perform_stmts,
                f"    EXIT SECTION.\n",
            ]
            result.paragraphs.append(wrapper)
            logger.debug("Synthetic section entry created: %s → %s", section_name, paras[0].name)

    # ── Relationship extraction ──────────────────────────────────────── #

    def _extract_stmt(self, stmt: str, para: ParagraphInfo) -> None:
        """Extract PERFORM / CALL / MOVE targets from a single statement."""

        # PERFORM … THROUGH/THRU <end-para>: both ends are dependencies
        for m in self._PERFORM_THRU.finditer(stmt):
            for grp in (1, 2):
                target = m.group(grp).upper()
                if target not in para.performs:
                    para.performs.append(target)

        # Plain PERFORM <name> (catches remaining ones after THRU was handled)
        for m in self._PERFORM.finditer(stmt):
            target = m.group(1).upper()
            if target in _PERFORM_KEYWORDS:
                continue
            if target not in para.performs:
                para.performs.append(target)

        # CALL 'literal'
        for m in self._CALL_LITERAL.finditer(stmt):
            called = m.group(1).upper().strip()
            if called and called not in para.calls:
                para.calls.append(called)

        # MOVE src TO dest  — light data-flow tracking
        for m in self._MOVE_TO.finditer(stmt):
            src  = m.group(1).upper()
            dest = m.group(2).upper()
            if src not in _FIGURATIVE and src not in para.reads:
                para.reads.append(src)
            if dest not in _FIGURATIVE and dest not in para.writes:
                para.writes.append(dest)
