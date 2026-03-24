import asyncio
import json
from datetime import datetime
from typing import Optional

import asyncpg
from fastapi import APIRouter, Depends, HTTPException, BackgroundTasks, Query, Request
from sse_starlette.sse import EventSourceResponse

from ..database import get_pool
from ..schemas import MigrationRunResponse, MigrationLogResponse
from ..services.migration_service import run_migration, run_retry

router = APIRouter(prefix="/api/projects", tags=["migration"])


def _run_row(row) -> dict:
    return {
        "id":           row["id"],
        "project_id":   row["project_id"],
        "status":       row["status"],
        "started_at":   row["started_at"],
        "completed_at": row["completed_at"],
        "created_at":   row["created_at"],
    }


@router.post("/{project_id}/migrate", response_model=MigrationRunResponse, status_code=201)
async def start_migration(
    project_id: str,
    background_tasks: BackgroundTasks,
    pool: asyncpg.Pool = Depends(get_pool),
):
    project = await pool.fetchrow("SELECT * FROM projects WHERE id=$1", project_id)
    if not project:
        raise HTTPException(status_code=404, detail="Project not found")
    if not project["cobol_dir"]:
        raise HTTPException(status_code=400, detail="No COBOL source uploaded yet")
    if project["status"] == "migrating":
        raise HTTPException(status_code=409, detail="Migration already in progress")

    run = await pool.fetchrow(
        "INSERT INTO migration_runs (project_id) VALUES ($1) RETURNING *",
        project_id,
    )
    run_id    = str(run["id"])
    proj_id   = str(project["id"])
    cobol_dir = project["cobol_dir"]
    copy_dir  = project["copybook_dir"]

    background_tasks.add_task(run_migration, pool, run_id, proj_id, cobol_dir, copy_dir)
    return _run_row(run)


@router.post("/{project_id}/runs/{run_id}/retry", response_model=MigrationRunResponse, status_code=201)
async def retry_failed(
    project_id: str,
    run_id: str,
    background_tasks: BackgroundTasks,
    program: Optional[str] = Query(default=None, description="Limit retry to a specific program"),
    pool: asyncpg.Pool = Depends(get_pool),
):
    """Create a new run that resets all failed paragraphs and re-migrates them."""
    original = await pool.fetchrow(
        "SELECT * FROM migration_runs WHERE id=$1 AND project_id=$2", run_id, project_id
    )
    if not original:
        raise HTTPException(status_code=404, detail="Run not found")
    if original["status"] not in ("failed", "completed"):
        raise HTTPException(status_code=409, detail="Can only retry a completed or failed run")

    project = await pool.fetchrow("SELECT * FROM projects WHERE id=$1", project_id)
    if project["status"] == "migrating":
        raise HTTPException(status_code=409, detail="Migration already in progress")

    retry_run = await pool.fetchrow(
        "INSERT INTO migration_runs (project_id) VALUES ($1) RETURNING *",
        project_id,
    )
    background_tasks.add_task(run_retry, pool, str(retry_run["id"]), project_id, program)
    return _run_row(retry_run)


@router.get("/{project_id}/runs", response_model=list[MigrationRunResponse])
async def list_runs(project_id: str, pool: asyncpg.Pool = Depends(get_pool)):
    rows = await pool.fetch(
        "SELECT * FROM migration_runs WHERE project_id=$1 ORDER BY created_at DESC",
        project_id,
    )
    return [_run_row(r) for r in rows]


@router.get("/{project_id}/runs/{run_id}", response_model=MigrationRunResponse)
async def get_run(project_id: str, run_id: str, pool: asyncpg.Pool = Depends(get_pool)):
    row = await pool.fetchrow(
        "SELECT * FROM migration_runs WHERE id=$1 AND project_id=$2", run_id, project_id
    )
    if not row:
        raise HTTPException(status_code=404, detail="Run not found")
    return _run_row(row)


@router.get("/{project_id}/runs/{run_id}/logs", response_model=list[MigrationLogResponse])
async def get_logs(project_id: str, run_id: str, pool: asyncpg.Pool = Depends(get_pool)):
    rows = await pool.fetch(
        "SELECT * FROM migration_logs WHERE run_id=$1 ORDER BY id", run_id
    )
    return [dict(r) for r in rows]


@router.get("/{project_id}/runs/{run_id}/stream")
async def stream_logs(
    project_id: str,
    run_id: str,
    request: Request,
    pool: asyncpg.Pool = Depends(get_pool),
):
    async def generator():
        last_id = 0
        while True:
            if await request.is_disconnected():
                break

            async with pool.acquire() as conn:
                logs = await conn.fetch(
                    """SELECT id, logged_at, level, step, message
                       FROM migration_logs
                       WHERE run_id=$1 AND id > $2
                       ORDER BY id LIMIT 50""",
                    run_id, last_id,
                )
                for log in logs:
                    last_id = log["id"]
                    yield {
                        "data": json.dumps({
                            "id":        log["id"],
                            "level":     log["level"],
                            "step":      log["step"],
                            "message":   log["message"],
                            "timestamp": log["logged_at"].isoformat(),
                        })
                    }

                # Check if run finished
                run = await conn.fetchrow(
                    "SELECT status FROM migration_runs WHERE id=$1", run_id
                )
                if run and run["status"] in ("completed", "failed") and not logs:
                    yield {
                        "event": "done",
                        "data":  json.dumps({"status": run["status"]}),
                    }
                    break

            await asyncio.sleep(0.4)

    return EventSourceResponse(generator())
