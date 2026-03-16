"""
Neo4j tools for reading and writing graph data.
Used by all agents as shared memory / state store.

Connection strategy: uses the Neo4j Transactional Cypher HTTP API over HTTPS
(port 7473) instead of the Bolt binary protocol (port 7687).  This avoids
firewall/VPN issues that block non-standard ports while keeping full TLS.
"""

import logging
from typing import Any, Dict, List, Optional

import requests

from config.settings import get_settings

logger = logging.getLogger(__name__)


class Neo4jTools:
    """Wrapper around Neo4j HTTP API providing agent-ready read/write operations."""

    def __init__(self):
        settings = get_settings()

        # Derive the HTTPS base URL from the bolt URI.
        # neo4j+s://71cd3c29.databases.neo4j.io  →  https://71cd3c29.databases.neo4j.io:7473
        host = (
            settings.NEO4J_URI
            .replace("neo4j+s://", "")
            .replace("neo4j://",   "")
            .replace("bolt+s://",  "")
            .replace("bolt://",    "")
            .rstrip("/")
        )
        self._base_url = f"https://{host}:7473"
        self._database = settings.NEO4J_DATABASE
        self._auth = (settings.NEO4J_USERNAME, settings.NEO4J_PASSWORD)
        self._commit_url = f"{self._base_url}/db/{self._database}/tx/commit"

        # Verify connectivity on startup.
        try:
            resp = requests.get(
                f"{self._base_url}/db/{self._database}",
                auth=self._auth,
                timeout=15,
            )
            if resp.status_code not in (200, 404):
                resp.raise_for_status()
        except Exception as exc:
            raise ConnectionError(
                f"Cannot connect to Neo4j at {self._base_url}.\n"
                "Common causes for Aura Free:\n"
                "  1. Instance is PAUSED — log in to console.neo4j.io and resume it.\n"
                "  2. Wrong credentials in .env / environment variables.\n"
                "  3. Firewall / VPN blocking port 7473.\n"
                f"Original error: {exc}"
            ) from exc

    def close(self):
        pass  # HTTP is stateless — nothing to close.

    # ------------------------------------------------------------------ #
    #  Internal HTTP helper                                                #
    # ------------------------------------------------------------------ #

    def _run(self, cypher: str, params: Optional[Dict[str, Any]] = None) -> List[Dict]:
        """POST a single Cypher statement via the HTTP API and return row dicts."""
        params = params or {}
        payload = {"statements": [{"statement": cypher, "parameters": params}]}
        resp = requests.post(
            self._commit_url,
            json=payload,
            auth=self._auth,
            headers={"Accept": "application/json;charset=UTF-8",
                     "Content-Type": "application/json"},
            timeout=30,
        )
        resp.raise_for_status()
        body = resp.json()
        if body.get("errors"):
            raise RuntimeError(f"Neo4j HTTP error: {body['errors']}")

        results = body.get("results", [{}])[0]
        columns = results.get("columns", [])
        rows = []
        for entry in results.get("data", []):
            rows.append(dict(zip(columns, entry["row"])))
        return rows

    # ------------------------------------------------------------------ #
    #  Generic query / write                                               #
    # ------------------------------------------------------------------ #

    def query(self, cypher: str, params: Optional[Dict[str, Any]] = None) -> List[Dict]:
        """Run a read query and return a list of record dicts."""
        return self._run(cypher, params)

    def write(self, cypher: str, params: Optional[Dict[str, Any]] = None) -> None:
        """Run a write query (no return value expected)."""
        self._run(cypher, params)

    # ------------------------------------------------------------------ #
    #  Program / Paragraph helpers                                         #
    # ------------------------------------------------------------------ #

    def upsert_program(self, name: str, file_path: str) -> None:
        cypher = """
        MERGE (prog:Program {name: $name})
        ON CREATE SET prog.file_path = $file_path,
                      prog.status    = 'pending',
                      prog.created   = timestamp(),
                      prog.updated   = timestamp()
        ON MATCH  SET prog.file_path = CASE WHEN $file_path <> '' THEN $file_path
                                            ELSE prog.file_path END,
                      prog.updated   = timestamp()
        """
        self.write(cypher, {"name": name, "file_path": file_path})
        logger.info("Upserted Program node: %s  file_path=%s", name, file_path or "(none)")

    def upsert_paragraph(
        self,
        program: str,
        name: str,
        start_line: int,
        end_line: int,
        source_code: str = "",
    ) -> None:
        cypher = """
        MERGE (p:Paragraph {name: $name, program: $program})
        ON CREATE SET p.status  = 'pending',
                      p.created = timestamp()
        SET p.start_line  = $start_line,
            p.end_line    = $end_line,
            p.source_code = $source_code,
            p.updated     = timestamp()
        WITH p
        MATCH (prog:Program {name: $program})
        MERGE (prog)-[:HAS_PARAGRAPH]->(p)
        """
        self.write(
            cypher,
            {
                "program": program,
                "name": name,
                "start_line": start_line,
                "end_line": end_line,
                "source_code": source_code,
            },
        )

    def upsert_copybook(self, name: str, program: str) -> None:
        cypher = """
        MERGE (c:Copybook {name: $name})
        WITH c
        MATCH (prog:Program {name: $program})
        MERGE (prog)-[:COPIES]->(c)
        """
        self.write(cypher, {"name": name, "program": program})

    def upsert_data_item(
        self, name: str, program: str, pic_type: str = "", level: int = 0
    ) -> None:
        cypher = """
        MERGE (d:DataItem {name: $name, program: $program})
        SET d.pic_type = $pic_type,
            d.level    = $level
        WITH d
        MATCH (prog:Program {name: $program})
        MERGE (prog)-[:HAS_DATA_ITEM]->(d)
        """
        self.write(
            cypher,
            {"name": name, "program": program, "pic_type": pic_type, "level": level},
        )

    def add_perform_relationship(
        self, from_para: str, to_para: str, program: str
    ) -> None:
        cypher = """
        MATCH (a:Paragraph {name: $from_para, program: $program})
        MATCH (b:Paragraph {name: $to_para,   program: $program})
        MERGE (a)-[:PERFORMS]->(b)
        """
        self.write(cypher, {"from_para": from_para, "to_para": to_para, "program": program})

    def add_call_relationship(self, from_program: str, to_program: str) -> None:
        cypher = """
        MERGE (a:Program {name: $from_program})
        MERGE (b:Program {name: $to_program})
        MERGE (a)-[:CALLS]->(b)
        """
        self.write(cypher, {"from_program": from_program, "to_program": to_program})

    def add_reads_relationship(self, para: str, data_item: str, program: str) -> None:
        cypher = """
        MATCH (p:Paragraph {name: $para,      program: $program})
        MATCH (d:DataItem  {name: $data_item, program: $program})
        MERGE (p)-[:READS]->(d)
        """
        self.write(cypher, {"para": para, "data_item": data_item, "program": program})

    def add_writes_relationship(self, para: str, data_item: str, program: str) -> None:
        cypher = """
        MATCH (p:Paragraph {name: $para,      program: $program})
        MATCH (d:DataItem  {name: $data_item, program: $program})
        MERGE (p)-[:WRITES]->(d)
        """
        self.write(cypher, {"para": para, "data_item": data_item, "program": program})

    # ------------------------------------------------------------------ #
    #  Status tracking                                                     #
    # ------------------------------------------------------------------ #

    def update_paragraph_status(
        self,
        name: str,
        program: str,
        status: str,
        generated_code: str = "",
        complexity: str = "",
        intent: str = "",
    ) -> None:
        cypher = """
        MATCH (p:Paragraph {name: $name, program: $program})
        SET p.status         = $status,
            p.generated_code = $generated_code,
            p.complexity     = $complexity,
            p.intent         = $intent,
            p.updated        = timestamp()
        """
        self.write(
            cypher,
            {
                "name": name,
                "program": program,
                "status": status,
                "generated_code": generated_code,
                "complexity": complexity,
                "intent": intent,
            },
        )

    def update_program_status(self, name: str, status: str) -> None:
        cypher = """
        MATCH (prog:Program {name: $name})
        SET prog.status  = $status,
            prog.updated = timestamp()
        """
        self.write(cypher, {"name": name, "status": status})

    def reset_paragraph_migration_status(self, program: str) -> int:
        """
        Reset all migrated/validated paragraphs for a program back to 'analysed'
        so the Migration Agent will process them again.
        Returns the number of paragraphs reset.
        """
        cypher = """
        MATCH (p:Paragraph {program: $program})
        WHERE p.status IN ['migrated', 'validated']
        SET p.status = 'analysed', p.updated = timestamp()
        RETURN count(p) AS reset_count
        """
        result = self.query(cypher, {"program": program})
        return result[0]["reset_count"] if result else 0

    def get_pending_programs(self) -> List[Dict]:
        return self.query(
            "MATCH (prog:Program {status: 'pending'}) RETURN prog.name AS name, prog.file_path AS file_path"
        )

    def get_paragraphs_by_status(self, status: str) -> List[Dict]:
        cypher = """
        MATCH (p:Paragraph {status: $status})
        RETURN p.name AS name, p.program AS program,
               p.source_code AS source_code, p.start_line AS start_line,
               p.end_line AS end_line
        """
        return self.query(cypher, {"status": status})

    # ------------------------------------------------------------------ #
    #  Graph RAG subgraph extraction                                       #
    # ------------------------------------------------------------------ #

    def get_migration_subgraph(self, para_name: str, program: str) -> Dict:
        """
        Return everything the Migration Agent needs to migrate one paragraph:
        - source code
        - performed child paragraphs (with source)
        - data items read/written
        - external programs called
        """
        cypher = """
        MATCH (p:Paragraph {name: $name, program: $program})
        OPTIONAL MATCH (p)-[:PERFORMS*1..5]->(child:Paragraph)
        OPTIONAL MATCH (p)-[:READS|WRITES]->(d:DataItem)
        OPTIONAL MATCH (p)-[:CALLS]->(ext:Program)
        RETURN
            p.source_code                      AS source_code,
            p.intent                           AS intent,
            collect(DISTINCT child.name)       AS child_paragraphs,
            collect(DISTINCT child.source_code) AS child_sources,
            collect(DISTINCT {name: d.name, pic_type: d.pic_type, level: d.level}) AS data_items,
            collect(DISTINCT ext.name)         AS external_calls
        """
        results = self.query(cypher, {"name": para_name, "program": program})
        return results[0] if results else {}

    def detect_circular_calls(self) -> List[Dict]:
        """Detect circular CALL chains in the graph."""
        cypher = """
        MATCH path = (p:Program)-[:CALLS*2..10]->(p)
        RETURN [n IN nodes(path) | n.name] AS cycle
        LIMIT 20
        """
        return self.query(cypher)

    def get_migration_order(self, program: str) -> List[Dict]:
        """Return paragraphs ordered by dependency depth (leaves first)."""
        cypher = """
        MATCH (p:Paragraph {program: $program})
        OPTIONAL MATCH (p)-[:PERFORMS*]->(dep:Paragraph)
        WITH p, count(dep) AS depth
        ORDER BY depth ASC
        RETURN p.name AS name, depth
        """
        return self.query(cypher, {"program": program})
