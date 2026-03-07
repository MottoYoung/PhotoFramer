"""
提示词测评配置模块

管理提示词版本和模型配置参数，生成测试矩阵
支持从 YAML 文件加载配置
"""

from dataclasses import dataclass, field
from typing import Iterator, Optional, List
import json
from pathlib import Path

try:
    import yaml
    YAML_AVAILABLE = True
except ImportError:
    YAML_AVAILABLE = False
    print("警告: 未安装 pyyaml，将使用内置配置。安装: pip install pyyaml")

# 配置文件路径
CONFIG_FILE = Path(__file__).parent / "config.yaml"

@dataclass
class PromptConfig:
    """提示词配置"""
    id: str                    # 唯一标识，如 "v1.0"
    system_instruction: str    # 系统指令
    user_prompt: str           # 用户提示词
    description: str = ""      # 变更说明
    
    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "system_instruction": self.system_instruction,
            "user_prompt": self.user_prompt,
            "description": self.description,
        }


@dataclass
class ModelConfig:
    """模型参数配置"""
    id: str                    # 唯一标识，如 "default", "high_temp"
    temperature: float = 1.0   # 0.0 - 2.0
    top_k: int = 40            # 1 - 40
    top_p: float = 0.95        # 0.0 - 1.0
    description: str = ""
    
    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "temperature": self.temperature,
            "top_k": self.top_k,
            "top_p": self.top_p,
            "description": self.description,
        }


@dataclass
class TestCase:
    """单个测试用例 = 提示词 × 模型配置"""
    prompt: PromptConfig
    model_config: ModelConfig
    
    @property
    def case_id(self) -> str:
        """组合ID，如 'v1.0_high_temp'"""
        return f"{self.prompt.id}_{self.model_config.id}"
    
    def to_dict(self) -> dict:
        return {
            "case_id": self.case_id,
            "prompt": self.prompt.to_dict(),
            "model_config": self.model_config.to_dict(),
        }


# ==================== 提示词版本定义 ==================== #

# 基础系统指令（从现有代码提取）
BASE_SYSTEM_INSTRUCTION = '''**Role:** You are an AI Photography Director ("Camera Coach"). Your goal is to guide a user to take a better photo by re-composing their shot.

**Task:**
Analyze the input image and generate **1 to 4** aesthetically superior compositions.
* **Quality over Quantity:** Only generate a composition if it meaningfully improves the original. Do not force 4 outputs if fewer options are sufficient.
* For each composition, provide a generative Target Image and structured guidance.

**Action Definitions:**
* `Shift`: Moving camera (Left, Right, Up, Down).
* `Zoom`: Adjusting focal length/distance (In, Out).
* `View-change`: Changing angle (High-angle, Low-angle, Side-view).

**Output Structure (Strict Sequence):**
You must output in the following order. The **Header JSON** must be the very first output.

1.  **[Header JSON]**: Contains the total count of compositions to be generated.
2.  **[Composition 1 JSON]**: Detailed steps for the first option.
3.  **[Generated Image 1]**: The visual result for option 1.
4.  **[Composition 2 JSON]**: (If applicable)
5.  **[Generated Image 2]**: (If applicable)
... and so on.

**JSON Schema Definition:**

**1. Header JSON:**
{
  "type": "header",
  "total_count": 2, // Integer between 1 and 4
  "analysis": "Brief analysis of the original shot (e.g., 'Lighting is good, but horizon is tilted.')."
}

**2. Composition JSON:**
{
  "type": "composition",
  "id": 1,
  "aesthetic_desc": "Short Chinese description of the improvement.",
  "steps": [
    {
      "step_order": 1,
      "action_type": "Shift", // 'Shift', 'Zoom', 'View-change'
      "direction": "Left",
      "guide_text": "向左平移，将人物置于三分线处。" // Chinese instruction
    },
    ...
  ]
}

**Content Requirements:**
1.  **Language:** All textual descriptions must be in **Chinese (中文)**.
2.  **Consistency:** Instructions must match the generated image.'''

BASE_USER_PROMPT = '''请分析这张照片，判断有几种（1-4种）更好的构图方案。
请首先输出包含 `total_count` 的 Header JSON，然后依次输出每种方案的 JSON 指令和对应的生成图片。'''


# 预定义提示词版本（可根据需要添加）
PROMPT_VERSIONS: list[PromptConfig] = [
    PromptConfig(
        id="v1_base",
        system_instruction=BASE_SYSTEM_INSTRUCTION,
        user_prompt=BASE_USER_PROMPT,
        description="基础版本，来自现有代码"
    ),
    PromptConfig(
        id="v2_concise",
        system_instruction='''你是一位摄影构图专家。分析用户照片，生成1-4个优化构图方案。

输出格式：先输出Header JSON，再依次输出每个方案的JSON和生成图片。
- Header: {"type": "header", "total_count": N, "analysis": "分析"}
- Composition: {"type": "composition", "id": N, "aesthetic_desc": "描述", "steps": [...]}

所有描述使用中文。''',
        user_prompt="分析这张照片，给出优化构图方案。",
        description="精简版，减少token消耗"
    ),
    PromptConfig(
        id="v3_detailed",
        system_instruction=BASE_SYSTEM_INSTRUCTION + '''

**Additional Guidelines:**
- Consider the rule of thirds, leading lines, and symmetry.
- Pay attention to foreground/background balance.
- Suggest adjustments for lighting and exposure if relevant.''',
        user_prompt=BASE_USER_PROMPT + "\n请特别关注构图规则（三分法、引导线等）。",
        description="详细版，增强构图规则引导"
    ),
]


# 预定义模型配置
MODEL_CONFIGS: list[ModelConfig] = [
    ModelConfig(
        id="default",
        temperature=1.0,
        top_k=40,
        top_p=0.95,
        description="默认配置"
    ),
    ModelConfig(
        id="creative",
        temperature=1.5,
        top_k=40,
        top_p=0.95,
        description="高创造性，更多样化的输出"
    ),
    ModelConfig(
        id="precise",
        temperature=0.5,
        top_k=20,
        top_p=0.8,
        description="高精确性，更稳定的输出"
    ),
]


# ==================== YAML 加载功能 ==================== #

def load_prompts_from_yaml(yaml_path: Path = CONFIG_FILE) -> List[PromptConfig]:
    """从 YAML 文件加载提示词配置"""
    if not YAML_AVAILABLE:
        print("YAML 不可用，使用内置配置")
        return PROMPT_VERSIONS
    
    if not yaml_path.exists():
        print(f"配置文件不存在: {yaml_path}，使用内置配置")
        return PROMPT_VERSIONS
    
    with open(yaml_path, "r", encoding="utf-8") as f:
        data = yaml.safe_load(f)
    
    prompts = []
    for p in data.get("prompts", []):
        prompts.append(PromptConfig(
            id=p["id"],
            system_instruction=p.get("system_instruction", ""),
            user_prompt=p.get("user_prompt", ""),
            description=p.get("description", "")
        ))
    
    return prompts if prompts else PROMPT_VERSIONS


def load_model_configs_from_yaml(yaml_path: Path = CONFIG_FILE) -> List[ModelConfig]:
    """从 YAML 文件加载模型配置"""
    if not YAML_AVAILABLE:
        return MODEL_CONFIGS
    
    if not yaml_path.exists():
        return MODEL_CONFIGS
    
    with open(yaml_path, "r", encoding="utf-8") as f:
        data = yaml.safe_load(f)
    
    configs = []
    for c in data.get("model_configs", []):
        configs.append(ModelConfig(
            id=c["id"],
            temperature=c.get("temperature", 1.0),
            top_k=c.get("top_k", 40),
            top_p=c.get("top_p", 0.95),
            description=c.get("description", "")
        ))
    
    return configs if configs else MODEL_CONFIGS


def load_config(yaml_path: Path = CONFIG_FILE) -> tuple[List[PromptConfig], List[ModelConfig]]:
    """从 YAML 文件加载所有配置"""
    return load_prompts_from_yaml(yaml_path), load_model_configs_from_yaml(yaml_path)


def generate_test_matrix(
    prompts: Optional[List[PromptConfig]] = None,
    model_configs: Optional[List[ModelConfig]] = None,
    from_yaml: bool = True
) -> Iterator[TestCase]:
    """
    生成测试矩阵：提示词 × 模型配置
    
    Args:
        prompts: 提示词列表，None 时从配置加载
        model_configs: 模型配置列表，None 时从配置加载
        from_yaml: 是否从 YAML 文件加载（默认 True）
        
    Yields:
        TestCase 对象
    """
    if prompts is None:
        prompts = load_prompts_from_yaml() if from_yaml else PROMPT_VERSIONS
    if model_configs is None:
        model_configs = load_model_configs_from_yaml() if from_yaml else MODEL_CONFIGS
    
    for prompt in prompts:
        for config in model_configs:
            yield TestCase(prompt=prompt, model_config=config)


def save_test_matrix(
    output_path: str | Path,
    prompts: Optional[List[PromptConfig]] = None,
    model_configs: Optional[List[ModelConfig]] = None
):
    """保存测试矩阵到JSON文件"""
    cases = list(generate_test_matrix(prompts, model_configs))
    prompts_list = prompts or load_prompts_from_yaml()
    configs_list = model_configs or load_model_configs_from_yaml()
    
    data = {
        "total_cases": len(cases),
        "prompts_count": len(prompts_list),
        "model_configs_count": len(configs_list),
        "cases": [case.to_dict() for case in cases]
    }
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    return data


if __name__ == "__main__":
    # 测试：打印测试矩阵
    print("=" * 60)
    print("测试矩阵预览")
    print("=" * 60)
    
    # 从 YAML 加载
    prompts = load_prompts_from_yaml()
    configs = load_model_configs_from_yaml()
    
    print(f"\n📂 配置文件: {CONFIG_FILE}")
    print(f"   YAML 可用: {YAML_AVAILABLE}")
    
    for case in generate_test_matrix():
        print(f"- {case.case_id}: {case.prompt.description} + {case.model_config.description}")
    
    print(f"\n总计: {len(prompts)} 提示词 × {len(configs)} 模型配置 = {len(prompts) * len(configs)} 组合")

