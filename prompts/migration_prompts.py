"""
Prompt templates for the COBOL → Java Spring Boot migration.

Three prompt pairs are defined:
  1. Analysis  — understand a paragraph's intent and complexity
  2. Migration — translate a paragraph to Java Spring Boot
  3. Validation — review generated Java code for correctness
"""

from typing import Dict, List

# ================================================================== #
#  ANALYSIS PROMPTS                                                   #
# ================================================================== #

ANALYSIS_SYSTEM_PROMPT = """You are an expert COBOL analyst and Java architect.
Your job is to analyse a COBOL paragraph and produce a structured JSON report
that will guide the migration agent.

Return ONLY valid JSON — no markdown fences, no extra text.
"""

ANALYSIS_RESPONSE_SCHEMA = """{
  "intent": "<one-sentence description of what this paragraph does>",
  "complexity": "LOW | MEDIUM | HIGH",
  "has_io": true | false,
  "has_sql": true | false,
  "has_cics": true | false,
  "shared_state_items": ["<WS-VAR-1>", ...],
  "migration_notes": "<important notes for the migration agent>"
}"""


def build_analysis_prompt(
    para_name: str,
    program: str,
    source_code: str,
    performs: List[Dict],
    reads: List[Dict],
    writes: List[Dict],
    external_calls: List[str],
) -> str:
    performs_text = "\n".join(
        f"  - {p['name']}: {p.get('source_code', '(no source)')[:200]}"
        for p in performs if p.get("name")
    ) or "  (none)"

    reads_text = "\n".join(
        f"  - {r['name']} [{r.get('pic_type', '?')}]"
        for r in reads if r.get("name")
    ) or "  (none)"

    writes_text = "\n".join(
        f"  - {w['name']} [{w.get('pic_type', '?')}]"
        for w in writes if w.get("name")
    ) or "  (none)"

    calls_text = "\n".join(f"  - {c}" for c in external_calls) or "  (none)"

    return f"""Analyse the following COBOL paragraph and return the JSON schema below.

Program : {program}
Paragraph: {para_name}

=== COBOL SOURCE ===
{source_code or '(source not available)'}

=== PERFORMS (child paragraphs) ===
{performs_text}

=== DATA ITEMS READ ===
{reads_text}

=== DATA ITEMS WRITTEN ===
{writes_text}

=== EXTERNAL PROGRAM CALLS ===
{calls_text}

Return JSON matching this schema:
{ANALYSIS_RESPONSE_SCHEMA}
"""


# ================================================================== #
#  MIGRATION PROMPTS                                                  #
# ================================================================== #

MIGRATION_SYSTEM_PROMPT = """You are an expert COBOL to Java Spring Boot migration engineer.
You receive a single COBOL paragraph with its full dependency context extracted from a Neo4j graph.

Generate a COMPLETE, compilable Spring Boot source file.
Do NOT generate skeleton or stub methods — every method must be fully implemented.

Required output structure (replace angle-bracket placeholders with real values):
─────────────────────────────────────────────────────────────────────────────────
package com.migration.<program_lower>;

import java.math.BigDecimal;
import java.math.RoundingMode;
// … include every import the method body actually needs …
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class <ProgramCamelCase>Service {

    // @Autowired repository / service fields required by this method, e.g.:
    // @Autowired private PolicyRepository policyRepository;

    /**
     * Javadoc — describe what this COBOL paragraph does.
     * <p>Original COBOL: paragraph {@code <PARA-NAME>} in program {@code <PROGRAM>}.
     */
    public <ReturnType> <methodName>(<params>) {
        // full implementation — no TODO placeholders
    }
}
─────────────────────────────────────────────────────────────────────────────────

COBOL → Java mapping rules:
- LINKAGE SECTION items        → method parameters (String, BigDecimal, int …)
- WORKING-STORAGE shared vars  → explicit method parameters or a return value object
- PERFORM <paragraph-name>     → Java method call camelCase(<paragraph-name>)()
- PERFORM <SECTION-NAME>       → Java method call camelCase(<SECTION-NAME>)()
                                  (section-wrapper methods are generated as separate
                                   paragraphs — do NOT inline their body here)
- CALL 'EXTERNAL-PGM'          → @Autowired service call / @FeignClient
- EXEC SQL                     → @Autowired JpaRepository<Entity,Long> method call
- EXEC CICS                    → @Transactional / Spring MVC pattern
- PIC 9(n)                     → int / long
- PIC 9(n)V9(d)                → BigDecimal  (always HALF_UP rounding)
- PIC X(n)                     → String
- COMP / COMP-4                → int / long
- COMP-3 / packed-decimal      → BigDecimal
- OCCURS n TIMES               → T[] array (0-based index in Java)
- OCCURS DEPENDING ON          → List<T>
- 88-level condition name      → boolean constant or enum

FORBIDDEN — do NOT generate any of these:
- initFilesAndData(), openFiles(), initWorkingStorage(), or similar void init stubs
- Unimplemented TODO method bodies
- Markdown code fences (``` or ```)
- Any prose explanation outside the Java source

Return ONLY the valid Java source code for the complete class file.
"""


def build_migration_prompt(
    para_name: str,
    program: str,
    source_code: str,
    intent: str,
    performs: List[Dict],
    reads: List[Dict],
    writes: List[Dict],
    external_calls: List[str],
    sql_tables: List[str],
    shared_state_items: List[str],
    migration_notes: str = "",
) -> str:
    performs_text = "\n".join(
        f"  - {p['name']}: {p.get('source_code', '')[:300]}"
        for p in performs if p.get("name")
    ) or "  (none)"

    reads_text = "\n".join(
        f"  - {r['name']} : {r.get('pic_type', '?')}"
        for r in reads if r.get("name")
    ) or "  (none)"

    writes_text = "\n".join(
        f"  - {w['name']} : {w.get('pic_type', '?')}"
        for w in writes if w.get("name")
    ) or "  (none)"

    calls_text = "\n".join(f"  - {c}" for c in external_calls) or "  (none)"
    tables_text = "\n".join(f"  - {t}" for t in sql_tables) or "  (none)"
    shared_text = "\n".join(f"  - {s}" for s in shared_state_items) or "  (none)"

    # Derive Spring Boot naming from the COBOL program name
    package_name = program.lower().replace("-", "")
    class_name   = "".join(p.capitalize() for p in program.replace("-", "_").split("_")) + "Service"

    return f"""Migrate the following COBOL paragraph to a complete Spring Boot source file.

Target class   : {class_name}
Target package : com.migration.{package_name}
Program        : {program}
Paragraph      : {para_name}
Intent         : {intent or '(not analysed yet)'}
{f'Notes     : {migration_notes}' if migration_notes else ''}

=== COBOL SOURCE ===
{source_code or '(source not available)'}

=== PERFORMS (child paragraph source — already migrated) ===
{performs_text}

=== DATA ITEMS READ (WORKING-STORAGE) ===
{reads_text}

=== DATA ITEMS WRITTEN (WORKING-STORAGE) ===
{writes_text}

=== SHARED STATE (items crossing paragraph boundaries) ===
{shared_text}
(These must become explicit method parameters or return values in Java)

=== EXTERNAL PROGRAM CALLS ===
{calls_text}

=== DB2 TABLES ACCESSED ===
{tables_text}

Generate the Java method now.
"""


# ================================================================== #
#  VALIDATION PROMPTS                                                 #
# ================================================================== #

VALIDATION_SYSTEM_PROMPT = """You are a senior Java code reviewer specialising in COBOL migration.
Review the generated Java method against the original COBOL source and return a structured JSON report.

Return ONLY valid JSON — no markdown fences, no extra text.
"""

VALIDATION_RESPONSE_SCHEMA = """{
  "pass": true | false,
  "issues": [
    {"severity": "ERROR | WARNING | INFO", "description": "<issue description>"}
  ],
  "missing_performs": ["<para-name>", ...],
  "missing_data_items": ["<item-name>", ...],
  "compiles": true | false | null,
  "suggestions": "<optional improvement suggestions>"
}"""


def build_validation_prompt(
    para_name: str,
    program: str,
    cobol_source: str,
    java_code: str,
    expected_performs: List[str],
    expected_data_items: List[str],
    failure_reason: str = "",
) -> str:
    performs_text = "\n".join(f"  - {p}" for p in expected_performs) or "  (none)"
    items_text = "\n".join(f"  - {i}" for i in expected_data_items) or "  (none)"
    failure_text = f"\nPrevious failure reason: {failure_reason}" if failure_reason else ""

    return f"""Review the generated Java code against the original COBOL paragraph.{failure_text}

Program   : {program}
Paragraph : {para_name}

=== ORIGINAL COBOL ===
{cobol_source or '(source not available)'}

=== GENERATED JAVA ===
{java_code or '(no code generated)'}

=== EXPECTED PERFORM CALLS (must appear as method calls) ===
{performs_text}

=== EXPECTED DATA ITEMS (must appear as parameters or locals) ===
{items_text}

Return JSON matching this schema:
{VALIDATION_RESPONSE_SCHEMA}
"""
