"""
LLM tools — thin wrapper around LangChain ChatAnthropic / ChatOpenAI.
Provides a single interface used by all agents.
"""

import logging
import time
from typing import Optional

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_core.language_models import BaseChatModel

from config.settings import get_settings

logger = logging.getLogger(__name__)

# Anthropic API keys are always "sk-ant-api03-…" or "sk-ant-…"
_ANTHROPIC_KEY_PREFIX = "sk-ant-"
# OpenAI keys start with "sk-"
_OPENAI_KEY_PREFIX = "sk-"


def _validate_key(key: str, provider: str, prefix: str) -> None:
    """Raise EnvironmentError if *key* looks like a placeholder or is too short."""
    if not key or "your_" in key.lower() or "_here" in key.lower():
        raise EnvironmentError(
            f"{provider} API key looks like a placeholder value. "
            f"Set a real key in your .env file or environment."
        )
    if not key.startswith(prefix):
        logger.warning(
            "%s key does not start with expected prefix '%s' — "
            "verify the key is correct.",
            provider, prefix,
        )


def _ssl_verify(settings) -> "bool | str":
    """
    Parse LLM_SSL_VERIFY into the value httpx expects:
      - True   → normal certificate verification (default)
      - False  → disable verification (corporate proxy with self-signed cert)
      - str    → path to a CA bundle PEM/CRT file
    """
    raw = settings.LLM_SSL_VERIFY.strip()
    if raw.lower() == "false":
        logger.warning(
            "LLM SSL verification DISABLED (LLM_SSL_VERIFY=false). "
            "Set LLM_SSL_VERIFY to your corporate CA bundle path for a more secure option."
        )
        return False
    if raw.lower() not in ("true", "1", ""):
        # Treat any other non-boolean value as a CA-bundle path
        logger.info("LLM SSL: using custom CA bundle at %s", raw)
        return raw
    return True


def build_llm(model_override: Optional[str] = None) -> BaseChatModel:
    """
    Build and return the configured LLM instance.
    Prefers Anthropic Claude; falls back to OpenAI if ANTHROPIC_API_KEY is absent.

    SDK-level retries are disabled (max_retries=0) so that LLMTools.call_with_retry
    has full control over back-off and error reporting.

    A custom httpx client is injected when LLM_SSL_VERIFY is overridden, which is
    needed behind corporate proxies that use self-signed certificate chains.
    """
    import httpx

    settings = get_settings()
    model = model_override or settings.LLM_MODEL
    verify = _ssl_verify(settings)

    if settings.ANTHROPIC_API_KEY:
        _validate_key(settings.ANTHROPIC_API_KEY, "Anthropic", _ANTHROPIC_KEY_PREFIX)
        from langchain_anthropic import ChatAnthropic
        logger.info("Using Anthropic model: %s", model)
        return ChatAnthropic(
            model=model,
            api_key=settings.ANTHROPIC_API_KEY,
            temperature=settings.LLM_TEMPERATURE,
            max_tokens=settings.LLM_MAX_TOKENS,
            max_retries=0,  # let call_with_retry handle retries
            http_client=httpx.Client(verify=verify) if verify is not True else None,
            http_async_client=httpx.AsyncClient(verify=verify) if verify is not True else None,
        )

    if settings.OPENAI_API_KEY:
        _validate_key(settings.OPENAI_API_KEY, "OpenAI", _OPENAI_KEY_PREFIX)
        from langchain_openai import ChatOpenAI
        logger.info("Using OpenAI model: %s", model)
        return ChatOpenAI(
            model=model,
            api_key=settings.OPENAI_API_KEY,
            temperature=settings.LLM_TEMPERATURE,
            max_tokens=settings.LLM_MAX_TOKENS,
            max_retries=0,  # let call_with_retry handle retries
            http_client=httpx.Client(verify=verify) if verify is not True else None,
            http_async_client=httpx.AsyncClient(verify=verify) if verify is not True else None,
        )

    raise EnvironmentError(
        "No LLM API key found. Set ANTHROPIC_API_KEY or OPENAI_API_KEY in your .env file."
    )


class LLMTools:
    """Convenience wrapper for agent LLM calls."""

    def __init__(self, model_override: Optional[str] = None):
        self._llm = build_llm(model_override)

    def call(self, system_prompt: str, human_prompt: str) -> str:
        """
        Send a system + human message pair and return the text response.
        """
        messages = [
            SystemMessage(content=system_prompt),
            HumanMessage(content=human_prompt),
        ]
        response = self._llm.invoke(messages)
        return response.content

    def call_with_retry(
        self, system_prompt: str, human_prompt: str, max_retries: int = 3
    ) -> str:
        """Call LLM with simple retry logic on transient errors."""
        settings = get_settings()
        delay = settings.RETRY_DELAY_SECONDS
        last_error: Optional[Exception] = None

        for attempt in range(1, max_retries + 1):
            try:
                return self.call(system_prompt, human_prompt)
            except Exception as exc:
                last_error = exc
                # Surface the actual error class + message so it's actionable
                logger.warning(
                    "LLM call attempt %d/%d failed [%s]: %s",
                    attempt, max_retries,
                    type(exc).__name__,
                    exc,
                )
                if attempt < max_retries:
                    sleep_for = delay * (2 ** (attempt - 1))
                    logger.info("Retrying in %ss…", sleep_for)
                    time.sleep(sleep_for)

        raise RuntimeError(f"LLM call failed after {max_retries} retries") from last_error

