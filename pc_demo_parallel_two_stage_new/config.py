"""
三阶段后端配置

目标：
1. Stage 0（软预筛）、Stage 1（分析）与 Stage 2（生图）可独立切换 provider / model
2. 默认使用 Gemini 组合，避免 Qwen 官方速率限制影响默认体验
3. 同时支持 Gemini 作为预筛器、分析器或生图器接入
"""
import os
from pathlib import Path
from dataclasses import dataclass
from typing import Dict


# ==================== Provider 选择 ==================== #
STAGE1_PROVIDER = os.environ.get("STAGE1_PROVIDER", "gemini").strip().lower()
STAGE2_PROVIDER = os.environ.get("STAGE2_PROVIDER", "gemini").strip().lower()
#stage0 是预筛阶段，用于从众多构图技术中筛选出更适合当前图片的技术，默认和 stage1 保持一致，但也可以单独指定为 "gemini" 或 "qwen"
STAGE0_PROVIDER = os.environ.get("STAGE0_PROVIDER", STAGE1_PROVIDER).strip().lower()

# ==================== API Keys / Base URLs ==================== #
DASHSCOPE_API_KEY = os.environ.get("DASHSCOPE_API_KEY")
DASHSCOPE_BASE_URL = os.environ.get(
    "DASHSCOPE_BASE_URL",
    "https://dashscope.aliyuncs.com/compatible-mode/v1",
)

GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY")
USE_GEMINI_PROXY = True
# 本地代理默认走 127.0.0.1:11087；如果你的代理是 socks，请改成 socks5://127.0.0.1:11087
GEMINI_PROXY_URL = os.environ.get("GEMINI_PROXY_URL", "http://127.0.0.1:11087").strip()

# Gemini 国内中转配置：
# - 关闭时：继续使用官方 Gemini API（GEMINI_API_KEY）
# - 开启时：使用兼容 Gemini REST 路径的国内中转
#   这里的 BASE URL 应填写“服务根地址”或“API 根地址”，不要填写到
#   `.../v1beta/models/{model}:generateContent` 这种完整请求路径，
#   因为 SDK 会自动拼接 `api_version + models/...`
USE_GEMINI_DOMESTIC_API = False
GEMINI_DOMESTIC_BASE_URL = os.environ.get("GEMINI_DOMESTIC_BASE_URL",
                                          "https://yinli.one")
GEMINI_DOMESTIC_API_KEY = os.environ.get("GEMINI_DOMESTIC_API_KEY")
GEMINI_DOMESTIC_API_VERSION = os.environ.get("GEMINI_DOMESTIC_API_VERSION", "v1beta").strip()


# ==================== Model Names ==================== #
QWEN_STAGE1_MODEL = os.environ.get("QWEN_STAGE1_MODEL", "qwen3.6-flash-2026-04-16")
QWEN_STAGE2_MODEL = os.environ.get("QWEN_STAGE2_MODEL", "qwen-image-2.0-2026-03-03")
QWEN_STAGE0_MODEL = os.environ.get("QWEN_STAGE0_MODEL", QWEN_STAGE1_MODEL)

GEMINI_STAGE1_MODEL = os.environ.get("GEMINI_STAGE1_MODEL", "gemini-3-flash-preview")
# GEMINI_STAGE1_MODEL = os.environ.get("GEMINI_STAGE1_MODEL", "gemini-2.5-flash")
# GEMINI_STAGE1_MODEL = os.environ.get("GEMINI_STAGE1_MODEL", "gemini-2.5-flash-lite")
GEMINI_STAGE2_MODEL = os.environ.get("GEMINI_STAGE2_MODEL", "gemini-2.5-flash-image")

GEMINI_STAGE0_MODEL = os.environ.get(
    "GEMINI_STAGE0_MODEL",
    os.environ.get("GEMINI_SOFT_PREFILTER_MODEL", "gemini-3.1-flash-lite"),
)

# 常用 Gemini 候选：
# - gemini-2.5-flash
# - gemini-2.5-flash-lite
# - gemini-2.5-pro
# - gemini-2.5-flash-image
# - gemini-2.0-flash-preview-image-generation
# - gemini-3.1-flash-image-preview
# - gemini-3-pro-image-preview


def get_stage0_model_name() -> str:
    if STAGE0_PROVIDER == "gemini":
        return GEMINI_STAGE0_MODEL
    return QWEN_STAGE0_MODEL


def get_stage1_model_name() -> str:
    if STAGE1_PROVIDER == "gemini":
        return GEMINI_STAGE1_MODEL
    return QWEN_STAGE1_MODEL


def get_stage2_model_name() -> str:
    if STAGE2_PROVIDER == "gemini":
        return GEMINI_STAGE2_MODEL
    return QWEN_STAGE2_MODEL


# ==================== 路径配置 ==================== #
BASE_DIR = Path(__file__).parent
TEMP_DIR = BASE_DIR / "temp"
TEMP_DIR.mkdir(exist_ok=True)
PROMPTS_DIR = BASE_DIR / "prompts"


def load_prompt_file(filename: str) -> str:
    prompt_path = PROMPTS_DIR / filename
    return prompt_path.read_text(encoding="utf-8").strip()


# ==================== 构图技术配置 ==================== #
@dataclass
class TechniqueConfig:
    id: str
    name: str
    user_prompt: str


SYSTEM_INSTRUCTION = load_prompt_file("stage1_system_instruction.md")
STAGE0_SOFT_PREFILTER_PROMPT_TEMPLATE = load_prompt_file("stage0_soft_prefilter.md")


TECHNIQUE_CONFIGS: Dict[str, TechniqueConfig] = {
    "rule_of_thirds": TechniqueConfig(
        id="rule_of_thirds",
        name="三分构图",
        user_prompt=load_prompt_file("techniques/rule_of_thirds.md"),
    ),
    "center_composition": TechniqueConfig(
        id="center_composition",
        name="中心构图",
        user_prompt=load_prompt_file("techniques/center_composition.md"),
    ),
    "leading_lines": TechniqueConfig(
        id="leading_lines",
        name="引导线构图",
        user_prompt=load_prompt_file("techniques/leading_lines.md"),
    ),
    "foreground_framing": TechniqueConfig(
        id="foreground_framing",
        name="前景/框架构图",
        user_prompt=load_prompt_file("techniques/foreground_framing.md"),
    ),
    "diagonal_composition": TechniqueConfig(
        id="diagonal_composition",
        name="对角线构图",
        user_prompt=load_prompt_file("techniques/diagonal_composition.md"),
    ),
}


# ==================== Stage 1 参数 ==================== #
ENABLE_STAGE0 = (
    os.environ.get(
        "ENABLE_STAGE0",
        os.environ.get("ENABLE_GEMINI_SOFT_PREFILTER", "true"),
    ).strip().lower() == "true"
)
STAGE0_MAX_TECHNIQUES = int(
    os.environ.get(
        "STAGE0_MAX_TECHNIQUES",
        os.environ.get("GEMINI_SOFT_PREFILTER_MAX_TECHNIQUES", "5"),
    )
)
STAGE0_TEMPERATURE = float(
    os.environ.get(
        "STAGE0_TEMPERATURE",
        os.environ.get("GEMINI_SOFT_PREFILTER_TEMPERATURE", "0.1"),
    )
)
STAGE0_TOP_P = float(os.environ.get("STAGE0_TOP_P", "0.9"))

MODEL_TEMPERATURE = float(os.environ.get("MODEL_TEMPERATURE", "0.35"))
MODEL_TOP_P = float(os.environ.get("MODEL_TOP_P", "0.80"))
MODEL_TOP_K = int(os.environ.get("MODEL_TOP_K", "40"))
MODEL_MAX_TOKENS = int(os.environ.get("MODEL_MAX_TOKENS", "1024"))

ENABLE_THINKING = os.environ.get("ENABLE_THINKING", "false").strip().lower() == "true"
MODEL_THINKING_BUDGET = int(os.environ.get("MODEL_THINKING_BUDGET", "0"))

# Stage 1 思考控制：
# - Gemini 2.5 Flash/Flash Lite：显式设为 thinking_budget=0，避免默认 dynamic thinking 带来额外时延。
# - Gemini 3 Flash：显式设为 thinking_level=minimal，避免默认 high dynamic thinking。
# - Gemini 2.5 Pro：无法完全关闭，只能压到最低预算。
# - Qwen：显式关闭 enable_thinking。
QWEN_STAGE1_ENABLE_THINKING = (
    os.environ.get("QWEN_STAGE1_ENABLE_THINKING", "false").strip().lower() == "true"
)
QWEN_STAGE1_THINKING_BUDGET = int(os.environ.get("QWEN_STAGE1_THINKING_BUDGET", "0"))
QWEN_STAGE0_ENABLE_THINKING = (
    os.environ.get("QWEN_STAGE0_ENABLE_THINKING", "false").strip().lower() == "true"
)
QWEN_STAGE0_THINKING_BUDGET = int(os.environ.get("QWEN_STAGE0_THINKING_BUDGET", "0"))

GEMINI_STAGE1_FORCE_MINIMAL_THINKING = (
    os.environ.get("GEMINI_STAGE1_FORCE_MINIMAL_THINKING", "true").strip().lower() == "true"
)
GEMINI_STAGE1_THINKING_LEVEL = os.environ.get("GEMINI_STAGE1_THINKING_LEVEL", "minimal").strip().lower()
GEMINI_STAGE1_THINKING_BUDGET = int(os.environ.get("GEMINI_STAGE1_THINKING_BUDGET", "0"))

# Gemini Stage 1 专用参数（会按模型支持情况有选择地下发）
GEMINI_STAGE1_TEMPERATURE = float(os.environ.get("GEMINI_STAGE1_TEMPERATURE", str(MODEL_TEMPERATURE)))
GEMINI_STAGE1_TOP_P = float(os.environ.get("GEMINI_STAGE1_TOP_P", str(MODEL_TOP_P)))
GEMINI_STAGE1_TOP_K = int(os.environ.get("GEMINI_STAGE1_TOP_K", str(MODEL_TOP_K)))
GEMINI_STAGE1_MAX_OUTPUT_TOKENS = int(os.environ.get("GEMINI_STAGE1_MAX_OUTPUT_TOKENS", str(MODEL_MAX_TOKENS)))
GEMINI_STAGE1_RESPONSE_MIME_TYPE = os.environ.get("GEMINI_STAGE1_RESPONSE_MIME_TYPE", "application/json")
GEMINI_STAGE1_FORCE_DISABLE_MAX_OUTPUT_TOKENS = (
    os.environ.get("GEMINI_STAGE1_FORCE_DISABLE_MAX_OUTPUT_TOKENS", "true").strip().lower() == "true"
)

STAGE1_MAX_CONCURRENCY = int(os.environ.get("STAGE1_MAX_CONCURRENCY", "5"))
STAGE1_TIMEOUT_SECONDS = float(os.environ.get("STAGE1_TIMEOUT_SECONDS", "20"))
STAGE2_TIMEOUT_SECONDS = float(os.environ.get("STAGE2_TIMEOUT_SECONDS", "40"))


# ==================== Stage 2 参数 ==================== #
IMAGE_MAX_RATE = float(os.environ.get("IMAGE_MAX_RATE", "2.0"))
IMAGE_DASHSCOPE_BASE_URL = os.environ.get(
    "IMAGE_DASHSCOPE_BASE_URL",
    "https://dashscope.aliyuncs.com/api/v1",
)

# Gemini Stage 2 专用参数（同样按模型支持情况有选择地下发）
GEMINI_STAGE2_TEMPERATURE = float(os.environ.get("GEMINI_STAGE2_TEMPERATURE", "1.0"))
GEMINI_STAGE2_TOP_P = float(os.environ.get("GEMINI_STAGE2_TOP_P", str(MODEL_TOP_P)))
GEMINI_STAGE2_TOP_K = int(os.environ.get("GEMINI_STAGE2_TOP_K", str(MODEL_TOP_K)))
GEMINI_STAGE2_MAX_OUTPUT_TOKENS = int(os.environ.get("GEMINI_STAGE2_MAX_OUTPUT_TOKENS", "1024"))
# 注意：
# - 2.5-flash-image 默认固定 1024px，不下发 image_size
# - 3.x image preview / 2.0 image-generation 才会按模型能力下发 image_size
GEMINI_STAGE2_IMAGE_SIZE = os.environ.get("GEMINI_STAGE2_IMAGE_SIZE", "1K")
GEMINI_STAGE2_INCLUDE_SOURCE_IMAGE = (
    os.environ.get("GEMINI_STAGE2_INCLUDE_SOURCE_IMAGE", "true").strip().lower() == "true"
)
GEMINI_STAGE2_FORCE_DISABLE_MAX_OUTPUT_TOKENS = (
    os.environ.get("GEMINI_STAGE2_FORCE_DISABLE_MAX_OUTPUT_TOKENS", "true").strip().lower() == "true"
)
GEMINI_STAGE2_MAX_CONCURRENCY = int(os.environ.get("GEMINI_STAGE2_MAX_CONCURRENCY", "2"))
GEMINI_STAGE2_MAX_RETRIES = int(os.environ.get("GEMINI_STAGE2_MAX_RETRIES", "2"))
GEMINI_STAGE2_RETRY_BASE_DELAY_MS = int(os.environ.get("GEMINI_STAGE2_RETRY_BASE_DELAY_MS", "450"))
