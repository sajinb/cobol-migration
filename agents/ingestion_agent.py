"""
Ingestion Agent
===============
Role    : Runs MAPA JAR (if result.csv doesn't exist), then reads the CSV
          + raw COBOL files and populates Neo4j.
Input   : COBOL source directory (+ optional pre-existing MAPA CSV path)
Output  : Populated graph nodes (Program, Paragraph, DataItem, Copybook)

One instance can be spawned per program for parallel ingestion.
The agent uses LangGraph to manage its internal state machine.

Pipeline:
  run_mapa → validate_inputs → apply_schema → ingest_csv → report
  (run_mapa is skipped if result.csv already exists)
"""

import logging
import os
from typing import Annotated, Dict, TypedDict

from langgraph.graph import StateGraph, START, END
from langgraph.graph.message import add_messages
from langchain_core.messages import AIMessage, HumanMessage

from config.settings import get_settings
from tools.neo4j_tools import Neo4jTools
from tools.file_tools import FileTools
from tools.mapa_runner import MapaRunner
from graph.importer import MapaCsvImporter
from graph.schema import apply_schema

logger = logging.getLogger(__name__)


# ------------------------------------------------------------------ #
#  State definition                                                   #
# ------------------------------------------------------------------ #

class IngestionState(TypedDict):
    messages: Annotated[list, add_messages]
    csv_path: str
    cobol_source_dir: str
    program_filter: str          # empty string = ingest all programs
    counts: Dict[str, int]
    status: str
    error: str


# ------------------------------------------------------------------ #
#  Agent nodes                                                        #
# ------------------------------------------------------------------ #

def _run_mapa(state: IngestionState) -> IngestionState:
    """
    Run the MAPA JAR to produce result.csv from the COBOL source directory.
    Skipped automatically if the CSV already exists.
    """
    if os.path.exists(state["csv_path"]):
        logger.info("MAPA CSV already exists at %s — skipping MAPA run.", state["csv_path"])
        return {
            **state,
            "status": "mapa_skipped",
            "messages": [AIMessage(content=f"CSV already exists: {state['csv_path']}. Skipping MAPA.")],
        }

    settings = get_settings()
    logger.info("result.csv not found — running MAPA JAR to generate it...")

    runner = MapaRunner(
        jar_path=settings.MAPA_JAR_PATH,
        jar_url=settings.MAPA_JAR_URL,
        java_executable=settings.MAPA_JAVA_EXECUTABLE,
        jvm_opts=settings.MAPA_JVM_OPTS,
        auto_download=settings.MAPA_AUTO_DOWNLOAD,
    )
    result = runner.run(
        cobol_dir=state["cobol_source_dir"],
        output_csv=state["csv_path"],
    )

    if not result["success"]:
        return {
            **state,
            "status": "failed",
            "error": result["error"],
            "messages": [AIMessage(content=f"MAPA failed: {result['error']}")],
        }

    row_count = result.get("row_count", "?")
    return {
        **state,
        "status": "mapa_done",
        "messages": [
            AIMessage(
                content=f"MAPA completed. Generated {state['csv_path']} with {row_count} rows."
            )
        ],
    }


def _validate_inputs(state: IngestionState) -> IngestionState:
    """Check that the CSV and source directory exist after MAPA run."""
    errors = []
    if not os.path.exists(state["csv_path"]):
        errors.append(f"MAPA CSV not found: {state['csv_path']}")
    if not os.path.isdir(state["cobol_source_dir"]):
        errors.append(f"COBOL source dir not found: {state['cobol_source_dir']}")

    if errors:
        return {
            **state,
            "status": "failed",
            "error": "; ".join(errors),
            "messages": [AIMessage(content=f"Validation failed: {'; '.join(errors)}")],
        }
    return {
        **state,
        "status": "validated",
        "messages": [AIMessage(content="Inputs validated. Starting ingestion.")],
    }


def _apply_schema_node(state: IngestionState) -> IngestionState:
    """Ensure Neo4j constraints and indexes are in place."""
    if state["status"] == "failed":
        return state
    try:
        neo4j = Neo4jTools()
        apply_schema(neo4j)
        neo4j.close()
        return {
            **state,
            "status": "schema_ready",
            "messages": [AIMessage(content="Neo4j schema applied.")],
        }
    except Exception as exc:
        return {
            **state,
            "status": "failed",
            "error": str(exc),
            "messages": [AIMessage(content=f"Schema error: {exc}")],
        }


def _ingest_csv(state: IngestionState) -> IngestionState:
    """Run the MAPA CSV importer."""
    if state["status"] == "failed":
        return state
    try:
        neo4j = Neo4jTools()
        importer = MapaCsvImporter(neo4j, state["cobol_source_dir"])
        counts = importer.run(state["csv_path"])
        neo4j.close()
        summary = (
            f"Ingestion complete — "
            f"programs: {counts['programs']}, "
            f"paragraphs: {counts['paragraphs']}, "
            f"relationships: {counts['relationships']}"
        )
        return {
            **state,
            "counts": counts,
            "status": "ingested",
            "messages": [AIMessage(content=summary)],
        }
    except Exception as exc:
        logger.exception("Ingestion failed")
        return {
            **state,
            "status": "failed",
            "error": str(exc),
            "messages": [AIMessage(content=f"Ingestion error: {exc}")],
        }


def _report(state: IngestionState) -> IngestionState:
    """Emit a final summary message."""
    if state["status"] == "failed":
        msg = f"Ingestion FAILED: {state.get('error', 'unknown error')}"
    else:
        counts = state.get("counts", {})
        msg = (
            f"Ingestion succeeded. "
            f"Programs: {counts.get('programs', 0)}, "
            f"Paragraphs: {counts.get('paragraphs', 0)}, "
            f"Relationships: {counts.get('relationships', 0)}"
        )
    return {
        **state,
        "messages": [AIMessage(content=msg)],
    }


# ------------------------------------------------------------------ #
#  Graph assembly                                                     #
# ------------------------------------------------------------------ #

def _route_after_mapa(state: IngestionState) -> str:
    return END if state["status"] == "failed" else "validate_inputs"


def _route_after_validate(state: IngestionState) -> str:
    return END if state["status"] == "failed" else "apply_schema"


def _route_after_schema(state: IngestionState) -> str:
    return END if state["status"] == "failed" else "ingest_csv"


def _route_after_ingest(state: IngestionState) -> str:
    return "report"


class IngestionAgent:
    """
    LangGraph-powered agent that:
      1. Runs MAPA JAR to generate result.csv (auto-skipped if CSV already exists)
      2. Ingests the CSV into the Neo4j graph

    Usage::

        agent = IngestionAgent()
        result = agent.run(cobol_source_dir="./cobol_samples")
        # csv_path is optional — defaults to settings.MAPA_CSV_PATH
    """

    def __init__(self):
        self._graph = self._build_graph()

    def _build_graph(self) -> StateGraph:
        builder = StateGraph(IngestionState)

        builder.add_node("run_mapa", _run_mapa)
        builder.add_node("validate_inputs", _validate_inputs)
        builder.add_node("apply_schema", _apply_schema_node)
        builder.add_node("ingest_csv", _ingest_csv)
        builder.add_node("report", _report)

        builder.add_edge(START, "run_mapa")
        builder.add_conditional_edges("run_mapa", _route_after_mapa)
        builder.add_conditional_edges("validate_inputs", _route_after_validate)
        builder.add_conditional_edges("apply_schema", _route_after_schema)
        builder.add_conditional_edges("ingest_csv", _route_after_ingest)
        builder.add_edge("report", END)

        return builder.compile()

    def run(
        self,
        csv_path: str,
        cobol_source_dir: str,
        program_filter: str = "",
    ) -> Dict:
        """
        Run the ingestion pipeline.
        Returns the final state dict with counts and status.
        """
        settings = get_settings()
        initial_state: IngestionState = {
            "messages": [HumanMessage(content="Start ingestion")],
            "csv_path": csv_path or settings.MAPA_CSV_PATH,
            "cobol_source_dir": cobol_source_dir or settings.COBOL_SOURCE_DIR,
            "program_filter": program_filter,
            "counts": {},
            "status": "starting",
            "error": "",
        }
        final_state = self._graph.invoke(initial_state)
        return final_state
