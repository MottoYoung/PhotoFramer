"""
同步测试运行器 - 阶段2：响应时间测量

使用同步 API 调用精确测量每次请求的响应时间。
适用于筛选后的优质提示词，关注实时性指标。
"""

import json
import time
import os
import base64
from pathlib import Path
from datetime import datetime
from dataclasses import dataclass, field, asdict
from typing import Any

from google import genai
from google.genai import types
from PIL import Image

from prompts_config import (
    PromptConfig, ModelConfig, TestCase,
    generate_test_matrix, PROMPT_VERSIONS, MODEL_CONFIGS
)


# ==================== 配置 ==================== #
# 【原Gemini官方API配置】
GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "AIzaSyC_KWWzdi39-TDTOhNRMZmlvFj8IwwH9u4")

# 【中转API配置】
# 使用 Bearer Token 鉴权格式: Authorization: Bearer sk-xxxxxx
# GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "sk-MgVuquDqVte3C4PlsXoKm7wxdhcnYV0IsWYtTX5NEJbdEGQ2")  # 中转API的key，格式如 sk-xxxxxx
# GEMINI_BASE_URL = os.environ.get("GEMINI_BASE_URL", "https://yinli.one/v1beta/models/gemini-2.5-flash-image:generateContent")  # 中转API地址

MODEL_NAME = "gemini-2.5-flash-image"

# 目录配置
BASE_DIR = Path(__file__).parent
RESULTS_DIR = BASE_DIR / "results"


@dataclass
class TimingResult:
    """单次调用的时间测量结果"""
    case_id: str
    run_index: int
    start_time: str             # ISO 格式时间戳
    end_time: str               # ISO 格式时间戳
    response_time_ms: float     # 响应时间（毫秒）
    success: bool
    error_message: str | None = None
    texts: list[str] = field(default_factory=list)
    images: list[str] = field(default_factory=list)  # 图片路径
    token_usage: dict | None = None
    
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
    """
    初始化 Gemini 客户端
    
    支持两种模式：
    1. 官方API：仅设置 GEMINI_API_KEY，不设置 GEMINI_BASE_URL
    2. 中转API：同时设置 GEMINI_API_KEY 和 GEMINI_BASE_URL
    
    中转API使用 Bearer Token 鉴权格式: Authorization: Bearer sk-xxxxxx
    """
    if not GEMINI_API_KEY:
        raise ValueError(
            "请设置 GEMINI_API_KEY 环境变量\n"
            "中转API格式: sk-xxxxxx\n"
            "官方API获取: https://aistudio.google.com/app/apikey"
        )
    
    # 【原Gemini官方API客户端初始化 - 已注释】
    return genai.Client(api_key=GEMINI_API_KEY)
    
    # 【中转API客户端初始化】
    # 使用 http_options 配置自定义 base URL 和鉴权头
    if GEMINI_BASE_URL:
        # 中转API模式：使用自定义 base URL
        client = genai.Client(
            api_key=GEMINI_API_KEY,
            http_options={
                "base_url": GEMINI_BASE_URL,
                # Bearer Token 鉴权由 SDK 自动处理，api_key 会被转换为 Authorization: Bearer {api_key}
            }
        )
        print(f"📡 使用中转API: {GEMINI_BASE_URL}")
    else:
        # 官方API模式
        client = genai.Client(api_key=GEMINI_API_KEY)
        print("📡 使用Gemini官方API")
    
    return client


def run_single_test(
    client: genai.Client,
    test_case: TestCase,
    input_image_path: str | Path,
    run_index: int,
    output_dir: Path
) -> TimingResult:
    """
    执行单次同步 API 调用并测量时间
    
    Args:
        client: Gemini 客户端
        test_case: 测试用例
        input_image_path: 输入图像路径
        run_index: 运行序号
        output_dir: 输出目录
        
    Returns:
        TimingResult 对象
    """
    case_dir = output_dir / test_case.case_id
    case_dir.mkdir(parents=True, exist_ok=True)
    
    # 加载图像
    image = Image.open(input_image_path)
    contents = [test_case.prompt.user_prompt, image]
    
    # 配置生成参数
    config = types.GenerateContentConfig(
        response_modalities=["Text", "Image"],
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
        
        # 结束计时
        end_ts = time.perf_counter()
        end_time = datetime.now()
        
        result.end_time = end_time.isoformat()
        result.response_time_ms = (end_ts - start_ts) * 1000
        result.success = True
        
        # 解析响应
        if hasattr(response, "parts"):
            text_idx = 0
            image_idx = 0
            for part in response.parts:
                if hasattr(part, "text") and part.text:
                    result.texts.append(part.text)
                    # 保存文本
                    txt_path = case_dir / f"run{run_index}_text_{text_idx}.txt"
                    with open(txt_path, "w", encoding="utf-8") as f:
                        f.write(part.text)
                    text_idx += 1
                
                elif hasattr(part, "inline_data") and part.inline_data:
                    # 保存图片
                    img = part.as_image()
                    img_path = case_dir / f"run{run_index}_image_{image_idx}.png"
                    img.save(img_path)
                    result.images.append(str(img_path))
                    image_idx += 1
        
        # 获取 token 使用量（如果可用）
        if hasattr(response, "usage_metadata"):
            result.token_usage = {
                "prompt_tokens": getattr(response.usage_metadata, "prompt_token_count", None),
                "candidates_tokens": getattr(response.usage_metadata, "candidates_token_count", None),
                "total_tokens": getattr(response.usage_metadata, "total_token_count", None)
            }
    
    except Exception as e:
        end_ts = time.perf_counter()
        end_time = datetime.now()
        result.end_time = end_time.isoformat()
        result.response_time_ms = (end_ts - start_ts) * 1000
        result.error_message = str(e)
    
    return result


def run_sync_assessment(
    input_image_path: str | Path,
    test_cases: list[TestCase] | None = None,
    repeats: int = 3,
    delay_between_calls: float = 1.0,
    prompt_id: str | None = None
) -> list[AggregatedResult]:
    """
    运行同步测评（阶段2）
    
    Args:
        input_image_path: 输入图像路径
        test_cases: 测试用例列表（默认从配置生成）
        repeats: 每个用例重复次数
        delay_between_calls: 调用间隔（秒），避免速率限制
        prompt_id: 指定提示词版本ID（默认全部）
        
    Returns:
        聚合结果列表
    """
    client = init_client()
    
    if test_cases is None:
        test_cases = list(generate_test_matrix())
        # 按 prompt_id 过滤
        if prompt_id:
            test_cases = [tc for tc in test_cases if tc.prompt.id == prompt_id]
            if not test_cases:
                raise ValueError(f"未找到提示词版本: {prompt_id}")
    
    # 创建输出目录
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    output_dir = RESULTS_DIR / f"sync_{timestamp}"
    output_dir.mkdir(parents=True, exist_ok=True)
    
    print(f"\n{'='*60}")
    print(f"⚡ 同步测评 - 阶段2: 响应时间测量")
    print(f"{'='*60}")
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
                client, test_case, input_image_path, run_idx, output_dir
            )
            timings.append(timing)
            
            if timing.success:
                print(f"✓ {timing.response_time_ms:.0f}ms")
            else:
                print(f"✗ {timing.error_message}")
            
            # 调用间隔
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
    
    # 构建提示词配置信息
    prompts_info = {}
    for tc in test_cases:
        if tc.prompt.id not in prompts_info:
            prompts_info[tc.prompt.id] = {
                "description": tc.prompt.description,
                "system_instruction": tc.prompt.system_instruction,
                "user_prompt": tc.prompt.user_prompt
            }
    
    # 保存汇总结果
    summary = {
        "timestamp": timestamp,
        "model_name": MODEL_NAME,
        "input_image": str(input_image_path),
        "total_cases": len(test_cases),
        "repeats_per_case": repeats,
        "prompts_config": prompts_info,
        "results": [r.to_dict() for r in all_results]
    }
    
    summary_path = output_dir / "summary.json"
    with open(summary_path, "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    
    print(f"\n✅ 测评完成，结果已保存到: {output_dir}")
    
    # 打印排行榜
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
    
    parser = argparse.ArgumentParser(description="同步测评提示词（阶段2：响应时间测量）")
    parser.add_argument("--image", "-i", required=True, help="输入图像路径")
    parser.add_argument("--repeats", "-r", type=int, default=3, help="每个用例重复次数")
    parser.add_argument("--delay", "-d", type=float, default=1.0, help="调用间隔（秒）")
    
    args = parser.parse_args()
    
    results = run_sync_assessment(
        input_image_path=args.image,
        repeats=args.repeats,
        delay_between_calls=args.delay
    )
