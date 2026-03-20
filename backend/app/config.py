from pathlib import Path
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    DATABASE_URL: str = "postgresql://postgres:postgres@localhost:5432/cobol_migration"
    # Root of the cobol-migration repo (parent of backend/)
    COBOL_MIGRATION_DIR: str = str(Path(__file__).parent.parent.parent)
    CORS_ORIGINS: list[str] = ["http://localhost:5173", "http://localhost:3000"]

    # MAPA JAR — set to a custom absolute path when the JARs are not in the project root
    MAPA_JAR_PATH: str = "./CallTree.jar"
    MAPA_AUTO_DOWNLOAD: bool = True

    class Config:
        env_file = ".env"

    @property
    def main_py(self) -> str:
        return str(Path(self.COBOL_MIGRATION_DIR) / "main.py")

    @property
    def projects_dir(self) -> Path:
        p = Path(self.COBOL_MIGRATION_DIR) / "projects"
        p.mkdir(parents=True, exist_ok=True)
        return p


_settings: Settings | None = None


def get_settings() -> Settings:
    global _settings
    if _settings is None:
        _settings = Settings()
    return _settings
