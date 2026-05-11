from config import STAGE0_PROVIDER, STAGE1_PROVIDER, STAGE2_PROVIDER
from services.stage0_gemini_provider import GeminiStage0Provider
from services.stage0_qwen_provider import QwenStage0Provider
from services.stage1_gemini_provider import GeminiStage1Provider
from services.stage1_qwen_provider import QwenStage1Provider
from services.stage2_gemini_provider import GeminiStage2Provider
from services.stage2_qwen_provider import QwenStage2Provider

_stage0_service = None
_stage1_service = None
_stage2_service = None


def get_stage0_service():
    global _stage0_service
    if _stage0_service is None:
        if STAGE0_PROVIDER == "gemini":
            _stage0_service = GeminiStage0Provider()
        else:
            _stage0_service = QwenStage0Provider()
    return _stage0_service


def get_stage1_service():
    global _stage1_service
    if _stage1_service is None:
        if STAGE1_PROVIDER == "gemini":
            _stage1_service = GeminiStage1Provider()
        else:
            _stage1_service = QwenStage1Provider()
    return _stage1_service


def get_stage2_service():
    global _stage2_service
    if _stage2_service is None:
        if STAGE2_PROVIDER == "gemini":
            _stage2_service = GeminiStage2Provider()
        else:
            _stage2_service = QwenStage2Provider()
    return _stage2_service


__all__ = [
    "get_stage0_service",
    "get_stage1_service",
    "get_stage2_service",
]
