"""
Migration Agent
===============
Role    : Converts a single COBOL paragraph (with full graph context) to
          Java Spring Boot service method code.
Input   : Paragraph subgraph from Neo4j (source + deps + data items)
Output  : Generated Java method stored on the Paragraph node in Neo4j
          (status: analysed → migrated)

Receives full Graph RAG context — not just the raw COBOL source.
"""

import logging
from typing import Annotated, Dict, List, TypedDict

from langgraph.graph import StateGraph, START, END
from langgraph.graph.message import add_messages
from langchain_core.messages import AIMessage, HumanMessage

from config.settings import get_settings
from tools.neo4j_tools import Neo4jTools
from tools.file_tools import FileTools
from tools.llm_tools import LLMTools
from graph.queries import GraphQueries
from prompts.migration_prompts import MIGRATION_SYSTEM_PROMPT, build_migration_prompt

settings = get_settings()

logger = logging.getLogger(__name__)


# ------------------------------------------------------------------ #
#  State definition                                                   #
# ------------------------------------------------------------------ #

class MigrationState(TypedDict):
    messages: Annotated[list, add_messages]
    program: str
    paragraph: str
    subgraph: Dict
    shared_state_items: List[str]
    java_code: str
    retry_count: int
    failure_reason: str
    status: str
    error: str


# ------------------------------------------------------------------ #
#  Agent nodes                                                        #
# ------------------------------------------------------------------ #

def _fetch_context(state: MigrationState) -> MigrationState:
    """Retrieve the full Graph RAG context for this paragraph."""
    try:
        neo4j = Neo4jTools()
        queries = GraphQueries(neo4j)
        subgraph = queries.get_paragraph_subgraph(state["paragraph"], state["program"])
        shared = queries.get_working_storage_shared_items(state["program"])
        neo4j.close()

        if not subgraph:
            return {
                **state,
                "status": "failed",
                "error": f"Paragraph {state['paragraph']} not found",
                "messages": [AIMessage(content="Paragraph not found in graph.")],
            }

        # Extract names of shared WORKING-STORAGE items
        shared_names = list({
            row["data_item"]
            for row in shared
            if row.get("written_by") == state["paragraph"]
            or row.get("read_by") == state["paragraph"]
        })

        return {
            **state,
            "subgraph": subgraph,
            "shared_state_items": shared_names,
            "status": "context_ready",
            "messages": [AIMessage(content=f"Context fetched for {state['paragraph']}.")],
        }
    except Exception as exc:
        logger.exception("Failed to fetch context")
        return {
            **state,
            "status": "failed",
            "error": str(exc),
            "messages": [AIMessage(content=f"Context error: {exc}")],
        }


def _generate_java(state: MigrationState) -> MigrationState:
    """Call the LLM to generate the Java method."""
    if state["status"] == "failed":
        return state
    try:
        sg = state["subgraph"]
        human_prompt = build_migration_prompt(
            para_name=state["paragraph"],
            program=state["program"],
            source_code=sg.get("source_code", ""),
            intent=sg.get("intent", ""),
            performs=sg.get("performs", []),
            reads=sg.get("reads", []),
            writes=sg.get("writes", []),
            external_calls=sg.get("external_calls", []),
            sql_tables=sg.get("sql_tables", []),
            shared_state_items=state.get("shared_state_items", []),
            migration_notes=state.get("failure_reason", ""),
        )
        llm = LLMTools()
        java_code = llm.call_with_retry(MIGRATION_SYSTEM_PROMPT, human_prompt)
        return {
            **state,
            "java_code": java_code,
            "status": "generated",
            "messages": [AIMessage(content=f"Java code generated for {state['paragraph']}.")],
        }
    except Exception as exc:
        logger.exception("Code generation failed")
        return {
            **state,
            "status": "failed",
            "error": str(exc),
            "messages": [AIMessage(content=f"Generation error: {exc}")],
        }


def _store_generated_code(state: MigrationState) -> MigrationState:
    """Persist the generated Java code to the Paragraph node in Neo4j."""
    if state["status"] == "failed":
        return state
    try:
        neo4j = Neo4jTools()
        neo4j.update_paragraph_status(
            name=state["paragraph"],
            program=state["program"],
            status="migrated",
            generated_code=state["java_code"],
        )
        neo4j.close()
        return {
            **state,
            "status": "migrated",
            "messages": [AIMessage(content=f"Java code stored for {state['paragraph']}.")],
        }
    except Exception as exc:
        logger.exception("Failed to store generated code")
        return {
            **state,
            "status": "failed",
            "error": str(exc),
            "messages": [AIMessage(content=f"Store error: {exc}")],
        }


# ------------------------------------------------------------------ #
#  Graph assembly                                                     #
# ------------------------------------------------------------------ #

class MigrationAgent:
    """
    LangGraph-powered agent that migrates a single COBOL paragraph to Java.

    Usage::

        agent = MigrationAgent()
        result = agent.run(program="POLICY", paragraph="CALC-PREMIUM")
        # Re-run with failure context from Validation Agent:
        result = agent.run(program="POLICY", paragraph="CALC-PREMIUM",
                           failure_reason="Missing null-check for WS-AGE-FACTOR")
    """

    def __init__(self):
        self._graph = self._build_graph()

    def _build_graph(self):
        builder = StateGraph(MigrationState)

        builder.add_node("fetch_context", _fetch_context)
        builder.add_node("generate_java", _generate_java)
        builder.add_node("store_generated_code", _store_generated_code)

        builder.add_edge(START, "fetch_context")
        builder.add_conditional_edges(
            "fetch_context",
            lambda s: END if s["status"] == "failed" else "generate_java",
        )
        builder.add_conditional_edges(
            "generate_java",
            lambda s: END if s["status"] == "failed" else "store_generated_code",
        )
        builder.add_edge("store_generated_code", END)

        return builder.compile()

    def run(
        self,
        program: str,
        paragraph: str,
        failure_reason: str = "",
        retry_count: int = 0,
    ) -> Dict:
        """Migrate a single paragraph. Returns final state dict."""
        initial: MigrationState = {
            "messages": [HumanMessage(content=f"Migrate {paragraph} in {program}")],
            "program": program,
            "paragraph": paragraph,
            "subgraph": {},
            "shared_state_items": [],
            "java_code": "",
            "retry_count": retry_count,
            "failure_reason": failure_reason,
            "status": "starting",
            "error": "",
        }
        return self._graph.invoke(initial)

    def run_for_program(self, program: str, force: bool = False) -> List[Dict]:
        """
        Migrate all analysed paragraphs in a program (in dependency order),
        then assemble all migrated fragments into a single @Service class file.

        Args:
            force: When True, reset any already-migrated/validated paragraphs
                   back to 'analysed' so they are re-processed.
        """
        neo4j = Neo4jTools()
        if force:
            reset_count = neo4j.reset_paragraph_migration_status(program)
            logger.info(
                "Force re-run: reset %d paragraph(s) in %s back to 'analysed'",
                reset_count, program,
            )
        queries = GraphQueries(neo4j)
        ordered = queries.get_migration_order(program)
        neo4j.close()

        results = []
        for row in ordered:
            if row.get("status") not in ("analysed", "failed"):
                continue
            result = self.run(program=program, paragraph=row["name"])
            results.append(result)

        # Assemble all migrated fragments into one @Service class
        self._assemble_and_write_service(program)
        return results

    def _assemble_and_write_service(self, program: str) -> str:
        """
        Query Neo4j for all migrated/validated paragraph code fragments,
        assemble them into a single Spring Boot @Service class, write
        companion files (JPA entities, repositories, DTOs), and return
        the service class path.

        Returns the path of the written service file (empty string on failure).
        """
        try:
            neo4j = Neo4jTools()
            queries = GraphQueries(neo4j)
            fragments = queries.get_migrated_code(program)
            neo4j.close()

            if not fragments:
                logger.warning("No migrated code found for program %s — skipping assembly", program)
                return ""

            # Write the main @Service class
            java_class = FileTools.assemble_service_class(program, fragments)
            out_path   = FileTools.write_service_class(settings.OUTPUT_DIR, program, java_class)

            # Write companion files (entities, repositories, DTOs)
            companions = FileTools.collect_companion_files(fragments)
            if companions:
                written = FileTools.write_companion_files(settings.OUTPUT_DIR, companions)
                logger.info(
                    "Wrote %d companion file(s) for %s: %s",
                    len(written), program, [Path(p).name for p in written],
                )

            logger.info(
                "Assembled %d fragment(s) for %s → %s",
                len(fragments), program, out_path,
            )
            return out_path
        except Exception as exc:
            logger.exception("Assembly failed for %s: %s", program, exc)
            return ""
