"""
三阶段后端配置。

目标：
1. Stage 0（软预筛）、Stage 1（分析）与 Stage 2（生图）可独立切换 provider / model
2. 默认使用 Gemini 组合，避免 Qwen 官方速率限制影响默认体验
3. 同时支持 Gemini 作为预筛器、分析器或生图器接入
"""

import os
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Optional, Tuple


def _get_env_str(
    name: str,
    default: str,
    aliases: Tuple[str, ...] = (),
) -> str:
    for key in (name, *aliases):
        value = os.environ.get(key)
        if value is not None:
            return value.strip()
    return default


def _get_env_optional_str(
    name: str,
    aliases: Tuple[str, ...] = (),
) -> Optional[str]:
    for key in (name, *aliases):
        value = os.environ.get(key)
        if value is not None:
            return value.strip()
    return None


def _get_env_bool(
    name: str,
    default: bool,
    aliases: Tuple[str, ...] = (),
) -> bool:
    value = _get_env_str(name, "true" if default else "false", aliases).lower()
    return value in {"1", "true", "yes", "on"}


def _get_env_int(
    name: str,
    default: int,
    aliases: Tuple[str, ...] = (),
) -> int:
    return int(_get_env_str(name, str(default), aliases))


def _get_env_float(
    name: str,
    default: float,
    aliases: Tuple[str, ...] = (),
) -> float:
    return float(_get_env_str(name, str(default), aliases))


def _select_model_name(provider: str, gemini_model: str, qwen_model: str) -> str:
    return gemini_model if provider == "gemini" else qwen_model


# ==================== Provider（qwen/gemini）选择 ==================== #
STAGE1_PROVIDER = _get_env_str("STAGE1_PROVIDER", "gemini").lower()
STAGE2_PROVIDER = _get_env_str("STAGE2_PROVIDER", "gemini").lower()
# Stage 0 是预筛阶段，默认跟随 Stage 1，也可以单独指定为 "gemini" 或 "qwen"。
STAGE0_PROVIDER = _get_env_str("STAGE0_PROVIDER", STAGE1_PROVIDER).lower()


# ==================== API Keys ==================== #
DASHSCOPE_API_KEY = _get_env_optional_str("DASHSCOPE_API_KEY")
GEMINI_API_KEY = _get_env_optional_str("GEMINI_API_KEY")
GEMINI_DOMESTIC_API_KEY = _get_env_optional_str("GEMINI_DOMESTIC_API_KEY")


# ==================== 网络 / 代理配置 ==================== #
# Qwen 国内访问配置
DASHSCOPE_BASE_URL = _get_env_str(
    "DASHSCOPE_BASE_URL",
    "https://dashscope.aliyuncs.com/compatible-mode/v1",
)
IMAGE_DASHSCOPE_BASE_URL = _get_env_str(
    "IMAGE_DASHSCOPE_BASE_URL",
    "https://dashscope.aliyuncs.com/api/v1",
)

# 本地代理默认走 127.0.0.1:11087；如果你的代理是 socks，请改成 socks5://127.0.0.1:11087
USE_GEMINI_PROXY = _get_env_bool("USE_GEMINI_PROXY", True)
GEMINI_PROXY_URL = _get_env_str(
    "GEMINI_PROXY_URL",
    "http://127.0.0.1:11087",
)

# Gemini 国内中转配置：
# - 关闭时：继续使用官方 Gemini API（GEMINI_API_KEY）
# - 开启时：使用兼容 Gemini REST 路径的国内中转
# - BASE URL 填服务根地址或 API 根地址，不要填完整 generateContent 路径
USE_GEMINI_DOMESTIC_API = _get_env_bool("USE_GEMINI_DOMESTIC_API", False)
GEMINI_DOMESTIC_BASE_URL = _get_env_str(
    "GEMINI_DOMESTIC_BASE_URL",
    "https://yinli.one",
)
GEMINI_DOMESTIC_API_VERSION = _get_env_str(
    "GEMINI_DOMESTIC_API_VERSION",
    "v1beta",
)


# ==================== 模型配置 ==================== #
QWEN_STAGE1_MODEL = _get_env_str("QWEN_STAGE1_MODEL", "qwen3.6-flash-2026-04-16")
QWEN_STAGE2_MODEL = _get_env_str("QWEN_STAGE2_MODEL", "qwen-image-2.0-2026-03-03")
QWEN_STAGE0_MODEL = _get_env_str("QWEN_STAGE0_MODEL", QWEN_STAGE1_MODEL)

GEMINI_STAGE1_MODEL = _get_env_str("GEMINI_STAGE1_MODEL", "gemini-3-flash-preview")
# 常用 Gemini Stage 1 候选：
# - gemini-2.5-flash
# - gemini-2.5-flash-lite
# - gemini-2.5-pro
GEMINI_STAGE2_MODEL = _get_env_str("GEMINI_STAGE2_MODEL", "gemini-2.5-flash-image")
# 常用 Gemini Stage 2 候选：
# - gemini-2.5-flash-image
# - gemini-2.0-flash-preview-image-generation
# - gemini-3.1-flash-image-preview
# - gemini-3-pro-image-preview
GEMINI_STAGE0_MODEL = _get_env_str("GEMINI_STAGE0_MODEL", "gemini-3.1-flash-lite")


def get_stage0_model_name() -> str:
    return _select_model_name(STAGE0_PROVIDER, GEMINI_STAGE0_MODEL, QWEN_STAGE0_MODEL)


def get_stage1_model_name() -> str:
    return _select_model_name(STAGE1_PROVIDER, GEMINI_STAGE1_MODEL, QWEN_STAGE1_MODEL)


def get_stage2_model_name() -> str:
    return _select_model_name(STAGE2_PROVIDER, GEMINI_STAGE2_MODEL, QWEN_STAGE2_MODEL)


# ==================== 路径配置 ==================== #
BASE_DIR = Path(__file__).resolve().parent
TEMP_DIR = BASE_DIR / "temp"
PROMPTS_DIR = BASE_DIR / "prompts"

TEMP_DIR.mkdir(exist_ok=True)


def load_prompt_file(filename: str) -> str:
    return (PROMPTS_DIR / filename).read_text(encoding="utf-8").strip()


# ==================== Prompt / 构图技术配置 ==================== #
@dataclass
class TechniqueConfig:
    id: str
    name: str
    user_prompt: str


SYSTEM_INSTRUCTION = load_prompt_file("stage1_system_instruction.md")
STAGE0_SOFT_PREFILTER_PROMPT_TEMPLATE = load_prompt_file("stage0_soft_prefilter.md")

_TECHNIQUE_DEFINITIONS = (
    ("rule_of_thirds", "三分构图"),
    ("center_composition", "中心构图"),
    ("leading_lines", "引导线构图"),
    ("foreground_framing", "前景/框架构图"),
    ("diagonal_composition", "对角线构图"),
)

TECHNIQUE_CONFIGS: Dict[str, TechniqueConfig] = {
    technique_id: TechniqueConfig(
        id=technique_id,
        name=technique_name,
        user_prompt=load_prompt_file(f"techniques/{technique_id}.md"),
    )
    for technique_id, technique_name in _TECHNIQUE_DEFINITIONS
}


# ==================== Stage 0 配置 ==================== #
ENABLE_STAGE0 = _get_env_bool(
    "ENABLE_STAGE0",
    False,
    aliases=("ENABLE_GEMINI_SOFT_PREFILTER",),
)
STAGE0_MAX_TECHNIQUES = _get_env_int(
    "STAGE0_MAX_TECHNIQUES",
    5,
    aliases=("GEMINI_SOFT_PREFILTER_MAX_TECHNIQUES",),
)
STAGE0_TEMPERATURE = _get_env_float(
    "STAGE0_TEMPERATURE",
    0.1,
    aliases=("GEMINI_SOFT_PREFILTER_TEMPERATURE",),
)
STAGE0_TOP_P = _get_env_float("STAGE0_TOP_P", 0.9)


# ==================== Stage 1 通用配置 ==================== #
MODEL_TEMPERATURE = _get_env_float("MODEL_TEMPERATURE", 0.35)
MODEL_TOP_P = _get_env_float("MODEL_TOP_P", 0.80)
MODEL_TOP_K = _get_env_int("MODEL_TOP_K", 40)
MODEL_MAX_TOKENS = _get_env_int("MODEL_MAX_TOKENS", 1024)

ENABLE_THINKING = _get_env_bool("ENABLE_THINKING", False)
MODEL_THINKING_BUDGET = _get_env_int("MODEL_THINKING_BUDGET", 0)

# Stage 1 思考控制：
# - Gemini 2.5 Flash / Flash Lite：显式设为 thinking_budget=0，避免默认 dynamic thinking 带来额外时延
# - Gemini 3 Flash：显式设为 thinking_level=minimal，避免默认 high dynamic thinking
# - Gemini 2.5 Pro：无法完全关闭，只能压到最低预算
# - Qwen：默认显式关闭 enable_thinking
QWEN_STAGE1_ENABLE_THINKING = _get_env_bool("QWEN_STAGE1_ENABLE_THINKING", False)
QWEN_STAGE1_THINKING_BUDGET = _get_env_int("QWEN_STAGE1_THINKING_BUDGET", 0)
QWEN_STAGE0_ENABLE_THINKING = _get_env_bool("QWEN_STAGE0_ENABLE_THINKING", False)
QWEN_STAGE0_THINKING_BUDGET = _get_env_int("QWEN_STAGE0_THINKING_BUDGET", 0)

GEMINI_STAGE1_FORCE_MINIMAL_THINKING = _get_env_bool(
    "GEMINI_STAGE1_FORCE_MINIMAL_THINKING",
    True,
)
GEMINI_STAGE1_THINKING_LEVEL = _get_env_str(
    "GEMINI_STAGE1_THINKING_LEVEL",
    "minimal",
).lower()
GEMINI_STAGE1_THINKING_BUDGET = _get_env_int("GEMINI_STAGE1_THINKING_BUDGET", 0)

# Gemini Stage 1 专用参数（会按模型支持情况有选择地下发）
GEMINI_STAGE1_TEMPERATURE = _get_env_float(
    "GEMINI_STAGE1_TEMPERATURE",
    MODEL_TEMPERATURE,
)
GEMINI_STAGE1_TOP_P = _get_env_float("GEMINI_STAGE1_TOP_P", MODEL_TOP_P)
GEMINI_STAGE1_TOP_K = _get_env_int("GEMINI_STAGE1_TOP_K", MODEL_TOP_K)
GEMINI_STAGE1_MAX_OUTPUT_TOKENS = _get_env_int(
    "GEMINI_STAGE1_MAX_OUTPUT_TOKENS",
    MODEL_MAX_TOKENS,
)
GEMINI_STAGE1_RESPONSE_MIME_TYPE = _get_env_str(
    "GEMINI_STAGE1_RESPONSE_MIME_TYPE",
    "application/json",
)
GEMINI_STAGE1_FORCE_DISABLE_MAX_OUTPUT_TOKENS = _get_env_bool(
    "GEMINI_STAGE1_FORCE_DISABLE_MAX_OUTPUT_TOKENS",
    True,
)

STAGE1_MAX_CONCURRENCY = _get_env_int("STAGE1_MAX_CONCURRENCY", 5)
STAGE1_TIMEOUT_SECONDS = _get_env_float("STAGE1_TIMEOUT_SECONDS", 20.0)
STAGE2_TIMEOUT_SECONDS = _get_env_float("STAGE2_TIMEOUT_SECONDS", 40.0)


# ==================== Stage 2 配置 ==================== #
IMAGE_MAX_RATE = _get_env_float("IMAGE_MAX_RATE", 2.0)

# Gemini Stage 2 专用参数（同样按模型支持情况有选择地下发）
GEMINI_STAGE2_TEMPERATURE = _get_env_float("GEMINI_STAGE2_TEMPERATURE", 1.0)
GEMINI_STAGE2_TOP_P = _get_env_float("GEMINI_STAGE2_TOP_P", MODEL_TOP_P)
GEMINI_STAGE2_TOP_K = _get_env_int("GEMINI_STAGE2_TOP_K", MODEL_TOP_K)
GEMINI_STAGE2_MAX_OUTPUT_TOKENS = _get_env_int(
    "GEMINI_STAGE2_MAX_OUTPUT_TOKENS",
    1024,
)
# 注意：
# - 2.5-flash-image 默认固定 1024px，不下发 image_size
# - 3.x image preview / 2.0 image-generation 才会按模型能力下发 image_size
GEMINI_STAGE2_IMAGE_SIZE = _get_env_str("GEMINI_STAGE2_IMAGE_SIZE", "1K")
GEMINI_STAGE2_INCLUDE_SOURCE_IMAGE = _get_env_bool(
    "GEMINI_STAGE2_INCLUDE_SOURCE_IMAGE",
    True,
)
GEMINI_STAGE2_FORCE_DISABLE_MAX_OUTPUT_TOKENS = _get_env_bool(
    "GEMINI_STAGE2_FORCE_DISABLE_MAX_OUTPUT_TOKENS",
    True,
)
GEMINI_STAGE2_MAX_CONCURRENCY = _get_env_int("GEMINI_STAGE2_MAX_CONCURRENCY", 2)
GEMINI_STAGE2_MAX_RETRIES = _get_env_int("GEMINI_STAGE2_MAX_RETRIES", 2)
GEMINI_STAGE2_RETRY_BASE_DELAY_MS = _get_env_int(
    "GEMINI_STAGE2_RETRY_BASE_DELAY_MS",
    450,
)
