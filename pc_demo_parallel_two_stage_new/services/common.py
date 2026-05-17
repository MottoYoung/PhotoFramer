"""
多阶段 provider 共享的数据结构与工具函数。
"""
import base64
import json
import re
import time
from dataclasses import dataclass, field
from datetime import datetime
from io import BytesIO
from typing import Any, Dict, List, Optional, Tuple

from PIL import Image

from schemas import CompositionStep, CompositionResult, ShotSpec


@dataclass
class Stage1Result:
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
    image_prompt: str = ""
    shot_spec: Optional[Dict[str, Any]] = None


@dataclass
class Stage1Timing:
    technique_id: str
    request_start_ts: float
    stream_created_ts: Optional[float] = None
    prompt_ready_ts: Optional[float] = None
    finish_ts: Optional[float] = None

    @property
    def create_ms(self) -> Optional[float]:
        if self.stream_created_ts is None:
            return None
        return (self.stream_created_ts - self.request_start_ts) * 1000

    @property
    def prompt_ms(self) -> Optional[float]:
        if self.prompt_ready_ts is None:
            return None
        return (self.prompt_ready_ts - self.request_start_ts) * 1000

    @property
    def finish_ms(self) -> Optional[float]:
        if self.finish_ts is None:
            return None
        return (self.finish_ts - self.request_start_ts) * 1000


@dataclass
class Stage2Timing:
    technique_id: str
    start_ts: float
    finish_ts: Optional[float] = None

    @property
    def total_ms(self) -> Optional[float]:
        if self.finish_ts is None:
            return None
        return (self.finish_ts - self.start_ts) * 1000


def parse_json_from_text(text: str) -> Optional[Dict[str, Any]]:
    """从模型文本中容错解析 JSON。"""
    json_match = re.search(r"```json\s*(.*?)\s*```", text, re.DOTALL)
    if json_match:
        try:
            return json.loads(json_match.group(1))
        except json.JSONDecodeError:
            pass

    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass

    brace_match = re.search(r"\{.*\}", text, re.DOTALL)
    if brace_match:
        try:
            return json.loads(brace_match.group(0))
        except json.JSONDecodeError:
            pass
    return None


def extract_complete_json_string_field(text: str, field_name: str) -> Optional[str]:
    """
    从部分 JSON 文本中提取一个完整字符串字段值。
    适合 prompt 前置流式截取。
    """
    pattern = rf'"{re.escape(field_name)}"\s*:\s*"((?:[^"\\]|\\.)*)"'
    match = re.search(pattern, text, re.DOTALL)
    if not match:
        return None
    raw = match.group(1)
    return (
        raw.replace('\\"', '"')
        .replace("\\\\", "\\")
        .replace("\\n", "\n")
        .replace("\\t", "\t")
        .replace("\\r", "\r")
    )


def extract_json_bool_field(text: str, field_name: str) -> Optional[bool]:
    """
    从部分 JSON 文本中提取布尔字段值。
    适合流式阶段尽早判断 is_applicable。
    """
    pattern = rf'"{re.escape(field_name)}"\s*:\s*(true|false)'
    match = re.search(pattern, text, re.IGNORECASE)
    if not match:
        return None
    return match.group(1).lower() == "true"


def prepare_image_bytes(image_bytes: bytes) -> Tuple[bytes, str]:
    """标准化图片字节，返回 (bytes, mime_type)。"""
    try:
        image = Image.open(BytesIO(image_bytes))
        fmt = image.format or "JPEG"
        mime_type = f"image/{fmt.lower()}"
        if image.mode == "RGBA":
            buffer = BytesIO()
            image.convert("RGB").save(buffer, format="JPEG")
            return buffer.getvalue(), "image/jpeg"
        return image_bytes, mime_type
    except Exception:
        return image_bytes, "image/jpeg"


def image_to_data_url(image_bytes: bytes, mime_type: str = "image/jpeg") -> str:
    b64_str = base64.b64encode(image_bytes).decode("utf-8")
    return f"data:{mime_type};base64,{b64_str}"


def build_stage1_result(
    *,
    technique_id: str,
    technique_name: str,
    start_time: datetime,
    end_time: datetime,
    start_ts: float,
    end_ts: float,
    parsed_json: Optional[Dict[str, Any]],
) -> Stage1Result:
    result = Stage1Result(
        technique_id=technique_id,
        technique_name=technique_name,
        start_time=start_time.isoformat(),
        end_time=end_time.isoformat(),
        response_time_ms=(end_ts - start_ts) * 1000,
        success=True,
    )
    if not parsed_json:
        return result

    result.is_applicable = parsed_json.get("is_applicable", False)
    composition_data = parsed_json.get("composition_data")
    if composition_data and result.is_applicable:
        result.aesthetic_desc = (
            composition_data.get("aesthetic_desc")
            or composition_data.get("aesthetic_desc_and_reasoning")
            or ""
        )
        result.steps = composition_data.get("steps", [])
        result.image_prompt = (
            composition_data.get("image_prompt")
            or composition_data.get("qwen_image_prompt")
            or ""
        )
        result.shot_spec = composition_data.get("shot_spec")
    return result


def to_composition_result(
    result: Stage1Result,
    image_base64: Optional[str] = None,
    timing: Optional[Dict[str, float]] = None,
) -> Optional[CompositionResult]:
    if not (result.success and result.is_applicable and result.image_prompt):
        return None

    steps = [
        CompositionStep(
            step_order=step.get("step_order", index + 1),
            action_type=step.get("action_type", "Shift"),
            direction=step.get("direction", ""),
            guide_text=step.get("guide_text", ""),
        )
        for index, step in enumerate(result.steps)
    ]

    shot_spec = None
    if result.shot_spec:
        try:
            shot_spec = ShotSpec(**result.shot_spec)
        except Exception:
            shot_spec = None

    return CompositionResult(
        technique=result.technique_id,
        technique_name=result.technique_name,
        aesthetic_desc=result.aesthetic_desc,
        steps=steps,
        shot_spec=shot_spec,
        image_base64=image_base64,
        image_prompt=result.image_prompt,
        timing=timing,
        response_time_ms=result.response_time_ms,
    )


def log_stage1_finish(provider_name: str, technique_id: str, timing: Stage1Timing) -> None:
    metrics: List[str] = []
    if timing.create_ms is not None:
        metrics.append(f"s1_create={timing.create_ms:.0f}ms")
    if timing.prompt_ms is not None:
        metrics.append(f"prompt_ready={timing.prompt_ms:.0f}ms")
    if timing.finish_ms is not None:
        metrics.append(f"s1_finish={timing.finish_ms:.0f}ms")
    if metrics:
        print(f"  ⏱️  [{provider_name}:{technique_id}] " + ", ".join(metrics), flush=True)


def log_stage2_finish(
    provider_name: str,
    technique_id: str,
    timing: Stage2Timing,
    *,
    success: bool,
    error_message: Optional[str] = None,
) -> None:
    status = "ok" if success else "fail"
    message = f"  ⏱️  [{provider_name}:{technique_id}] s2_finish={(timing.total_ms or 0.0):.0f}ms status={status}"
    if error_message:
        message += f" error={error_message}"
    print(message, flush=True)


def now_perf() -> float:
    return time.perf_counter()
