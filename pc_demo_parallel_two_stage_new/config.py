"""
两阶段后端配置

目标：
1. Stage 1（分析）与 Stage 2（生图）可独立切换 provider / model
2. 默认使用 Gemini 组合，避免 Qwen 官方速率限制影响默认体验
3. 同时支持 Gemini 作为分析器或生图器接入
"""
import os
from pathlib import Path
from dataclasses import dataclass
from typing import Dict


# ==================== Provider 选择 ==================== #
# STAGE1_PROVIDER = os.environ.get("STAGE1_PROVIDER", "gemini").strip().lower()
# STAGE2_PROVIDER = os.environ.get("STAGE2_PROVIDER", "gemini").strip().lower()
STAGE1_PROVIDER = os.environ.get("STAGE1_PROVIDER", "gemini").strip().lower()
STAGE2_PROVIDER = os.environ.get("STAGE2_PROVIDER", "gemini").strip().lower()

# ==================== API Keys / Base URLs ==================== #
DASHSCOPE_API_KEY = os.environ.get("DASHSCOPE_API_KEY")
DASHSCOPE_BASE_URL = os.environ.get(
    "DASHSCOPE_BASE_URL",
    "https://dashscope.aliyuncs.com/compatible-mode/v1",
)

GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY")


# ==================== Model Names ==================== #
QWEN_STAGE1_MODEL = os.environ.get("QWEN_STAGE1_MODEL", "qwen3.6-flash-2026-04-16")
QWEN_STAGE2_MODEL = os.environ.get("QWEN_STAGE2_MODEL", "qwen-image-2.0-2026-03-03")

GEMINI_STAGE1_MODEL = os.environ.get("GEMINI_STAGE1_MODEL", "gemini-3-flash-preview")
# GEMINI_STAGE1_MODEL = os.environ.get("GEMINI_STAGE1_MODEL", "gemini-2.5-flash")
# GEMINI_STAGE1_MODEL = os.environ.get("GEMINI_STAGE1_MODEL", "gemini-2.5-flash-lite")
GEMINI_STAGE2_MODEL = os.environ.get("GEMINI_STAGE2_MODEL", "gemini-2.5-flash-image")

# 常用 Gemini 候选：
# - gemini-2.5-flash
# - gemini-2.5-flash-lite
# - gemini-2.5-pro
# - gemini-2.5-flash-image
# - gemini-2.0-flash-preview-image-generation
# - gemini-3.1-flash-image-preview
# - gemini-3-pro-image-preview


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


# ==================== 构图技术配置 ==================== #
@dataclass
class TechniqueConfig:
    id: str
    name: str
    user_prompt: str


SYSTEM_INSTRUCTION = """
# Role
你是一位顶尖摄影指导与视觉分析师。你的任务不是直接生成图片，而是：
1. 分析用户上传的原始取景画面；
2. 判断指定构图技巧是否真实可达、是否值得推荐；
3. 若适用，输出严格结构化的构图引导 JSON；
4. 同时输出一个给 Stage 2 图像模型使用的 `image_prompt`，用于生成参考构图图。

# Core Rules
1. 真实性约束：参考构图必须忠于原图主体、场景、光照和物理关系。严禁虚构主体、移动真实物体、替换主体、改变季节/天气/时间。
2. 不得伪造构图支撑元素：若某构图需要原图中并不存在的线条、前景、框架、反射、道路、树枝、阴影等，必须 `is_applicable=false`。
3. 物理可达性约束：若需要穿墙、飞起来、大范围绕行或明显超出普通手机用户可执行范围，也必须 `is_applicable=false`。
4. 步骤必须可执行、可验证。每一步只允许一个主动作，并从以下集合中选择：
   - Shift: Left / Right / Up / Down
   - Level: CW / CCW
   - Zoom: In / Out
   - Orbit: Left / Right
   - RaiseCamera: Up
   - LowerCamera: Down
   - Step: Forward / Backward
5. 优先最短可执行路径。默认 1 步；必要时可 2-3 步。
6. 如需改变视角，优先顺序是：先 1 个粗机位动作（Orbit / RaiseCamera / LowerCamera / Step），再 Shift 对位，最后 Zoom 调整主体大小。
7. 禁止连续两个机位变化动作；禁止 Orbit 与 Step 同时出现在一个方案中。
8. `image_prompt` 是给下游生图模型的参考描述，不是最终用户文案。它必须尽量精确描述原图主体、场景和目标构图，避免模型幻觉。

# Output Contract
你的文本响应必须只包含一个 JSON 对象，不要输出解释，不要输出 markdown。

JSON 结构如下：
{
  "is_applicable": true,
  "technique": "string",
  "composition_data": {
    "image_prompt": "string",
    "aesthetic_desc": "string",
    "steps": [
      {
        "step_order": 1,
        "action_type": "string",
        "direction": "string",
        "guide_text": "string"
      }
    ],
    "shot_spec": {
      "subject_hint": "string",
      "viewpoint_required": true,
      "target_subject_center": [0.34, 0.52],
      "target_subject_size": 0.28,
      "camera_move_summary": "string",
      "validation_notes": "string"
    }
  }
}

# Field Rules
1. `composition_data.image_prompt` 必须最先输出，便于支持流式 prompt 前置截取。
2. `image_prompt` 用英文，描述原图真实主体与目标构图，不要写 JSON，不要写解释。
3. `image_prompt` 必须简洁，尽量控制在 1 句、40-70 个英文词，优先保留对主体、场景和目标构图真正必要的信息。
4. `aesthetic_desc` 和 `guide_text` 用中文。
5. `steps` 最多 3 步。
6. 若 3 步，必须是：1 个机位变化动作 + 1 个 Shift/Level + 1 个 Zoom。
7. 若 `is_applicable=false`，则 `composition_data` 必须为 null。
8. `shot_spec.target_subject_center` 与 `target_subject_size` 是弱几何先验。只在主体明确时填写，否则置 null。
9. `target_subject_center` / `target_subject_size` 需保守、稳定、近似，不能伪装成像素级真值。
"""


TECHNIQUE_CONFIGS: Dict[str, TechniqueConfig] = {
    "rule_of_thirds": TechniqueConfig(
        id="rule_of_thirds",
        name="三分构图",
        user_prompt="""# Task Request
The user has uploaded an image. Please apply the Rule of Thirds technique.
Prefer Shift / Zoom first. Only use Orbit / RaiseCamera / LowerCamera / Step when thirds placement truly requires a new camera pose.""",
    ),
    "center_composition": TechniqueConfig(
        id="center_composition",
        name="中心构图",
        user_prompt="""# Task Request
The user has uploaded an image. Please apply Center Composition / Symmetry.
If symmetry can be fixed by leveling or recentering, avoid camera-pose changes.""",
    ),
    "leading_lines": TechniqueConfig(
        id="leading_lines",
        name="引导线构图",
        user_prompt="""# Task Request
The user has uploaded an image. Please apply Leading Lines.
Only use this technique when the scene already contains real, visually credible lines.
Never invent roads, rails, shadows, reflections, streaks, or cables to fake leading lines.
Prefer Shift over Step and Orbit. If real leading lines are weak or absent, return is_applicable=false.""",
    ),
    "foreground_framing": TechniqueConfig(
        id="foreground_framing",
        name="前景/框架构图",
        user_prompt="""# Task Request
The user has uploaded an image. Please apply Foreground Framing.
Only use this technique when there is already a real foreground object or frame candidate in the scene.
Never fabricate leaves, windows, poles, blur blobs, or fake frame edges.
If no real foreground/frame candidate exists, return is_applicable=false.""",
    ),
    "diagonal_composition": TechniqueConfig(
        id="diagonal_composition",
        name="对角线构图",
        user_prompt="""# Task Request
The user has uploaded an image. Please apply Diagonal Composition.
Only use this technique when the scene already contains real diagonal energy from existing edges, roads, architecture, limbs, shadows, or object placement.
Do not tilt the whole camera to fake diagonal energy. If convincing diagonal structure does not already exist, return is_applicable=false.""",
    ),
}


# ==================== Stage 1 参数 ==================== #
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

# Gemini Stage 1 预筛参数：
# 在高延迟网络 + 重模型场景下可能有帮助；
# 当 Stage 1 已经切到 2.5-lite 时，额外多一跳请求往往不划算，所以默认关闭。
ENABLE_GEMINI_TECHNIQUE_PREFILTER = (
    os.environ.get("ENABLE_GEMINI_TECHNIQUE_PREFILTER", "false").strip().lower() == "true"
)
GEMINI_PREFILTER_MODEL = os.environ.get("GEMINI_PREFILTER_MODEL", "gemini-2.5-flash-lite")
GEMINI_PREFILTER_MAX_TECHNIQUES = int(os.environ.get("GEMINI_PREFILTER_MAX_TECHNIQUES", "3"))
GEMINI_PREFILTER_TEMPERATURE = float(os.environ.get("GEMINI_PREFILTER_TEMPERATURE", "0.1"))


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
