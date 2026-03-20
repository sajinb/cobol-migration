"""
Neo4j tools for reading and writing graph data.
Used by all agents as shared memory / state store.

Connection strategy: uses the Neo4j Transactional Cypher HTTP API.
Local Neo4j: http://localhost:7474  (NEO4J_URI=bolt://localhost:7687)
"""

import logging
from typing import Any, Dict, List, Optional

import requests

from config.settings import get_settings

logger = logging.getLogger(__name__)


def _build_http_base(uri: str, http_port_override: int) -> str:
    """
    Return base_url derived from the bolt/neo4j URI.

    Examples
    --------
    bolt://localhost:7687   → http://localhost:7474
    neo4j://localhost:7687  → http://localhost:7474
    """
    _, _, rest = uri.partition("://")
    host = rest.split(":")[0].rstrip("/")
    port = http_port_override if http_port_override else 7474
    return f"http://{host}:{port}"


class Neo4jTools:
    """Wrapper around Neo4j HTTP API providing agent-ready read/write operations."""

    def __init__(self):
        settings = get_settings()

        self._base_url = _build_http_base(
            settings.NEO4J_URI, settings.NEO4J_HTTP_PORT
        )
        self._database  = settings.NEO4J_DATABASE
        self._auth      = (settings.NEO4J_USERNAME, settings.NEO4J_PASSWORD)
        self._commit_url = f"{self._base_url}/db/{self._database}/tx/commit"

        logger.info("Neo4j HTTP endpoint: %s", self._commit_url)

        # Verify connectivity on startup.
        try:
            resp = requests.post(
                self._commit_url,
                json={"statements": [{"statement": "RETURN 1"}]},
                auth=self._auth,
                headers={"Accept": "application/json;charset=UTF-8",
                         "Content-Type": "application/json"},
                timeout=15,
            )
            resp.raise_for_status()
            body = resp.json()
            if body.get("errors"):
                raise RuntimeError(body["errors"])
        except Exception as exc:
            raise ConnectionError(
                f"Cannot connect to Neo4j at {self._commit_url}.\n"
                "Troubleshooting:\n"
                "  Confirm Neo4j is running and bolt://localhost:7687 is reachable.\n"
                "  Default HTTP API is http://localhost:7474 — set NEO4J_HTTP_PORT if different.\n"
                "  Check NEO4J_USERNAME / NEO4J_PASSWORD in .env.\n"
                f"Original error: {exc}"
            ) from exc

    def close(self):
        pass  # HTTP is stateless — nothing to close.

    # ------------------------------------------------------------------ #
    #  Internal HTTP helpers                                               #
    # ------------------------------------------------------------------ #

    def _post(self, statements: list) -> dict:
        """POST one or more statements in a single HTTP request."""
        resp = requests.post(
            self._commit_url,
            json={"statements": statements},
            auth=self._auth,
            headers={"Accept": "application/json;charset=UTF-8",
                     "Content-Type": "application/json"},
            timeout=60,
        )
        resp.raise_for_status()
        return resp.json()

    def _run(self, cypher: str, params: Optional[Dict[str, Any]] = None) -> List[Dict]:
        """POST a single Cypher statement and return row dicts."""
        params = params or {}
        body = self._post([{"statement": cypher, "parameters": params}])
        if body.get("errors"):
            raise RuntimeError(f"Neo4j HTTP error: {body['errors']}")
        results = body.get("results", [{}])[0]
        columns = results.get("columns", [])
        return [dict(zip(columns, entry["row"])) for entry in results.get("data", [])]

    def write_batch(
        self,
        statements: List[tuple],
        batch_size: int = 200,
    ) -> None:
        """
        Execute multiple (cypher, params) pairs in as few HTTP requests as possible.

        Sending all writes for one program in a handful of requests instead of
        one-per-write eliminates the N+1 HTTP round-trip bottleneck.

        :param statements: list of (cypher_str, params_dict) tuples
        :param batch_size: max statements per HTTP request (Neo4j default limit ~500)
        """
        if not statements:
            return
        for i in range(0, len(statements), batch_size):
            chunk = statements[i : i + batch_size]
            payload = [
                {"statement": cypher, "parameters": params or {}}
                for cypher, params in chunk
            ]
            body = self._post(payload)
            errors = body.get("errors", [])
            if errors:
                raise RuntimeError(f"Neo4j batch error: {errors}")

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
        error: str = "",
    ) -> None:
        cypher = """
        MATCH (p:Paragraph {name: $name, program: $program})
        SET p.status         = $status,
            p.generated_code = $generated_code,
            p.complexity     = $complexity,
            p.intent         = $intent,
            p.error          = $error,
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
                "error": error,
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

    def reset_failed_paragraphs(self, program: Optional[str] = None) -> int:
        """
        Reset all paragraphs with status='failed' back to 'pending' so they
        can be re-processed by the pipeline.  Optionally scoped to one program.
        Returns the number of paragraphs reset.
        """
        if program:
            cypher = """
            MATCH (p:Paragraph {program: $program})
            WHERE p.status = 'failed'
            SET p.status = 'pending', p.error = '', p.updated = timestamp()
            RETURN count(p) AS reset_count
            """
            result = self.query(cypher, {"program": program})
        else:
            cypher = """
            MATCH (p:Paragraph)
            WHERE p.status = 'failed'
            SET p.status = 'pending', p.error = '', p.updated = timestamp()
            RETURN count(p) AS reset_count
            """
            result = self.query(cypher)
        return result[0]["reset_count"] if result else 0

    def get_failed_paragraphs(self, program: Optional[str] = None) -> List[Dict]:
        """Return all paragraphs currently in 'failed' status, optionally filtered by program."""
        if program:
            cypher = """
            MATCH (p:Paragraph {program: $program, status: 'failed'})
            RETURN p.name AS name, p.program AS program, p.error AS error
            ORDER BY p.program, p.name
            """
            return self.query(cypher, {"program": program})
        cypher = """
        MATCH (p:Paragraph {status: 'failed'})
        RETURN p.name AS name, p.program AS program, p.error AS error
        ORDER BY p.program, p.name
        """
        return self.query(cypher)

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
