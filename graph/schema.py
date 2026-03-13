"""
Neo4j schema definitions — constraints and indexes for the COBOL migration graph.

Node labels:
  Program    — one per .cbl file
  Paragraph  — named block of COBOL code (maps to a Java method)
  DataItem   — WORKING-STORAGE / LINKAGE SECTION variable
  Copybook   — shared .cpy file
  Table      — DB2 table referenced via EXEC SQL

Relationships:
  (Program)-[:HAS_PARAGRAPH]->(Paragraph)
  (Program)-[:COPIES]->(Copybook)
  (Program)-[:CALLS]->(Program)
  (Paragraph)-[:PERFORMS]->(Paragraph)
  (Paragraph)-[:READS]->(DataItem)
  (Paragraph)-[:WRITES]->(DataItem)
  (Paragraph)-[:CALLS]->(Program)
  (Paragraph)-[:EXECUTES_SQL]->(Table)
  (Program)-[:HAS_DATA_ITEM]->(DataItem)
"""

import logging
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from tools.neo4j_tools import Neo4jTools

logger = logging.getLogger(__name__)

# ------------------------------------------------------------------ #
#  Uniqueness constraints                                             #
# ------------------------------------------------------------------ #
SCHEMA_CONSTRAINTS = [
    "CREATE CONSTRAINT program_name IF NOT EXISTS FOR (p:Program) REQUIRE p.name IS UNIQUE",
    "CREATE CONSTRAINT copybook_name IF NOT EXISTS FOR (c:Copybook) REQUIRE c.name IS UNIQUE",
    "CREATE CONSTRAINT table_name IF NOT EXISTS FOR (t:Table) REQUIRE t.name IS UNIQUE",
    # Paragraphs are unique within a program (composite key)
    "CREATE CONSTRAINT para_name_program IF NOT EXISTS FOR (p:Paragraph) REQUIRE (p.name, p.program) IS UNIQUE",
    # DataItems are unique within a program
    "CREATE CONSTRAINT data_item_name_program IF NOT EXISTS FOR (d:DataItem) REQUIRE (d.name, d.program) IS UNIQUE",
]

# ------------------------------------------------------------------ #
#  Lookup indexes                                                     #
# ------------------------------------------------------------------ #
SCHEMA_INDEXES = [
    "CREATE INDEX para_status IF NOT EXISTS FOR (p:Paragraph) ON (p.status)",
    "CREATE INDEX program_status IF NOT EXISTS FOR (p:Program) ON (p.status)",
    "CREATE INDEX para_program IF NOT EXISTS FOR (p:Paragraph) ON (p.program)",
]

# ------------------------------------------------------------------ #
#  Relationship indexes                                               #
#                                                                     #
#  Creating a relationship index registers the relationship type      #
#  token in Neo4j's type store even before any actual relationships   #
#  exist.  This prevents GQL status 01N51 ("relationship type does   #
#  not exist") notifications that fire on OPTIONAL MATCH queries      #
#  when the graph is still empty.                                     #
# ------------------------------------------------------------------ #
SCHEMA_REL_INDEXES = [
    "CREATE INDEX rel_has_paragraph  IF NOT EXISTS FOR ()-[r:HAS_PARAGRAPH]-()  ON (r.order)",
    "CREATE INDEX rel_has_data_item  IF NOT EXISTS FOR ()-[r:HAS_DATA_ITEM]-()  ON (r.level)",
    "CREATE INDEX rel_performs       IF NOT EXISTS FOR ()-[r:PERFORMS]-()        ON (r.via)",
    "CREATE INDEX rel_calls          IF NOT EXISTS FOR ()-[r:CALLS]-()           ON (r.call_type)",
    "CREATE INDEX rel_copies         IF NOT EXISTS FOR ()-[r:COPIES]-()          ON (r.version)",
    "CREATE INDEX rel_reads          IF NOT EXISTS FOR ()-[r:READS]-()           ON (r.access_type)",
    "CREATE INDEX rel_writes         IF NOT EXISTS FOR ()-[r:WRITES]-()          ON (r.access_type)",
    "CREATE INDEX rel_executes_sql   IF NOT EXISTS FOR ()-[r:EXECUTES_SQL]-()    ON (r.sql_type)",
]


def apply_schema(neo4j: "Neo4jTools") -> None:
    """Apply all constraints and indexes to the connected Neo4j instance."""
    for stmt in SCHEMA_CONSTRAINTS + SCHEMA_INDEXES + SCHEMA_REL_INDEXES:
        try:
            neo4j.write(stmt)
            logger.debug("Applied schema statement: %s", stmt[:60])
        except Exception as exc:
            # Constraint/index may already exist on re-runs
            logger.warning("Schema statement skipped (%s): %s", exc, stmt[:60])
    logger.info("Neo4j schema applied successfully.")
