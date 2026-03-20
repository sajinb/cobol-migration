import asyncio
import os
import re
import subprocess
import sys
import traceback
from datetime import datetime, timezone

import asyncpg

from ..config import get_settings

# Log line format:  2024-01-01 12:00:00  INFO      agents.migration_agent — msg
_LOG_RE = re.compile(r"\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\s+(\w+)\s+(\S+)\s+[—-]")

_STEP_MAP = {
    "ingestion":  "ingest",
    "analysis":   "analyse",
    "migration":  "migrate",
    "validation": "validate",
    "mapa":       "mapa",
    "importer":   "ingest",
    # orchestrator logs span all phases — do not map to a stage so the last
    # known sub-agent stage remains active in the UI.
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


def _subprocess_env(settings) -> dict:
    """
    Build an env dict for pipeline subprocesses.
    Forwards MAPA_JAR_PATH and MAPA_AUTO_DOWNLOAD from the backend Settings
    so the pipeline always uses the values declared here, regardless of what
    the pipeline's own .env file contains.
    """
    env = os.environ.copy()
    env["MAPA_JAR_PATH"] = settings.MAPA_JAR_PATH
    env["MAPA_AUTO_DOWNLOAD"] = str(settings.MAPA_AUTO_DOWNLOAD).lower()
    return env


def _validate_pipeline(settings) -> None:
    """
    Raise RuntimeError with a clear, actionable message if the pipeline
    entry-point cannot be found or is the wrong main.py.

    Two failure modes are caught:
    1. main.py does not exist at all.
    2. main.py exists but belongs to the backend (backend/app/main.py) rather
       than the pipeline root — detected by the absence of an agents/ directory
       alongside it.  This happens when COBOL_MIGRATION_DIR is auto-computed
       from __file__ and resolves to backend/app/ instead of the project root.
    """
    from pathlib import Path as _Path

    def _fix_hint():
        return (
            f"Fix — add one of the following to your backend/.env file:\n"
            f"\n"
            f"  Option A — point directly to main.py:\n"
            f"    PIPELINE_MAIN_PY=C:\\path\\to\\cobol-migration\\main.py\n"
            f"\n"
            f"  Option B — set the pipeline root directory:\n"
            f"    COBOL_MIGRATION_DIR=C:\\path\\to\\cobol-migration\n"
            f"\n"
            f"Current values:\n"
            f"  COBOL_MIGRATION_DIR = {settings.COBOL_MIGRATION_DIR}\n"
            f"  PIPELINE_MAIN_PY    = {settings.PIPELINE_MAIN_PY or '(not set)'}\n"
        )

    main_py = _Path(settings.main_py)

    if not main_py.exists():
        raise RuntimeError(
            f"Pipeline entry-point not found: {main_py}\n"
            f"\n"
            f"The backend cannot locate main.py. This usually means the backend\n"
            f"is deployed in a different directory from the pipeline code.\n"
            f"\n"
            + _fix_hint()
        )

    # Verify this is the pipeline's main.py and not the backend's main.py.
    # The pipeline root always contains an agents/ directory; the backend does not.
    pipeline_dir = main_py.parent
    if not (pipeline_dir / "agents").is_dir():
        raise RuntimeError(
            f"Wrong main.py resolved: {main_py}\n"
            f"\n"
            f"The file exists but it appears to be the backend entry-point, not\n"
            f"the pipeline entry-point (no 'agents/' directory found alongside it).\n"
            f"COBOL_MIGRATION_DIR is likely pointing at backend/app/ instead of\n"
            f"the project root.\n"
            f"\n"
            + _fix_hint()
        )


async def _run_subprocess(cmd: list, cwd: str, env: dict, line_cb) -> int:
    """
    Run cmd as a subprocess and call line_cb(text) for each output line.
    Returns the process exit code.

    Uses subprocess.Popen in a thread-pool executor instead of
    asyncio.create_subprocess_exec so it works on Windows SelectorEventLoop
    (uvicorn on Windows does not always use ProactorEventLoop even though
    Python 3.8+ sets it as the default policy — the policy set at import
    time can be overridden by uvicorn before the event loop is created).
    """
    loop = asyncio.get_running_loop()
    queue: asyncio.Queue = asyncio.Queue()

    popen_kwargs: dict = {
        "stdout": subprocess.PIPE,
        "stderr": subprocess.STDOUT,
        "cwd":    cwd,
        "env":    env,
    }
    if sys.platform == "win32":
        popen_kwargs["creationflags"] = subprocess.CREATE_NO_WINDOW

    def _reader() -> int:
        try:
            proc = subprocess.Popen(cmd, **popen_kwargs)
            for raw in proc.stdout:
                loop.call_soon_threadsafe(queue.put_nowait, raw)
            proc.stdout.close()
            return proc.wait()
        except Exception as exc:
            msg = f"[runner] {type(exc).__name__}: {exc}\n{traceback.format_exc()}"
            loop.call_soon_threadsafe(queue.put_nowait, msg.encode())
            return -1
        finally:
            loop.call_soon_threadsafe(queue.put_nowait, None)  # sentinel

    future = loop.run_in_executor(None, _reader)

    while True:
        item = await queue.get()
        if item is None:
            break
        text = item.decode("utf-8", errors="replace").rstrip()
        if text:
            await line_cb(text)

    return await future


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
        _validate_pipeline(settings)

        async def _log(text):
            await _save_log(pool, run_id, text, _parse_level(text), _parse_step(text))

        returncode = await _run_subprocess(cmd, settings.COBOL_MIGRATION_DIR,
                                           _subprocess_env(settings), _log)
        final_status = "completed" if returncode == 0 else "failed"
        if returncode != 0:
            await _save_log(pool, run_id,
                            f"Process exited with code {returncode}", "ERROR")

    except Exception as exc:
        detail = f"{type(exc).__name__}: {exc}" if str(exc) else type(exc).__name__
        await _save_log(pool, run_id,
                        f"Subprocess error: {detail}\n{traceback.format_exc()}",
                        "ERROR")
        final_status = "failed"

    # Save the terminal log line BEFORE updating run status so the SSE stream
    # never sends the "done" event before this message is committed.
    await _save_log(pool, run_id,
                    f"Migration {final_status}.",
                    "INFO" if final_status == "completed" else "ERROR")

    async with pool.acquire() as conn:
        await conn.execute(
            "UPDATE migration_runs SET status=$1, completed_at=$2 WHERE id=$3",
            final_status, datetime.now(timezone.utc), run_id,
        )
        await conn.execute("UPDATE projects SET status=$1 WHERE id=$2", final_status, project_id)


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
        _validate_pipeline(settings)

        async def _log(text):
            await _save_log(pool, run_id, text, _parse_level(text), _parse_step(text))

        returncode = await _run_subprocess(cmd, settings.COBOL_MIGRATION_DIR,
                                           _subprocess_env(settings), _log)
        final_status = "completed" if returncode == 0 else "failed"
        if returncode != 0:
            await _save_log(pool, run_id,
                            f"Process exited with code {returncode}", "ERROR")

    except Exception as exc:
        detail = f"{type(exc).__name__}: {exc}" if str(exc) else type(exc).__name__
        await _save_log(pool, run_id,
                        f"Subprocess error: {detail}\n{traceback.format_exc()}",
                        "ERROR")
        final_status = "failed"

    await _save_log(pool, run_id,
                    f"Retry {final_status}.",
                    "INFO" if final_status == "completed" else "ERROR")

    async with pool.acquire() as conn:
        await conn.execute(
            "UPDATE migration_runs SET status=$1, completed_at=$2 WHERE id=$3",
            final_status, datetime.now(timezone.utc), run_id,
        )
        await conn.execute("UPDATE projects SET status=$1 WHERE id=$2", final_status, project_id)
