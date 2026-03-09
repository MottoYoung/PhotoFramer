"""
并行同步测试运行器

使用 asyncio 并发执行 5 种构图方案的 API 调用。
每种构图方案独立计时、独立处理错误。
"""

import asyncio
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
    ParallelPromptSet, ModelConfig, CompositionRequest,
    generate_composition_requests, load_config
)


# ==================== 配置 ==================== #
# 【Gemini官方API配置】
GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY")

# 【中转API配置 - 可选】
# GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "sk-xxxxxx")
# GEMINI_BASE_URL = os.environ.get("GEMINI_BASE_URL", "https://your-relay.api/v1beta")

# MODEL_NAME = "gemini-2.5-flash-image"
# MODEL_NAME = "gemini-3-pro-image-preview"
MODEL_NAME="gemini-3.1-flash-image-preview"

# 目录配置
BASE_DIR = Path(__file__).parent
RESULTS_DIR = BASE_DIR / "results"


@dataclass
class SingleResult:
    """单个构图方案的调用结果"""
    technique_id: str
    request_id: str
    start_time: str
    end_time: str
    response_time_ms: float
    success: bool
    is_applicable: bool | None = None  # 该构图是否适用
    error_message: str | None = None
    texts: list[str] = field(default_factory=list)
    images: list[str] = field(default_factory=list)
    parsed_json: dict | None = None
    token_usage: dict | None = None

    def to_dict(self) -> dict:
        return asdict(self)


@dataclass
class ParallelRunResult:
    """一次并行运行的聚合结果"""
    run_index: int
    timestamp: str
    total_techniques: int
    successful_techniques: int
    applicable_techniques: int
    total_time_ms: float  # 整体运行时间（并发）
    results: list[SingleResult] = field(default_factory=list)

    def to_dict(self) -> dict:
        d = asdict(self)
        d["results"] = [r.to_dict() for r in self.results]
        return d


def init_client() -> genai.Client:
    """初始化 Gemini 客户端"""
    if not GEMINI_API_KEY:
        raise ValueError(
            "请设置 GEMINI_API_KEY 环境变量\n"
            "官方API获取: https://aistudio.google.com/app/apikey"
        )
    return genai.Client(api_key=GEMINI_API_KEY)


def _parse_json_from_text(text: str) -> dict | None:
    """从响应文本中解析 JSON"""
    import re
    # 尝试提取 JSON 块
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


async def call_gemini_async(
    client: genai.Client,
    image_path: str | Path,
    system_instruction: str,
    user_prompt: str,
    technique_id: str,
    model_config: ModelConfig,
    output_dir: Path,
    run_index: int
) -> SingleResult:
    """
    异步调用单个构图方案的 Gemini API
    
    注意：google-genai SDK 本身是同步的，这里使用 asyncio.to_thread 包装
    每个任务独立加载图像，避免并发访问同一 PIL Image 对象的竞态条件
    """
    start_time = datetime.now()
    start_ts = time.perf_counter()
    
    result = SingleResult(
        technique_id=technique_id,
        request_id=f"{technique_id}_{model_config.id}",
        start_time=start_time.isoformat(),
        end_time="",
        response_time_ms=0,
        success=False
    )
    
    try:
        # 每个任务独立加载图像，避免竞态条件
        image = Image.open(image_path)
        contents = [user_prompt, image]
        config = types.GenerateContentConfig(
            response_modalities=["Text", "Image"],
            system_instruction=system_instruction,
            temperature=model_config.temperature,
            top_k=model_config.top_k,
            top_p=model_config.top_p,
        )
        
        # 使用 asyncio.to_thread 将同步调用转为异步
        response = await asyncio.to_thread(
            client.models.generate_content,
            model=MODEL_NAME,
            contents=contents,
            config=config
        )
        
        end_ts = time.perf_counter()
        end_time = datetime.now()
        
        result.end_time = end_time.isoformat()
        result.response_time_ms = (end_ts - start_ts) * 1000
        result.success = True
        
        # 解析响应
        case_dir = output_dir / technique_id
        case_dir.mkdir(parents=True, exist_ok=True)
        
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
                    
                    # 尝试解析 JSON
                    if result.parsed_json is None:
                        result.parsed_json = _parse_json_from_text(part.text)
                
                elif hasattr(part, "inline_data") and part.inline_data:
                    img = part.as_image()
                    img_path = case_dir / f"run{run_index}_image_{image_idx}.png"
                    img.save(img_path)
                    result.images.append(str(img_path))
                    image_idx += 1
        
        # 提取 is_applicable 字段
        if result.parsed_json:
            result.is_applicable = result.parsed_json.get("is_applicable", None)
        
        # Token 使用量
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


async def run_parallel_requests(
    client: genai.Client,
    image_path: str | Path,
    requests: list[CompositionRequest],
    output_dir: Path,
    run_index: int
) -> ParallelRunResult:
    """
    并发执行多个构图请求
    """
    start_ts = time.perf_counter()
    timestamp = datetime.now().isoformat()
    
    # 创建所有异步任务
    tasks = []
    for req in requests:
        task = asyncio.create_task(
            call_gemini_async(
                client=client,
                image_path=image_path,
                system_instruction=req.system_instruction,
                user_prompt=req.user_prompt,
                technique_id=req.technique_id,
                model_config=req.model_config,
                output_dir=output_dir,
                run_index=run_index
            )
        )
        tasks.append(task)
    
    # 并发执行所有请求
    results = await asyncio.gather(*tasks, return_exceptions=True)
    
    end_ts = time.perf_counter()
    total_time_ms = (end_ts - start_ts) * 1000
    
    # 处理结果
    single_results: list[SingleResult] = []
    for r in results:
        if isinstance(r, Exception):
            # 任务异常（不应发生，因为异常已在 call_gemini_async 中捕获）
            single_results.append(SingleResult(
                technique_id="unknown",
                request_id="unknown",
                start_time=timestamp,
                end_time=timestamp,
                response_time_ms=0,
                success=False,
                error_message=str(r)
            ))
        else:
            single_results.append(r)
    
    successful = sum(1 for r in single_results if r.success)
    applicable = sum(1 for r in single_results if r.is_applicable is True)
    
    return ParallelRunResult(
        run_index=run_index,
        timestamp=timestamp,
        total_techniques=len(requests),
        successful_techniques=successful,
        applicable_techniques=applicable,
        total_time_ms=total_time_ms,
        results=single_results
    )


def run_parallel_assessment(
    input_image_path: str | Path,
    repeats: int = 1,
    delay_between_runs: float = 2.0,
    prompt_id: str | None = None
) -> list[ParallelRunResult]:
    """
    运行并行测评
    
    Args:
        input_image_path: 输入图像路径
        repeats: 重复运行次数
        delay_between_runs: 每次运行之间的间隔（秒）
        prompt_id: 指定提示词版本ID（默认第一个）
        
    Returns:
        ParallelRunResult 列表
    """
    client = init_client()
    requests = generate_composition_requests(prompt_id=prompt_id)
    
    # 创建输出目录
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    output_dir = RESULTS_DIR / f"parallel_{timestamp}"
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # 验证图像路径存在
    input_image_path = Path(input_image_path)
    if not input_image_path.exists():
        raise FileNotFoundError(f"输入图像不存在: {input_image_path}")
    
    print(f"\n{'='*60}")
    print(f"🚀 并行测评 - 5 种构图方案并发请求")
    print(f"{'='*60}")
    print(f"   构图技术数: {len(requests)}")
    print(f"   重复次数: {repeats}")
    print(f"   输出目录: {output_dir}")
    print(f"   请求列表:")
    for req in requests:
        print(f"      • {req.technique_id}")
    
    all_results: list[ParallelRunResult] = []
    
    for run_idx in range(repeats):
        print(f"\n[运行 {run_idx+1}/{repeats}]")
        
        # 运行并行请求（传递图像路径，每个任务独立加载）
        result = asyncio.run(run_parallel_requests(
            client=client,
            image_path=input_image_path,
            requests=requests,
            output_dir=output_dir,
            run_index=run_idx
        ))
        
        all_results.append(result)
        
        print(f"   ⏱️ 总耗时: {result.total_time_ms:.0f}ms")
        print(f"   ✓ 成功: {result.successful_techniques}/{result.total_techniques}")
        print(f"   📸 适用: {result.applicable_techniques}/{result.total_techniques}")
        
        # 打印各技术详情
        for r in result.results:
            status = "✓" if r.success else "✗"
            applicable = "适用" if r.is_applicable else ("不适用" if r.is_applicable is False else "未知")
            print(f"      {status} {r.technique_id}: {r.response_time_ms:.0f}ms [{applicable}]")
        
        if run_idx < repeats - 1:
            time.sleep(delay_between_runs)
    
    # 保存汇总结果
    summary = {
        "timestamp": timestamp,
        "model_name": MODEL_NAME,
        "input_image": str(input_image_path),
        "total_runs": repeats,
        "techniques": [req.technique_id for req in requests],
        "runs": [r.to_dict() for r in all_results]
    }
    
    summary_path = output_dir / "summary.json"
    with open(summary_path, "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    
    print(f"\n✅ 测评完成，结果已保存到: {output_dir}")
    
    # 打印统计摘要
    if all_results:
        avg_total_time = sum(r.total_time_ms for r in all_results) / len(all_results)
        print(f"\n{'='*60}")
        print(f"📊 统计摘要")
        print(f"{'='*60}")
        print(f"   平均并行耗时: {avg_total_time:.0f}ms")
        
        # 按技术统计
        tech_times: dict[str, list[float]] = {}
        for run in all_results:
            for r in run.results:
                if r.success:
                    if r.technique_id not in tech_times:
                        tech_times[r.technique_id] = []
                    tech_times[r.technique_id].append(r.response_time_ms)
        
        print(f"\n   按技术平均响应时间:")
        for tech, times in sorted(tech_times.items(), key=lambda x: sum(x[1])/len(x[1])):
            avg = sum(times) / len(times)
            print(f"      • {tech}: {avg:.0f}ms")
    
    return all_results


if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description="并行测评提示词（5种构图并发）")
    parser.add_argument("--image", "-i", required=True, help="输入图像路径")
    parser.add_argument("--repeats", "-r", type=int, default=1, help="重复运行次数")
    parser.add_argument("--delay", "-d", type=float, default=2.0, help="运行间隔（秒）")
    
    args = parser.parse_args()
    
    results = run_parallel_assessment(
        input_image_path=args.image,
        repeats=args.repeats,
        delay_between_runs=args.delay
    )
