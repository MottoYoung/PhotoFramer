"""
Qwen Stage 1 provider。
保留 prompt 前置流式截取能力，便于与任意 Stage 2 组合成流水线。
"""
import asyncio
from datetime import datetime
from typing import Any, AsyncGenerator, Dict, List, Optional, Tuple

from openai import OpenAI

from config import (
    DASHSCOPE_API_KEY,
    DASHSCOPE_BASE_URL,
    MODEL_TEMPERATURE,
    MODEL_TOP_P,
    QWEN_STAGE1_ENABLE_THINKING,
    QWEN_STAGE1_THINKING_BUDGET,
    QWEN_STAGE1_MODEL,
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


class QwenStage1Provider:
    provider_name = "qwen"
    model_name = QWEN_STAGE1_MODEL
    supports_prompt_streaming_pipeline = True

    def __init__(self):
        if not DASHSCOPE_API_KEY:
            raise ValueError("请设置 DASHSCOPE_API_KEY 环境变量")
        self.client = OpenAI(
            api_key=DASHSCOPE_API_KEY,
            base_url=DASHSCOPE_BASE_URL,
        )
        print("✅ QwenStage1Provider 初始化成功")

    def prepare_image_payload(self, image_bytes: bytes) -> Tuple[bytes, str, str]:
        processed, mime = prepare_image_bytes(image_bytes)
        return processed, mime, image_to_data_url(processed, mime)

    def _build_messages(self, technique_id: str, image_data_url: str) -> list:
        technique_config = TECHNIQUE_CONFIGS[technique_id]
        prompt_text = technique_config.user_prompt
        if not QWEN_STAGE1_ENABLE_THINKING:
            prompt_text = prompt_text.rstrip() + "\n/no_think"
        return [
            {"role": "system", "content": SYSTEM_INSTRUCTION},
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": prompt_text},
                    {"type": "image_url", "image_url": {"url": image_data_url}},
                ],
            },
        ]

    def _build_extra_body(self) -> dict:
        return {
            "enable_thinking": QWEN_STAGE1_ENABLE_THINKING,
            "thinking_budget": QWEN_STAGE1_THINKING_BUDGET,
        }

    def _stream_call_sync(self, image_data_url: str, technique_id: str) -> str:
        stream = self.client.chat.completions.create(
            model=self.model_name,
            messages=self._build_messages(technique_id, image_data_url),
            stream=True,
            temperature=MODEL_TEMPERATURE,
            top_p=MODEL_TOP_P,
            extra_body=self._build_extra_body(),
        )
        full_content = ""
        for chunk in stream:
            delta = chunk.choices[0].delta
            if delta.content is not None:
                full_content += delta.content
        return full_content

    def _stream_call_with_prompt_callback(
        self,
        image_data_url: str,
        technique_id: str,
        loop: asyncio.AbstractEventLoop,
        event_queue: asyncio.Queue,
    ) -> tuple[str, Stage1Timing]:
        timing = Stage1Timing(technique_id=technique_id, request_start_ts=now_perf())
        stream = self.client.chat.completions.create(
            model=self.model_name,
            messages=self._build_messages(technique_id, image_data_url),
            stream=True,
            temperature=MODEL_TEMPERATURE,
            top_p=MODEL_TOP_P,
            extra_body=self._build_extra_body(),
        )
        timing.stream_created_ts = now_perf()
        full_content = ""
        prompt_sent = False
        applicability_seen = False

        for chunk in stream:
            delta = chunk.choices[0].delta
            if delta.content is None:
                continue
            full_content += delta.content
            if not applicability_seen:
                applicability = extract_json_bool_field(full_content, "is_applicable")
                if applicability is not None:
                    applicability_seen = True
                    if not applicability:
                        prompt_sent = True
                        print(
                            f"  ⏭️  [qwen:{technique_id}] callback is_applicable=false "
                            f"{(now_perf() - timing.request_start_ts) * 1000:.0f}ms",
                            flush=True,
                        )
                        continue
                    print(
                        f"  ✅ [qwen:{technique_id}] callback is_applicable=true "
                        f"{(now_perf() - timing.request_start_ts) * 1000:.0f}ms",
                        flush=True,
                    )

            if applicability_seen and not prompt_sent:
                prompt = extract_complete_json_string_field(full_content, "image_prompt")
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
        return full_content, timing

    def stream_pipeline_sync(
        self,
        image_data_url: str,
        technique_id: str,
        loop: asyncio.AbstractEventLoop,
        prompt_ready_event: asyncio.Event,
        prompt_ref: list,
    ) -> tuple[str, Stage1Timing]:
        timing = Stage1Timing(technique_id=technique_id, request_start_ts=now_perf())
        stream = self.client.chat.completions.create(
            model=self.model_name,
            messages=self._build_messages(technique_id, image_data_url),
            stream=True,
            temperature=MODEL_TEMPERATURE,
            top_p=MODEL_TOP_P,
            extra_body=self._build_extra_body(),
        )
        timing.stream_created_ts = now_perf()
        full_content = ""
        prompt_sent = False
        applicability_seen = False

        for chunk in stream:
            delta = chunk.choices[0].delta
            if delta.content is None:
                continue
            full_content += delta.content
            if not applicability_seen:
                applicability = extract_json_bool_field(full_content, "is_applicable")
                if applicability is not None:
                    applicability_seen = True
                    if not applicability:
                        prompt_sent = True
                        loop.call_soon_threadsafe(prompt_ready_event.set)
                        print(
                            f"  ⏭️  [qwen:{technique_id}] is_applicable=false "
                            f"{(now_perf() - timing.request_start_ts) * 1000:.0f}ms",
                            flush=True,
                        )
                        continue
                    print(
                        f"  ✅ [qwen:{technique_id}] is_applicable=true "
                        f"{(now_perf() - timing.request_start_ts) * 1000:.0f}ms",
                        flush=True,
                    )

            if applicability_seen and not prompt_sent:
                prompt = extract_complete_json_string_field(full_content, "image_prompt")
                if prompt:
                    prompt_sent = True
                    prompt_ref[0] = prompt
                    timing.prompt_ready_ts = now_perf()
                    loop.call_soon_threadsafe(prompt_ready_event.set)
                    print(
                        f"  ⚡ [qwen:{technique_id}] prompt 就绪 {timing.prompt_ms or 0:.0f}ms",
                        flush=True,
                    )

        if not prompt_sent:
            loop.call_soon_threadsafe(prompt_ready_event.set)
        timing.finish_ts = now_perf()
        return full_content, timing

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
                print(f"⚠️ [qwen] 任务异常: {result}")
                continue
            composition = to_composition_result(result)
            if composition:
                compositions.append(composition)
                applicable_count += 1
            else:
                print(
                    f"✓ [qwen:{result.technique_id}] {result.response_time_ms:.0f}ms "
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
            end_ts = now_perf()
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
