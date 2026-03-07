"""
配置管理模块
从环境变量读取配置，提供默认值
"""
import os
from pathlib import Path
from dataclasses import dataclass
from typing import Dict

# ==================== API 配置 ==================== #
GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "AIzaSyC_KWWzdi39-TDTOhNRMZmlvFj8IwwH9u4")

# ==================== 模型配置 ==================== #
MODEL_NAME = "gemini-2.5-flash-image"

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


# 共享的 System Instruction
SYSTEM_INSTRUCTION = """# Role: AI Photography Director
# Goal: Analyze the user's input image and guide them to take a better photo based on a specific composition technique.

# CRITICAL OUTPUT PROTOCOL (STRICT EXECUTION ORDER):
1.  **Analyze**: Determine if the requested technique is valid for this scene.
2.  **JSON Output**: Output the JSON object containing the decision and instructions.
3.  **VISUAL PROOF (MANDATORY)**: 
    * IF `is_applicable` is `true`: You **MUST** generate the Target Image immediately after the JSON. **Failure to generate an image when `is_applicable` is true is a CRITICAL ERROR.**
    * IF `is_applicable` is `false`: Do NOT generate an image.

# Action Definitions (For JSON Instructions):
* `Shift`: Moving camera (Left, Right, Up, Down) or leveling frame (Rotate-CW, Rotate-CCW).
* `Zoom`: Adjusting focal length/distance (In, Out).
* `View-change`: Changing angle (High-angle, Low-angle, Side-view) or position (move one step left/right).

# JSON Schema (Strict):
You must return a JSON object with this exact structure:
```json
{
  "is_applicable": true,
  "technique": "string",
  "composition_data": {
    "aesthetic_desc": "string (Chinese description)",
    "steps": [
      {
        "step_order": 1,
        "action_type": "Shift|Zoom|View-change",
        "direction": "string",
        "guide_text": "string (Chinese instruction)"
      }
    ]
  }
}
```

# General Constraints:
1.  **Language**: All descriptive text (`aesthetic_desc`, `guide_text`) must be in **Chinese (中文)**.
2.  **Realism**: The target image must be a realistic re-framing of the original scene.
3.  **Completeness**: Do not stop generation after the JSON if `is_applicable` is true. The task is ONLY complete after the image is generated."""


# 5 种构图技术的 User Prompt
TECHNIQUE_CONFIGS: Dict[str, TechniqueConfig] = {
    "rule_of_thirds": TechniqueConfig(
        id="rule_of_thirds",
        name="三分构图",
        user_prompt="""# Task Request
The user has uploaded an image. Please apply the **"Rule of Thirds"** technique.
1.  **Technique Goal**: Place the main subject on the grid lines or intersection points.
2.  **Technique Name for JSON**: `rule_of_thirds`
3.  **Analysis**: Check if the subject can be shifted to a third-line to improve balance."""
    ),
    "center_composition": TechniqueConfig(
        id="center_composition",
        name="中心构图",
        user_prompt="""# Task Request
The user has uploaded an image. Please apply the **"Center Composition / Symmetry"** technique.
1.  **Technique Goal**: Place the subject perfectly in the center to emphasize stability, symmetry, or importance.
2.  **Technique Name for JSON**: `center_composition`
3.  **Analysis**: Check if the subject works well when centered (e.g., portraits, symmetrical architecture)."""
    ),
    "leading_lines": TechniqueConfig(
        id="leading_lines",
        name="引导线构图",
        user_prompt="""# Task Request
The user has uploaded an image. Please apply the **"Leading Lines"** technique.
1.  **Technique Goal**: Find natural lines (roads, fences, rivers, edges) and align the shot so they point towards the subject.
2.  **Technique Name for JSON**: `leading_lines`
3.  **Analysis**: Crucial step - Check if there are ACTUAL lines in the scene. If no lines exist, set `is_applicable` to `false`."""
    ),
    "foreground_framing": TechniqueConfig(
        id="foreground_framing",
        name="前景/框架构图",
        user_prompt="""# Task Request
The user has uploaded an image. Please apply the **"Foreground Framing"** technique.
1.  **Technique Goal**: Use elements close to the lens (leaves, windows, objects) to frame the main subject, adding depth.
2.  **Technique Name for JSON**: `foreground_framing`
3.  **Analysis**: Check if there are potential foreground elements to use. If the scene is completely open/empty, set `is_applicable` to `false`."""
    ),
    "diagonal_composition": TechniqueConfig(
        id="diagonal_composition",
        name="对角线构图",
        user_prompt="""# Task Request
The user has uploaded an image. Please apply the **"Diagonal Composition"** technique.
1.  **Technique Goal**: Use **EXISTING** linear elements (fences, roads, architecture edges) to guide the eye diagonally.
2.  **Technique Name for JSON**: `diagonal_composition`
3.  **Strict Analysis Constraints**:
   * **DO NOT simply tilt the camera** (Dutch Angle) to create a fake diagonal if the subject is vertical.
   * Only set `is_applicable` to `true` if there are **actual physical lines** in the scene that can be aligned diagonally.
   * If the scene is static and vertical/horizontal dominant, set `is_applicable` to `false`."""
    ),
}

# 模型参数
MODEL_TEMPERATURE = 1.0
MODEL_TOP_K = 40
MODEL_TOP_P = 0.95
