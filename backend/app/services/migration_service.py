import asyncio
import re
import sys
from datetime import datetime, timezone

import asyncpg

from ..config import get_settings

# Log line format:  2024-01-01 12:00:00  INFO      agents.migration_agent — msg
_LOG_RE = re.compile(r"\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\s+(\w+)\s+(\S+)\s+[—-]")

_STEP_MAP = {
    "ingestion":    "ingest",
    "analysis":     "analyse",
    "migration":    "migrate",
    "validation":   "validate",
    "orchestrator": "orchestrate",
    "mapa":         "mapa",
    "importer":     "ingest",
}


def _parse_level(line: str) -> str:
    m = _LOG_RE.match(line)
    if m:
        return m.group(1).upper()
    low = line.lower()
    if "error" in low:
        return "ERROR"
    if "warn" in low:
        return "WARNING"
    return "INFO"


def _parse_step(line: str) -> str | None:
    m = _LOG_RE.match(line)
    if m:
        module = m.group(2).lower()
        for keyword, step in _STEP_MAP.items():
            if keyword in module:
                return step
    return None


async def _save_log(pool: asyncpg.Pool, run_id: str, message: str,
                    level: str = "INFO", step: str | None = None) -> None:
    async with pool.acquire() as conn:
        await conn.execute(
            "INSERT INTO migration_logs (run_id, level, step, message) VALUES ($1, $2, $3, $4)",
            run_id, level, step, message,
        )


async def run_migration(pool: asyncpg.Pool, run_id: str, project_id: str,
                        cobol_dir: str, copybook_dir: str | None) -> None:
    """Background task: spawn main.py, stream logs into DB, update run/project status."""
    settings = get_settings()

    async with pool.acquire() as conn:
        await conn.execute(
            "UPDATE migration_runs SET status='running', started_at=$1 WHERE id=$2",
            datetime.now(timezone.utc), run_id,
        )
        await conn.execute("UPDATE projects SET status='migrating' WHERE id=$1", project_id)

    cmd = [sys.executable, settings.main_py, "run", "--cobol-dir", cobol_dir]
    if copybook_dir:
        cmd += ["--copy", copybook_dir]

    try:
        proc = await asyncio.create_subprocess_exec(
            *cmd,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.STDOUT,
            cwd=settings.COBOL_MIGRATION_DIR,
        )

        async for raw in proc.stdout:
            text = raw.decode("utf-8", errors="replace").rstrip()
            if text:
                await _save_log(pool, run_id, text, _parse_level(text), _parse_step(text))

        await proc.wait()
        final_status = "completed" if proc.returncode == 0 else "failed"

    except Exception as exc:
        await _save_log(pool, run_id, f"Subprocess error: {exc}", "ERROR", "error")
        final_status = "failed"

    async with pool.acquire() as conn:
        await conn.execute(
            "UPDATE migration_runs SET status=$1, completed_at=$2 WHERE id=$3",
            final_status, datetime.now(timezone.utc), run_id,
        )
        await conn.execute("UPDATE projects SET status=$1 WHERE id=$2", final_status, project_id)

    await _save_log(pool, run_id,
                    f"Migration {final_status}.",
                    "INFO" if final_status == "completed" else "ERROR",
                    "done")


async def run_retry(pool: asyncpg.Pool, run_id: str, project_id: str,
                    program: str | None = None) -> None:
    """Background task: retry failed paragraphs via 'main.py retry [--program PROGRAM]'."""
    settings = get_settings()

    async with pool.acquire() as conn:
        await conn.execute(
            "UPDATE migration_runs SET status='running', started_at=$1 WHERE id=$2",
            datetime.now(timezone.utc), run_id,
        )
        await conn.execute("UPDATE projects SET status='migrating' WHERE id=$1", project_id)

    cmd = [sys.executable, settings.main_py, "retry"]
    if program:
        cmd += ["--program", program]

    try:
        proc = await asyncio.create_subprocess_exec(
            *cmd,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.STDOUT,
            cwd=settings.COBOL_MIGRATION_DIR,
        )

        async for raw in proc.stdout:
            text = raw.decode("utf-8", errors="replace").rstrip()
            if text:
                await _save_log(pool, run_id, text, _parse_level(text), _parse_step(text))

        await proc.wait()
        final_status = "completed" if proc.returncode == 0 else "failed"

    except Exception as exc:
        await _save_log(pool, run_id, f"Subprocess error: {exc}", "ERROR", "error")
        final_status = "failed"

    async with pool.acquire() as conn:
        await conn.execute(
            "UPDATE migration_runs SET status=$1, completed_at=$2 WHERE id=$3",
            final_status, datetime.now(timezone.utc), run_id,
        )
        await conn.execute("UPDATE projects SET status=$1 WHERE id=$2", final_status, project_id)

    await _save_log(pool, run_id,
                    f"Retry {final_status}.",
                    "INFO" if final_status == "completed" else "ERROR",
                    "done")
