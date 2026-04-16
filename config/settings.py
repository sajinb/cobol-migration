"""
Settings module for COBOL Migration Agent System.
Loads configuration from environment variables or .env file.
"""

import os
from functools import lru_cache
from dotenv import load_dotenv

load_dotenv()


class Settings:
    """Central configuration for all agents and tools."""

    # Neo4j — local instance
    NEO4J_URI: str = os.getenv("NEO4J_URI", "bolt://localhost:7687")
    NEO4J_USERNAME: str = os.getenv("NEO4J_USERNAME", "neo4j")
    NEO4J_PASSWORD: str = os.getenv("NEO4J_PASSWORD", "neo4j")
    NEO4J_DATABASE: str = os.getenv("NEO4J_DATABASE", "neo4j")
    NEO4J_HTTP_PORT: int = int(os.getenv("NEO4J_HTTP_PORT", "7474"))

    # LLM
    ANTHROPIC_API_KEY: str = os.getenv("ANTHROPIC_API_KEY", "")
    OPENAI_API_KEY: str = os.getenv("OPENAI_API_KEY", "")
    LLM_MODEL: str = os.getenv("LLM_MODEL", "claude-sonnet-4-6")
    LLM_TEMPERATURE: float = float(os.getenv("LLM_TEMPERATURE", "0.1"))
    LLM_MAX_TOKENS: int = int(os.getenv("LLM_MAX_TOKENS", "4096"))
    # SSL verification for LLM HTTP calls.
    # Set to "false" to disable (corporate proxy with self-signed cert),
    # or to an absolute path pointing to your corporate CA bundle (.pem/.crt).
    # Leave unset (default "true") for normal certificate verification.
    LLM_SSL_VERIFY: str = os.getenv("LLM_SSL_VERIFY", "true")

    # Migration Pipeline
    COBOL_SOURCE_DIR: str = os.getenv("COBOL_SOURCE_DIR", "./cobol_samples")
    MAPA_CSV_PATH: str = os.getenv("MAPA_CSV_PATH", "./cobol_samples/result.csv")
    OUTPUT_DIR: str = os.getenv("OUTPUT_DIR", "./output")
    LOG_LEVEL: str = os.getenv("LOG_LEVEL", "INFO")

    # MAPA JAR — static analysis tool that generates result.csv from COBOL source
    # Repo: https://github.com/cschneid-the-elder/mapa
    MAPA_JAR_PATH: str = os.getenv("MAPA_JAR_PATH", "./CallTree.jar")
    MAPA_JAR_URL: str = os.getenv(
        "MAPA_JAR_URL",
        "https://github.com/cschneid-the-elder/mapa/raw/refs/heads/master/cobol/CallTree.jar",
    )
    MAPA_AUTO_DOWNLOAD: bool = os.getenv("MAPA_AUTO_DOWNLOAD", "true").lower() == "true"
    MAPA_JAVA_EXECUTABLE: str = os.getenv("MAPA_JAVA_EXECUTABLE", "java")
    MAPA_JVM_OPTS: str = os.getenv("MAPA_JVM_OPTS", "-Xmx2g")
    MAPA_COPYBOOK_DIR: str = os.getenv("MAPA_COPYBOOK_DIR", "")  # empty = no -copy flag passed

    # Agent Concurrency
    MAX_PARALLEL_AGENTS: int = int(os.getenv("MAX_PARALLEL_AGENTS", "5"))
    MAX_RETRIES: int = int(os.getenv("MAX_RETRIES", "3"))
    RETRY_DELAY_SECONDS: int = int(os.getenv("RETRY_DELAY_SECONDS", "2"))

    # Optional project-level migration config YAML.
    # Run  python main.py config-init  to auto-generate a skeleton from Neo4j.
    # Leave unset or empty to run without any project config.
    MIGRATION_CONFIG_PATH: str = os.getenv("MIGRATION_CONFIG_PATH", "./migration_config.yaml")

    # Migration status values tracked in Neo4j
    STATUS_PENDING: str = "pending"
    STATUS_INGESTED: str = "ingested"
    STATUS_ANALYSED: str = "analysed"
    STATUS_MIGRATED: str = "migrated"
    STATUS_VALIDATED: str = "validated"
    STATUS_FAILED: str = "failed"


def load_migration_config(path: str = None) -> dict:
    """
    Load migration_config.yaml if it exists and return as a dict.
    Returns an empty dict when the file is absent or path is blank.
    The result is NOT cached — callers that need it at prompt-build time
    should call this once and pass it down.
    """
    import yaml

    cfg_path = path or get_settings().MIGRATION_CONFIG_PATH
    if not cfg_path:
        return {}
    from pathlib import Path as _Path
    p = _Path(cfg_path)
    if not p.exists():
        return {}
    try:
        with p.open(encoding="utf-8") as fh:
            return yaml.safe_load(fh) or {}
    except Exception:
        return {}


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """Return a cached singleton Settings instance."""
    return Settings()
