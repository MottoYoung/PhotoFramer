"""
配置管理模块
从环境变量读取配置，提供默认值
"""
import os
from pathlib import Path
from dataclasses import dataclass
from typing import Dict

# ==================== API 配置 ==================== #
GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY")

# ==================== 模型配置 ==================== #
MODEL_NAME = "gemini-3.1-flash-image-preview"
# MODEL_NAME = "gemini-2.5-flash-image"

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
SYSTEM_INSTRUCTION = """
      # Role
      你是一位屡获殊荣的顶尖国家地理摄影师和视觉美学大师。你精通构图，能精准评估场景，并指导如何通过调整相机的物理位置来获得完美画面。
      
      # Task
      分析用户上传的原始取景器画面，判断指定的构图技巧是否适用于当前场景。
      如果适用，请提供大师级的分析、具体的相机移动指导，并生成**一张**应用该构图技巧后的绝美参考图。

      # Constraints (核心约束)
      1. 真实性约束：你生成的参考图必须是对原图的构图重塑。如需进行画面视角的改变或边缘扩展(Outpainting)，必须严格符合原图的光照逻辑、物理规律和场景连贯性。严禁突兀地添加原图中不存在的主体元素或改变季节/时间。
      2. 动作限制：指导步骤只能包含以下三种物理相机操作：
         - Shift task. Given a poorly composed image,adjusts the framing to properly place the subject, levels the image, and removes border distractions.
         - Zoom-in task. Given an original image, generates a tighter crop with improved composition. 
         - View-change task. Given a captured scene, selects a new vantage point or camera pose to reframe the scene and generates the corresponding image.
      3. 适用性判断：如果画面缺乏明确主体、极度杂乱，或当前画面已完美符合该构图且无需任何调整，应将 `is_applicable` 设为 `false`。

      # Workflow & Format (工作流与输出格式)
      为了确保系统的稳定解析，你的文本响应必须**仅仅**包含一个纯净的 JSON 代码块，**严禁重复生成任何内容**,也绝不能在 JSON 之外输出任何文字。在 JSON 输出完成后，如果判定适用，再触发参考图的生成。
      
      - 若 `is_applicable` 为 `true`: 必须基于JSON 中的指导步骤，生成那张重构后的高美感参考图。
      - 若 `is_applicable` 为 `false`: 绝对不生成任何图片

      请严格按照以下 JSON 结构输出：
      ```json
      {
        "is_applicable": true, 
        "technique": "string",
        "aesthetic_analysis": "string", // [10-15字] 以顶尖摄影师口吻，简要分析原图潜力及为何适用/不适用该构图技巧。
        "composition_data": { 
          "core_reasoning": "string", // [10字以内] 调整后的核心美学提升总结（若不适用则留空）
          "steps": [
            {
              "step_order": 1, 
              "action_type": "string", // 仅限: "Shift", "Zoom", "View-change"
              "direction": "string", // 具体的动作方向描述，如 "向左平移并微调水平"
              "guide_text": "string" // [重点] 面向普通用户的简明中文指导15字以内，例如：“请拿着手机向左平移两步，让人物位于左侧三分线上”
            }
          ]
        }
      }"""


# 5 种构图技术的 User Prompt
TECHNIQUE_CONFIGS: Dict[str, TechniqueConfig] = {
    "rule_of_thirds": TechniqueConfig(
        id="rule_of_thirds",
        name="三分构图",
        user_prompt="""# Task Request
    The user has uploaded an image. Please apply the **"Rule of Thirds"** technique to improve the composition of the uploaded image.)"""
    ),
    "center_composition": TechniqueConfig(
        id="center_composition",
        name="中心构图",
        user_prompt="""# Task Request
    The user has uploaded an image. Please apply the **"Center Composition / Symmetry"** technique to improve the composition of the uploaded image.) """
    ),
    "leading_lines": TechniqueConfig(
        id="leading_lines",
        name="引导线构图",
        user_prompt="""# Task Request
    The user has uploaded an image. Please apply the **"Leading Lines"** technique to improve the composition of the uploaded image.)"""
    ),
    "foreground_framing": TechniqueConfig(
        id="foreground_framing",
        name="前景/框架构图",
        user_prompt="""# Task Request
    The user has uploaded an image. Please apply the **"Foreground Framing"** technique to improve the composition of the uploaded image.)"""
    ),
    "diagonal_composition": TechniqueConfig(
        id="diagonal_composition",
        name="对角线构图",
        user_prompt="""# Task Request
    The user has uploaded an image. Please apply the **"Diagonal Composition"** technique to improve the composition of the uploaded image.)"""
    ),
}

# 模型参数
MODEL_TEMPERATURE = 1.0
MODEL_TOP_K = 40
MODEL_TOP_P = 0.95
MODEL_MAX_TOKENS = 1024
IMAGE_SIZE = "512"
THINKING_LEVEL="MINIMAL"
