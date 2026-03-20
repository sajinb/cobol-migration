import asyncio
import shutil
from pathlib import Path


async def clone_repo(github_url: str, dest: Path) -> None:
    """Git-clone *github_url* into *dest* (removes existing dir first)."""
    if dest.exists():
        shutil.rmtree(dest)
    dest.mkdir(parents=True, exist_ok=True)
    proc = await asyncio.create_subprocess_exec(
        "git", "clone", "--depth", "1", github_url, str(dest),
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    _, stderr = await proc.communicate()
    if proc.returncode != 0:
        raise RuntimeError(f"git clone failed: {stderr.decode()}")


def detect_dirs(base: Path) -> tuple[str, str | None]:
    """Return (cobol_dir, copybook_dir) by scanning common folder names."""
    cobol_candidates = ["cobol", "src", "cbl", "programs"]
    copy_candidates  = ["copybooks", "copy", "copybook", "cpy"]

    cobol_dir: str = str(base)
    copybook_dir: str | None = None

    for name in cobol_candidates:
        candidate = base / name
        if candidate.is_dir():
            cobol_dir = str(candidate)
            break

    for name in copy_candidates:
        candidate = base / name
        if candidate.is_dir():
            copybook_dir = str(candidate)
            break

    return cobol_dir, copybook_dir
