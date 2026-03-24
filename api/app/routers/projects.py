import shutil
import zipfile
import io
from pathlib import Path

import asyncpg
from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, status

from ..database import get_pool
from ..schemas import ProjectCreate, ProjectResponse, GithubSourceRequest
from ..services.github_service import clone_repo, detect_dirs
from ..config import get_settings

router = APIRouter(prefix="/api/projects", tags=["projects"])


def _row_to_project(row) -> dict:
    return {
        "id":           row["id"],
        "name":         row["name"],
        "source_type":  row["source_type"],
        "github_url":   row["github_url"],
        "cobol_dir":    row["cobol_dir"],
        "copybook_dir": row["copybook_dir"],
        "status":       row["status"],
        "created_at":   row["created_at"],
        "updated_at":   row["updated_at"],
    }


@router.post("/", response_model=ProjectResponse, status_code=status.HTTP_201_CREATED)
async def create_project(body: ProjectCreate, pool: asyncpg.Pool = Depends(get_pool)):
    row = await pool.fetchrow(
        "INSERT INTO projects (name) VALUES ($1) RETURNING *",
        body.name,
    )
    return _row_to_project(row)


@router.get("/", response_model=list[ProjectResponse])
async def list_projects(pool: asyncpg.Pool = Depends(get_pool)):
    rows = await pool.fetch("SELECT * FROM projects ORDER BY created_at DESC")
    return [_row_to_project(r) for r in rows]


@router.get("/{project_id}", response_model=ProjectResponse)
async def get_project(project_id: str, pool: asyncpg.Pool = Depends(get_pool)):
    row = await pool.fetchrow("SELECT * FROM projects WHERE id=$1", project_id)
    if not row:
        raise HTTPException(status_code=404, detail="Project not found")
    return _row_to_project(row)


@router.delete("/{project_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_project(project_id: str, pool: asyncpg.Pool = Depends(get_pool)):
    await pool.execute("DELETE FROM projects WHERE id=$1", project_id)
    # Clean up uploaded files
    proj_dir = get_settings().projects_dir / project_id
    if proj_dir.exists():
        shutil.rmtree(proj_dir)


# ── Source: GitHub ────────────────────────────────────────────────────────────

@router.post("/{project_id}/source/github", response_model=ProjectResponse)
async def set_github_source(
    project_id: str,
    body: GithubSourceRequest,
    pool: asyncpg.Pool = Depends(get_pool),
):
    row = await pool.fetchrow("SELECT * FROM projects WHERE id=$1", project_id)
    if not row:
        raise HTTPException(status_code=404, detail="Project not found")

    settings = get_settings()
    dest = settings.projects_dir / project_id / "repo"

    try:
        await clone_repo(body.github_url, dest)
    except RuntimeError as exc:
        raise HTTPException(status_code=400, detail=str(exc))

    # Resolve cobol_dir and copybook_dir
    if body.cobol_subfolder:
        cobol_dir = str(dest / body.cobol_subfolder)
    else:
        detected_cobol, detected_copy = detect_dirs(dest)
        cobol_dir = detected_cobol
        if not body.copybook_subfolder:
            body.copybook_subfolder = None  # will be detected

    copybook_dir = (
        str(dest / body.copybook_subfolder) if body.copybook_subfolder
        else detect_dirs(dest)[1]
    )

    updated = await pool.fetchrow(
        """UPDATE projects
           SET source_type='github', github_url=$1, cobol_dir=$2,
               copybook_dir=$3, status='ready'
           WHERE id=$4 RETURNING *""",
        body.github_url, cobol_dir, copybook_dir, project_id,
    )
    return _row_to_project(updated)


# ── Source: ZIP upload ────────────────────────────────────────────────────────

@router.post("/{project_id}/source/upload", response_model=ProjectResponse)
async def upload_source(
    project_id: str,
    file: UploadFile = File(...),
    pool: asyncpg.Pool = Depends(get_pool),
):
    row = await pool.fetchrow("SELECT * FROM projects WHERE id=$1", project_id)
    if not row:
        raise HTTPException(status_code=404, detail="Project not found")

    if not file.filename.endswith(".zip"):
        raise HTTPException(status_code=400, detail="Only ZIP files are accepted")

    settings = get_settings()
    dest = settings.projects_dir / project_id / "repo"
    if dest.exists():
        shutil.rmtree(dest)
    dest.mkdir(parents=True, exist_ok=True)

    content = await file.read()
    try:
        with zipfile.ZipFile(io.BytesIO(content)) as zf:
            zf.extractall(dest)
    except zipfile.BadZipFile:
        raise HTTPException(status_code=400, detail="Invalid ZIP file")

    # If the zip has a single top-level folder, descend into it
    children = list(dest.iterdir())
    if len(children) == 1 and children[0].is_dir():
        base = children[0]
    else:
        base = dest

    cobol_dir, copybook_dir = detect_dirs(base)

    updated = await pool.fetchrow(
        """UPDATE projects
           SET source_type='upload', cobol_dir=$1, copybook_dir=$2, status='ready'
           WHERE id=$3 RETURNING *""",
        cobol_dir, copybook_dir, project_id,
    )
    return _row_to_project(updated)
