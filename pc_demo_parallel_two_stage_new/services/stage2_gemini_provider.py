"""
Gemini Stage 2 provider。
使用 gemini-2.5-flash-image 进行参考图生成。
"""
import asyncio
import base64
import ssl
from typing import List, Optional

from google import genai
from google.genai import types

from config import (
    GEMINI_API_KEY,
    GEMINI_STAGE2_FORCE_DISABLE_MAX_OUTPUT_TOKENS,
    GEMINI_STAGE2_IMAGE_SIZE,
    GEMINI_STAGE2_INCLUDE_SOURCE_IMAGE,
    GEMINI_STAGE2_MAX_CONCURRENCY,
    GEMINI_STAGE2_MAX_OUTPUT_TOKENS,
    GEMINI_STAGE2_MAX_RETRIES,
    GEMINI_STAGE2_MODEL,
    GEMINI_STAGE2_RETRY_BASE_DELAY_MS,
    GEMINI_STAGE2_TEMPERATURE,
    GEMINI_STAGE2_TOP_K,
    GEMINI_STAGE2_TOP_P,
)
from schemas.image import ImageRequest, ImageResult
from services.common import Stage2Timing, log_stage2_finish, now_perf
from services.gemini_profiles import get_gemini_model_profile, normalize_gemini_image_size
from services.prompt_adapters import adapt_prompt_for_stage2


class GeminiStage2Provider:
    provider_name = "gemini"
    model_name = GEMINI_STAGE2_MODEL

    def __init__(self):
        if not GEMINI_API_KEY:
            raise ValueError("请设置 GEMINI_API_KEY 环境变量")
        self.client = self._create_client()
        self.profile = get_gemini_model_profile(self.model_name)
        self._semaphore = asyncio.Semaphore(max(1, GEMINI_STAGE2_MAX_CONCURRENCY))
        print("✅ GeminiStage2Provider 初始化成功")

    def _create_client(self):
        return genai.Client(api_key=GEMINI_API_KEY)

    def _build_contents(self, request: ImageRequest):
        contents: List[object] = []
        if GEMINI_STAGE2_INCLUDE_SOURCE_IMAGE and request.original_image_b64 and self.profile.supports_image_input:
            prefix, b64 = request.original_image_b64.split("base64,", 1)
            mime = prefix.removeprefix("data:").rstrip(";")
            contents.append(
                types.Part.from_bytes(
                    data=base64.b64decode(b64),
                    mime_type=mime,
                )
            )
        contents.append(
            adapt_prompt_for_stage2(
                provider_name=self.provider_name,
                model_name=self.model_name,
                prompt=request.image_prompt,
                has_source_image=bool(request.original_image_b64),
            )
        )
        return contents

    def _build_config(self) -> types.GenerateContentConfig:
        normalized_image_size = normalize_gemini_image_size(
            GEMINI_STAGE2_IMAGE_SIZE,
            self.profile,
        )
        config_kwargs = {
            "temperature": GEMINI_STAGE2_TEMPERATURE,
            "top_p": GEMINI_STAGE2_TOP_P,
            "response_modalities": list(self.profile.default_response_modalities),
        }
        if self.profile.supports_top_k:
            config_kwargs["top_k"] = GEMINI_STAGE2_TOP_K
        if (
            self.profile.supports_max_output_tokens
            and not GEMINI_STAGE2_FORCE_DISABLE_MAX_OUTPUT_TOKENS
        ):
            config_kwargs["max_output_tokens"] = GEMINI_STAGE2_MAX_OUTPUT_TOKENS
        if self.profile.supports_image_config and normalized_image_size:
            # 只对支持 image_config 的模型下发，避免 text-only / 2.5 flash 类模型报参数错误。
            config_kwargs["image_config"] = types.ImageConfig(
                image_size=normalized_image_size
            )
        return types.GenerateContentConfig(**config_kwargs)

    def _extract_image_base64(self, response) -> Optional[str]:
        if not getattr(response, "candidates", None):
            return None
        for part in response.candidates[0].content.parts:
            inline_data = getattr(part, "inline_data", None)
            if inline_data and getattr(inline_data, "data", None):
                mime = inline_data.mime_type or "image/png"
                b64 = base64.b64encode(inline_data.data).decode("utf-8")
                return f"data:{mime};base64,{b64}"
        return None

    def _is_retryable_error(self, error: Exception) -> bool:
        if isinstance(error, ssl.SSLError):
            return True
        message = str(error).lower()
        retryable_markers = (
            "unexpected_eof_while_reading",
            "eof occurred in violation of protocol",
            "server disconnected",
            "connection reset by peer",
            "remoteprotocolerror",
            "read timeout",
            "timed out",
            "temporary failure",
            "temporarily unavailable",
            "connection aborted",
        )
        return any(marker in message for marker in retryable_markers)

    async def _generate_with_retries(self, request: ImageRequest):
        last_error: Exception | None = None
        for attempt in range(GEMINI_STAGE2_MAX_RETRIES + 1):
            try:
                return await asyncio.to_thread(
                    self.client.models.generate_content,
                    model=self.model_name,
                    contents=self._build_contents(request),
                    config=self._build_config(),
                )
            except Exception as error:
                last_error = error
                if attempt >= GEMINI_STAGE2_MAX_RETRIES or not self._is_retryable_error(error):
                    raise
                delay_ms = GEMINI_STAGE2_RETRY_BASE_DELAY_MS * (attempt + 1)
                print(
                    f"  🔁 [gemini:{request.technique_id}] "
                    f"retry {attempt + 1}/{GEMINI_STAGE2_MAX_RETRIES} after {delay_ms}ms "
                    f"error={error}",
                    flush=True,
                )
                # 重建 client，避免复用到已损坏的连接池状态
                self.client = self._create_client()
                await asyncio.sleep(delay_ms / 1000.0)
        if last_error is not None:
            raise last_error

    async def generate_single_with_timing(
        self,
        request: ImageRequest,
    ) -> tuple[ImageResult, Stage2Timing]:
        timing = Stage2Timing(technique_id=request.technique_id, start_ts=now_perf())
        result = ImageResult(technique_id=request.technique_id, success=False)
        try:
            if not self.profile.supports_image_output:
                raise ValueError(
                    f"Gemini 模型 `{self.model_name}` 不支持图像输出，请切换到图像生成模型"
                )
            async with self._semaphore:
                response = await self._generate_with_retries(request)
            result.image_base64 = self._extract_image_base64(response)
            result.success = result.image_base64 is not None
            if not result.success:
                result.error_message = "Gemini 未返回图片"
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
        payloads = await asyncio.gather(
            *[self.generate_single_with_timing(req) for req in requests],
            return_exceptions=True,
        )
        results: List[ImageResult] = []
        for payload in payloads:
            if isinstance(payload, Exception):
                continue
            result, _ = payload
            results.append(result)
        total_ms = (now_perf() - start_ts) * 1000
        print(f"🏁 [gemini-stage2] total={total_ms:.0f}ms requests={len(requests)}", flush=True)
        return results, total_ms
