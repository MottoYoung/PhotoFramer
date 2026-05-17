"""
Gemini Stage 1 provider。
职责：输出 JSON 分析结果，并支持 image_prompt 前置流式截取。
"""
import asyncio
import base64
from datetime import datetime
from io import BytesIO
from typing import Any, AsyncGenerator, Dict, List, Optional, Tuple

from google.genai import types
from PIL import Image

from config import (
    ENABLE_THINKING,
    GEMINI_STAGE1_FORCE_DISABLE_MAX_OUTPUT_TOKENS,
    GEMINI_STAGE1_FORCE_MINIMAL_THINKING,
    GEMINI_STAGE1_MAX_OUTPUT_TOKENS,
    GEMINI_STAGE1_MODEL,
    GEMINI_STAGE1_RESPONSE_MIME_TYPE,
    GEMINI_STAGE1_TEMPERATURE,
    GEMINI_STAGE1_THINKING_BUDGET,
    GEMINI_STAGE1_THINKING_LEVEL,
    GEMINI_STAGE1_TOP_K,
    GEMINI_STAGE1_TOP_P,
    MODEL_THINKING_BUDGET,
    SYSTEM_INSTRUCTION,
    TECHNIQUE_CONFIGS,
)
from schemas import CompositionResult
from services.common import (
    Stage1Result,
    Stage1Timing,
    build_stage1_result,
    extract_complete_json_string_field,
    extract_json_bool_field,
    image_to_data_url,
    log_stage1_finish,
    now_perf,
    parse_json_from_text,
    prepare_image_bytes,
    to_composition_result,
)
from services.gemini_client_factory import create_gemini_client, describe_gemini_backend
from services.gemini_profiles import get_gemini_model_profile


class GeminiStage1Provider:
    provider_name = "gemini"
    model_name = GEMINI_STAGE1_MODEL
    supports_prompt_streaming_pipeline = True

    def __init__(self):
        self.client = create_gemini_client()
        self.profile = get_gemini_model_profile(self.model_name)
        print(f"✅ GeminiStage1Provider 初始化成功 [{describe_gemini_backend()}]")

    def prepare_image_payload(self, image_bytes: bytes) -> Tuple[bytes, str, str]:
        processed, mime = prepare_image_bytes(image_bytes)
        return processed, mime, image_to_data_url(processed, mime)

    def _image_from_data_url(self, image_data_url: str) -> Image.Image:
        prefix, b64 = image_data_url.split("base64,", 1)
        image_bytes = base64.b64decode(b64)
        image = Image.open(BytesIO(image_bytes))
        if image.mode == "RGBA":
            image = image.convert("RGB")
        return image

    def _build_config(
        self,
        response_mime_type: Optional[str] = GEMINI_STAGE1_RESPONSE_MIME_TYPE,
    ) -> types.GenerateContentConfig:
        config_kwargs = {
            "system_instruction": SYSTEM_INSTRUCTION,
            "temperature": GEMINI_STAGE1_TEMPERATURE,
            "top_p": GEMINI_STAGE1_TOP_P,
            "response_modalities": list(self.profile.default_response_modalities),
        }
        if response_mime_type:
            config_kwargs["response_mime_type"] = response_mime_type
        if self.profile.supports_top_k:
            config_kwargs["top_k"] = GEMINI_STAGE1_TOP_K
        if (
            self.profile.supports_max_output_tokens
            and not GEMINI_STAGE1_FORCE_DISABLE_MAX_OUTPUT_TOKENS
        ):
            config_kwargs["max_output_tokens"] = GEMINI_STAGE1_MAX_OUTPUT_TOKENS
        if self.profile.supports_thinking:
            thinking_config = None
            normalized_model = self.model_name.strip().lower()
            if ENABLE_THINKING:
                thinking_config = types.ThinkingConfig(
                    thinking_budget=MODEL_THINKING_BUDGET
                )
            elif GEMINI_STAGE1_FORCE_MINIMAL_THINKING:
                if normalized_model.startswith("gemini-3"):
                    thinking_config = types.ThinkingConfig(
                        thinking_level=GEMINI_STAGE1_THINKING_LEVEL
                    )
                elif normalized_model.startswith("gemini-2.5-pro"):
                    # Gemini 2.5 Pro 不能完全禁用思考，压到最低预算。
                    thinking_config = types.ThinkingConfig(thinking_budget=128)
                else:
                    thinking_config = types.ThinkingConfig(
                        thinking_budget=GEMINI_STAGE1_THINKING_BUDGET
                    )
            if thinking_config is not None:
                config_kwargs["thinking_config"] = thinking_config
        return types.GenerateContentConfig(**config_kwargs)

    def _generate_content_stream_with_fallback(self, image: Image.Image, technique_id: str):
        try:
            return self.client.models.generate_content_stream(
                model=self.model_name,
                contents=[TECHNIQUE_CONFIGS[technique_id].user_prompt, image],
                config=self._build_config(),
            )
        except Exception as error:
            if not GEMINI_STAGE1_RESPONSE_MIME_TYPE:
                raise
            print(
                f"  ⚠️ [gemini:{technique_id}] json mime stream failed, retry without response_mime_type "
                f"error={type(error).__name__}: {error}",
                flush=True,
            )
            return self.client.models.generate_content_stream(
                model=self.model_name,
                contents=[TECHNIQUE_CONFIGS[technique_id].user_prompt, image],
                config=self._build_config(response_mime_type=None),
            )

    def _stream_call_sync(self, image_data_url: str, technique_id: str) -> str:
        image = self._image_from_data_url(image_data_url)
        start_ts = now_perf()
        print(
            f"  🚀 [gemini:{technique_id}] stream request start "
            f"model={self.model_name} mime={GEMINI_STAGE1_RESPONSE_MIME_TYPE}",
            flush=True,
        )
        try:
            stream = self._generate_content_stream_with_fallback(image, technique_id)
            full_text = ""
            chunk_count = 0
            first_text_logged = False
            for chunk in stream:
                chunk_count += 1
                text = getattr(chunk, "text", None)
                if text:
                    full_text += text
                    if not first_text_logged:
                        first_text_logged = True
                        elapsed_ms = (now_perf() - start_ts) * 1000
                        print(
                            f"  📥 [gemini:{technique_id}] first text chunk "
                            f"{elapsed_ms:.0f}ms chars={len(text)}",
                            flush=True,
                        )
            elapsed_ms = (now_perf() - start_ts) * 1000
            print(
                f"  ✅ [gemini:{technique_id}] stream finished "
                f"{elapsed_ms:.0f}ms chunks={chunk_count} chars={len(full_text)}",
                flush=True,
            )
            return full_text
        except Exception as error:
            elapsed_ms = (now_perf() - start_ts) * 1000
            print(
                f"  ❌ [gemini:{technique_id}] stream request failed "
                f"{elapsed_ms:.0f}ms error={type(error).__name__}: {error}",
                flush=True,
            )
            raise

    def _stream_call_with_prompt_callback(
        self,
        image_data_url: str,
        technique_id: str,
        loop: asyncio.AbstractEventLoop,
        event_queue: asyncio.Queue,
    ) -> tuple[str, Stage1Timing]:
        image = self._image_from_data_url(image_data_url)
        timing = Stage1Timing(technique_id=technique_id, request_start_ts=now_perf())
        print(
            f"  🚀 [gemini:{technique_id}] stream+callback start model={self.model_name}",
            flush=True,
        )
        try:
            stream = self._generate_content_stream_with_fallback(image, technique_id)
            timing.stream_created_ts = now_perf()
            full_text = ""
            prompt_sent = False
            applicability_seen = False
            first_text_logged = False

            for chunk in stream:
                text = getattr(chunk, "text", None)
                if not text:
                    continue
                full_text += text
                if not first_text_logged:
                    first_text_logged = True
                    print(
                        f"  📥 [gemini:{technique_id}] callback first text "
                        f"{timing.finish_ms or 0 if False else (now_perf() - timing.request_start_ts) * 1000:.0f}ms",
                        flush=True,
                    )
                if not applicability_seen:
                    applicability = extract_json_bool_field(full_text, "is_applicable")
                    if applicability is not None:
                        applicability_seen = True
                        if not applicability:
                            prompt_sent = True
                            print(
                                f"  ⏭️  [gemini:{technique_id}] callback is_applicable=false "
                                f"{(now_perf() - timing.request_start_ts) * 1000:.0f}ms",
                                flush=True,
                            )
                            continue
                        print(
                            f"  ✅ [gemini:{technique_id}] callback is_applicable=true "
                            f"{(now_perf() - timing.request_start_ts) * 1000:.0f}ms",
                            flush=True,
                        )

                if applicability_seen and not prompt_sent:
                    prompt = extract_complete_json_string_field(full_text, "image_prompt")
                    if prompt:
                        prompt_sent = True
                        timing.prompt_ready_ts = now_perf()
                        loop.call_soon_threadsafe(
                            event_queue.put_nowait,
                            {
                                "event": "prompt_ready",
                                "technique": technique_id,
                                "technique_name": TECHNIQUE_CONFIGS[technique_id].name,
                                "image_prompt": prompt,
                                "prompt_ready_ms": round(timing.prompt_ms or 0.0),
                            },
                        )
            timing.finish_ts = now_perf()
            if not prompt_sent:
                print(
                    f"  ⚠️ [gemini:{technique_id}] callback stream finished without image_prompt "
                    f"chars={len(full_text)}",
                    flush=True,
                )
            return full_text, timing
        except Exception as error:
            elapsed_ms = (now_perf() - timing.request_start_ts) * 1000
            print(
                f"  ❌ [gemini:{technique_id}] stream+callback failed "
                f"{elapsed_ms:.0f}ms error={type(error).__name__}: {error}",
                flush=True,
            )
            raise

    def stream_pipeline_sync(
        self,
        image_data_url: str,
        technique_id: str,
        loop: asyncio.AbstractEventLoop,
        prompt_ready_event: asyncio.Event,
        prompt_ref: list,
    ) -> tuple[str, Stage1Timing]:
        image = self._image_from_data_url(image_data_url)
        timing = Stage1Timing(technique_id=technique_id, request_start_ts=now_perf())
        print(
            f"  🚀 [gemini:{technique_id}] pipeline stream start "
            f"model={self.model_name} top_p={GEMINI_STAGE1_TOP_P} temp={GEMINI_STAGE1_TEMPERATURE}",
            flush=True,
        )
        try:
            stream = self._generate_content_stream_with_fallback(image, technique_id)
            timing.stream_created_ts = now_perf()
            full_text = ""
            prompt_sent = False
            applicability_seen = False
            chunk_count = 0
            first_text_logged = False

            for chunk in stream:
                chunk_count += 1
                text = getattr(chunk, "text", None)
                if not text:
                    continue
                full_text += text
                if not first_text_logged:
                    first_text_logged = True
                    elapsed_ms = (now_perf() - timing.request_start_ts) * 1000
                    print(
                        f"  📥 [gemini:{technique_id}] pipeline first text "
                        f"{elapsed_ms:.0f}ms chars={len(text)}",
                        flush=True,
                    )
                if not applicability_seen:
                    applicability = extract_json_bool_field(full_text, "is_applicable")
                    if applicability is not None:
                        applicability_seen = True
                        if not applicability:
                            prompt_sent = True
                            loop.call_soon_threadsafe(prompt_ready_event.set)
                            print(
                                f"  ⏭️  [gemini:{technique_id}] is_applicable=false "
                                f"{(now_perf() - timing.request_start_ts) * 1000:.0f}ms",
                                flush=True,
                            )
                            continue
                        print(
                            f"  ✅ [gemini:{technique_id}] is_applicable=true "
                            f"{(now_perf() - timing.request_start_ts) * 1000:.0f}ms",
                            flush=True,
                        )

                if applicability_seen and not prompt_sent:
                    prompt = extract_complete_json_string_field(full_text, "image_prompt")
                    if prompt:
                        prompt_sent = True
                        prompt_ref[0] = prompt
                        timing.prompt_ready_ts = now_perf()
                        loop.call_soon_threadsafe(prompt_ready_event.set)
                        print(
                            f"  ⚡ [gemini:{technique_id}] prompt 就绪 {timing.prompt_ms or 0:.0f}ms "
                            f"prompt_chars={len(prompt)} total_chars={len(full_text)}",
                            flush=True,
                        )

            if not prompt_sent:
                print(
                    f"  ⚠️ [gemini:{technique_id}] pipeline stream ended without image_prompt "
                    f"chunks={chunk_count} chars={len(full_text)}",
                    flush=True,
                )
                loop.call_soon_threadsafe(prompt_ready_event.set)
            timing.finish_ts = now_perf()
            elapsed_ms = (timing.finish_ts - timing.request_start_ts) * 1000
            print(
                f"  ✅ [gemini:{technique_id}] pipeline stream finish "
                f"{elapsed_ms:.0f}ms chunks={chunk_count} chars={len(full_text)}",
                flush=True,
            )
            return full_text, timing
        except Exception as error:
            elapsed_ms = (now_perf() - timing.request_start_ts) * 1000
            loop.call_soon_threadsafe(prompt_ready_event.set)
            print(
                f"  ❌ [gemini:{technique_id}] pipeline stream failed "
                f"{elapsed_ms:.0f}ms error={type(error).__name__}: {error}",
                flush=True,
            )
            raise

    def parse_single_result(
        self,
        technique_id: str,
        full_content: str,
        start_ts: float,
        end_ts: float,
        start_time: datetime,
        end_time: datetime,
        timing: Optional[Stage1Timing] = None,
    ) -> Stage1Result:
        parsed_json = parse_json_from_text(full_content)
        result = build_stage1_result(
            technique_id=technique_id,
            technique_name=TECHNIQUE_CONFIGS[technique_id].name,
            start_time=start_time,
            end_time=end_time,
            start_ts=start_ts,
            end_ts=end_ts,
            parsed_json=parsed_json,
        )
        if timing is not None:
            timing.finish_ts = timing.finish_ts or now_perf()
            log_stage1_finish(self.provider_name, technique_id, timing)
        return result

    async def _call_single_technique(self, image_data_url: str, technique_id: str) -> Stage1Result:
        start_time = datetime.now()
        start_ts = now_perf()
        try:
            full_content = await asyncio.to_thread(
                self._stream_call_sync,
                image_data_url,
                technique_id,
            )
            end_ts = now_perf()
            return self.parse_single_result(
                technique_id,
                full_content,
                start_ts,
                end_ts,
                start_time,
                datetime.now(),
            )
        except Exception as error:
            end_ts = now_perf()
            print(
                f"  ❌ [gemini:{technique_id}] analyze_parallel single failed "
                f"{(end_ts - start_ts) * 1000:.0f}ms error={type(error).__name__}: {error}",
                flush=True,
            )
            return Stage1Result(
                technique_id=technique_id,
                technique_name=TECHNIQUE_CONFIGS[technique_id].name,
                start_time=start_time.isoformat(),
                end_time=datetime.now().isoformat(),
                response_time_ms=(end_ts - start_ts) * 1000,
                success=False,
                error_message=str(error),
            )

    async def analyze_parallel(
        self,
        image_bytes: bytes,
        techniques: Optional[List[str]] = None,
    ) -> Tuple[List[CompositionResult], float, int, int]:
        start_ts = now_perf()
        _, _, image_data_url = self.prepare_image_payload(image_bytes)
        techniques = techniques or list(TECHNIQUE_CONFIGS.keys())
        tasks = [
            asyncio.create_task(self._call_single_technique(image_data_url, technique_id))
            for technique_id in techniques
        ]
        results = await asyncio.gather(*tasks, return_exceptions=True)
        compositions: List[CompositionResult] = []
        applicable_count = 0

        for result in results:
            if isinstance(result, Exception):
                print(f"⚠️ [gemini] 任务异常: {result}")
                continue
            composition = to_composition_result(result)
            if composition:
                compositions.append(composition)
                applicable_count += 1
            else:
                print(
                    f"✓ [gemini:{result.technique_id}] {result.response_time_ms:.0f}ms "
                    f"applicable={result.is_applicable}, prompt={'✓' if result.image_prompt else '✗'}"
                )

        total_time_ms = (now_perf() - start_ts) * 1000
        return compositions, total_time_ms, len(techniques), applicable_count

    async def _call_single_technique_streaming(
        self,
        image_data_url: str,
        technique_id: str,
        loop: asyncio.AbstractEventLoop,
        event_queue: asyncio.Queue,
    ) -> None:
        start_time = datetime.now()
        start_ts = now_perf()
        try:
            full_content, timing = await asyncio.to_thread(
                self._stream_call_with_prompt_callback,
                image_data_url,
                technique_id,
                loop,
                event_queue,
            )
            end_ts = timing.finish_ts or now_perf()
            result = self.parse_single_result(
                technique_id,
                full_content,
                start_ts,
                end_ts,
                start_time,
                datetime.now(),
                timing=timing,
            )
            if result.is_applicable:
                composition = to_composition_result(result)
                if composition:
                    await event_queue.put(
                        {
                            "event": "technique_complete",
                            "technique": result.technique_id,
                            "technique_name": result.technique_name,
                            "aesthetic_desc": result.aesthetic_desc,
                            "steps": [step.model_dump() for step in composition.steps],
                            "shot_spec": composition.shot_spec.model_dump() if composition.shot_spec else None,
                            "image_prompt": result.image_prompt,
                            "response_time_ms": result.response_time_ms,
                        }
                    )
            else:
                await event_queue.put(
                    {
                        "event": "technique_not_applicable",
                        "technique": technique_id,
                        "technique_name": TECHNIQUE_CONFIGS[technique_id].name,
                        "response_time_ms": result.response_time_ms,
                    }
                )
        except Exception as error:
            print(
                f"  ❌ [gemini:{technique_id}] streaming worker failed "
                f"error={type(error).__name__}: {error}",
                flush=True,
            )
            await event_queue.put(
                {
                    "event": "technique_error",
                    "technique": technique_id,
                    "error": str(error),
                }
            )

    async def analyze_stream(
        self,
        image_bytes: bytes,
        techniques: Optional[List[str]] = None,
    ) -> AsyncGenerator[Dict[str, Any], None]:
        start_ts = now_perf()
        _, _, image_data_url = self.prepare_image_payload(image_bytes)
        techniques = techniques or list(TECHNIQUE_CONFIGS.keys())
        loop = asyncio.get_running_loop()
        event_queue: asyncio.Queue = asyncio.Queue()

        async def run_all():
            tasks = [
                self._call_single_technique_streaming(image_data_url, tid, loop, event_queue)
                for tid in techniques
            ]
            await asyncio.gather(*tasks, return_exceptions=True)
            await event_queue.put(None)

        asyncio.create_task(run_all())
        applicable_count = 0
        while True:
            event = await event_queue.get()
            if event is None:
                break
            if event["event"] == "technique_complete":
                applicable_count += 1
            yield event

        yield {
            "event": "summary",
            "total_techniques": len(techniques),
            "applicable_count": applicable_count,
            "total_time_ms": (now_perf() - start_ts) * 1000,
        }
