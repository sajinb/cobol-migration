import asyncio
import sys
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from .config import get_settings
from .database import create_pool, init_schema
from .routers import projects, migration

# On Windows, asyncio.create_subprocess_exec requires ProactorEventLoop.
# Python 3.8+ sets this as default, but uvicorn can override it.
# Explicitly enforce it here so subprocess spawning always works.
if sys.platform == "win32":
    asyncio.set_event_loop_policy(asyncio.WindowsProactorEventLoopPolicy())


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    app.state.pool = await create_pool()
    await init_schema(app.state.pool)
    yield
    await app.state.pool.close()


app = FastAPI(title="COBOL Migration API", version="1.0.0", lifespan=lifespan)

settings = get_settings()
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(projects.router)
app.include_router(migration.router)


@app.get("/health")
async def health():
    return {"status": "ok"}
