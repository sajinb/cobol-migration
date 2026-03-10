"""
LLM tools — thin wrapper around LangChain ChatAnthropic / ChatOpenAI.
Provides a single interface used by all agents.
"""

import logging
from typing import Optional

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_core.language_models import BaseChatModel

from config.settings import get_settings

logger = logging.getLogger(__name__)


def build_llm(model_override: Optional[str] = None) -> BaseChatModel:
    """
    Build and return the configured LLM instance.
    Prefers Anthropic Claude; falls back to OpenAI if ANTHROPIC_API_KEY is absent.
    """
    settings = get_settings()
    model = model_override or settings.LLM_MODEL

    if settings.ANTHROPIC_API_KEY:
        from langchain_anthropic import ChatAnthropic
        logger.info("Using Anthropic model: %s", model)
        return ChatAnthropic(
            model=model,
            api_key=settings.ANTHROPIC_API_KEY,
            temperature=settings.LLM_TEMPERATURE,
            max_tokens=settings.LLM_MAX_TOKENS,
        )

    if settings.OPENAI_API_KEY:
        from langchain_openai import ChatOpenAI
        logger.info("Using OpenAI model: %s", model)
        return ChatOpenAI(
            model=model,
            api_key=settings.OPENAI_API_KEY,
            temperature=settings.LLM_TEMPERATURE,
            max_tokens=settings.LLM_MAX_TOKENS,
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
        import time

        settings = get_settings()
        delay = settings.RETRY_DELAY_SECONDS
        last_error: Optional[Exception] = None

        for attempt in range(1, max_retries + 1):
            try:
                return self.call(system_prompt, human_prompt)
            except Exception as exc:
                last_error = exc
                logger.warning(
                    "LLM call attempt %d/%d failed: %s", attempt, max_retries, exc
                )
                if attempt < max_retries:
                    time.sleep(delay * (2 ** (attempt - 1)))

        raise RuntimeError(f"LLM call failed after {max_retries} retries") from last_error
