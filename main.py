"""
COBOL Migration Pipeline — Entry Point
=======================================
Run the full pipeline (MAPA runs automatically if result.csv is missing):
    python main.py run

Or individual phases:
    python main.py mapa     --cobol-dir ./cobol_samples
    python main.py ingest   --cobol-dir ./cobol_samples
    python main.py analyse  --program POLICY
    python main.py migrate  --program POLICY
    python main.py validate                                   # all migrated paragraphs
    python main.py validate --program POLICY                  # all paragraphs in program
    python main.py validate --program POLICY --paragraph CALC-PREMIUM
    python main.py report
    python main.py schema
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
from tools.mapa_runner import MapaRunner
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
        copybook_dir=args.copy or "",
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
        copybook_dir=args.copy or settings.MAPA_COPYBOOK_DIR,
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
    force = getattr(args, "force", False)
    if args.paragraph:
        result = agent.run(program=args.program, paragraph=args.paragraph)
        print(f"Migration status: {result.get('status')}")
        if result.get("java_code"):
            print("\n--- Generated Java ---")
            print(result["java_code"])
    else:
        results = agent.run_for_program(args.program, force=force)
        print(f"Migrated {len(results)} paragraphs in {args.program}")
    return result if args.paragraph else results


def cmd_validate(args):
    agent = ValidationAgent()
    program = getattr(args, "program", None)
    paragraph = getattr(args, "paragraph", None)

    if paragraph:
        # Single paragraph
        result = agent.run(program=program, paragraph=paragraph)
        passed = result.get("passed", False)
        print(f"Validation: {'PASSED' if passed else 'FAILED'}")
        if not passed:
            print(f"Issues: {json.dumps(result.get('verdict', {}).get('issues', []), indent=2)}")
        return result

    # Batch: all migrated paragraphs (optionally filtered by program)
    neo4j = Neo4jTools()
    rows = neo4j.get_paragraphs_by_status("migrated")
    neo4j.close()
    if program:
        rows = [r for r in rows if r["program"] == program]

    passed_count = failed_count = 0
    failures = []
    for row in rows:
        r = agent.run(program=row["program"], paragraph=row["name"])
        if r.get("passed"):
            passed_count += 1
        else:
            failed_count += 1
            failures.append({
                "paragraph": f"{row['program']}.{row['name']}",
                "reason": r.get("failure_reason", ""),
            })

    print(f"\nValidation complete — PASSED: {passed_count}, FAILED: {failed_count}")
    for f in failures:
        print(f"  FAILED  {f['paragraph']}: {f['reason']}")
    return {"passed": failed_count == 0, "passed_count": passed_count, "failed_count": failed_count}


def cmd_report(_args):
    neo4j = Neo4jTools()
    queries = GraphQueries(neo4j)
    summary = queries.get_migration_summary()
    neo4j.close()
    _print_report({"status_counts": summary})
    return summary


def cmd_mapa(args):
    """Run the MAPA JAR against a COBOL source directory to produce result.csv."""
    settings = get_settings()

    runner = MapaRunner(
        jar_path=args.jar or settings.MAPA_JAR_PATH,
        jar_url=settings.MAPA_JAR_URL,
        java_executable=settings.MAPA_JAVA_EXECUTABLE,
        jvm_opts=args.jvm_opts or settings.MAPA_JVM_OPTS,
        auto_download=settings.MAPA_AUTO_DOWNLOAD,
    )
    result = runner.run(
        cobol_dir=args.cobol_dir or settings.COBOL_SOURCE_DIR,
        output_csv=args.output or settings.MAPA_CSV_PATH,
        copybook_dir=args.copy or settings.MAPA_COPYBOOK_DIR or None,
    )

    if result["success"]:
        print(f"MAPA succeeded.")
        print(f"  CSV written to : {result['csv_path']}")
        print(f"  Rows generated : {result.get('row_count', '?')}")
    else:
        print(f"MAPA FAILED: {result['error']}")
        if result.get("stderr"):
            print(f"  stderr: {result['stderr'][:500]}")
        sys.exit(1)
    return result


def cmd_assemble(args):
    """Assemble all migrated paragraph fragments for a program into a single @Service class."""
    agent = MigrationAgent()
    out_path = agent._assemble_and_write_service(args.program)
    if out_path:
        print(f"Service class written: {out_path}")
    else:
        print(f"No migrated code found for {args.program}.")
    return out_path


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
    p_full.add_argument("--copy", help="Path to copybook directory (passed to CallTree.jar -copy)")
    p_full.add_argument("--programs", help="Comma-separated program names to migrate (default: all)")

    # MAPA runner — generate result.csv from COBOL sources
    p_mapa = sub.add_parser("mapa", help="Run CallTree.jar to generate result.csv from COBOL source files")
    p_mapa.add_argument("--jar", help="Path to CallTree.jar (auto-downloaded if absent)")
    p_mapa.add_argument("--cobol-dir", dest="cobol_dir", help="Path to COBOL source directory")
    p_mapa.add_argument("--copy", help="Path to copybook directory (passed to CallTree.jar -copy)")
    p_mapa.add_argument("--output", help="Output CSV path (default: settings.MAPA_CSV_PATH)")
    p_mapa.add_argument("--jvm-opts", dest="jvm_opts", help="JVM options, e.g. '-Xmx4g'")

    # Individual phases
    p_ingest = sub.add_parser("ingest", help="Run MAPA (if needed) then ingest CSV into Neo4j")
    p_ingest.add_argument("--csv", help="Path to MAPA result.csv")
    p_ingest.add_argument("--cobol-dir", dest="cobol_dir", help="Path to COBOL source directory")
    p_ingest.add_argument("--copy", help="Path to copybook directory (passed to CallTree.jar -copy)")

    p_analyse = sub.add_parser("analyse", help="Analyse paragraphs in Neo4j")
    p_analyse.add_argument("--program", required=True, help="Program name")
    p_analyse.add_argument("--paragraph", help="Specific paragraph name (default: all)")

    p_migrate = sub.add_parser("migrate", help="Migrate paragraphs to Java")
    p_migrate.add_argument("--program", required=True, help="Program name")
    p_migrate.add_argument("--paragraph", help="Specific paragraph name (default: all)")
    p_migrate.add_argument(
        "--force", action="store_true",
        help="Reset already-migrated/validated paragraphs and re-generate Java code",
    )

    p_validate = sub.add_parser("validate", help="Validate generated Java code")
    p_validate.add_argument("--program", help="Program name (default: all programs)")
    p_validate.add_argument("--paragraph", help="Paragraph name (default: all migrated paragraphs)")

    p_assemble = sub.add_parser("assemble", help="Assemble migrated fragments into a single @Service class file")
    p_assemble.add_argument("--program", required=True, help="Program name")

    sub.add_parser("report", help="Print migration status report from Neo4j")
    sub.add_parser("schema", help="Apply Neo4j schema constraints and indexes")

    args = parser.parse_args()

    dispatch = {
        "run": cmd_full_pipeline,
        "mapa": cmd_mapa,
        "ingest": cmd_ingest,
        "analyse": cmd_analyse,
        "migrate": cmd_migrate,
        "validate": cmd_validate,
        "assemble": cmd_assemble,
        "report": cmd_report,
        "schema": cmd_schema,
    }

    if args.command not in dispatch:
        parser.print_help()
        sys.exit(1)

    dispatch[args.command](args)


if __name__ == "__main__":
    main()
