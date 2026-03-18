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

All paragraphs from the same COBOL program are assembled into ONE shared @Service class after
migration.  Your output must therefore be a METHOD FRAGMENT — not a standalone class file.

Use EXACTLY these section markers (verbatim) in your output:

// ===IMPORTS===
import java.math.BigDecimal;
// … every import the method body or companion files need, one per line …

// ===FIELDS===
@Autowired
private SomeRepository someRepository;
// … every @Autowired / @Value field declaration the method needs …

// ===METHOD===
/**
 * Javadoc — describe what this COBOL paragraph does.
 * <p>Original COBOL: {@code <PARA-NAME>} in program {@code <PROGRAM>}.
 */
public <ReturnType> <methodName>(<params>) {
    // full implementation — no TODO placeholders
}

// ===COMPANION_FILE: ClassName.java===
package com.migration.programname;
// Complete Java source for a companion file (e.g. @Entity, JpaRepository interface, DTO).
// Include this block ONLY when a new class/interface must be created as a separate file.
// Do NOT generate companion files for standard Spring / JDK classes.
// ===END_COMPANION===

COBOL → Java mapping rules:
- WORKING-STORAGE vars         → private service fields (class-level, NOT method params)
                                  Reason: WORKING-STORAGE persists for the program lifetime,
                                  mapping to instance state in a Spring @Service singleton.
- LINKAGE SECTION items        → method parameters (String, BigDecimal, int …)
- PERFORM <paragraph-name>     → Direct method call: camelCase(paragraphName)()
                                  If the ALREADY MIGRATED Java signature is shown below,
                                  call it with EXACTLY those parameters.
- PERFORM <SECTION-NAME>       → Direct method call: camelCase(sectionName)()
                                  (all methods live in the same @Service class)
- CALL 'EXTERNAL-PGM'          → @Autowired service call / @FeignClient
- EXEC SQL / file I/O (VSAM)   → @Autowired JpaRepository<Entity,Long>
                                  Create a companion file for the @Entity and the Repository
                                  interface if they don't already exist.
- EXEC CICS                    → @Transactional / Spring MVC pattern
- PIC 9(n)                     → int / long
- PIC 9(n)V9(d)                → BigDecimal  (always HALF_UP rounding)
- PIC X(n)                     → String
- COMP / COMP-4                → int / long
- COMP-3 / packed-decimal      → BigDecimal
- OCCURS n TIMES               → T[] array (0-based index in Java)
- OCCURS DEPENDING ON          → List<T>
- 88-level condition name      → boolean constant or enum

FORBIDDEN:
- Do NOT wrap the output in a class declaration
- Do NOT generate inner stub or placeholder methods for PERFORMS targets —
  every PERFORM target is already (or will be) a real method in this class
- Do NOT use markdown fences (``` or ```)
- Do NOT add prose outside the marker sections
- Do NOT resolve ALTER statement targets statically.
  ALTER is a runtime mechanism — the COBOL source is authoritative.
  If the source says PERFORM OLD-PARAGRAPH, emit oldParagraph() exactly once.
  Add a comment noting the ALTER concern, but never substitute a different
  method or omit the call that is written in the source.

Return ONLY the marker sections and their content.
"""


def _extract_java_signature(generated_code: str) -> str:
    """
    Extract the public/private method signature line(s) from already-migrated Java code.
    Returns a concise signature string for use in the migration prompt context.
    """
    import re
    if not generated_code:
        return ""
    # Strip markers and fences
    code = generated_code.replace("```java", "").replace("```", "")
    # Find the first method signature: optional access modifier + return type + name + params
    m = re.search(
        r'((?:public|private|protected)\s+(?:static\s+)?[\w<>\[\],\s]+\s+\w+\s*\([^)]*\))',
        code,
        re.MULTILINE,
    )
    return m.group(1).strip() if m else ""


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
    # For each PERFORM target, show the Java signature if already migrated,
    # otherwise fall back to the COBOL source so the LLM has context.
    def _performs_entry(p: Dict) -> str:
        name = p.get("name", "")
        if not name:
            return ""
        gen_code = p.get("generated_code", "") or ""
        sig = _extract_java_signature(gen_code) if gen_code else ""
        if sig:
            return f"  - {name}  [ALREADY MIGRATED — Java signature: {sig}]"
        cobol = (p.get("source_code", "") or "")[:300]
        return f"  - {name}: {cobol}" if cobol else f"  - {name}"

    performs_text = "\n".join(
        e for p in performs if (e := _performs_entry(p))
    ) or "  (none)"

    reads_text = "\n".join(
        f"  - {r['name']} : {r.get('pic_type', '?')}"
        for r in reads if r.get("name")
    ) or "  (none)"

    writes_text = "\n".join(
        f"  - {w['name']} : {w.get('pic_type', '?')}"
        for w in writes if w.get("name")
    ) or "  (none)"

    calls_text  = "\n".join(f"  - {c}" for c in external_calls) or "  (none)"
    tables_text = "\n".join(f"  - {t}" for t in sql_tables) or "  (none)"
    shared_text = "\n".join(f"  - {s}" for s in shared_state_items) or "  (none)"

    # Derive Spring Boot naming from the COBOL program name
    package_name = program.lower().replace("-", "")
    class_name   = "".join(p.capitalize() for p in program.replace("-", "_").split("_")) + "Service"

    return f"""Migrate the following COBOL paragraph to a Spring Boot method fragment.

Target class   : {class_name}
Target package : com.migration.{package_name}
Program        : {program}
Paragraph      : {para_name}
Intent         : {intent or '(not analysed yet)'}
{f'Notes     : {migration_notes}' if migration_notes else ''}

=== COBOL SOURCE ===
{source_code or '(source not available)'}

=== PERFORMS (child paragraphs — call as methods in the same class) ===
{performs_text}
NOTE: If a child shows [ALREADY MIGRATED], call it using EXACTLY that Java signature.
Do NOT re-implement its logic here.

=== DATA ITEMS READ (WORKING-STORAGE) ===
{reads_text}

=== DATA ITEMS WRITTEN (WORKING-STORAGE) ===
{writes_text}

=== SHARED STATE (items crossing paragraph boundaries) ===
{shared_text}
(Map these to private service fields — NOT method parameters)

=== EXTERNAL PROGRAM CALLS ===
{calls_text}

=== DB2 TABLES / FILES ACCESSED ===
{tables_text}

Generate the method fragment now.
"""


# ================================================================== #
#  VALIDATION PROMPTS                                                 #
# ================================================================== #

VALIDATION_SYSTEM_PROMPT = """You are a senior Java code reviewer specialising in COBOL migration.
Review the generated Java method against the original COBOL source and return a structured JSON report.

CRITICAL OUTPUT RULES — you MUST follow these exactly:
- Output ONLY the raw JSON object. Nothing else.
- Do NOT wrap the JSON in markdown code fences (no ```json, no ```)
- Do NOT add any explanation, preamble, or trailing text
- The very first character of your response must be `{`
- The very last character of your response must be `}`
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

=== EXPECTED PERFORM CALLS (must appear as direct method calls — no stub bodies) ===
{performs_text}

=== EXPECTED DATA ITEMS (must appear as service fields in ===FIELDS===, method parameters, or local variables) ===
{items_text}

=== COMPANION FILES ===
If the COBOL accesses DB2 tables or VSAM files, expect a ===COMPANION_FILE: ...=== block
containing a JPA @Entity and/or JpaRepository interface. Flag as ERROR if tables/files
are used but no companion file was generated.

Return JSON matching this schema:
{VALIDATION_RESPONSE_SCHEMA}
"""
