"""
Qwen-Image 2.0 并行提示词测评配置模块

加载 YAML 中的并行提示词配置（不含 system prompt）
"""

from dataclasses import dataclass
from typing import Dict, List
from pathlib import Path

try:
    import yaml
    YAML_AVAILABLE = True
except ImportError:
    YAML_AVAILABLE = False
    print("警告: 未安装 pyyaml，将使用空配置。安装: pip install pyyaml")


CONFIG_FILE = Path(__file__).parent / "config.yaml"


@dataclass
class PromptSet:
    """并行提示词集合"""
    id: str
    description: str
    user_prompts: Dict[str, str]  # technique_id -> user_prompt

    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "description": self.description,
            "user_prompts": self.user_prompts,
        }


@dataclass
class CompositionRequest:
    """单个构图方案请求"""
    technique_id: str
    user_prompt: str
    prompt_set_id: str

    @property
    def request_id(self) -> str:
        return f"{self.technique_id}_{self.prompt_set_id}"

    def to_dict(self) -> dict:
        return {
            "request_id": self.request_id,
            "technique_id": self.technique_id,
            "prompt_set_id": self.prompt_set_id,
            "user_prompt": self.user_prompt,
        }


TECHNIQUE_MAP = {
    "user_prompt_A": "rule_of_thirds",
    "user_prompt_B": "center_composition",
    "user_prompt_C": "leading_lines",
    "user_prompt_D": "foreground_framing",
    "user_prompt_E": "diagonal_composition",
}


def load_prompts_from_yaml(yaml_path: Path = CONFIG_FILE) -> List[PromptSet]:
    if not YAML_AVAILABLE:
        return []
    if not yaml_path.exists():
        print(f"配置文件不存在: {yaml_path}")
        return []

    with open(yaml_path, "r", encoding="utf-8") as f:
        data = yaml.safe_load(f) or {}

    prompt_sets: List[PromptSet] = []
    for p in data.get("prompts", []):
        user_prompts: Dict[str, str] = {}
        for yaml_key, technique_id in TECHNIQUE_MAP.items():
            if yaml_key in p:
                user_prompts[technique_id] = p[yaml_key]

        if user_prompts:
            prompt_sets.append(
                PromptSet(
                    id=p.get("id", "unnamed"),
                    description=p.get("description", ""),
                    user_prompts=user_prompts,
                )
            )

    return prompt_sets


def get_prompt_set_by_id(prompt_id: str | None = None) -> PromptSet:
    prompt_sets = load_prompts_from_yaml()
    if not prompt_sets:
        raise ValueError("未找到提示词配置")

    if prompt_id is None:
        return prompt_sets[0]

    for ps in prompt_sets:
        if ps.id == prompt_id:
            return ps

    available_ids = [ps.id for ps in prompt_sets]
    raise ValueError(f"未找到提示词集合: {prompt_id}，可用: {available_ids}")


def generate_composition_requests(
    prompt_set: PromptSet | None = None,
    prompt_id: str | None = None,
) -> List[CompositionRequest]:
    if prompt_set is None:
        prompt_set = get_prompt_set_by_id(prompt_id)

    requests: List[CompositionRequest] = []
    for technique_id, user_prompt in prompt_set.user_prompts.items():
        requests.append(
            CompositionRequest(
                technique_id=technique_id,
                user_prompt=user_prompt,
                prompt_set_id=prompt_set.id,
            )
        )

    return requests


if __name__ == "__main__":
    print("=" * 60)
    print("Qwen 并行提示词配置预览")
    print("=" * 60)
    print(f"配置文件: {CONFIG_FILE}")
    prompt_sets = load_prompts_from_yaml()
    print(f"提示词集合数: {len(prompt_sets)}")
    for ps in prompt_sets:
        print(f"- {ps.id}: {ps.description}")
        for tech in ps.user_prompts.keys():
            print(f"  • {tech}")
