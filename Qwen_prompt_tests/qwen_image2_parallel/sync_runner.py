"""
Qwen-Image 2.0 并行同步测试运行器

使用 asyncio 并发执行 5 种构图方案的图片生成。
只生成图片，不输出文本。

速率限制：Qwen API 限制每秒最多 2 个并发请求，
通过 AsyncRateLimiter（令牌桶）在发送前进行限速。
"""

import asyncio
import json
import time
import os
import base64
import mimetypes
import urllib.request
from pathlib import Path
from datetime import datetime
from dataclasses import dataclass, field, asdict

import dashscope
from dashscope import MultiModalConversation

from prompts_config import (
    CompositionRequest,
    generate_composition_requests, load_prompts_from_yaml, get_prompt_set_by_id
)


# ==================== 配置 ==================== #
# 以下为中国（北京）地域url，若使用新加坡地域的模型，需将url替换为：https://dashscope-intl.aliyuncs.com/api/v1
DASHSCOPE_BASE_URL = os.environ.get("DASHSCOPE_BASE_URL", "https://dashscope.aliyuncs.com/api/v1")
dashscope.base_http_api_url = DASHSCOPE_BASE_URL

DASHSCOPE_API_KEY = os.environ.get("DASHSCOPE_API_KEY")
MODEL_NAME = "qwen-image-2.0-2026-03-03"

BASE_DIR = Path(__file__).parent
RESULTS_DIR = BASE_DIR / "results"


@dataclass
class SingleResult:
    technique_id: str
    request_id: str
    prompt_set_id: str
    start_time: str
    end_time: str
    response_time_ms: float
    success: bool
    error_message: str | None = None
    image_urls: list[str] = field(default_factory=list)
    saved_images: list[str] = field(default_factory=list)
    response_meta: dict | None = None

    def to_dict(self) -> dict:
        return asdict(self)


@dataclass
class ParallelRunResult:
    run_index: int
    timestamp: str
    total_techniques: int
    successful_techniques: int
    total_time_ms: float
    results: list[SingleResult] = field(default_factory=list)

    def to_dict(self) -> dict:
        d = asdict(self)
        d["results"] = [r.to_dict() for r in self.results]
        return d


# ==================== 工具函数 ==================== #

def _encode_file(file_path: str | Path) -> str:
    mime_type, _ = mimetypes.guess_type(str(file_path))
    if not mime_type or not mime_type.startswith("image/"):
        raise ValueError("不支持或无法识别的图像格式")

    with open(file_path, "rb") as image_file:
        encoded_string = base64.b64encode(image_file.read()).decode("utf-8")
    return f"data:{mime_type};base64,{encoded_string}"


def _response_to_meta(response) -> dict:
    meta = {
        "status_code": getattr(response, "status_code", None),
        "code": getattr(response, "code", None),
        "message": getattr(response, "message", None),
    }
    return meta


def _extract_image_urls(response) -> list[str]:
    urls: list[str] = []
    try:
        contents = response.output.choices[0].message.content
        for item in contents:
            if isinstance(item, dict) and "image" in item:
                urls.append(item["image"])
    except Exception:
        pass
    return urls


def _guess_extension(content_type: str | None, url: str) -> str:
    if content_type:
        ct = content_type.split(";")[0].strip()
        ext = mimetypes.guess_extension(ct)
        if ext:
            return ext
    # 回退：从 URL 取扩展名
    try:
        from urllib.parse import urlparse
        path = urlparse(url).path
        ext = Path(path).suffix
        if ext:
            return ext
    except Exception:
        pass
    return ".png"


def _download_image(url: str, output_path: Path) -> Path:
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        data = resp.read()
        ext = _guess_extension(resp.headers.get("Content-Type"), url)
    final_path = output_path.with_suffix(ext)
    with open(final_path, "wb") as f:
        f.write(data)
    return final_path


# ==================== 速率限制器 ==================== #

class AsyncRateLimiter:
    """
    基于令牌桶的异步速率限制器。

    保证在任意 1 秒时间窗口内，最多发出 `max_rate` 个请求。
    调用 `acquire()` 会在必要时自动等待，直到令牌可用。
    """

    def __init__(self, max_rate: float, period: float = 1.0):
        """
        Args:
            max_rate: 每个 period 内允许的最大请求数。
            period:   时间窗口（秒），默认 1.0 秒。
        """
        self._max_rate = max_rate
        self._period = period
        # 两次请求之间的最小间隔
        self._min_interval = period / max_rate
        self._lock = asyncio.Lock()
        self._last_check = 0.0  # 上次发放令牌的时间戳

    async def acquire(self) -> None:
        """等待直到可以安全发送下一个请求。"""
        async with self._lock:
            now = asyncio.get_event_loop().time()
            elapsed = now - self._last_check
            wait_time = self._min_interval - elapsed
            if wait_time > 0:
                await asyncio.sleep(wait_time)
            self._last_check = asyncio.get_event_loop().time()


# ==================== 核心逻辑 ==================== #

async def call_qwen_async(
    image_b64: str,
    user_prompt: str,
    technique_id: str,
    prompt_set_id: str,
    output_dir: Path,
    run_index: int,
    images_per_prompt: int = 1,
    negative_prompt: str = " ",
    prompt_extend: bool = False,
    watermark: bool = False,
    semaphore: asyncio.Semaphore | None = None,
    rate_limiter: AsyncRateLimiter | None = None,
    download_images: bool = False,
) -> SingleResult:
    start_time = datetime.now()
    start_ts = time.perf_counter()

    result = SingleResult(
        technique_id=technique_id,
        request_id=f"{technique_id}_{prompt_set_id}",
        prompt_set_id=prompt_set_id,
        start_time=start_time.isoformat(),
        end_time="",
        response_time_ms=0,
        success=False,
    )

    # 速率限制：先抢令牌，再发请求（保证每秒不超过 max_rate 个）
    if rate_limiter:
        await rate_limiter.acquire()

    if semaphore:
        await semaphore.acquire()

    try:
        messages = [
            {
                "role": "user",
                "content": [
                    {"image": image_b64},
                    {"text": user_prompt},
                ],
            }
        ]

        response = await asyncio.to_thread(
            MultiModalConversation.call,
            api_key=DASHSCOPE_API_KEY,
            model=MODEL_NAME,
            messages=messages,
            stream=False,
            n=images_per_prompt,
            watermark=watermark,
            negative_prompt=negative_prompt,
            prompt_extend=prompt_extend,
        )

        end_ts = time.perf_counter()
        end_time = datetime.now()

        result.end_time = end_time.isoformat()
        result.response_time_ms = (end_ts - start_ts) * 1000
        result.response_meta = _response_to_meta(response)

        if response.status_code == 200:
            result.success = True
            result.image_urls = _extract_image_urls(response)

            # 保存图片（可选）
            if download_images and result.image_urls:
                case_dir = output_dir / technique_id
                case_dir.mkdir(parents=True, exist_ok=True)
                for idx, url in enumerate(result.image_urls):
                    img_path = case_dir / f"run{run_index}_image_{idx}"
                    saved = await asyncio.to_thread(_download_image, url, img_path)
                    result.saved_images.append(str(saved))
        else:
            result.error_message = f"HTTP {response.status_code}: {response.message}"

    except Exception as e:
        end_ts = time.perf_counter()
        end_time = datetime.now()
        result.end_time = end_time.isoformat()
        result.response_time_ms = (end_ts - start_ts) * 1000
        result.error_message = str(e)

    finally:
        if semaphore:
            semaphore.release()

    return result


async def run_parallel_requests(
    image_b64: str,
    requests: list[CompositionRequest],
    output_dir: Path,
    run_index: int,
    images_per_prompt: int = 1,
    negative_prompt: str = " ",
    prompt_extend: bool = True,
    watermark: bool = False,
    max_concurrency: int | None = None,
    max_rate: float = 2.0,
    download_images: bool = False,
) -> ParallelRunResult:
    start_ts = time.perf_counter()
    timestamp = datetime.now().isoformat()

    semaphore = None
    if max_concurrency and max_concurrency > 0:
        semaphore = asyncio.Semaphore(max_concurrency)

    # 速率限制器：每秒最多 max_rate 个请求（Qwen 默认限制 2/s）
    rate_limiter = AsyncRateLimiter(max_rate=max_rate) if max_rate > 0 else None

    tasks = []
    for req in requests:
        tasks.append(
            asyncio.create_task(
                call_qwen_async(
                    image_b64=image_b64,
                    user_prompt=req.user_prompt,
                    technique_id=req.technique_id,
                    prompt_set_id=req.prompt_set_id,
                    output_dir=output_dir,
                    run_index=run_index,
                    images_per_prompt=images_per_prompt,
                    negative_prompt=negative_prompt,
                    prompt_extend=prompt_extend,
                    watermark=watermark,
                    semaphore=semaphore,
                    rate_limiter=rate_limiter,
                    download_images=download_images,
                )
            )
        )

    results = await asyncio.gather(*tasks, return_exceptions=True)

    end_ts = time.perf_counter()
    total_time_ms = (end_ts - start_ts) * 1000

    single_results: list[SingleResult] = []
    for r in results:
        if isinstance(r, Exception):
            single_results.append(
                SingleResult(
                    technique_id="unknown",
                    request_id="unknown",
                    prompt_set_id="unknown",
                    start_time=timestamp,
                    end_time=timestamp,
                    response_time_ms=0,
                    success=False,
                    error_message=str(r),
                )
            )
        else:
            single_results.append(r)

    successful = sum(1 for r in single_results if r.success)

    return ParallelRunResult(
        run_index=run_index,
        timestamp=timestamp,
        total_techniques=len(requests),
        successful_techniques=successful,
        total_time_ms=total_time_ms,
        results=single_results,
    )


def run_parallel_assessment(
    input_image_path: str | Path,
    repeats: int = 1,
    delay_between_runs: float = 2.0,
    prompt_id: str | None = None,
    images_per_prompt: int = 1,
    negative_prompt: str = " ",
    prompt_extend: bool = True,
    watermark: bool = False,
    max_concurrency: int | None = None,
    max_rate: float = 2.0,
    download_images: bool = False,
) -> list[ParallelRunResult]:
    if not DASHSCOPE_API_KEY:
        raise ValueError(
            "请设置 DASHSCOPE_API_KEY 环境变量\n"
            "获取API Key: https://help.aliyun.com/zh/model-studio/get-api-key"
        )

    input_image_path = Path(input_image_path)
    if not input_image_path.exists():
        raise FileNotFoundError(f"输入图像不存在: {input_image_path}")

    image_b64 = _encode_file(input_image_path)

    if prompt_id:
        prompt_sets_to_run = [get_prompt_set_by_id(prompt_id)]
    else:
        prompt_sets_to_run = load_prompts_from_yaml()
        if not prompt_sets_to_run:
            raise ValueError("config.yaml 中没有找到任何提示词配置")

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    root_output_dir = RESULTS_DIR / f"parallel_{timestamp}"
    root_output_dir.mkdir(parents=True, exist_ok=True)

    print(f"\n{'='*60}")
    print(f"🚀 Qwen 并行测评 - {len(prompt_sets_to_run)} 个提示词版本")
    print(f"{'='*60}")
    print(f"   提示词版本: {[ps.id for ps in prompt_sets_to_run]}")
    print(f"   重复次数: {repeats}")
    print(f"   每提示词输出图片数: {images_per_prompt}")
    print(f"   速率限制: {max_rate} 请求/秒")
    print(f"   输出目录: {root_output_dir}")

    all_results: list[ParallelRunResult] = []
    all_summaries: dict[str, list[dict]] = {}

    for ps in prompt_sets_to_run:
        requests = generate_composition_requests(prompt_set=ps)
        output_dir = root_output_dir / ps.id
        output_dir.mkdir(parents=True, exist_ok=True)

        print(f"\n{'─'*60}")
        print(f"📝 提示词版本: {ps.id}  ({ps.description})")
        print(f"   构图技术: {[req.technique_id for req in requests]}")

        prompt_results: list[ParallelRunResult] = []

        for run_idx in range(repeats):
            print(f"\n  [运行 {run_idx+1}/{repeats}]")

            result = asyncio.run(
                run_parallel_requests(
                    image_b64=image_b64,
                    requests=requests,
                    output_dir=output_dir,
                    run_index=run_idx,
                    images_per_prompt=images_per_prompt,
                    negative_prompt=negative_prompt,
                    prompt_extend=prompt_extend,
                    watermark=watermark,
                    max_concurrency=max_concurrency,
                    max_rate=max_rate,
                    download_images=download_images,
                )
            )

            prompt_results.append(result)
            all_results.append(result)

            print(f"   ⏱️ 总耗时: {result.total_time_ms:.0f}ms")
            print(f"   ✓ 成功: {result.successful_techniques}/{result.total_techniques}")

            for r in result.results:
                if r.success:
                    print(f"      ✓ {r.technique_id}: {r.response_time_ms:.0f}ms [{len(r.image_urls)} 张]")
                else:
                    print(f"      ✗ {r.technique_id}: {r.response_time_ms:.0f}ms [错误: {r.error_message}]")

            if run_idx < repeats - 1:
                time.sleep(delay_between_runs)

        all_summaries[ps.id] = [r.to_dict() for r in prompt_results]

    summary = {
        "timestamp": timestamp,
        "model_name": MODEL_NAME,
        "input_image": str(input_image_path),
        "total_prompt_versions": len(prompt_sets_to_run),
        "repeats_per_version": repeats,
        "images_per_prompt": images_per_prompt,
        "prompt_extend": prompt_extend,
        "negative_prompt": negative_prompt,
        "watermark": watermark,
        "max_concurrency": max_concurrency,
        "max_rate": max_rate,
        "download_images": download_images,
        "prompt_versions": {ps.id: ps.description for ps in prompt_sets_to_run},
        "runs_by_prompt": all_summaries,
    }

    summary_path = root_output_dir / "summary.json"
    with open(summary_path, "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)

    print(f"\n✅ 测评完成，结果已保存到: {root_output_dir}")

    return all_results


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Qwen-Image 2.0 并行测评提示词")
    parser.add_argument("--image", "-i", required=True, help="输入图像路径")
    parser.add_argument("--repeats", "-r", type=int, default=1, help="重复运行次数")
    parser.add_argument("--delay", "-d", type=float, default=2.0, help="运行间隔（秒）")
    parser.add_argument("--prompt", "-p", help="指定提示词版本ID（默认全部）")
    parser.add_argument("--n", type=int, default=1, help="每个提示词生成图片数量（1-6）")
    parser.add_argument("--negative", default=" ", help="negative_prompt")
    parser.add_argument("--no-extend", action="store_true", help="关闭 prompt_extend")
    parser.add_argument("--watermark", action="store_true", help="输出水印")
    parser.add_argument("--max-concurrency", type=int, default=None, help="最大并发数")
    parser.add_argument("--max-rate", type=float, default=2.0, help="每秒最大请求数（Qwen 限制 2/s，默认 2.0）")
    parser.add_argument("--download", action="store_true", help="下载生成图片到本地")

    args = parser.parse_args()

    run_parallel_assessment(
        input_image_path=args.image,
        repeats=args.repeats,
        delay_between_runs=args.delay,
        prompt_id=args.prompt,
        images_per_prompt=args.n,
        negative_prompt=args.negative,
        prompt_extend=not args.no_extend,
        watermark=args.watermark,
        max_concurrency=args.max_concurrency,
        max_rate=args.max_rate,
        download_images=args.download,
    )
