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
                                  EXCEPTION: if the field is defined inside a COPY copybook
                                  that has a companion @Entity, do NOT declare it as a raw
                                  service field — instead instantiate the @Entity object and
                                  use its setters/getters inside the method body.
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
- PIC X(n)                     → String  (do NOT add trailing-space padding)
- COMP / COMP-4                → int / long
- COMP-3 / packed-decimal      → BigDecimal
- OCCURS n TIMES               → T[] array (0-based index in Java)
- OCCURS DEPENDING ON          → List<T>
- 88-level condition name      → boolean constant or enum

Numeric-edited PIC clauses (display formatting):
- PIC $ZZ,ZZ9.99 / PIC ZZZ,ZZ9 / PIC Z(n)9 etc.
                               → String field in Java (the field holds the FORMATTED string)
  * Count the total digit positions to determine the maximum value the field can hold:
    - Z = zero-suppressed digit position (shows space when zero)
    - 9 = mandatory digit position
    - $ / , / . = insertion characters (not digit positions)
  * Example: PIC $ZZ,ZZ9.99 has 5 digit positions + 2 decimal → max $99,999.99
  * Use a private static final DecimalFormat for the corresponding pattern:
      - Z → '#' in DecimalFormat (optional digit, suppresses leading zeros)
      - 9 → '0' in DecimalFormat (mandatory digit)
      - $ → literal '$', , → ',', . → '.'
      - PIC $ZZ,ZZ9.99 → new DecimalFormat("$#,##0.00")
  * MOVE <numeric> TO <edited-pic-field>:
      this.wsDisplayPay = DISPLAY_PAY_FORMAT.format(wsGross.doubleValue());
  * Declare the formatter as a private static final class constant (NOT inside the method):
      private static final DecimalFormat DISPLAY_PAY_FORMAT = new DecimalFormat("$#,##0.00");
  * Import: import java.text.DecimalFormat;

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
    copybooks: List[str] = None,
    migration_notes: str = "",
    project_config: Dict = None,
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
    copybooks_text = "\n".join(f"  - {cb}" for cb in (copybooks or [])) or "  (none)"

    # Derive Spring Boot naming from the COBOL program name, overridable via project_config
    package_name = program.lower().replace("-", "")
    class_name   = "".join(p.capitalize() for p in program.replace("-", "_").split("_")) + "Service"

    def _to_pascal(name: str) -> str:
        return "".join(w.capitalize() for w in name.replace("-", "_").split("_"))

    prog_cfg = {}
    if project_config:
        for entry in (project_config.get("programs") or []):
            if entry.get("name") == program:
                prog_cfg = entry
                break
        pkg = (project_config.get("target") or {}).get("package") or None
        if pkg:
            package_name = f"{pkg}.{package_name}"
        if prog_cfg.get("target_class"):
            class_name = prog_cfg["target_class"]

    # Build === PROJECT CONTEXT === section — always emit if config exists,
    # falling back to sensible defaults for any null field.
    project_ctx_lines = []
    if project_config:
        pkg = (project_config.get("target") or {}).get("package") or None
        if pkg:
            project_ctx_lines.append(f"  base_package   : {pkg}")

        for call in (prog_cfg.get("external_calls") or []):
            call_prog = call.get("program", "")
            call_type = call.get("type") or "autowired"           # default: autowired
            svc = call.get("service_class") or f"{_to_pascal(call_prog)}Service"  # default: derived
            project_ctx_lines.append(
                f"  external_call  : {call_prog} → {call_type} ({svc})"
            )

        for tbl, entity in (project_config.get("db2_tables") or {}).items():
            entity_name = entity or _to_pascal(tbl)               # default: CamelCase table name
            project_ctx_lines.append(f"  db2_table      : {tbl} → @Entity {entity_name}")

        for override_pic, java_type in (project_config.get("pic_overrides") or {}).items():
            if java_type:
                project_ctx_lines.append(f"  pic_override   : PIC {override_pic} → {java_type}")

        for rule in (prog_cfg.get("business_rules") or []):
            project_ctx_lines.append(f"  business_rule  : {rule}")

    project_ctx_section = (
        "\n=== PROJECT CONTEXT (from migration_config.yaml) ===\n"
        + "\n".join(project_ctx_lines)
        + "\nApply these settings when generating the Java method.\n"
    ) if project_ctx_lines else ""

    return f"""Migrate the following COBOL paragraph to a Spring Boot method fragment.

Target class   : {class_name}
Target package : {package_name}
Program        : {program}
Paragraph      : {para_name}
Intent         : {intent or '(not analysed yet)'}
{f'Notes          : {migration_notes}' if migration_notes else ''}{project_ctx_section}
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

=== COPYBOOKS USED BY THIS PROGRAM ===
{copybooks_text}
Each copybook is a shared data layout (like a COBOL record struct). For every copybook listed:
- ALWAYS generate a ===COMPANION_FILE: <CopyBookName>.java=== annotated with @Entity,
  @Table(name = "<copybook_name_snake_case>"), @Id, @GeneratedValue(strategy = GenerationType.IDENTITY).
- ALWAYS generate a ===COMPANION_FILE: <CopyBookName>Repository.java=== JpaRepository interface.
- ALWAYS @Autowired the repository in ===FIELDS=== of the service.
- Use the copybook name (CamelCase) as the Java class name.
- Include: import jakarta.persistence.*; in the @Entity companion file.
- If a companion for this copybook was already generated by a prior paragraph, skip it.

CRITICAL — use the entity inside the method body:
- Do NOT declare raw service-level fields for data items that come from this copybook.
- Instead, instantiate the @Entity at the start of the method:
    <CopyBookName> record = new <CopyBookName>();
- Use record.set<FieldName>(...) to assign values (MOVE statements).
- Use record.get<FieldName>() to read values (DISPLAY / computations).
- At the end of the method call emprecRepository.save(record) (or appropriate repository method)
  so the populated record is persisted.

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
Copybook-derived @Entity companions are ALWAYS expected (one per COPY statement).
Flag as ERROR only if:
- A copybook is listed but no @Entity companion file was generated.
- A repository is @Autowired in ===FIELDS=== but never called inside the method body.
- The method body assigns copybook fields as raw service-level fields (e.g. this.empId = ...)
  instead of using the entity object's setters (e.g. record.setEmpId(...)).
Do NOT flag @Entity generation as an error — copybook entities are intentional.

Return JSON matching this schema:
{VALIDATION_RESPONSE_SCHEMA}
"""
