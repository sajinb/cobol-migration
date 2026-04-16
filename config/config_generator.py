"""
Generate a migration_config.yaml skeleton from the Neo4j graph.

Auto-populated from the graph (after ingest):
  - program names, file paths
  - copybook dependencies
  - external CALL targets
  - DataItem names and PIC types

Null placeholders require human input:
  - target Java package
  - external call integration type (autowired / feignclient / rest)
  - DB2 table → JPA entity class name
  - custom PIC type overrides
  - business rules (compliance, rounding, encoding notes)
"""

from pathlib import Path

import yaml

from tools.neo4j_tools import Neo4jTools


def generate(output_path: str = "./migration_config.yaml") -> dict:
    """Query Neo4j and write a YAML skeleton to *output_path*. Returns the config dict."""
    neo4j = Neo4jTools()
    try:
        data = _build_config(neo4j)
    finally:
        neo4j.close()

    _write_yaml(data, output_path)
    return data


def _build_config(neo4j: Neo4jTools) -> dict:
    programs_data = []

    prog_rows = neo4j.query(
        "MATCH (p:Program) RETURN p.name AS name, p.file_path AS file ORDER BY p.name"
    )

    for row in prog_rows:
        prog_name = row["name"]
        file_path = row["file"] or ""

        cb_rows = neo4j.query(
            "MATCH (p:Program {name: $prog})-[:COPIES]->(c:Copybook) "
            "RETURN c.name AS name ORDER BY c.name",
            {"prog": prog_name},
        )
        copybooks = [r["name"] for r in cb_rows]

        call_rows = neo4j.query(
            """
            MATCH (pg:Program {name: $prog})-[:HAS_PARAGRAPH]->(par:Paragraph)
                  -[:CALLS]->(ext:Program)
            RETURN DISTINCT ext.name AS name ORDER BY ext.name
            """,
            {"prog": prog_name},
        )
        external_calls = [
            {
                "program": r["name"],
                "type": None,           # autowired | feignclient | rest
                "service_class": None,  # e.g. PolicyValidationService
            }
            for r in call_rows
        ]

        programs_data.append({
            "name": prog_name,
            "file": file_path,
            "target_class": None,       # e.g. EmployeeService
            "copybooks": copybooks,
            "external_calls": external_calls,
            "business_rules": [],       # free-text hints injected into the LLM migration prompt
        })

    table_rows = neo4j.query(
        "MATCH (par:Paragraph)-[:EXECUTES_SQL]->(t:Table) "
        "RETURN DISTINCT t.name AS name ORDER BY t.name"
    )
    db2_tables = {r["name"]: None for r in table_rows}

    return {
        "target": {
            "package": None,    # e.g. com.mybank.payroll
        },
        "programs": programs_data,
        "db2_tables": db2_tables if db2_tables else {},
        "pic_overrides": {},    # e.g. "9(18)V9(2)": "BigDecimal"
    }


def _write_yaml(data: dict, output_path: str) -> None:
    """Write config dict to YAML with an explanatory header comment block."""
    raw = yaml.dump(data, default_flow_style=False, sort_keys=False, allow_unicode=True)

    header = """\
# migration_config.yaml — auto-generated skeleton
# ================================================
# Fields marked with "null" are OPTIONAL — the pipeline runs with sensible defaults
# if you leave them as-is. Fill them in to get more accurate Java output.
#
# Defaults used when a field is null:
#   target.package      → com.migration.<programname>
#   target_class        → <ProgramName>Service  (derived from COBOL program name)
#   external_calls.type → autowired
#   external_calls.service_class → <CalledProgram>Service  (derived)
#   db2_tables.<TABLE>  → <TableName>  (CamelCase of the table name)
#
# To improve accuracy, fill in:
#   1. target.package      — your Java base package (e.g. com.mybank.payroll)
#   2. programs[].target_class — the Spring @Service class name (e.g. EmployeeService)
#   3. external_calls[].type:
#        autowired   — same Spring context, injected via @Autowired
#        feignclient — crosses a service boundary (Feign / REST client)
#        rest        — plain RestTemplate / WebClient call
#      external_calls[].service_class — simple or fully-qualified class name
#   4. db2_tables — map each DB2/VSAM table to a JPA @Entity class name
#   5. pic_overrides — override the default PIC → Java type if needed
#        e.g.  "9(18)V9(2)": "BigDecimal"
#   6. business_rules — free-text migration hints per program (the LLM sees these)
#        e.g. compliance rules, rounding conventions, encoding notes

"""
    Path(output_path).parent.mkdir(parents=True, exist_ok=True)
    Path(output_path).write_text(header + raw, encoding="utf-8")
    null_count = raw.count(": null")
    print(f"Written: {output_path}")
    print(f"  {null_count} field(s) require human input (marked as 'null')")
