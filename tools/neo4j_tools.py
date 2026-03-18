"""
Neo4j tools for reading and writing graph data.
Used by all agents as shared memory / state store.

Connection strategy: uses the Neo4j Transactional Cypher HTTP API.
- On-prem / local: http://localhost:7474  (NEO4J_URI=bolt://localhost:7687)
- Neo4j Aura cloud: https://<host>:443    (NEO4J_URI=neo4j+s://<host>)

The HTTP port is auto-detected from the URI scheme; override with NEO4J_HTTP_PORT.
"""

import logging
from typing import Any, Dict, List, Optional

import urllib3
import requests

from config.settings import get_settings

logger = logging.getLogger(__name__)

# Aura cloud uses corporate SSL inspection in some environments; suppress the
# warning only when we are actually disabling verification (see __init__).
_AURA_SCHEMES = {"neo4j+s", "bolt+s"}


def _build_http_base(uri: str, http_port_override: int) -> tuple[str, bool]:
    """
    Return (base_url, use_tls) derived from the bolt/neo4j URI.

    Examples
    --------
    bolt://localhost:7687      → http://localhost:7474,  tls=False
    bolt+s://host.io          → https://host.io,         tls=True
    neo4j+s://host.io         → https://host.io,         tls=True
    neo4j://localhost:7687    → http://localhost:7474,   tls=False
    """
    scheme, _, rest = uri.partition("://")
    # strip any bolt port appended to the host
    host = rest.split(":")[0].rstrip("/")
    tls = scheme in _AURA_SCHEMES

    if http_port_override:
        port = http_port_override
    elif tls:
        port = 443          # Neo4j Aura — HTTPS on 443
    else:
        port = 7474         # on-prem — HTTP on 7474

    proto = "https" if tls else "http"
    base = f"{proto}://{host}:{port}" if port not in (80, 443) else f"{proto}://{host}"
    return base, tls


class Neo4jTools:
    """Wrapper around Neo4j HTTP API providing agent-ready read/write operations."""

    def __init__(self):
        settings = get_settings()

        self._base_url, self._tls = _build_http_base(
            settings.NEO4J_URI, settings.NEO4J_HTTP_PORT
        )
        self._database  = settings.NEO4J_DATABASE
        self._auth      = (settings.NEO4J_USERNAME, settings.NEO4J_PASSWORD)
        self._commit_url = f"{self._base_url}/db/{self._database}/tx/commit"

        # For Aura over a corporate SSL-inspection proxy the proxy re-signs the
        # certificate with a company CA not in Python's bundle — skip verify.
        # For on-prem HTTP there is no TLS at all so verify is irrelevant.
        self._verify = False if self._tls else True

        if self._tls and not self._verify:
            urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

        logger.info("Neo4j HTTP endpoint: %s  (tls=%s)", self._commit_url, self._tls)

        # Verify connectivity on startup.
        try:
            resp = requests.post(
                self._commit_url,
                json={"statements": [{"statement": "RETURN 1"}]},
                auth=self._auth,
                headers={"Accept": "application/json;charset=UTF-8",
                         "Content-Type": "application/json"},
                timeout=15,
                verify=self._verify,
            )
            resp.raise_for_status()
            body = resp.json()
            if body.get("errors"):
                raise RuntimeError(body["errors"])
        except Exception as exc:
            raise ConnectionError(
                f"Cannot connect to Neo4j at {self._commit_url}.\n"
                "Troubleshooting:\n"
                "  On-prem : confirm Neo4j is running and bolt://localhost:7687 is reachable.\n"
                "            Default HTTP API is http://localhost:7474 — set NEO4J_HTTP_PORT=7474.\n"
                "  Aura    : instance may be PAUSED — resume at console.neo4j.io.\n"
                "  Credentials: check NEO4J_USERNAME / NEO4J_PASSWORD in .env.\n"
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
            verify=self._verify,
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
