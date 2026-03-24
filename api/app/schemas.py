from pydantic import BaseModel
from typing import Optional
from datetime import datetime
import uuid


class ProjectCreate(BaseModel):
    name: str


class GithubSourceRequest(BaseModel):
    github_url: str
    cobol_subfolder: Optional[str] = None
    copybook_subfolder: Optional[str] = None


class ProjectResponse(BaseModel):
    id: uuid.UUID
    name: str
    source_type: Optional[str]
    github_url: Optional[str]
    cobol_dir: Optional[str]
    copybook_dir: Optional[str]
    status: str
    created_at: datetime
    updated_at: datetime


class MigrationRunResponse(BaseModel):
    id: uuid.UUID
    project_id: uuid.UUID
    status: str
    started_at: Optional[datetime]
    completed_at: Optional[datetime]
    created_at: datetime


class MigrationLogResponse(BaseModel):
    id: int
    run_id: uuid.UUID
    logged_at: datetime
    level: str
    step: Optional[str]
    message: str
