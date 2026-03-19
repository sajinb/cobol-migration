"""
Pre-built Graph RAG Cypher queries used by the Analysis and Migration agents.
All methods return plain Python dicts / lists — no Neo4j objects.
"""

import logging
from typing import Dict, List, TYPE_CHECKING

if TYPE_CHECKING:
    from tools.neo4j_tools import Neo4jTools

logger = logging.getLogger(__name__)


class GraphQueries:
    """Reusable query library for the COBOL migration graph."""

    def __init__(self, neo4j: "Neo4jTools"):
        self._neo4j = neo4j

    # ------------------------------------------------------------------ #
    #  Program-level queries                                              #
    # ------------------------------------------------------------------ #

    def get_program_overview(self, program: str) -> Dict:
        """Return paragraph count, copybook count and call dependencies for a program."""
        cypher = """
        MATCH (prog:Program {name: $program})
        OPTIONAL MATCH (prog)-[:HAS_PARAGRAPH]->(p:Paragraph)
        OPTIONAL MATCH (prog)-[:COPIES]->(c:Copybook)
        OPTIONAL MATCH (prog)-[:CALLS]->(ext:Program)
        RETURN
            prog.name                     AS name,
            prog.file_path                AS file_path,
            count(DISTINCT p)             AS paragraph_count,
            count(DISTINCT c)             AS copybook_count,
            collect(DISTINCT ext.name)    AS external_calls
        """
        results = self._neo4j.query(cypher, {"program": program})
        return results[0] if results else {}

    def get_all_programs(self) -> List[Dict]:
        """List all Program nodes with status."""
        return self._neo4j.query(
            "MATCH (p:Program) RETURN p.name AS name, p.status AS status, p.file_path AS file_path"
        )

    # ------------------------------------------------------------------ #
    #  Paragraph-level queries                                            #
    # ------------------------------------------------------------------ #

    def get_paragraph_subgraph(self, para_name: str, program: str) -> Dict:
        """
        Full subgraph for one paragraph — used as Graph RAG context for the LLM.
        Returns source code, child paragraphs, data items, and external calls.
        """
        cypher = """
        MATCH (p:Paragraph {name: $name, program: $program})
        OPTIONAL MATCH (p)-[:PERFORMS]->(child:Paragraph)
        OPTIONAL MATCH (p)-[:READS]->(r:DataItem)
        OPTIONAL MATCH (p)-[:WRITES]->(w:DataItem)
        OPTIONAL MATCH (p)-[:CALLS]->(ext:Program)
        OPTIONAL MATCH (p)-[:EXECUTES_SQL]->(tbl:Table)
        OPTIONAL MATCH (prog:Program {name: $program})-[:COPIES]->(c:Copybook)
        RETURN
            p.name                             AS paragraph_name,
            p.source_code                      AS source_code,
            p.intent                           AS intent,
            p.complexity                       AS complexity,
            collect(DISTINCT {
                name:           child.name,
                source_code:    child.source_code,
                generated_code: child.generated_code
            })                                 AS performs,
            collect(DISTINCT {
                name:     r.name,
                pic_type: r.pic_type,
                level:    r.level,
                access:   'READ'
            })                                 AS reads,
            collect(DISTINCT {
                name:     w.name,
                pic_type: w.pic_type,
                level:    w.level,
                access:   'WRITE'
            })                                 AS writes,
            collect(DISTINCT ext.name)         AS external_calls,
            collect(DISTINCT tbl.name)         AS sql_tables,
            collect(DISTINCT c.name)           AS copybooks
        """
        results = self._neo4j.query(cypher, {"name": para_name, "program": program})
        return results[0] if results else {}

    def get_dependency_depth(self, para_name: str, program: str) -> int:
        """Return the maximum PERFORM chain depth from this paragraph."""
        cypher = """
        MATCH path = (:Paragraph {name: $name, program: $program})-[:PERFORMS*]->(leaf:Paragraph)
        WHERE NOT (leaf)-[:PERFORMS]->()
        RETURN max(length(path)) AS depth
        """
        results = self._neo4j.query(cypher, {"name": para_name, "program": program})
        depth = results[0].get("depth") if results else None
        return depth if depth is not None else 0

    def get_shared_paragraphs(self) -> List[Dict]:
        """Find paragraphs that appear in more than one program (shared copybook logic)."""
        cypher = """
        MATCH (p:Paragraph)
        WITH p.name AS para_name, collect(DISTINCT p.program) AS programs
        WHERE size(programs) > 1
        RETURN para_name, programs, size(programs) AS program_count
        ORDER BY program_count DESC
        """
        return self._neo4j.query(cypher)

    def get_working_storage_shared_items(self, program: str) -> List[Dict]:
        """
        Find DataItems that are both READ by one paragraph and WRITTEN by another
        — these are implicit data dependencies that need explicit Java parameters.
        """
        cypher = """
        MATCH (writer:Paragraph {program: $program})-[:WRITES]->(d:DataItem)
        MATCH (reader:Paragraph {program: $program})-[:READS]->(d)
        WHERE writer.name <> reader.name
        RETURN d.name AS data_item, d.pic_type AS pic_type,
               writer.name AS written_by, reader.name AS read_by
        ORDER BY d.name
        """
        return self._neo4j.query(cypher, {"program": program})

    def get_migration_order(self, program: str) -> List[Dict]:
        """
        Return paragraphs ordered so that leaves (no outgoing PERFORMS) come first.
        This ensures dependencies are migrated before callers.
        """
        cypher = """
        MATCH (p:Paragraph {program: $program})
        OPTIONAL MATCH (p)-[:PERFORMS*]->(dep:Paragraph)
        WITH p, count(dep) AS depth
        ORDER BY depth ASC
        RETURN p.name AS name, p.status AS status, depth
        """
        return self._neo4j.query(cypher, {"program": program})

    # ------------------------------------------------------------------ #
    #  Validation / completeness checks                                   #
    # ------------------------------------------------------------------ #

    def get_unmigrated_paragraphs(self, program: str) -> List[Dict]:
        cypher = """
        MATCH (p:Paragraph {program: $program})
        WHERE p.status <> 'validated'
        RETURN p.name AS name, p.status AS status
        """
        return self._neo4j.query(cypher, {"program": program})

    def get_circular_call_chains(self) -> List[Dict]:
        """Detect CALL cycles between programs."""
        cypher = """
        MATCH path = (prog:Program)-[:CALLS*2..10]->(prog)
        RETURN [n IN nodes(path) | n.name] AS cycle
        LIMIT 20
        """
        return self._neo4j.query(cypher)

    def get_migration_summary(self) -> Dict:
        """High-level counts for the migration dashboard."""
        cypher = """
        MATCH (p:Paragraph)
        RETURN p.status AS status, count(*) AS count
        """
        rows = self._neo4j.query(cypher)
        return {row["status"]: row["count"] for row in rows}

    def get_migrated_code(self, program: str) -> List[Dict]:
        """
        Return migrated/validated paragraphs in dependency order (leaves first)
        so that the assembled class lists helper methods before their callers.

        Each row has 'name' and 'generated_code'.
        """
        cypher = """
        MATCH (p:Paragraph {program: $program})
        WHERE p.status IN ['migrated', 'validated']
          AND p.generated_code IS NOT NULL
        OPTIONAL MATCH (p)-[:PERFORMS*]->(dep:Paragraph)
        WITH p, count(dep) AS depth
        ORDER BY depth ASC
        RETURN p.name AS name, p.generated_code AS generated_code
        """
        return self._neo4j.query(cypher, {"program": program})
