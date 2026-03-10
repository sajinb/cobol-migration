"""
COBOL Migration Pipeline — Entry Point
=======================================
Run the full pipeline:
    python main.py

Or individual phases:
    python main.py --phase ingest
    python main.py --phase analyse --program POLICY
    python main.py --phase migrate --program POLICY
    python main.py --phase validate --program POLICY
    python main.py --phase report
"""

import argparse
import json
import logging
import sys
from pathlib import Path

from config.settings import get_settings
from agents.orchestrator_agent import OrchestratorAgent
from agents.ingestion_agent import IngestionAgent
from agents.analysis_agent import AnalysisAgent
from agents.migration_agent import MigrationAgent
from agents.validation_agent import ValidationAgent
from graph.schema import apply_schema
from tools.neo4j_tools import Neo4jTools
from graph.queries import GraphQueries


def _configure_logging():
    settings = get_settings()
    logging.basicConfig(
        level=getattr(logging, settings.LOG_LEVEL, logging.INFO),
        format="%(asctime)s  %(levelname)-8s  %(name)s — %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )


def _print_report(report: dict):
    print("\n" + "=" * 60)
    print("  COBOL Migration Report")
    print("=" * 60)
    counts = report.get("status_counts", {})
    for status, count in sorted(counts.items()):
        print(f"  {status:<15} {count}")
    circulars = report.get("circular_call_chains", [])
    if circulars:
        print(f"\n  Circular call chains detected ({len(circulars)}):")
        for c in circulars:
            print(f"    {' → '.join(c.get('cycle', []))}")
    retries = report.get("retry_queue", [])
    if retries:
        print(f"\n  Paragraphs still failing ({len(retries)}):")
        for r in retries:
            print(f"    {r['program']}.{r['paragraph']}: {r.get('reason', '')}")
    print("=" * 60)


def cmd_full_pipeline(args):
    """Run the complete ingestion → analyse → migrate → validate pipeline."""
    agent = OrchestratorAgent()
    programs = args.programs.split(",") if args.programs else []
    result = agent.run(
        csv_path=args.csv or "",
        cobol_source_dir=args.cobol_dir or "",
        programs=programs,
    )
    _print_report(result.get("report", {}))
    return result


def cmd_ingest(args):
    settings = get_settings()
    agent = IngestionAgent()
    result = agent.run(
        csv_path=args.csv or settings.MAPA_CSV_PATH,
        cobol_source_dir=args.cobol_dir or settings.COBOL_SOURCE_DIR,
    )
    print(f"Ingestion status: {result.get('status')}")
    print(f"Counts: {result.get('counts', {})}")
    return result


def cmd_analyse(args):
    agent = AnalysisAgent()
    if args.paragraph:
        result = agent.run(program=args.program, paragraph=args.paragraph)
        print(f"Analysis result: {result.get('analysis_result', {})}")
    else:
        results = agent.run_for_program(args.program)
        print(f"Analysed {len(results)} paragraphs in {args.program}")
    return results if not args.paragraph else result


def cmd_migrate(args):
    agent = MigrationAgent()
    if args.paragraph:
        result = agent.run(program=args.program, paragraph=args.paragraph)
        print(f"Migration status: {result.get('status')}")
        if result.get("java_code"):
            print("\n--- Generated Java ---")
            print(result["java_code"])
    else:
        results = agent.run_for_program(args.program)
        print(f"Migrated {len(results)} paragraphs in {args.program}")
    return result if args.paragraph else results


def cmd_validate(args):
    agent = ValidationAgent()
    result = agent.run(program=args.program, paragraph=args.paragraph)
    verdict = result.get("verdict", {})
    passed = result.get("passed", False)
    print(f"Validation: {'PASSED' if passed else 'FAILED'}")
    if not passed:
        print(f"Issues: {json.dumps(verdict.get('issues', []), indent=2)}")
    return result


def cmd_report(_args):
    neo4j = Neo4jTools()
    queries = GraphQueries(neo4j)
    summary = queries.get_migration_summary()
    neo4j.close()
    _print_report({"status_counts": summary})
    return summary


def cmd_schema(_args):
    """Apply / verify Neo4j schema constraints and indexes."""
    neo4j = Neo4jTools()
    apply_schema(neo4j)
    neo4j.close()
    print("Neo4j schema applied successfully.")


# ------------------------------------------------------------------ #
#  CLI                                                                #
# ------------------------------------------------------------------ #

def main():
    _configure_logging()

    parser = argparse.ArgumentParser(
        description="COBOL → Java Spring Boot Migration Pipeline"
    )
    sub = parser.add_subparsers(dest="command")

    # Full pipeline
    p_full = sub.add_parser("run", help="Run the full migration pipeline")
    p_full.add_argument("--csv", help="Path to MAPA result.csv")
    p_full.add_argument("--cobol-dir", dest="cobol_dir", help="Path to COBOL source directory")
    p_full.add_argument("--programs", help="Comma-separated program names to migrate (default: all)")

    # Individual phases
    p_ingest = sub.add_parser("ingest", help="Ingest MAPA CSV into Neo4j")
    p_ingest.add_argument("--csv", help="Path to MAPA result.csv")
    p_ingest.add_argument("--cobol-dir", dest="cobol_dir", help="Path to COBOL source directory")

    p_analyse = sub.add_parser("analyse", help="Analyse paragraphs in Neo4j")
    p_analyse.add_argument("--program", required=True, help="Program name")
    p_analyse.add_argument("--paragraph", help="Specific paragraph name (default: all)")

    p_migrate = sub.add_parser("migrate", help="Migrate paragraphs to Java")
    p_migrate.add_argument("--program", required=True, help="Program name")
    p_migrate.add_argument("--paragraph", help="Specific paragraph name (default: all)")

    p_validate = sub.add_parser("validate", help="Validate generated Java code")
    p_validate.add_argument("--program", required=True, help="Program name")
    p_validate.add_argument("--paragraph", required=True, help="Paragraph name")

    sub.add_parser("report", help="Print migration status report from Neo4j")
    sub.add_parser("schema", help="Apply Neo4j schema constraints and indexes")

    args = parser.parse_args()

    dispatch = {
        "run": cmd_full_pipeline,
        "ingest": cmd_ingest,
        "analyse": cmd_analyse,
        "migrate": cmd_migrate,
        "validate": cmd_validate,
        "report": cmd_report,
        "schema": cmd_schema,
    }

    if args.command not in dispatch:
        parser.print_help()
        sys.exit(1)

    dispatch[args.command](args)


if __name__ == "__main__":
    main()
