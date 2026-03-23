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
from typing import Annotated, Dict, List, TypedDict

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
    copybook_dir: str            # optional; empty string = no -copy flag
    program_filter: str          # empty string = ingest all programs
    counts: Dict[str, int]
    status: str
    error: str
    # Populated when MAPA's COPY-neutralisation retry fires; forwarded to the
    # importer so it can write Copybook nodes + COPIES edges without the CSV.
    copy_deps: Dict[str, List[str]]
    # Names of Program nodes written during this ingestion run — used by the
    # orchestrator to scope analysis/migration to only fresh programs.
    ingested_programs: List[str]


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
        copybook_dir=state["copybook_dir"] or None,
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
        # Carry any COPY dependencies extracted during COPY-neutralisation retry
        # so _ingest_csv can create Copybook nodes + COPIES edges from them.
        "copy_deps": result.get("copy_deps", {}),
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
    """Run the MAPA CSV importer, then wire in any COPY deps from the retry."""
    if state["status"] == "failed":
        return state
    try:
        neo4j = Neo4jTools()
        importer = MapaCsvImporter(neo4j, state["cobol_source_dir"])
        counts = importer.run(state["csv_path"])

        # If the MAPA run took the COPY-neutralisation retry path, the CSV has
        # no COPY records — ingest them from the extracted copy_deps instead.
        copy_deps = state.get("copy_deps") or {}
        if copy_deps:
            copy_counts = importer.ingest_copy_deps(copy_deps, state["copybook_dir"])
            counts["copybooks"] = counts.get("copybooks", 0) + copy_counts["copybooks"]
            counts["data_items"] = counts.get("data_items", 0) + copy_counts["data_items"]
            counts["relationships"] = counts.get("relationships", 0) + copy_counts["relationships"]
            logger.info(
                "copy_deps ingested — copybooks=%d  data_items=%d",
                copy_counts["copybooks"], copy_counts["data_items"],
            )

        neo4j.close()
        ingested_programs: List[str] = counts.pop("program_names", [])
        summary = (
            f"Ingestion complete — "
            f"programs: {counts['programs']}, "
            f"paragraphs: {counts['paragraphs']}, "
            f"relationships: {counts['relationships']}"
        )
        return {
            **state,
            "counts": counts,
            "ingested_programs": ingested_programs,
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
        copybook_dir: str = "",
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
            "copybook_dir": copybook_dir or settings.MAPA_COPYBOOK_DIR,
            "program_filter": program_filter,
            "counts": {},
            "status": "starting",
            "error": "",
            "copy_deps": {},
            "ingested_programs": [],
        }
        final_state = self._graph.invoke(initial_state)
        return final_state
