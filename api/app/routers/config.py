import sys
from pathlib import Path

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel

import asyncpg
from ..database import get_pool
from ..config import get_settings

router = APIRouter(prefix="/api/projects", tags=["config"])


def _config_path(project_id: str) -> Path:
    settings = get_settings()
    return settings.projects_dir / project_id / "migration_config.yaml"


class ConfigBody(BaseModel):
    yaml: str


@router.post("/{project_id}/config/generate", status_code=200)
async def generate_config(
    project_id: str,
    pool: asyncpg.Pool = Depends(get_pool),
):
    """Generate migration_config.yaml skeleton from Neo4j graph data (run after ingest)."""
    project = await pool.fetchrow("SELECT id FROM projects WHERE id=$1", project_id)
    if not project:
        raise HTTPException(status_code=404, detail="Project not found")

    out_path = _config_path(project_id)
    out_path.parent.mkdir(parents=True, exist_ok=True)

    settings = get_settings()
    pipeline_dir = str(Path(settings.COBOL_MIGRATION_DIR))

    # Run the generator in-process but with sys.path adjusted to the pipeline root
    # so that config.config_generator can import tools.neo4j_tools correctly.
    if pipeline_dir not in sys.path:
        sys.path.insert(0, pipeline_dir)

    try:
        from config.config_generator import generate  # noqa: PLC0415
        generate(output_path=str(out_path))
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Config generation failed: {exc}")

    return {"path": str(out_path), "yaml": out_path.read_text(encoding="utf-8")}


@router.get("/{project_id}/config")
async def get_config(
    project_id: str,
    pool: asyncpg.Pool = Depends(get_pool),
):
    """Read migration_config.yaml for this project. Returns empty string if not yet generated."""
    project = await pool.fetchrow("SELECT id FROM projects WHERE id=$1", project_id)
    if not project:
        raise HTTPException(status_code=404, detail="Project not found")

    cfg = _config_path(project_id)
    return {"yaml": cfg.read_text(encoding="utf-8") if cfg.exists() else ""}


@router.put("/{project_id}/config")
async def save_config(
    project_id: str,
    body: ConfigBody,
    pool: asyncpg.Pool = Depends(get_pool),
):
    """Save migration_config.yaml content for this project."""
    project = await pool.fetchrow("SELECT id FROM projects WHERE id=$1", project_id)
    if not project:
        raise HTTPException(status_code=404, detail="Project not found")

    cfg = _config_path(project_id)
    cfg.parent.mkdir(parents=True, exist_ok=True)
    cfg.write_text(body.yaml, encoding="utf-8")
    return {"saved": True}
