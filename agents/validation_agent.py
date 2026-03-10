"""
Validation Agent
================
Role    : Reviews generated Java code for correctness and completeness.
          Compares against the original COBOL source and the graph context.
Input   : Paragraph node (generated_code + source_code) from Neo4j
Output  : pass/fail verdict + issue list stored on node
          On FAIL → signals Orchestrator to re-queue to Migration Agent

Checks:
  - All PERFORM calls resolved in Java
  - All data items mapped (as params or locals)
  - JSON verdict: pass/fail + issue list
"""

import json
import logging
from typing import Annotated, Dict, List, TypedDict

from langgraph.graph import StateGraph, START, END
from langgraph.graph.message import add_messages
from langchain_core.messages import AIMessage, HumanMessage

from tools.neo4j_tools import Neo4jTools
from tools.llm_tools import LLMTools
from graph.queries import GraphQueries
from prompts.migration_prompts import VALIDATION_SYSTEM_PROMPT, build_validation_prompt

logger = logging.getLogger(__name__)


# ------------------------------------------------------------------ #
#  State definition                                                   #
# ------------------------------------------------------------------ #

class ValidationState(TypedDict):
    messages: Annotated[list, add_messages]
    program: str
    paragraph: str
    subgraph: Dict
    java_code: str
    verdict: Dict           # JSON returned by LLM
    passed: bool
    failure_reason: str
    status: str
    error: str


# ------------------------------------------------------------------ #
#  Agent nodes                                                        #
# ------------------------------------------------------------------ #

def _fetch_for_validation(state: ValidationState) -> ValidationState:
    """Retrieve the paragraph node and its subgraph from Neo4j."""
    try:
        neo4j = Neo4jTools()
        queries = GraphQueries(neo4j)
        subgraph = queries.get_paragraph_subgraph(state["paragraph"], state["program"])

        # Pull the generated Java code from the node itself
        rows = neo4j.query(
            "MATCH (p:Paragraph {name: $name, program: $prog}) RETURN p.generated_code AS code",
            {"name": state["paragraph"], "prog": state["program"]},
        )
        neo4j.close()

        java_code = rows[0]["code"] if rows else ""
        if not java_code:
            return {
                **state,
                "status": "failed",
                "error": "No generated code found for paragraph",
                "messages": [AIMessage(content="No generated code found.")],
            }

        return {
            **state,
            "subgraph": subgraph,
            "java_code": java_code,
            "status": "ready_for_review",
            "messages": [AIMessage(content=f"Fetched generated code for {state['paragraph']}.")],
        }
    except Exception as exc:
        logger.exception("Fetch failed in Validation Agent")
        return {
            **state,
            "status": "failed",
            "error": str(exc),
            "messages": [AIMessage(content=f"Fetch error: {exc}")],
        }


def _review_with_llm(state: ValidationState) -> ValidationState:
    """Ask LLM to review the generated Java against the original COBOL."""
    if state["status"] == "failed":
        return state
    try:
        sg = state["subgraph"]

        expected_performs = [p["name"] for p in sg.get("performs", []) if p.get("name")]
        all_data_items = (
            [r["name"] for r in sg.get("reads", []) if r.get("name")]
            + [w["name"] for w in sg.get("writes", []) if w.get("name")]
        )

        human_prompt = build_validation_prompt(
            para_name=state["paragraph"],
            program=state["program"],
            cobol_source=sg.get("source_code", ""),
            java_code=state["java_code"],
            expected_performs=expected_performs,
            expected_data_items=list(set(all_data_items)),
        )

        llm = LLMTools()
        raw = llm.call_with_retry(VALIDATION_SYSTEM_PROMPT, human_prompt)

        verdict = json.loads(raw)
        passed = bool(verdict.get("pass", False))
        failure_reason = ""
        if not passed:
            issues = verdict.get("issues", [])
            failure_reason = "; ".join(
                f"[{i.get('severity','?')}] {i.get('description','')}"
                for i in issues
            )

        return {
            **state,
            "verdict": verdict,
            "passed": passed,
            "failure_reason": failure_reason,
            "status": "reviewed",
            "messages": [
                AIMessage(
                    content=f"Validation {'PASSED' if passed else 'FAILED'} for {state['paragraph']}."
                )
            ],
        }
    except json.JSONDecodeError:
        # Non-JSON response — treat as manual review needed
        logger.warning("LLM returned non-JSON verdict")
        return {
            **state,
            "verdict": {"pass": False, "issues": [{"severity": "WARNING", "description": "Non-JSON LLM response"}]},
            "passed": False,
            "failure_reason": "LLM returned non-JSON — needs manual review",
            "status": "reviewed",
            "messages": [AIMessage(content="Validation returned non-JSON — flagged for review.")],
        }
    except Exception as exc:
        logger.exception("LLM review failed")
        return {
            **state,
            "status": "failed",
            "error": str(exc),
            "messages": [AIMessage(content=f"Review error: {exc}")],
        }


def _store_verdict(state: ValidationState) -> ValidationState:
    """Update the Paragraph node status in Neo4j based on validation result."""
    if state["status"] == "failed":
        return state
    try:
        neo4j = Neo4jTools()
        new_status = "validated" if state["passed"] else "failed"
        cypher = """
        MATCH (p:Paragraph {name: $name, program: $program})
        SET p.status         = $status,
            p.validation_pass = $passed,
            p.failure_reason  = $reason,
            p.updated         = timestamp()
        """
        neo4j.write(
            cypher,
            {
                "name": state["paragraph"],
                "program": state["program"],
                "status": new_status,
                "passed": state["passed"],
                "reason": state.get("failure_reason", ""),
            },
        )
        neo4j.close()
        return {
            **state,
            "status": "verdict_stored",
            "messages": [AIMessage(content=f"Verdict stored: {new_status}")],
        }
    except Exception as exc:
        logger.exception("Failed to store verdict")
        return {
            **state,
            "status": "failed",
            "error": str(exc),
            "messages": [AIMessage(content=f"Store error: {exc}")],
        }


# ------------------------------------------------------------------ #
#  Graph assembly                                                     #
# ------------------------------------------------------------------ #

class ValidationAgent:
    """
    LangGraph-powered agent that validates generated Java code.

    Usage::

        agent = ValidationAgent()
        result = agent.run(program="POLICY", paragraph="CALC-PREMIUM")
        if not result["passed"]:
            # Re-queue to MigrationAgent with result["failure_reason"]
    """

    def __init__(self):
        self._graph = self._build_graph()

    def _build_graph(self):
        builder = StateGraph(ValidationState)

        builder.add_node("fetch_for_validation", _fetch_for_validation)
        builder.add_node("review_with_llm", _review_with_llm)
        builder.add_node("store_verdict", _store_verdict)

        builder.add_edge(START, "fetch_for_validation")
        builder.add_conditional_edges(
            "fetch_for_validation",
            lambda s: END if s["status"] == "failed" else "review_with_llm",
        )
        builder.add_conditional_edges(
            "review_with_llm",
            lambda s: END if s["status"] == "failed" else "store_verdict",
        )
        builder.add_edge("store_verdict", END)

        return builder.compile()

    def run(self, program: str, paragraph: str) -> Dict:
        """Validate a single paragraph. Returns final state dict."""
        initial: ValidationState = {
            "messages": [HumanMessage(content=f"Validate {paragraph} in {program}")],
            "program": program,
            "paragraph": paragraph,
            "subgraph": {},
            "java_code": "",
            "verdict": {},
            "passed": False,
            "failure_reason": "",
            "status": "starting",
            "error": "",
        }
        return self._graph.invoke(initial)
