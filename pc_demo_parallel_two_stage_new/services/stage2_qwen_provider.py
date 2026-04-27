"""
Qwen Stage 2 provider。
"""
import asyncio
import base64
from typing import List, Optional

import dashscope
from dashscope import MultiModalConversation

from config import (
    DASHSCOPE_API_KEY,
    IMAGE_DASHSCOPE_BASE_URL,
    IMAGE_MAX_RATE,
    QWEN_STAGE2_MODEL,
)
from schemas.image import ImageRequest, ImageResult
from services.common import Stage2Timing, log_stage2_finish, now_perf
from services.prompt_adapters import adapt_prompt_for_stage2

dashscope.base_http_api_url = IMAGE_DASHSCOPE_BASE_URL


class AsyncRateLimiter:
    def __init__(self, max_rate: float, period: float = 1.0):
        self._min_interval = period / max_rate
        self._lock = asyncio.Lock()
        self._last_check = 0.0

    async def acquire(self) -> None:
        async with self._lock:
            loop = asyncio.get_event_loop()
            now = loop.time()
            wait_time = self._min_interval - (now - self._last_check)
            if wait_time > 0:
                await asyncio.sleep(wait_time)
            self._last_check = loop.time()


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


def _url_to_base64(url: str) -> Optional[str]:
    import mimetypes
    import urllib.request

    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=60) as resp:
            data = resp.read()
            content_type = resp.headers.get("Content-Type", "image/png")
            mime = content_type.split(";")[0].strip()
            if mime == "application/octet-stream":
                mime = mimetypes.guess_type(url)[0] or "image/png"
        b64 = base64.b64encode(data).decode("utf-8")
        return f"data:{mime};base64,{b64}"
    except Exception as error:
        print(f"  ⚠️ [qwen-stage2] 图片下载失败: {error}")
        return None


class QwenStage2Provider:
    provider_name = "qwen"
    model_name = QWEN_STAGE2_MODEL

    def __init__(self):
        if not DASHSCOPE_API_KEY:
            raise ValueError("请设置 DASHSCOPE_API_KEY 环境变量")
        self._rate_limiter = AsyncRateLimiter(max_rate=IMAGE_MAX_RATE)
        print(f"✅ QwenStage2Provider 初始化成功（限速 {IMAGE_MAX_RATE} req/s）")

    def _call_sync(
        self,
        image_prompt: str,
        original_image_b64: Optional[str],
    ) -> tuple[bool, list[str], str]:
        adapted_prompt = adapt_prompt_for_stage2(
            provider_name=self.provider_name,
            model_name=self.model_name,
            prompt=image_prompt,
            has_source_image=bool(original_image_b64),
        )
        content = []
        if original_image_b64:
            content.append({"image": original_image_b64})
        content.append({"text": adapted_prompt})
        response = MultiModalConversation.call(
            api_key=DASHSCOPE_API_KEY,
            model=self.model_name,
            messages=[{"role": "user", "content": content}],
            stream=False,
            n=1,
            watermark=False,
            negative_prompt=" ",
            prompt_extend=False,
        )
        if response.status_code == 200:
            return True, _extract_image_urls(response), ""
        return False, [], f"HTTP {response.status_code}: {getattr(response, 'message', '')}"

    async def generate_single_with_timing(
        self,
        request: ImageRequest,
    ) -> tuple[ImageResult, Stage2Timing]:
        timing = Stage2Timing(technique_id=request.technique_id, start_ts=now_perf())
        result = ImageResult(technique_id=request.technique_id, success=False)
        await self._rate_limiter.acquire()
        try:
            success, urls, error_message = await asyncio.to_thread(
                self._call_sync,
                request.image_prompt,
                request.original_image_b64,
            )
            if success and urls:
                result.success = True
                result.image_base64 = await asyncio.to_thread(_url_to_base64, urls[0])
            else:
                result.error_message = error_message or "未返回图片"
        except Exception as error:
            result.error_message = str(error)

        timing.finish_ts = now_perf()
        result.response_time_ms = timing.total_ms or 0.0
        log_stage2_finish(
            self.provider_name,
            request.technique_id,
            timing,
            success=result.success,
            error_message=result.error_message,
        )
        return result, timing

    async def generate_parallel(
        self,
        requests: List[ImageRequest],
    ) -> tuple[List[ImageResult], float]:
        start_ts = now_perf()
        tasks = [asyncio.create_task(self.generate_single_with_timing(req)) for req in requests]
        payloads = await asyncio.gather(*tasks, return_exceptions=True)
        results: List[ImageResult] = []
        for payload in payloads:
            if isinstance(payload, Exception):
                continue
            result, _ = payload
            results.append(result)
        total_ms = (now_perf() - start_ts) * 1000
        print(f"🏁 [qwen-stage2] total={total_ms:.0f}ms requests={len(requests)}", flush=True)
        return results, total_ms
