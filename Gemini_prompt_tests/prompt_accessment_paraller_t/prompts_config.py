"""
并行提示词测评配置模块

管理并行构图方案的提示词配置和模型参数
支持从 YAML 文件加载配置
"""

from dataclasses import dataclass, field
from typing import Iterator, Dict, List, Optional
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
class ModelConfig:
    """模型参数配置"""
    id: str
    temperature: float = 1.0
    top_k: int = 40
    top_p: float = 0.95
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
class ParallelPromptSet:
    """并行提示词集合 = 统一 System Prompt + 多个 User Prompt"""
    id: str
    description: str
    system_instruction: str
    user_prompts: Dict[str, str]  # technique_id -> user_prompt
    
    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "description": self.description,
            "system_instruction": self.system_instruction,
            "user_prompts": self.user_prompts,
        }


@dataclass
class CompositionRequest:
    """单个构图方案请求"""
    technique_id: str           # 构图技术ID，如 "rule_of_thirds"
    user_prompt: str            # 对应的 User Prompt
    system_instruction: str     # 共享的 System Prompt
    model_config: ModelConfig   # 模型配置
    
    @property
    def request_id(self) -> str:
        """请求ID，用于结果匹配"""
        return f"{self.technique_id}_{self.model_config.id}"
    
    def to_dict(self) -> dict:
        return {
            "request_id": self.request_id,
            "technique_id": self.technique_id,
            "user_prompt": self.user_prompt,
            "model_config": self.model_config.to_dict(),
        }


# ==================== YAML 加载功能 ==================== #

def load_prompts_from_yaml(yaml_path: Path = CONFIG_FILE) -> List[ParallelPromptSet]:
    """从 YAML 文件加载并行提示词配置"""
    if not YAML_AVAILABLE:
        print("YAML 不可用，使用内置配置")
        return []
    
    if not yaml_path.exists():
        print(f"配置文件不存在: {yaml_path}")
        return []
    
    with open(yaml_path, "r", encoding="utf-8") as f:
        data = yaml.safe_load(f)
    
    prompt_sets = []
    for p in data.get("prompts", []):
        # 收集所有 user_prompt_X 字段
        user_prompts = {}
        technique_map = {
            "user_prompt_A": "rule_of_thirds",
            "user_prompt_B": "center_composition", 
            "user_prompt_C": "leading_lines",
            "user_prompt_D": "foreground_framing",
            "user_prompt_E": "diagonal_composition",
        }
        
        for yaml_key, technique_id in technique_map.items():
            if yaml_key in p:
                user_prompts[technique_id] = p[yaml_key]
        
        if user_prompts:
            prompt_sets.append(ParallelPromptSet(
                id=p["id"],
                description=p.get("description", ""),
                system_instruction=p.get("system_instruction", ""),
                user_prompts=user_prompts,
            ))
    
    return prompt_sets


def load_model_configs_from_yaml(yaml_path: Path = CONFIG_FILE) -> List[ModelConfig]:
    """从 YAML 文件加载模型配置"""
    if not YAML_AVAILABLE:
        return [ModelConfig(id="default")]
    
    if not yaml_path.exists():
        return [ModelConfig(id="default")]
    
    with open(yaml_path, "r", encoding="utf-8") as f:
        data = yaml.safe_load(f)
    
    configs = []
    for c in data.get("model_configs", []):
        configs.append(ModelConfig(
            id=c["id"],
            temperature=c.get("temperature", 1.0),
            top_k=c.get("top_k", 40),
            top_p=c.get("top_p", 0.95),
            description=c.get("description", ""),
        ))
    
    return configs if configs else [ModelConfig(id="default")]


def get_prompt_set_by_id(prompt_id: str | None = None) -> ParallelPromptSet:
    """
    根据 ID 获取提示词集合
    
    Args:
        prompt_id: 提示词集合 ID，None 时返回第一个
        
    Returns:
        ParallelPromptSet 对象
    """
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
    prompt_set: ParallelPromptSet | None = None,
    prompt_id: str | None = None,
    model_config: ModelConfig | None = None,
) -> List[CompositionRequest]:
    """
    生成构图请求列表
    
    Args:
        prompt_set: 并行提示词集合，优先级最高
        prompt_id: 提示词集合 ID，用于按 ID 选择
        model_config: 模型配置，None 时从配置加载第一个
        
    Returns:
        CompositionRequest 列表（每种构图技术一个）
    """
    if prompt_set is None:
        prompt_set = get_prompt_set_by_id(prompt_id)
    
    if model_config is None:
        model_configs = load_model_configs_from_yaml()
        model_config = model_configs[0]
    
    requests = []
    for technique_id, user_prompt in prompt_set.user_prompts.items():
        requests.append(CompositionRequest(
            technique_id=technique_id,
            user_prompt=user_prompt,
            system_instruction=prompt_set.system_instruction,
            model_config=model_config,
        ))
    
    return requests


def generate_all_requests(
    model_config: ModelConfig | None = None,
) -> Dict[str, List[CompositionRequest]]:
    """
    为所有提示词版本生成构图请求
    
    Args:
        model_config: 模型配置，None 时从配置加载第一个
        
    Returns:
        字典，key 为提示词版本 ID，value 为 CompositionRequest 列表
    """
    prompt_sets = load_prompts_from_yaml()
    if model_config is None:
        model_configs = load_model_configs_from_yaml()
        model_config = model_configs[0]
    
    all_requests = {}
    for ps in prompt_sets:
        requests = []
        for technique_id, user_prompt in ps.user_prompts.items():
            requests.append(CompositionRequest(
                technique_id=technique_id,
                user_prompt=user_prompt,
                system_instruction=ps.system_instruction,
                model_config=model_config,
            ))
        all_requests[ps.id] = requests
    
    return all_requests


def generate_test_matrix(
    prompt_sets: List[ParallelPromptSet] | None = None,
    model_configs: List[ModelConfig] | None = None,
) -> Iterator[tuple[str, str, CompositionRequest]]:
    """
    生成完整测试矩阵：提示词版本 × 构图技术 × 模型配置
    
    Args:
        prompt_sets: 提示词集合列表，None 时从配置加载
        model_configs: 模型配置列表，None 时从配置加载
        
    Yields:
        (prompt_id, technique_id, CompositionRequest) 元组
    """
    if prompt_sets is None:
        prompt_sets = load_prompts_from_yaml()
    if model_configs is None:
        model_configs = load_model_configs_from_yaml()
    
    for ps in prompt_sets:
        for model_config in model_configs:
            for technique_id, user_prompt in ps.user_prompts.items():
                yield (
                    ps.id,
                    technique_id,
                    CompositionRequest(
                        technique_id=technique_id,
                        user_prompt=user_prompt,
                        system_instruction=ps.system_instruction,
                        model_config=model_config,
                    )
                )


def load_config(yaml_path: Path = CONFIG_FILE) -> tuple[List[ParallelPromptSet], List[ModelConfig]]:
    """从 YAML 文件加载所有配置"""
    return load_prompts_from_yaml(yaml_path), load_model_configs_from_yaml(yaml_path)


if __name__ == "__main__":
    # 测试：打印配置预览
    print("=" * 60)
    print("并行提示词配置预览")
    print("=" * 60)
    
    print(f"\n📂 配置文件: {CONFIG_FILE}")
    print(f"   YAML 可用: {YAML_AVAILABLE}")
    
    prompt_sets = load_prompts_from_yaml()
    model_configs = load_model_configs_from_yaml()
    
    print(f"\n📋 提示词集合 ({len(prompt_sets)}):")
    for ps in prompt_sets:
        print(f"  - {ps.id}: {ps.description}")
        print(f"    包含 {len(ps.user_prompts)} 种构图技术:")
        for tech_id in ps.user_prompts.keys():
            print(f"      • {tech_id}")
    
    print(f"\n⚙️ 模型配置 ({len(model_configs)}):")
    for c in model_configs:
        print(f"  - {c.id}: temp={c.temperature}, topK={c.top_k}, topP={c.top_p}")
    
    print(f"\n🎯 生成请求示例 (第一个提示词版本):")
    if prompt_sets:
        requests = generate_composition_requests()
        for req in requests:
            print(f"  - {req.request_id}")
    
    print(f"\n📦 完整测试矩阵:")
    all_reqs = list(generate_test_matrix())
    print(f"   总计: {len(prompt_sets)} 提示词版本 × {len(model_configs)} 模型配置 × 5 构图技术 = {len(all_reqs)} 组合")
    
    # 按提示词版本分组显示
    from collections import defaultdict
    by_prompt = defaultdict(list)
    for prompt_id, technique_id, req in all_reqs:
        by_prompt[prompt_id].append(technique_id)
    
    for prompt_id, techs in by_prompt.items():
        print(f"   - {prompt_id}: {len(set(techs))} 技术")
