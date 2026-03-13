"""
Orchestrator Agent
==================
Role    : Plans and delegates migration work across all other agents.
          Manages sequencing, retries, and progress tracking.
          Uses Neo4j as shared state — paragraphs move through statuses:
            pending → ingested → analysed → migrated → validated

Input   : List of COBOL programs to migrate (or ALL pending programs)
Output  : Final migration report (counts per status, failed paragraphs)

Uses LangGraph with a supervisor loop pattern:
  Ingest → Analyse (parallel) → Migrate (dependency-ordered) → Validate → Report
"""

import logging
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import Annotated, Dict, List, Optional, TypedDict

from langgraph.graph import StateGraph, START, END
from langgraph.graph.message import add_messages
from langchain_core.messages import AIMessage, HumanMessage

from config.settings import get_settings
from tools.neo4j_tools import Neo4jTools
from graph.queries import GraphQueries
from agents.ingestion_agent import IngestionAgent
from agents.analysis_agent import AnalysisAgent
from agents.migration_agent import MigrationAgent
from agents.validation_agent import ValidationAgent

logger = logging.getLogger(__name__)


# ------------------------------------------------------------------ #
#  State definition                                                   #
# ------------------------------------------------------------------ #

class OrchestratorState(TypedDict):
    messages: Annotated[list, add_messages]
    csv_path: str
    cobol_source_dir: str
    copybook_dir: str                 # optional; empty = no -copy flag
    programs_to_migrate: List[str]    # empty = ALL programs
    ingestion_done: bool
    analysis_done: bool
    migration_done: bool
    validation_done: bool
    retry_queue: List[Dict]           # {"program": ..., "paragraph": ..., "reason": ...}
    report: Dict
    status: str
    error: str


# ------------------------------------------------------------------ #
#  Agent nodes                                                        #
# ------------------------------------------------------------------ #

def _run_ingestion(state: OrchestratorState) -> OrchestratorState:
    """Run the Ingestion Agent to populate Neo4j from MAPA CSV."""
    logger.info("[Orchestrator] Starting ingestion...")
    agent = IngestionAgent()
    result = agent.run(
        csv_path=state["csv_path"],
        cobol_source_dir=state["cobol_source_dir"],
        copybook_dir=state["copybook_dir"],
    )
    if result.get("status") == "failed":
        return {
            **state,
            "status": "failed",
            "error": f"Ingestion failed: {result.get('error', '')}",
            "messages": [AIMessage(content=f"Ingestion failed: {result.get('error', '')}")],
        }
    counts = result.get("counts", {})
    return {
        **state,
        "ingestion_done": True,
        "status": "ingested",
        "messages": [
            AIMessage(
                content=f"Ingestion complete. Programs: {counts.get('programs', 0)}, "
                        f"Paragraphs: {counts.get('paragraphs', 0)}"
            )
        ],
    }


def _run_analysis(state: OrchestratorState) -> OrchestratorState:
    """Run Analysis Agent for all pending paragraphs (parallel per program)."""
    if state["status"] == "failed":
        return state

    logger.info("[Orchestrator] Starting analysis phase...")
    settings = get_settings()
    neo4j = Neo4jTools()
    queries = GraphQueries(neo4j)

    # Determine which programs to analyse
    if state["programs_to_migrate"]:
        programs = state["programs_to_migrate"]
    else:
        programs = [p["name"] for p in queries.get_all_programs()]
    neo4j.close()

    agent = AnalysisAgent()
    failed_count = 0

    with ThreadPoolExecutor(max_workers=settings.MAX_PARALLEL_AGENTS) as executor:
        futures = {
            executor.submit(agent.run_for_program, program): program
            for program in programs
        }
        for future in as_completed(futures):
            program = futures[future]
            try:
                results = future.result()
                failed = sum(1 for r in results if r.get("status") == "failed")
                failed_count += failed
                logger.info(
                    "[Orchestrator] Analysis done for %s — %d paragraphs, %d failed",
                    program, len(results), failed,
                )
            except Exception as exc:
                logger.error("[Orchestrator] Analysis error for %s: %s", program, exc)
                failed_count += 1

    return {
        **state,
        "analysis_done": True,
        "status": "analysed",
        "messages": [
            AIMessage(
                content=f"Analysis phase complete. "
                        f"Programs processed: {len(programs)}. "
                        f"Failed paragraphs: {failed_count}."
            )
        ],
    }


def _run_migration(state: OrchestratorState) -> OrchestratorState:
    """Run Migration Agent for all analysed paragraphs, plus retries."""
    if state["status"] == "failed":
        return state

    logger.info("[Orchestrator] Starting migration phase...")
    settings = get_settings()
    neo4j = Neo4jTools()
    queries = GraphQueries(neo4j)

    if state["programs_to_migrate"]:
        programs = state["programs_to_migrate"]
    else:
        programs = [p["name"] for p in queries.get_all_programs()]
    neo4j.close()

    agent = MigrationAgent()
    retry_queue = []

    with ThreadPoolExecutor(max_workers=settings.MAX_PARALLEL_AGENTS) as executor:
        futures = {
            executor.submit(agent.run_for_program, program): program
            for program in programs
        }
        for future in as_completed(futures):
            program = futures[future]
            try:
                results = future.result()
                for r in results:
                    if r.get("status") == "failed":
                        retry_queue.append({
                            "program": r["program"],
                            "paragraph": r["paragraph"],
                            "reason": r.get("error", ""),
                        })
            except Exception as exc:
                logger.error("[Orchestrator] Migration error for %s: %s", program, exc)

    return {
        **state,
        "migration_done": True,
        "retry_queue": retry_queue,
        "status": "migrated",
        "messages": [
            AIMessage(
                content=f"Migration phase complete. "
                        f"Failed/queued for retry: {len(retry_queue)}."
            )
        ],
    }


def _run_validation(state: OrchestratorState) -> OrchestratorState:
    """Validate all migrated paragraphs. Failed ones go back to Migration Agent."""
    if state["status"] == "failed":
        return state

    logger.info("[Orchestrator] Starting validation phase...")
    settings = get_settings()

    neo4j = Neo4jTools()
    migrated = neo4j.get_paragraphs_by_status("migrated")
    neo4j.close()

    val_agent = ValidationAgent()
    mig_agent = MigrationAgent()
    new_retry_queue = []
    programs_needing_reassembly: set = set()

    for row in migrated:
        program = row["program"]
        paragraph = row["name"]

        result = val_agent.run(program=program, paragraph=paragraph)
        if not result.get("passed", False):
            reason = result.get("failure_reason", "")
            logger.warning(
                "[Orchestrator] Validation FAILED for %s.%s — %s", program, paragraph, reason
            )
            # Retry migration with failure context
            retry_result = mig_agent.run(
                program=program,
                paragraph=paragraph,
                failure_reason=reason,
                retry_count=1,
            )
            if retry_result.get("status") == "migrated":
                # Re-assemble the service file for this program after the retry
                programs_needing_reassembly.add(program)
            else:
                new_retry_queue.append({
                    "program": program,
                    "paragraph": paragraph,
                    "reason": reason,
                })

    # Re-assemble service files for programs where retries produced new code
    for program in programs_needing_reassembly:
        try:
            mig_agent._assemble_and_write_service(program)
            logger.info("[Orchestrator] Re-assembled service for %s after retry.", program)
        except Exception as exc:
            logger.error("[Orchestrator] Re-assembly failed for %s: %s", program, exc)

    return {
        **state,
        "validation_done": True,
        "retry_queue": new_retry_queue,
        "status": "validated",
        "messages": [
            AIMessage(
                content=f"Validation phase complete. "
                        f"Remaining failures: {len(new_retry_queue)}."
            )
        ],
    }


def _generate_report(state: OrchestratorState) -> OrchestratorState:
    """Compile the final migration report from Neo4j status counts."""
    neo4j = Neo4jTools()
    queries = GraphQueries(neo4j)
    summary = queries.get_migration_summary()
    circular = queries.get_circular_call_chains()
    neo4j.close()

    explicitly_requested = state.get("programs_to_migrate") or []
    total_programs = (
        len(explicitly_requested)
        if explicitly_requested
        else summary.get("programs", 0)
    )
    report = {
        "status_counts": summary,
        "circular_call_chains": circular,
        "retry_queue": state.get("retry_queue", []),
        "total_programs": total_programs,
    }

    msg = (
        "=== Migration Report ===\n"
        + "\n".join(f"  {k}: {v}" for k, v in summary.items())
        + f"\n  Circular call chains detected: {len(circular)}"
        + f"\n  Paragraphs still failing: {len(state.get('retry_queue', []))}"
    )

    return {
        **state,
        "report": report,
        "status": "complete",
        "messages": [AIMessage(content=msg)],
    }


# ------------------------------------------------------------------ #
#  Graph assembly                                                     #
# ------------------------------------------------------------------ #

def _should_continue(state: OrchestratorState) -> str:
    if state["status"] == "failed":
        return END
    status_to_next = {
        "ingested": "run_analysis",
        "analysed": "run_migration",
        "migrated": "run_validation",
        "validated": "generate_report",
    }
    return status_to_next.get(state["status"], END)


class OrchestratorAgent:
    """
    Top-level LangGraph orchestrator that runs the full COBOL migration pipeline.

    Usage::

        agent = OrchestratorAgent()
        report = agent.run(
            csv_path="./cobol_samples/result.csv",
            cobol_source_dir="./cobol_samples",
            programs=["POLICY", "BILLING"],   # empty = migrate all
        )
        print(report["report"])
    """

    def __init__(self):
        self._graph = self._build_graph()

    def _build_graph(self):
        builder = StateGraph(OrchestratorState)

        builder.add_node("run_ingestion", _run_ingestion)
        builder.add_node("run_analysis", _run_analysis)
        builder.add_node("run_migration", _run_migration)
        builder.add_node("run_validation", _run_validation)
        builder.add_node("generate_report", _generate_report)

        builder.add_edge(START, "run_ingestion")
        builder.add_conditional_edges("run_ingestion", _should_continue)
        builder.add_conditional_edges("run_analysis", _should_continue)
        builder.add_conditional_edges("run_migration", _should_continue)
        builder.add_conditional_edges("run_validation", _should_continue)
        builder.add_edge("generate_report", END)

        return builder.compile()

    def run(
        self,
        csv_path: str = "",
        cobol_source_dir: str = "",
        copybook_dir: str = "",
        programs: Optional[List[str]] = None,
    ) -> Dict:
        """
        Run the full migration pipeline.
        Returns the final state including the migration report.
        """
        settings = get_settings()
        initial: OrchestratorState = {
            "messages": [HumanMessage(content="Start COBOL migration pipeline")],
            "csv_path": csv_path or settings.MAPA_CSV_PATH,
            "cobol_source_dir": cobol_source_dir or settings.COBOL_SOURCE_DIR,
            "copybook_dir": copybook_dir or settings.MAPA_COPYBOOK_DIR,
            "programs_to_migrate": programs or [],
            "ingestion_done": False,
            "analysis_done": False,
            "migration_done": False,
            "validation_done": False,
            "retry_queue": [],
            "report": {},
            "status": "starting",
            "error": "",
        }
        return self._graph.invoke(initial)
