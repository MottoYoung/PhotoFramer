"""
配置管理模块
从环境变量读取配置，提供默认值
"""
import os
from pathlib import Path
from dataclasses import dataclass
from typing import Dict


# ==================== API 配置 ==================== #
DASHSCOPE_API_KEY = os.environ.get("DASHSCOPE_API_KEY")
DASHSCOPE_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"

# ==================== 模型配置 ==================== #
MODEL_NAME = "qwen3.5-flash-2026-02-23"

# ==================== 路径配置 ==================== #
BASE_DIR = Path(__file__).parent
TEMP_DIR = BASE_DIR / "temp"
TEMP_DIR.mkdir(exist_ok=True)


# ==================== 构图技术配置 ==================== #
@dataclass
class TechniqueConfig:
    """单个构图技术的配置"""
    id: str
    name: str
    user_prompt: str


# 共享的 System Instruction（保持与调试版本完全一致）
SYSTEM_INSTRUCTION = """# Role: AI Photography Director & Vision Analyst
# Goal: Analyze the user's uploaded raw image and requested composition technique. Output a structured JSON containing user-guided camera instructions and a highly descriptive image generation prompt for a downstream diffusion model (Qwen-image-2.0) to create a visual reference.

# Workflow:
1. Analyze: Examine the user's image content (subjects, background, lighting) and determine if the requested technique is physically and aesthetically applicable.
2. Formulate Steps: If applicable, write step-by-step camera movement instructions for the user.
3. Formulate Image Prompt: Write a detailed English prompt for an image generator to recreate the scene with the PERFECTED composition. It MUST accurately describe the original subjects and environment to prevent hallucination.
4. Output JSON: Output ONLY a valid JSON object. No markdown text outside the JSON.

# Action Definitions (For JSON user steps):
* `Shift`: Moving camera (Left, Right, Up, Down).
* `Zoom`: Adjusting focal length/distance (In, Out).
* `View-change`: Changing angle (High-angle, Low-angle, Side-view).

# JSON Schema (Strictly required):
```json
{
  "is_applicable": true,
  "technique": "string",
  "composition_data": {
    "qwen_image_prompt": "string (English)",
    "aesthetic_desc_and_reasoning": "string (中文)",
    "steps": [
      {
        "step_order": 1,
        "action_type": "string",
        "direction": "string",
        "guide_text": "string (中文)"
      }
    ]
  }
}

# Constraints & Rules:

1. Pure JSON: Your entire response must be ONLY the JSON object. Do not include introductory or concluding text. Do not wrap in `json` codeblocks if the API restricts it (or strictly use them if required by the parser).

2. Field Order (CRITICAL for streaming optimization): Inside `composition_data`, you MUST output `qwen_image_prompt` FIRST, before `aesthetic_desc_and_reasoning` and `steps`. This order is mandatory.

3. Language: User-facing text (`aesthetic_desc_and_reasoning`, `guide_text`) MUST be in Chinese. The `qwen_image_prompt` MUST be in English for optimal image generation.

4. Null state: If `is_applicable` is false, `composition_data` must be exactly `null`."""


# 5 种构图技术的 User Prompt
TECHNIQUE_CONFIGS: Dict[str, TechniqueConfig] = {
    "rule_of_thirds": TechniqueConfig(
        id="rule_of_thirds",
        name="三分构图",
        user_prompt="""# Task Request
The user has uploaded an image. Please apply the "Rule of Thirds" (三分构图) technique.

1. Technique Goal: Place the main subject on the grid lines or intersection points.
2. Technique Name for JSON: `rule_of_thirds`
3. Analysis: Check if the subject can be shifted to a third-line to improve balance. If yes, generate instructions and a `qwen_image_prompt` that emphasizes "subject placed on rule-of-thirds intersection, balanced composition"."""
    ),
    "center_composition": TechniqueConfig(
        id="center_composition",
        name="中心构图",
        user_prompt="""# Task Request
The user has uploaded an image. Please apply the "Center Composition / Symmetry" (中心/对称构图) technique.

1. Technique Goal: Place the subject perfectly in the center to emphasize stability, symmetry, or importance.
2. Technique Name for JSON: `center_composition`
3. Analysis: Check if the subject works well when centered (e.g., portraits, symmetrical architecture). If yes, generate instructions and a `qwen_image_prompt` that emphasizes "perfectly centered, symmetrical composition, balanced framing"."""
    ),
    "leading_lines": TechniqueConfig(
        id="leading_lines",
        name="引导线构图",
        user_prompt="""# Task Request
The user has uploaded an image. Please apply the "Leading Lines" (引导线构图) technique.

1. Technique Goal: Find natural lines (roads, fences, rivers, edges) and align the shot so they point towards the subject.
2. Technique Name for JSON: `leading_lines`
3. Analysis: Crucial step - Check if there are ACTUAL lines in the scene. If no lines exist, set `is_applicable` to `false`. If yes, generate instructions and a `qwen_image_prompt` that emphasizes "leading lines directing viewer's gaze toward subject, strong linear perspective"."""
    ),
    "foreground_framing": TechniqueConfig(
        id="foreground_framing",
        name="前景/框架构图",
        user_prompt="""# Task Request
The user has uploaded an image. Please apply the "Foreground Framing" (前景/框架构图) technique.

1. Technique Goal: Use elements close to the lens (leaves, windows, objects) to frame the main subject, adding depth.
2. Technique Name for JSON: `foreground_framing`
3. Analysis: Check if there are potential foreground elements to use. If the scene is completely open/empty, set `is_applicable` to `false`. If yes, generate instructions and a `qwen_image_prompt` that emphasizes "natural foreground frame surrounding subject, layered depth, framed composition"."""
    ),
    "diagonal_composition": TechniqueConfig(
        id="diagonal_composition",
        name="对角线构图",
        user_prompt="""# Task Request
The user has uploaded an image. Please apply the "Diagonal Composition" (对角线构图) technique.

1. Technique Goal: Use EXISTING linear elements (fences, roads, architecture edges) to guide the eye diagonally.
2. Technique Name for JSON: `diagonal_composition`
3. Strict Analysis Constraints:
   * DO NOT simply tilt the camera (Dutch Angle) to create a fake diagonal if the subject is vertical.
   * Only set `is_applicable` to `true` if there are actual physical lines in the scene that can be aligned diagonally.
   * If the scene is static and vertical/horizontal dominant, set `is_applicable` to `false`.
4. If yes, generate instructions and a `qwen_image_prompt` that emphasizes "strong diagonal lines, dynamic composition, leading diagonal elements"."""
    ),
}

# ==================== 模型参数 ==================== #
MODEL_TEMPERATURE = 0.7
MODEL_TOP_P = 0.8
ENABLE_THINKING=False
# thinking_budget 性能权衡：
# 0    = 禁用思考，最快（建议先测试）
# 512  = 轻量思考，平衡速度与质量
# 4096 = 深度思考，输出最准确但最慢（之前导致 S1 耗时 38-50s）
MODEL_THINKING_BUDGET = 0

# ==================== Stage 2: 图像生成模型配置 ==================== #
IMAGE_MODEL_NAME = "qwen-image-2.0-2026-03-03"
IMAGE_MAX_RATE = 2.0          # Qwen-image API 限速：每秒最多 2 个请求
IMAGE_DASHSCOPE_BASE_URL = os.environ.get(
    "DASHSCOPE_BASE_URL",
    "https://dashscope.aliyuncs.com/api/v1"  # 中国大陆；海外用 dashscope-intl.aliyuncs.com
)
