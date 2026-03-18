"""
Analysis Agent
==============
Role    : Traverse the graph for each paragraph, classify complexity,
          extract business intent, and detect shared WORKING-STORAGE state.
Input   : Paragraph subgraph from Neo4j
Output  : complexity score, intent summary, migration notes stored back on
          the Paragraph node in Neo4j (status: pending → analysed)

Runs in parallel — one instance per paragraph (or batched per program).
"""

import json
import logging
from typing import Annotated, Dict, List, TypedDict

from langgraph.graph import StateGraph, START, END
from langgraph.graph.message import add_messages
from langchain_core.messages import AIMessage, HumanMessage

from config.settings import get_settings
from tools.neo4j_tools import Neo4jTools
from tools.llm_tools import LLMTools
from graph.queries import GraphQueries
from prompts.migration_prompts import (
    ANALYSIS_SYSTEM_PROMPT,
    build_analysis_prompt,
)

logger = logging.getLogger(__name__)


# ------------------------------------------------------------------ #
#  State definition                                                   #
# ------------------------------------------------------------------ #

class AnalysisState(TypedDict):
    messages: Annotated[list, add_messages]
    program: str
    paragraph: str
    subgraph: Dict
    analysis_result: Dict
    status: str
    error: str


# ------------------------------------------------------------------ #
#  Agent nodes                                                        #
# ------------------------------------------------------------------ #

def _fetch_subgraph(state: AnalysisState) -> AnalysisState:
    """Pull the paragraph's subgraph from Neo4j."""
    try:
        neo4j = Neo4jTools()
        queries = GraphQueries(neo4j)
        subgraph = queries.get_paragraph_subgraph(state["paragraph"], state["program"])
        neo4j.close()

        if not subgraph:
            return {
                **state,
                "status": "failed",
                "error": f"Paragraph {state['paragraph']} not found in graph",
                "messages": [AIMessage(content="Paragraph not found in graph.")],
            }
        return {
            **state,
            "subgraph": subgraph,
            "status": "subgraph_fetched",
            "messages": [AIMessage(content=f"Subgraph fetched for {state['paragraph']}.")],
        }
    except Exception as exc:
        logger.exception("Failed to fetch subgraph")
        return {
            **state,
            "status": "failed",
            "error": str(exc),
            "messages": [AIMessage(content=f"Error fetching subgraph: {exc}")],
        }


def _call_llm_analysis(state: AnalysisState) -> AnalysisState:
    """Send subgraph to LLM for analysis."""
    if state["status"] == "failed":
        return state
    try:
        sg = state["subgraph"]
        human_prompt = build_analysis_prompt(
            para_name=state["paragraph"],
            program=state["program"],
            source_code=sg.get("source_code", ""),
            performs=sg.get("performs", []),
            reads=sg.get("reads", []),
            writes=sg.get("writes", []),
            external_calls=sg.get("external_calls", []),
        )
        llm = LLMTools()
        raw = llm.call_with_retry(ANALYSIS_SYSTEM_PROMPT, human_prompt)

        # Parse JSON response
        result = json.loads(raw)
        return {
            **state,
            "analysis_result": result,
            "status": "analysed",
            "messages": [AIMessage(content=f"Analysis complete: {result.get('complexity', '?')} complexity")],
        }
    except json.JSONDecodeError as exc:
        logger.warning("LLM returned non-JSON: %s", exc)
        # Fallback: store raw text as intent
        return {
            **state,
            "analysis_result": {"intent": raw if "raw" in dir() else "", "complexity": "UNKNOWN"},
            "status": "analysed",
            "messages": [AIMessage(content="Analysis complete (non-JSON response, stored as intent).")],
        }
    except Exception as exc:
        logger.exception("LLM analysis failed")
        return {
            **state,
            "status": "failed",
            "error": str(exc),
            "messages": [AIMessage(content=f"Analysis error: {exc}")],
        }


def _persist_failure(state: AnalysisState) -> AnalysisState:
    """Write the failure status and error message back to Neo4j."""
    try:
        neo4j = Neo4jTools()
        neo4j.update_paragraph_status(
            name=state["paragraph"],
            program=state["program"],
            status="failed",
            error=state.get("error", "unknown error"),
        )
        neo4j.close()
    except Exception as exc:
        logger.exception("Could not persist failure for %s: %s", state["paragraph"], exc)
    return state


def _store_analysis(state: AnalysisState) -> AnalysisState:
    """Write analysis results back to the Paragraph node in Neo4j."""
    if state["status"] == "failed":
        return state
    try:
        result = state["analysis_result"]
        neo4j = Neo4jTools()
        neo4j.update_paragraph_status(
            name=state["paragraph"],
            program=state["program"],
            status="analysed",
            complexity=result.get("complexity", ""),
            intent=result.get("intent", ""),
        )
        neo4j.close()
        return {
            **state,
            "status": "stored",
            "messages": [AIMessage(content=f"Analysis stored for {state['paragraph']}.")],
        }
    except Exception as exc:
        logger.exception("Failed to store analysis")
        return {
            **state,
            "status": "failed",
            "error": str(exc),
            "messages": [AIMessage(content=f"Store error: {exc}")],
        }


# ------------------------------------------------------------------ #
#  Graph assembly                                                     #
# ------------------------------------------------------------------ #

def _route(state: AnalysisState) -> str:
    return END if state["status"] == "failed" else "next"


class AnalysisAgent:
    """
    LangGraph-powered agent that analyses a single COBOL paragraph.

    Usage::

        agent = AnalysisAgent()
        result = agent.run(program="POLICY", paragraph="CALC-PREMIUM")
    """

    def __init__(self):
        self._graph = self._build_graph()

    def _build_graph(self):
        builder = StateGraph(AnalysisState)

        builder.add_node("fetch_subgraph", _fetch_subgraph)
        builder.add_node("call_llm_analysis", _call_llm_analysis)
        builder.add_node("store_analysis", _store_analysis)
        builder.add_node("persist_failure", _persist_failure)

        builder.add_edge(START, "fetch_subgraph")
        builder.add_conditional_edges(
            "fetch_subgraph",
            lambda s: "persist_failure" if s["status"] == "failed" else "call_llm_analysis",
        )
        builder.add_conditional_edges(
            "call_llm_analysis",
            lambda s: "persist_failure" if s["status"] == "failed" else "store_analysis",
        )
        builder.add_conditional_edges(
            "store_analysis",
            lambda s: "persist_failure" if s["status"] == "failed" else END,
        )
        builder.add_edge("persist_failure", END)

        return builder.compile()

    def run(self, program: str, paragraph: str) -> Dict:
        """Analyse a single paragraph. Returns final state dict."""
        initial: AnalysisState = {
            "messages": [HumanMessage(content=f"Analyse {paragraph} in {program}")],
            "program": program,
            "paragraph": paragraph,
            "subgraph": {},
            "analysis_result": {},
            "status": "starting",
            "error": "",
        }
        return self._graph.invoke(initial)

    def run_for_program(self, program: str) -> List[Dict]:
        """Run analysis for all pending paragraphs in a program."""
        neo4j = Neo4jTools()
        rows = neo4j.query(
            "MATCH (p:Paragraph {program: $prog, status: 'pending'}) RETURN p.name AS name",
            {"prog": program},
        )
        neo4j.close()

        results = []
        for row in rows:
            result = self.run(program=program, paragraph=row["name"])
            results.append(result)
        return results
