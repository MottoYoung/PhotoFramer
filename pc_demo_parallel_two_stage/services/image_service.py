"""
Stage 2 并行图像生成服务
使用 Qwen-image-2.0 + AsyncRateLimiter（令牌桶，最多 2 req/s）
"""
import asyncio
import base64
import time
from dataclasses import dataclass
from datetime import datetime
from typing import List, Optional

import dashscope
from dashscope import MultiModalConversation

from config import (
    DASHSCOPE_API_KEY,
    IMAGE_MODEL_NAME,
    IMAGE_MAX_RATE,
    IMAGE_DASHSCOPE_BASE_URL,
)
from schemas.image import ImageRequest, ImageResult


# 配置 dashscope base URL（必须在模块加载时设置）
dashscope.base_http_api_url = IMAGE_DASHSCOPE_BASE_URL


# ==================== 速率限制器 ==================== #

class AsyncRateLimiter:
    """
    基于令牌桶的异步速率限制器（移植自参考代码）。
    保证在任意 period 秒内，最多发出 max_rate 个请求。
    """

    def __init__(self, max_rate: float, period: float = 1.0):
        self._min_interval = period / max_rate
        self._lock = asyncio.Lock()
        self._last_check = 0.0

    async def acquire(self) -> None:
        """等待直到可以安全发送下一个请求。"""
        async with self._lock:
            now = asyncio.get_event_loop().time()
            wait_time = self._min_interval - (now - self._last_check)
            if wait_time > 0:
                await asyncio.sleep(wait_time)
            self._last_check = asyncio.get_event_loop().time()


# ==================== 工具函数 ==================== #

def _extract_image_urls(response) -> list[str]:
    """从 API 响应中提取图片 URL 列表"""
    urls: list[str] = []
    try:
        contents = response.output.choices[0].message.content
        for item in contents:
            if isinstance(item, dict) and "image" in item:
                urls.append(item["image"])
    except Exception:
        pass
    return urls


def _url_to_base64(url: str) -> Optional[str]:
    """将图片 URL 下载并转为 base64 data URL"""
    import urllib.request
    import mimetypes
    from urllib.parse import urlparse
    from pathlib import Path

    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=60) as resp:
            data = resp.read()
            content_type = resp.headers.get("Content-Type", "image/png")
            mime = content_type.split(";")[0].strip()
        b64 = base64.b64encode(data).decode("utf-8")
        return f"data:{mime};base64,{b64}"
    except Exception as e:
        print(f"  ⚠️ 图片下载失败: {e}")
        return None


# ==================== 核心服务 ==================== #


@dataclass
class Stage2Timing:
    """Stage 2 核心时延（开始到完成）"""
    technique_id: str
    start_ts: float
    finish_ts: Optional[float] = None

    @property
    def s2_total_ms(self) -> Optional[float]:
        if self.finish_ts is None:
            return None
        return (self.finish_ts - self.start_ts) * 1000


class ImageGenerationService:
    """并行图像生成服务（Stage 2）"""

    def __init__(self):
        if not DASHSCOPE_API_KEY:
            raise ValueError(
                "请设置 DASHSCOPE_API_KEY 环境变量\n"
                "获取方式：https://dashscope.console.aliyun.com/apiKey"
            )
        self._rate_limiter = AsyncRateLimiter(max_rate=IMAGE_MAX_RATE)
        print(f"✅ ImageGenerationService 初始化成功（限速 {IMAGE_MAX_RATE} req/s）")

    def _log_stage2_finish(
        self,
        technique_id: str,
        timing: Stage2Timing,
    ) -> None:
        if timing.s2_total_ms is not None:
            print(
                f"  ⏱️  [{technique_id}] S2 完成 | s2_total={timing.s2_total_ms:.0f}ms",
                flush=True,
            )

    def _call_qwen_image_sync(
        self,
        technique_id: str,
        qwen_image_prompt: str,
        original_image_b64: Optional[str],
    ) -> tuple[bool, list[str], str]:
        """
        同步调用 Qwen-image-2.0（在线程内运行）。

        消息格式：
        - 有原图：[{"image": original_b64}, {"text": prompt}]  → 图生图引导
        - 无原图：[{"text": prompt}]                           → 纯文生图

        Returns: (success, image_urls, error_message)
        """
        content = []
        if original_image_b64:
            content.append({"image": original_image_b64})
        content.append({"text": qwen_image_prompt})

        messages = [{"role": "user", "content": content}]

        response = MultiModalConversation.call(
            api_key=DASHSCOPE_API_KEY,
            model=IMAGE_MODEL_NAME,
            messages=messages,
            stream=False,
            n=1,
            watermark=False,
            negative_prompt=" ",
            prompt_extend=False,
        )

        if response.status_code == 200:
            urls = _extract_image_urls(response)
            return True, urls, ""
        else:
            return False, [], f"HTTP {response.status_code}: {getattr(response, 'message', '')}"

    async def _generate_single_internal(
        self,
        request: ImageRequest,
    ) -> tuple[ImageResult, Stage2Timing]:
        """
        异步生成单张图片。
        先通过速率限制器获取令牌，再 asyncio.to_thread 执行同步 SDK 调用。
        图片 URL 下载后转为 base64 内嵌返回。
        """
        timing = Stage2Timing(
            technique_id=request.technique_id,
            start_ts=time.perf_counter(),
        )

        result = ImageResult(
            technique_id=request.technique_id,
            success=False,
            response_time_ms=0.0,
        )

        # 速率限制
        await self._rate_limiter.acquire()

        try:
            success, urls, error_msg = await asyncio.to_thread(
                self._call_qwen_image_sync,
                request.technique_id,
                request.qwen_image_prompt,
                request.original_image_b64,
            )

            if success and urls:
                result.success = True
                # 将 URL 转为 base64（异步线程下载）
                b64 = await asyncio.to_thread(_url_to_base64, urls[0])
                result.image_base64 = b64
                timing.finish_ts = time.perf_counter()
                result.response_time_ms = timing.s2_total_ms or 0.0
                print(
                    f"  ✓ [{request.technique_id}] 生图成功 "
                    f"{result.response_time_ms:.0f}ms, b64={'有' if b64 else '无（下载失败）'}",
                    flush=True,
                )
                self._log_stage2_finish(request.technique_id, timing)
            else:
                timing.finish_ts = time.perf_counter()
                result.response_time_ms = timing.s2_total_ms or 0.0
                result.error_message = error_msg or "未返回图片"
                print(
                    f"  ✗ [{request.technique_id}] 生图失败: {result.error_message}",
                    flush=True,
                )
                self._log_stage2_finish(request.technique_id, timing)

        except Exception as e:
            timing.finish_ts = time.perf_counter()
            result.response_time_ms = timing.s2_total_ms or 0.0
            result.error_message = str(e)
            print(f"  ❌ [{request.technique_id}] 异常: {e}", flush=True)
            self._log_stage2_finish(request.technique_id, timing)

        return result, timing

    async def generate_single(self, request: ImageRequest) -> ImageResult:
        result, _ = await self._generate_single_internal(request)
        return result

    async def generate_single_with_timing(
        self,
        request: ImageRequest,
    ) -> tuple[ImageResult, Stage2Timing]:
        return await self._generate_single_internal(request)

    async def generate_parallel(
        self,
        requests: List[ImageRequest],
    ) -> tuple[List[ImageResult], float]:
        """
        并行生成多张图片，统一受速率限制器约束。

        Returns: (results, total_time_ms)
        """
        start_ts = time.perf_counter()

        print(f"\n{'='*60}")
        print(f"🎨 开始并行生图，共 {len(requests)} 个请求（限速 {IMAGE_MAX_RATE} req/s）")
        print(f"{'='*60}")

        tasks = [
            asyncio.create_task(self._generate_single_internal(req))
            for req in requests
        ]
        results = await asyncio.gather(*tasks, return_exceptions=True)

        end_ts = time.perf_counter()
        total_time_ms = (end_ts - start_ts) * 1000

        image_results: List[ImageResult] = []
        for r in results:
            if isinstance(r, Exception):
                print(f"⚠️ 任务异常: {r}")
                image_results.append(ImageResult(
                    technique_id="unknown",
                    success=False,
                    error_message=str(r),
                ))
            else:
                image_result, _ = r
                image_results.append(image_result)

        successful = sum(1 for r in image_results if r.success)
        print(f"\n📊 生图统计: 成功 {successful}/{len(requests)}")
        print(f"⏱️  并行总耗时: {total_time_ms:.0f}ms")

        return image_results, total_time_ms


# ==================== 全局单例 ==================== #

_image_service: Optional[ImageGenerationService] = None


def get_image_service() -> ImageGenerationService:
    """获取 ImageGenerationService 单例"""
    global _image_service
    if _image_service is None:
        _image_service = ImageGenerationService()
    return _image_service
