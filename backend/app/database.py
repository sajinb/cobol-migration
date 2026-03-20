import asyncpg
from fastapi import Request
from .config import get_settings


async def create_pool() -> asyncpg.Pool:
    return await asyncpg.create_pool(get_settings().DATABASE_URL, min_size=2, max_size=10)


def get_pool(request: Request) -> asyncpg.Pool:
    return request.app.state.pool


async def init_schema(pool: asyncpg.Pool) -> None:
    import os
    sql_path = os.path.join(os.path.dirname(os.path.dirname(__file__)), "init_db.sql")
    with open(sql_path) as f:
        sql = f.read()
    async with pool.acquire() as conn:
        await conn.execute(sql)
