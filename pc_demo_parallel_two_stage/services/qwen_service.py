"""
并行化 Qwen LLM 服务
核心逻辑：
1. analyze_composition_parallel() - 等待全部完成后返回（原版，供 /analyze 使用）
2. analyze_composition_stream()   - 实时检测 qwen_image_prompt，立即通过 SSE 推送（供 /analyze/stream 使用）
"""
import asyncio
import re
import json
import time
import base64
from io import BytesIO
from typing import List, Optional, Tuple, Dict, Any, AsyncGenerator, Callable
from dataclasses import dataclass, field
from datetime import datetime

from openai import OpenAI
from PIL import Image

from config import (
    DASHSCOPE_API_KEY,
    DASHSCOPE_BASE_URL,
    MODEL_NAME,
    SYSTEM_INSTRUCTION,
    TECHNIQUE_CONFIGS,
    MODEL_TEMPERATURE,
    MODEL_TOP_P,
    ENABLE_THINKING,
    MODEL_THINKING_BUDGET,
)
from schemas import CompositionStep, CompositionResult


@dataclass
class SingleTechniqueResult:
    """单个构图技术的调用结果（内部使用）"""
    technique_id: str
    technique_name: str
    start_time: str
    end_time: str
    response_time_ms: float
    success: bool
    is_applicable: bool = False
    error_message: Optional[str] = None
    aesthetic_desc: str = ""
    steps: List[Dict[str, Any]] = field(default_factory=list)
    qwen_image_prompt: str = ""


@dataclass
class Stage1Timing:
    """Stage 1 核心时延（创建、prompt_ready、完成）"""
    technique_id: str
    request_start_ts: float
    stream_created_ts: Optional[float] = None
    prompt_ready_ts: Optional[float] = None
    finish_ts: Optional[float] = None

    @property
    def s1_create_ms(self) -> Optional[float]:
        if self.stream_created_ts is None:
            return None
        return (self.stream_created_ts - self.request_start_ts) * 1000

    @property
    def prompt_ready_ms(self) -> Optional[float]:
        if self.prompt_ready_ts is None:
            return None
        return (self.prompt_ready_ts - self.request_start_ts) * 1000

    @property
    def s1_finish_ms(self) -> Optional[float]:
        if self.finish_ts is None:
            return None
        return (self.finish_ts - self.request_start_ts) * 1000


class QwenService:
    """并行化 Qwen LLM 服务类"""

    def __init__(self):
        """初始化 OpenAI 兼容客户端（指向 DashScope）"""
        if not DASHSCOPE_API_KEY:
            raise ValueError(
                "请设置 DASHSCOPE_API_KEY 环境变量\n"
                "获取方式：https://dashscope.console.aliyun.com/apiKey"
            )
        self.client = OpenAI(
            api_key=DASHSCOPE_API_KEY,
            base_url=DASHSCOPE_BASE_URL,
        )
        print("✅ Qwen 客户端初始化成功")

    # ==================== 工具方法 ==================== #

    def _parse_json_from_text(self, text: str) -> Optional[Dict]:
        """从响应文本中解析 JSON（三重容错）"""
        json_match = re.search(r'```json\s*(.*?)\s*```', text, re.DOTALL)
        if json_match:
            try:
                return json.loads(json_match.group(1))
            except json.JSONDecodeError:
                pass
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            pass
        brace_match = re.search(r'\{.*\}', text, re.DOTALL)
        if brace_match:
            try:
                return json.loads(brace_match.group(0))
            except json.JSONDecodeError:
                pass
        return None

    def _extract_complete_json_string_field(self, text: str, field_name: str) -> Optional[str]:
        """
        从部分 JSON 文本中实时提取一个完整的字符串字段值。

        匹配 "field_name": "value"（处理转义字符），
        仅当 value 的结束引号已出现时才返回，否则返回 None。
        适用于 qwen_image_prompt 的流式实时检测。
        """
        pattern = rf'"{re.escape(field_name)}"\s*:\s*"((?:[^"\\]|\\.)*)"'
        match = re.search(pattern, text, re.DOTALL)
        if match:
            raw = match.group(1)
            # 处理常见 JSON 转义序列
            return (
                raw.replace('\\"', '"')
                   .replace('\\\\', '\\')
                   .replace('\\n', '\n')
                   .replace('\\t', '\t')
                   .replace('\\r', '\r')
            )
        return None

    def _image_to_base64_url(self, image_bytes: bytes, mime_type: str = "image/jpeg") -> str:
        """将图片字节转为 base64 data URL，用于构造多模态消息"""
        b64_str = base64.b64encode(image_bytes).decode("utf-8")
        return f"data:{mime_type};base64,{b64_str}"

    def _log_stage1_finish(
        self,
        technique_id: str,
        timing: Stage1Timing,
    ) -> None:
        metrics = []
        if timing.s1_create_ms is not None:
            metrics.append(f"s1_create={timing.s1_create_ms:.0f}ms")
        if timing.prompt_ready_ms is not None:
            metrics.append(f"prompt_ready={timing.prompt_ready_ms:.0f}ms")
        if timing.s1_finish_ms is not None:
            metrics.append(f"s1_finish={timing.s1_finish_ms:.0f}ms")

        if metrics:
            print(
                f"  ⏱️  [{technique_id}] S1 完成 | " + ", ".join(metrics),
                flush=True,
            )

    def _build_messages(self, technique_id: str, image_data_url: str) -> list:
        """构建 API 调用消息列表"""
        technique_config = TECHNIQUE_CONFIGS[technique_id]
        return [
            {"role": "system", "content": SYSTEM_INSTRUCTION},
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": technique_config.user_prompt},
                    {"type": "image_url", "image_url": {"url": image_data_url}},
                ],
            },
        ]

    def _prepare_image(self, image_bytes: bytes) -> Tuple[bytes, str]:
        """处理图片：识别格式，RGBA 转 RGB，返回 (bytes, mime_type)"""
        try:
            image = Image.open(BytesIO(image_bytes))
            fmt = image.format or "JPEG"
            mime_type = f"image/{fmt.lower()}"
            if image.mode == "RGBA":
                buf = BytesIO()
                image.convert("RGB").save(buf, format="JPEG")
                return buf.getvalue(), "image/jpeg"
            return image_bytes, mime_type
        except Exception:
            return image_bytes, "image/jpeg"

    # ==================== 同步流式调用（在线程内运行） ==================== #

    def _stream_call_sync(self, image_data_url: str, technique_id: str) -> str:
        """
        同步流式调用，收集完整响应。
        供 analyze_composition_parallel（非流式端点）使用。
        """
        stream = self.client.chat.completions.create(
            model=MODEL_NAME,
            messages=self._build_messages(technique_id, image_data_url),
            stream=True,
            temperature=MODEL_TEMPERATURE,
            top_p=MODEL_TOP_P,
            extra_body={"enable_thinking": ENABLE_THINKING},
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
        """
        同步流式调用，带实时 prompt 检测。
        每个 chunk 后检测 qwen_image_prompt 是否已完整，
        一旦完整立即通过 loop.call_soon_threadsafe 放入事件队列。
        供 analyze_composition_stream（SSE 端点）使用。
        """
        timing = Stage1Timing(
            technique_id=technique_id,
            request_start_ts=time.perf_counter(),
        )

        stream = self.client.chat.completions.create(
            model=MODEL_NAME,
            messages=self._build_messages(technique_id, image_data_url),
            stream=True,
            temperature=MODEL_TEMPERATURE,
            top_p=MODEL_TOP_P,
            extra_body={"enable_thinking": ENABLE_THINKING},
        )
        timing.stream_created_ts = time.perf_counter()

        full_content = ""
        prompt_sent = False  # 确保 prompt_ready 事件只发送一次

        for chunk in stream:
            delta = chunk.choices[0].delta
            if delta.content is not None:
                full_content += delta.content

                # 仅在 prompt 未发送时尝试检测
                if not prompt_sent:
                    prompt = self._extract_complete_json_string_field(
                        full_content, "qwen_image_prompt"
                    )
                    if prompt:
                        prompt_sent = True
                        timing.prompt_ready_ts = time.perf_counter()
                        prompt_elapsed_ms = timing.prompt_ready_ms or 0.0
                        event = {
                            "event": "prompt_ready",
                            "technique": technique_id,
                            "technique_name": TECHNIQUE_CONFIGS[technique_id].name,
                            "qwen_image_prompt": prompt,
                            "prompt_ready_ms": round(prompt_elapsed_ms),
                        }
                        # 从同步线程安全地写入异步队列
                        loop.call_soon_threadsafe(event_queue.put_nowait, event)
                        print(
                            f"  🚀 [{technique_id}] prompt_ready 推送 "
                            f"| 提示词生成耗时: {prompt_elapsed_ms:.0f}ms",
                            flush=True,
                        )

        return full_content, timing

    def _stream_call_pipeline_sync(
        self,
        image_data_url: str,
        technique_id: str,
        loop: asyncio.AbstractEventLoop,
        prompt_ready_event: asyncio.Event,
        prompt_ref: list,  # 可变容器：prompt_ref[0] 被设置为检测到的 prompt 字符串
    ) -> tuple[str, Stage1Timing]:
        """
        流水线同步流式调用（最优模式）：
        - 检测到 qwen_image_prompt 后立即 set prompt_ready_event（触发 S2 并发运行）
        - 继续流式输出获取完整 JSON（steps, aesthetic_desc 等）
        - 流结束时确保 event 被 set（防止 S2 因无 prompt 而永久等待）
        """
        timing = Stage1Timing(
            technique_id=technique_id,
            request_start_ts=time.perf_counter(),
        )

        stream = self.client.chat.completions.create(
            model=MODEL_NAME,
            messages=self._build_messages(technique_id, image_data_url),
            stream=True,
            temperature=MODEL_TEMPERATURE,
            top_p=MODEL_TOP_P,
            extra_body={"enable_thinking": ENABLE_THINKING},
        )
        timing.stream_created_ts = time.perf_counter()

        full_content = ""
        prompt_sent = False

        for chunk in stream:
            delta = chunk.choices[0].delta
            if delta.content is not None:
                full_content += delta.content

                if not prompt_sent:
                    prompt = self._extract_complete_json_string_field(
                        full_content, "qwen_image_prompt"
                    )
                    if prompt:
                        prompt_sent = True
                        prompt_ref[0] = prompt
                        timing.prompt_ready_ts = time.perf_counter()
                        elapsed_ms = timing.prompt_ready_ms or 0.0
                        # 触发 S2：从同步线程安全地 set 异步 Event
                        loop.call_soon_threadsafe(prompt_ready_event.set)
                        print(
                            f"  ⚡ [{technique_id}] prompt 就绪 {elapsed_ms:.0f}ms "
                            f"→ S2 立即触发，S1 继续获取 steps...",
                            flush=True,
                        )

        # 确保 event 始终被 set（不适用/无 prompt 场景，防止 S2 永久等待）
        if not prompt_sent:
            loop.call_soon_threadsafe(prompt_ready_event.set)

        return full_content, timing

    # ==================== 结果转换 ==================== #

    def _build_composition_result(self, r: SingleTechniqueResult) -> Optional[CompositionResult]:
        """将内部结果对象转为 CompositionResult Pydantic 模型"""
        if not (r.success and r.is_applicable and r.qwen_image_prompt):
            return None
        steps = [
            CompositionStep(
                step_order=step.get("step_order", i + 1),
                action_type=step.get("action_type", "Shift"),
                direction=step.get("direction", ""),
                guide_text=step.get("guide_text", ""),
            )
            for i, step in enumerate(r.steps)
        ]
        return CompositionResult(
            technique=r.technique_id,
            technique_name=r.technique_name,
            aesthetic_desc=r.aesthetic_desc,
            steps=steps,
            qwen_image_prompt=r.qwen_image_prompt,
            response_time_ms=r.response_time_ms,
        )

    def _parse_single_result(
        self,
        technique_id: str,
        full_content: str,
        start_ts: float,
        end_ts: float,
        start_time: datetime,
        end_time: datetime,
        timing: Optional[Stage1Timing] = None,
    ) -> SingleTechniqueResult:
        """解析单次调用的完整文本，填充 SingleTechniqueResult"""
        result = SingleTechniqueResult(
            technique_id=technique_id,
            technique_name=TECHNIQUE_CONFIGS[technique_id].name,
            start_time=start_time.isoformat(),
            end_time=end_time.isoformat(),
            response_time_ms=(end_ts - start_ts) * 1000,
            success=True,
        )
        parsed_json = self._parse_json_from_text(full_content)
        if parsed_json:
            result.is_applicable = parsed_json.get("is_applicable", False)
            composition_data = parsed_json.get("composition_data")
            if composition_data and result.is_applicable:
                result.aesthetic_desc = composition_data.get("aesthetic_desc_and_reasoning", "")
                result.steps = composition_data.get("steps", [])
                result.qwen_image_prompt = composition_data.get("qwen_image_prompt", "")
        else:
            print(f"  ⚠️  [{technique_id}] JSON 解析失败，原始内容前200字: {full_content[:200]}")
        if timing is not None:
            timing.finish_ts = time.perf_counter()
            self._log_stage1_finish(technique_id, timing)
        return result

    # ==================== 非流式并行接口（/analyze） ==================== #

    async def _call_single_technique(
        self,
        image_data_url: str,
        technique_id: str,
    ) -> SingleTechniqueResult:
        """异步调用单个构图技术（非流式，等待完整响应）"""
        start_time = datetime.now()
        start_ts = time.perf_counter()

        result = SingleTechniqueResult(
            technique_id=technique_id,
            technique_name=TECHNIQUE_CONFIGS[technique_id].name,
            start_time=start_time.isoformat(),
            end_time="",
            response_time_ms=0,
            success=False,
        )
        try:
            full_content = await asyncio.to_thread(
                self._stream_call_sync,
                image_data_url,
                technique_id,
            )
            end_ts = time.perf_counter()
            end_time = datetime.now()
            result = self._parse_single_result(
                technique_id, full_content, start_ts, end_ts, start_time, end_time
            )
        except Exception as e:
            end_ts = time.perf_counter()
            end_time = datetime.now()
            result.end_time = end_time.isoformat()
            result.response_time_ms = (end_ts - start_ts) * 1000
            result.error_message = str(e)
            print(f"❌ {technique_id} 调用失败: {e}")
        return result

    async def analyze_composition_parallel(
        self,
        image_bytes: bytes,
        techniques: Optional[List[str]] = None,
    ) -> Tuple[List[CompositionResult], float, int, int]:
        """并行分析 5 种构图方案，等待全部完成后返回（供 /analyze 使用）"""
        start_ts = time.perf_counter()

        image_bytes, mime_type = self._prepare_image(image_bytes)
        image_data_url = self._image_to_base64_url(image_bytes, mime_type)

        techniques = techniques or list(TECHNIQUE_CONFIGS.keys())
        tasks = [
            asyncio.create_task(self._call_single_technique(image_data_url, tid))
            for tid in techniques
        ]
        results = await asyncio.gather(*tasks, return_exceptions=True)

        end_ts = time.perf_counter()
        total_time_ms = (end_ts - start_ts) * 1000

        compositions: List[CompositionResult] = []
        successful_count = 0
        applicable_count = 0

        for r in results:
            if isinstance(r, Exception):
                print(f"⚠️ 任务异常: {r}")
                continue
            if r.success:
                successful_count += 1
                has_prompt = "✓" if r.qwen_image_prompt else "✗"
                print(
                    f"✓ {r.technique_id}: {r.response_time_ms:.0f}ms, "
                    f"applicable={r.is_applicable}, qwen_prompt={has_prompt}"
                )
                composition = self._build_composition_result(r)
                if composition:
                    applicable_count += 1
                    compositions.append(composition)
            else:
                print(f"✗ {r.technique_id}: {r.error_message}")

        print(f"\n📊 统计: 成功 {successful_count}/{len(techniques)}, 适用 {applicable_count}/{len(techniques)}")
        print(f"⏱️  并行总耗时: {total_time_ms:.0f}ms")

        return compositions, total_time_ms, len(techniques), applicable_count

    # ==================== 流式并行接口（/analyze/stream） ==================== #

    async def _call_single_technique_streaming(
        self,
        image_data_url: str,
        technique_id: str,
        loop: asyncio.AbstractEventLoop,
        event_queue: asyncio.Queue,
    ) -> None:
        """
        异步调用单个构图技术（流式模式）。
        检测到 qwen_image_prompt 后立即发送 prompt_ready 事件，
        全部完成后发送 technique_complete 事件。
        """
        start_time = datetime.now()
        start_ts = time.perf_counter()
        try:
            full_content, timing = await asyncio.to_thread(
                self._stream_call_with_prompt_callback,
                image_data_url,
                technique_id,
                loop,
                event_queue,
            )
            end_ts = time.perf_counter()
            end_time = datetime.now()

            result = self._parse_single_result(
                technique_id,
                full_content,
                start_ts,
                end_ts,
                start_time,
                end_time,
                timing=timing,
            )

            if result.is_applicable:
                composition = self._build_composition_result(result)
                if composition:
                    # 发送完整结果事件（此时 prompt 早已推送）
                    await event_queue.put({
                        "event": "technique_complete",
                        "technique": result.technique_id,
                        "technique_name": result.technique_name,
                        "aesthetic_desc": result.aesthetic_desc,
                        "steps": [s.model_dump() for s in composition.steps],
                        "qwen_image_prompt": result.qwen_image_prompt,
                        "response_time_ms": result.response_time_ms,
                    })
            else:
                # 不适用的技术也发一个通知，便于客户端计数
                await event_queue.put({
                    "event": "technique_not_applicable",
                    "technique": technique_id,
                    "technique_name": TECHNIQUE_CONFIGS[technique_id].name,
                    "response_time_ms": result.response_time_ms,
                })

        except Exception as e:
            print(f"❌ [{technique_id}] 流式调用失败: {e}")
            await event_queue.put({
                "event": "technique_error",
                "technique": technique_id,
                "error": str(e),
            })

    async def analyze_composition_stream(
        self,
        image_bytes: bytes,
        techniques: Optional[List[str]] = None,
    ) -> AsyncGenerator[Dict, None]:
        """
        流式分析 5 种构图方案，通过异步生成器实时 yield 事件。

        事件类型（按时间顺序）：
        - prompt_ready:           qwen_image_prompt 已提取，立即推送（最早）
        - technique_complete:     某技术的完整 JSON 解析完成
        - technique_not_applicable: 某技术不适用
        - technique_error:        某技术调用失败
        - summary:                全部技术处理完毕，汇总统计
        """
        start_ts = time.perf_counter()

        image_bytes, mime_type = self._prepare_image(image_bytes)
        image_data_url = self._image_to_base64_url(image_bytes, mime_type)

        techniques = techniques or list(TECHNIQUE_CONFIGS.keys())
        loop = asyncio.get_event_loop()
        event_queue: asyncio.Queue = asyncio.Queue()

        # 启动所有技术的并发任务（后台运行）
        async def _run_all():
            tasks = [
                self._call_single_technique_streaming(
                    image_data_url, tid, loop, event_queue
                )
                for tid in techniques
            ]
            await asyncio.gather(*tasks, return_exceptions=True)
            # 所有任务完成后放入哨兵
            await event_queue.put(None)

        asyncio.create_task(_run_all())

        # 持续从队列读取事件并 yield
        applicable_count = 0
        completed_count = 0
        all_response_times: Dict[str, float] = {}  # technique_id -> response_time_ms

        while True:
            event = await event_queue.get()
            if event is None:
                break  # 全部完成
            if event["event"] == "technique_complete":
                applicable_count += 1
                completed_count += 1
                all_response_times[event["technique"]] = event.get("response_time_ms", 0)
            elif event["event"] == "technique_not_applicable":
                completed_count += 1
                all_response_times[event["technique"]] = event.get("response_time_ms", 0)
            elif event["event"] == "technique_error":
                completed_count += 1
            yield event

        end_ts = time.perf_counter()
        total_time_ms = (end_ts - start_ts) * 1000

        # 找出最长请求
        slowest_technique = ""
        max_response_ms = 0.0
        if all_response_times:
            slowest_technique = max(all_response_times, key=all_response_times.get)
            max_response_ms = all_response_times[slowest_technique]

        print(f"\n📊 流式统计: 适用 {applicable_count}/{len(techniques)}")
        print(f"⏱️  并行总耗时: {total_time_ms:.0f}ms")
        if slowest_technique:
            print(
                f"🐢 最慢请求: [{slowest_technique}] "
                f"{max_response_ms:.0f}ms"
            )

        # 最终汇总事件（也携带最慢请求信息）
        yield {
            "event": "summary",
            "total_techniques": len(techniques),
            "applicable_count": applicable_count,
            "total_time_ms": total_time_ms,
            "slowest_technique": slowest_technique,
            "max_response_ms": round(max_response_ms),
        }


# 全局服务实例（延迟初始化）
_qwen_service: Optional[QwenService] = None


def get_qwen_service() -> QwenService:
    """获取 Qwen 服务单例"""
    global _qwen_service
    if _qwen_service is None:
        _qwen_service = QwenService()
    return _qwen_service
