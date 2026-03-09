"""
同步测试运行器 - 纯指令/有参考图版本

输入：原始照片 + 参考构图图（目标图）
输出：从原图到参考图的相机操作 JSON 指令（纯文本，无图像生成）

使用方法:
    python main.py sync -i <原图路径> --ref <参考图路径> [-p <提示词版本>] [-r <重复次数>]
"""

import json
import time
import os
import re
from pathlib import Path
from datetime import datetime
from dataclasses import dataclass, field, asdict
from typing import Optional

from google import genai
from google.genai import types
from PIL import Image

from prompts_config import (
    PromptConfig, ModelConfig, TestCase,
    generate_test_matrix, PROMPT_VERSIONS, MODEL_CONFIGS
)


# ==================== 配置 ==================== #
GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "AIzaSyC_KWWzdi39-TDTOhNRMZmlvFj8IwwH9u4")

# 纯文本输出，使用 flash 模型（无需图像生成）
MODEL_NAME = "gemini-2.5-flash"

# 目录配置
BASE_DIR = Path(__file__).parent
RESULTS_DIR = BASE_DIR / "results"


@dataclass
class TimingResult:
    """单次调用的测量结果"""
    case_id: str
    run_index: int
    start_time: str
    end_time: str
    response_time_ms: float
    success: bool
    error_message: Optional[str] = None
    raw_text: str = ""
    parsed_json: Optional[dict] = None
    token_usage: Optional[dict] = None

    def to_dict(self) -> dict:
        return asdict(self)


@dataclass
class AggregatedResult:
    """聚合测量结果"""
    case_id: str
    prompt_id: str
    model_config_id: str
    total_runs: int
    successful_runs: int
    failed_runs: int
    avg_response_time_ms: float
    min_response_time_ms: float
    max_response_time_ms: float
    std_response_time_ms: float
    all_timings: list[TimingResult] = field(default_factory=list)

    def to_dict(self) -> dict:
        result = asdict(self)
        result["all_timings"] = [t.to_dict() for t in self.all_timings]
        return result


def init_client() -> genai.Client:
    """初始化 Gemini 官方 API 客户端"""
    if not GEMINI_API_KEY:
        raise ValueError(
            "请设置 GEMINI_API_KEY 环境变量\n"
            "获取方式: https://aistudio.google.com/app/apikey"
        )
    client = genai.Client(api_key=GEMINI_API_KEY)
    print("📡 使用Gemini官方API")
    return client


def _parse_json_from_text(text: str) -> Optional[dict]:
    """从响应文本中解析 JSON"""
    # 尝试提取 ```json ... ``` 块
    json_match = re.search(r'```json\s*(.*?)\s*```', text, re.DOTALL)
    if json_match:
        try:
            return json.loads(json_match.group(1))
        except json.JSONDecodeError:
            pass

    # 尝试直接解析
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass

    # 尝试查找 { ... } 结构
    brace_match = re.search(r'\{.*\}', text, re.DOTALL)
    if brace_match:
        try:
            return json.loads(brace_match.group(0))
        except json.JSONDecodeError:
            pass

    return None


def run_single_test(
    client: genai.Client,
    original_image_path: str | Path,
    reference_image_path: str | Path,
    test_case: TestCase,
    run_index: int,
    output_dir: Path
) -> TimingResult:
    """
    执行单次 API 调用：输入原图 + 参考图，输出操作指令 JSON

    Args:
        client: Gemini 客户端
        original_image_path: 原始照片路径（用户当前画面）
        reference_image_path: 参考构图图路径（目标效果）
        test_case: 测试用例（含 prompt 和模型配置）
        run_index: 运行序号
        output_dir: 输出目录
    """
    case_dir = output_dir / test_case.case_id
    case_dir.mkdir(parents=True, exist_ok=True)

    # 加载两张图片
    original_image = Image.open(original_image_path)
    reference_image = Image.open(reference_image_path)

    # 构建 contents：user_prompt + 原图 + 参考图
    # 顺序与 user_prompt 中的"图1"、"图2"对应
    contents = [
        test_case.prompt.user_prompt,
        original_image,
        reference_image,
    ]

    config = types.GenerateContentConfig(
        response_modalities=["Text"],  # 纯文本输出，不生成图片
        system_instruction=test_case.prompt.system_instruction,
        temperature=test_case.model_config.temperature,
        top_k=test_case.model_config.top_k,
        top_p=test_case.model_config.top_p,
    )

    # 开始计时
    start_time = datetime.now()
    start_ts = time.perf_counter()

    result = TimingResult(
        case_id=test_case.case_id,
        run_index=run_index,
        start_time=start_time.isoformat(),
        end_time="",
        response_time_ms=0,
        success=False
    )

    try:
        response = client.models.generate_content(
            model=MODEL_NAME,
            contents=contents,
            config=config
        )

        end_ts = time.perf_counter()
        end_time = datetime.now()

        result.end_time = end_time.isoformat()
        result.response_time_ms = (end_ts - start_ts) * 1000
        result.success = True

        # 提取文本输出
        if hasattr(response, "parts"):
            for part in response.parts:
                if hasattr(part, "text") and part.text:
                    result.raw_text += part.text

        # 解析 JSON
        if result.raw_text:
            result.parsed_json = _parse_json_from_text(result.raw_text)

        # 保存原始文本输出
        txt_path = case_dir / f"run{run_index}_output.txt"
        with open(txt_path, "w", encoding="utf-8") as f:
            f.write(result.raw_text)

        # 保存解析后的 JSON（如果成功）
        if result.parsed_json:
            json_path = case_dir / f"run{run_index}_parsed.json"
            with open(json_path, "w", encoding="utf-8") as f:
                json.dump(result.parsed_json, f, ensure_ascii=False, indent=2)

        # Token 使用量
        if hasattr(response, "usage_metadata"):
            result.token_usage = {
                "prompt_tokens": getattr(response.usage_metadata, "prompt_token_count", None),
                "candidates_tokens": getattr(response.usage_metadata, "candidates_token_count", None),
                "total_tokens": getattr(response.usage_metadata, "total_token_count", None),
            }

    except Exception as e:
        end_ts = time.perf_counter()
        end_time = datetime.now()
        result.end_time = end_time.isoformat()
        result.response_time_ms = (end_ts - start_ts) * 1000
        result.error_message = str(e)

    return result


def run_sync_assessment(
    original_image_path: str | Path,
    reference_image_path: str | Path,
    repeats: int = 3,
    delay_between_calls: float = 2.0,
    prompt_id: Optional[str] = None
) -> list[AggregatedResult]:
    """
    运行同步测评（原图 + 参考图 → 操作指令）

    Args:
        original_image_path: 原始照片路径
        reference_image_path: 参考构图图路径
        repeats: 每个用例重复次数
        delay_between_calls: 调用间隔（秒）
        prompt_id: 指定提示词版本ID（默认全部）
    """
    client = init_client()

    original_image_path = Path(original_image_path)
    reference_image_path = Path(reference_image_path)

    if not original_image_path.exists():
        raise FileNotFoundError(f"原始图像不存在: {original_image_path}")
    if not reference_image_path.exists():
        raise FileNotFoundError(f"参考图像不存在: {reference_image_path}")

    # 获取测试用例
    test_cases = list(generate_test_matrix())
    if prompt_id:
        test_cases = [tc for tc in test_cases if tc.prompt.id == prompt_id]
        if not test_cases:
            raise ValueError(f"未找到提示词版本: {prompt_id}")

    # 创建输出目录
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    output_dir = RESULTS_DIR / f"sync_{timestamp}"
    output_dir.mkdir(parents=True, exist_ok=True)

    print(f"\n{'='*60}")
    print(f"⚡ 同步测评 - 纯指令/有参考图")
    print(f"{'='*60}")
    print(f"   原始图像: {original_image_path.name}")
    print(f"   参考图像: {reference_image_path.name}")
    print(f"   测试用例数: {len(test_cases)}")
    print(f"   每用例重复: {repeats} 次")
    print(f"   总调用次数: {len(test_cases) * repeats}")
    print(f"   输出目录: {output_dir}")

    all_results: list[AggregatedResult] = []

    for case_idx, test_case in enumerate(test_cases):
        print(f"\n[{case_idx+1}/{len(test_cases)}] 测试: {test_case.case_id}")

        timings: list[TimingResult] = []

        for run_idx in range(repeats):
            print(f"   运行 {run_idx+1}/{repeats}...", end=" ")

            timing = run_single_test(
                client=client,
                original_image_path=original_image_path,
                reference_image_path=reference_image_path,
                test_case=test_case,
                run_index=run_idx,
                output_dir=output_dir
            )
            timings.append(timing)

            if timing.success:
                json_ok = "✓JSON" if timing.parsed_json else "✗JSON"
                steps_count = len(timing.parsed_json.get("steps", [])) if timing.parsed_json else 0
                print(f"✓ {timing.response_time_ms:.0f}ms [{json_ok}, {steps_count}步]")
            else:
                print(f"✗ {timing.error_message}")

            if run_idx < repeats - 1:
                time.sleep(delay_between_calls)

        # 计算聚合指标
        successful_timings = [t for t in timings if t.success]
        response_times = [t.response_time_ms for t in successful_timings]

        if response_times:
            import statistics
            avg_time = statistics.mean(response_times)
            min_time = min(response_times)
            max_time = max(response_times)
            std_time = statistics.stdev(response_times) if len(response_times) > 1 else 0
        else:
            avg_time = min_time = max_time = std_time = 0

        aggregated = AggregatedResult(
            case_id=test_case.case_id,
            prompt_id=test_case.prompt.id,
            model_config_id=test_case.model_config.id,
            total_runs=repeats,
            successful_runs=len(successful_timings),
            failed_runs=repeats - len(successful_timings),
            avg_response_time_ms=avg_time,
            min_response_time_ms=min_time,
            max_response_time_ms=max_time,
            std_response_time_ms=std_time,
            all_timings=timings
        )
        all_results.append(aggregated)

        print(f"   📊 平均: {avg_time:.0f}ms, 最快: {min_time:.0f}ms, 最慢: {max_time:.0f}ms")

    # 保存汇总结果
    prompts_info = {}
    for tc in test_cases:
        if tc.prompt.id not in prompts_info:
            prompts_info[tc.prompt.id] = {
                "description": tc.prompt.description,
                "system_instruction": tc.prompt.system_instruction,
                "user_prompt": tc.prompt.user_prompt,
            }

    summary = {
        "timestamp": timestamp,
        "model_name": MODEL_NAME,
        "original_image": str(original_image_path),
        "reference_image": str(reference_image_path),
        "total_cases": len(test_cases),
        "repeats_per_case": repeats,
        "prompts_config": prompts_info,
        "results": [r.to_dict() for r in all_results],
    }

    summary_path = output_dir / "summary.json"
    with open(summary_path, "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)

    print(f"\n✅ 测评完成，结果已保存到: {output_dir}")

    print(f"\n{'='*60}")
    print("📈 响应时间排行榜（平均，从快到慢）")
    print(f"{'='*60}")
    sorted_results = sorted(all_results, key=lambda r: r.avg_response_time_ms)
    for i, r in enumerate(sorted_results):
        status = "✓" if r.failed_runs == 0 else f"({r.failed_runs}失败)"
        print(f"   {i+1}. {r.case_id}: {r.avg_response_time_ms:.0f}ms {status}")

    return all_results


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="同步测评（原图+参考图 → 操作指令）")
    parser.add_argument("--image", "-i", required=True, help="原始照片路径")
    parser.add_argument("--ref", required=True, help="参考构图图路径（目标效果）")
    parser.add_argument("--prompt", "-p", help="指定提示词版本ID（默认全部）")
    parser.add_argument("--repeats", "-r", type=int, default=3, help="每用例重复次数")
    parser.add_argument("--delay", "-d", type=float, default=2.0, help="调用间隔（秒）")

    args = parser.parse_args()

    results = run_sync_assessment(
        original_image_path=args.image,
        reference_image_path=args.ref,
        repeats=args.repeats,
        delay_between_calls=args.delay,
        prompt_id=args.prompt,
    )
