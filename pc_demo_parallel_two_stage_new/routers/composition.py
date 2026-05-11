"""
构图分析 API 路由（provider 化两阶段版本）。
"""
import asyncio
import json
import time
from datetime import datetime

from fastapi import APIRouter, File, HTTPException, UploadFile
from fastapi.responses import StreamingResponse

from config import (
    STAGE1_MAX_CONCURRENCY,
    STAGE1_TIMEOUT_SECONDS,
    STAGE2_TIMEOUT_SECONDS,
    TECHNIQUE_CONFIGS,
    get_stage1_model_name,
)
from schemas import AnalysisResponse, HealthResponse
from schemas.composition import CompositionResult
from schemas.image import ImageRequest
from services import get_stage1_service, get_stage2_service
from services.common import to_composition_result

router = APIRouter(tags=["Composition"])


def _normalized_steps_signature(composition: CompositionResult) -> tuple:
    return tuple(
        (
            step.action_type.strip().lower(),
            step.direction.strip().lower(),
        )
        for step in composition.steps[:3]
    )


def _subject_center(composition: CompositionResult) -> tuple[float, float] | None:
    shot_spec = composition.shot_spec
    center = shot_spec.target_subject_center if shot_spec else None
    if not center or len(center) < 2:
        return None
    return float(center[0]), float(center[1])


def _subject_size(composition: CompositionResult) -> float | None:
    shot_spec = composition.shot_spec
    if not shot_spec or shot_spec.target_subject_size is None:
        return None
    return float(shot_spec.target_subject_size)


def _count_viewpoint_moves(composition: CompositionResult) -> int:
    viewpoint_actions = {"orbit", "raisecamera", "lowercamera", "step"}
    return sum(
        1
        for step in composition.steps
        if step.action_type.strip().lower() in viewpoint_actions
    )


def _duplicate_score(candidate: CompositionResult, kept: CompositionResult) -> float:
    score = 0.0

    candidate_signature = _normalized_steps_signature(candidate)
    kept_signature = _normalized_steps_signature(kept)
    if candidate_signature == kept_signature:
        score += 0.55
    elif candidate_signature and kept_signature and candidate_signature[:1] == kept_signature[:1]:
        score += 0.20

    candidate_viewpoint_required = bool(candidate.shot_spec and candidate.shot_spec.viewpoint_required)
    kept_viewpoint_required = bool(kept.shot_spec and kept.shot_spec.viewpoint_required)
    if candidate_viewpoint_required == kept_viewpoint_required:
        score += 0.10
    if _count_viewpoint_moves(candidate) == _count_viewpoint_moves(kept):
        score += 0.05

    candidate_center = _subject_center(candidate)
    kept_center = _subject_center(kept)
    if candidate_center and kept_center:
        center_dx = abs(candidate_center[0] - kept_center[0])
        center_dy = abs(candidate_center[1] - kept_center[1])
        max_delta = max(center_dx, center_dy)
        if max_delta <= 0.06:
            score += 0.20
        elif max_delta <= 0.10:
            score += 0.10
    elif candidate_center is None and kept_center is None:
        score += 0.05

    candidate_size = _subject_size(candidate)
    kept_size = _subject_size(kept)
    if candidate_size is not None and kept_size is not None:
        size_delta = abs(candidate_size - kept_size)
        if size_delta <= 0.08:
            score += 0.10
        elif size_delta <= 0.12:
            score += 0.05
    elif candidate_size is None and kept_size is None:
        score += 0.03

    if candidate.technique == kept.technique:
        score += 0.20

    return score


def _is_near_duplicate(candidate: CompositionResult, kept: CompositionResult) -> bool:
    if candidate.technique == kept.technique:
        return True
    return _duplicate_score(candidate, kept) >= 0.85


def _diversify_compositions(compositions: list[CompositionResult]) -> list[CompositionResult]:
    if len(compositions) <= 1:
        return compositions

    prioritized = sorted(
        compositions,
        key=lambda composition: (
            -(1 if composition.shot_spec and composition.shot_spec.viewpoint_required else 0),
            -len(composition.steps),
            composition.response_time_ms or 0.0,
        ),
    )
    diversified: list[CompositionResult] = []
    for composition in prioritized:
        duplicate_of = next(
            (kept for kept in diversified if _is_near_duplicate(composition, kept)),
            None,
        )
        if duplicate_of is not None:
            score = _duplicate_score(composition, duplicate_of)
            print(
                f"  🪄 [{composition.technique}] 与 [{duplicate_of.technique}] 近似重复 "
                f"(score={score:.2f})，已跳过",
                flush=True,
            )
            continue
        diversified.append(composition)

    return diversified


@router.post("/composition_analyze", response_model=AnalysisResponse)
async def analyze_composition(
    image: UploadFile = File(..., description="要分析的原始图片（JPEG/PNG）"),
):
    if not image.content_type or not image.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail=f"不支持的文件类型: {image.content_type}")

    try:
        image_bytes = await image.read()
        overall_start = time.perf_counter()
        techniques = list(TECHNIQUE_CONFIGS.keys())
        stage1 = get_stage1_service()
        stage2 = get_stage2_service()
        stage1_semaphore = asyncio.Semaphore(max(1, STAGE1_MAX_CONCURRENCY))
        timeout_stats = {
            "stage1_timeouts": 0,
            "stage2_timeouts": 0,
            "stage2_failures": 0,
        }

        print(f"\n{'=' * 60}")
        print(f"📷 接收到图片: {image.filename}, 大小: {len(image_bytes)} bytes")
        print(
            f"🚀 启动两阶段分析 "
            f"[stage1={stage1.provider_name}:{stage1.model_name}] "
            f"[stage2={stage2.provider_name}:{stage2.model_name}] "
            f"[timeouts s1={STAGE1_TIMEOUT_SECONDS:.0f}s s2={STAGE2_TIMEOUT_SECONDS:.0f}s total<=60s]"
        )
        print(f"{'=' * 60}")

        if getattr(stage1, "supports_prompt_streaming_pipeline", False):
            loop = asyncio.get_running_loop()
            _, _, image_data_url = stage1.prepare_image_payload(image_bytes)
            original_b64 = image_data_url

            async def pipeline_single(technique_id: str) -> CompositionResult | None:
                async with stage1_semaphore:
                    prompt_ready_event = asyncio.Event()
                    prompt_ref = [None]
                    stage2_started_ts_ref = [None]
                    start_time = datetime.now()
                    start_ts = time.perf_counter()

                    async def run_s2():
                        await prompt_ready_event.wait()
                        prompt = prompt_ref[0]
                        if not prompt:
                            return None
                        stage2_started_ts_ref[0] = time.perf_counter()
                        return await stage2.generate_single_with_timing(
                            ImageRequest(
                                technique_id=technique_id,
                                image_prompt=prompt,
                                original_image_b64=original_b64,
                            )
                        )

                    s1_task = asyncio.create_task(
                        asyncio.to_thread(
                            stage1.stream_pipeline_sync,
                            image_data_url,
                            technique_id,
                            loop,
                            prompt_ready_event,
                            prompt_ref,
                        )
                    )
                    s2_task = asyncio.create_task(run_s2())

                    try:
                        s1_payload = await asyncio.wait_for(
                            s1_task,
                            timeout=STAGE1_TIMEOUT_SECONDS,
                        )
                    except asyncio.TimeoutError:
                        s1_task.cancel()
                        s2_task.cancel()
                        await asyncio.gather(s1_task, s2_task, return_exceptions=True)
                        timeout_stats["stage1_timeouts"] += 1
                        print(
                            f"  ⏱️ [{technique_id}] stage1 timeout after "
                            f"{STAGE1_TIMEOUT_SECONDS:.1f}s",
                            flush=True,
                        )
                        return None
                    except Exception as error:
                        s2_task.cancel()
                        await asyncio.gather(s2_task, return_exceptions=True)
                        print(
                            f"  ❌ [{technique_id}] S1 异常: {type(error).__name__}: {error}",
                            flush=True,
                        )
                        return None

                    if not s1_payload:
                        s2_task.cancel()
                        await asyncio.gather(s2_task, return_exceptions=True)
                        print(f"  ❌ [{technique_id}] S1 异常: {s1_payload}", flush=True)
                        return None

                    full_content, stage1_timing = s1_payload
                    stage1_end_ts = stage1_timing.finish_ts or time.perf_counter()
                    stage1_result = stage1.parse_single_result(
                        technique_id,
                        full_content,
                        start_ts,
                        stage1_end_ts,
                        start_time,
                        datetime.now(),
                        timing=stage1_timing,
                    )
                    if not stage1_result.is_applicable or not prompt_ref[0]:
                        if not s2_task.done():
                            s2_task.cancel()
                        await asyncio.gather(s2_task, return_exceptions=True)
                        print(f"  ⏭️  [{technique_id}] 不适用，跳过", flush=True)
                        return None

                    image_b64 = None
                    stage2_ms = None
                    s2_payload = None
                    try:
                        if stage2_started_ts_ref[0] is None:
                            s2_payload = await asyncio.wait_for(
                                s2_task,
                                timeout=STAGE2_TIMEOUT_SECONDS,
                            )
                        else:
                            stage2_elapsed = time.perf_counter() - stage2_started_ts_ref[0]
                            remaining_timeout = max(
                                0.0,
                                STAGE2_TIMEOUT_SECONDS - stage2_elapsed,
                            )
                            if remaining_timeout == 0.0:
                                raise asyncio.TimeoutError()
                            s2_payload = await asyncio.wait_for(
                                s2_task,
                                timeout=remaining_timeout,
                            )
                    except asyncio.TimeoutError:
                        s2_task.cancel()
                        await asyncio.gather(s2_task, return_exceptions=True)
                        s2_payload = None
                        timeout_stats["stage2_timeouts"] += 1
                        print(
                            f"  ⏱️ [{technique_id}] stage2 timeout after "
                            f"{STAGE2_TIMEOUT_SECONDS:.1f}s，保留 stage1 结果",
                            flush=True,
                        )
                    except Exception as error:
                        s2_payload = error
                        timeout_stats["stage2_failures"] += 1
                        print(
                            f"  ⚠️ [{technique_id}] stage2 失败，保留 stage1 结果 "
                            f"error={type(error).__name__}: {error}",
                            flush=True,
                        )

                    if not isinstance(s2_payload, Exception) and s2_payload is not None:
                        image_result, _ = s2_payload
                        if image_result:
                            stage2_ms = image_result.response_time_ms
                            if image_result.success:
                                image_b64 = image_result.image_base64
                            else:
                                print(
                                    f"  ⚠️ [{technique_id}] stage2 未返回可用图片，保留 stage1 结果",
                                    flush=True,
                                )
                    total_ms = (time.perf_counter() - start_ts) * 1000
                    timing = {
                        "stage1_ms": round(stage1_result.response_time_ms, 2),
                        "total_ms": round(total_ms, 2),
                    }
                    if stage1_timing.prompt_ms is not None:
                        timing["prompt_ready_ms"] = round(stage1_timing.prompt_ms, 2)
                    if stage2_ms is not None:
                        timing["stage2_ms"] = round(stage2_ms, 2)

                    metrics = [
                        f"s1={timing['stage1_ms']:.0f}ms",
                        f"total={timing['total_ms']:.0f}ms",
                    ]
                    if "prompt_ready_ms" in timing:
                        metrics.insert(0, f"prompt={timing['prompt_ready_ms']:.0f}ms")
                    if "stage2_ms" in timing:
                        metrics.insert(-1, f"s2={timing['stage2_ms']:.0f}ms")
                    print(f"  🧩 [{technique_id}] " + ", ".join(metrics), flush=True)

                    return to_composition_result(
                        stage1_result,
                        image_base64=image_b64,
                        timing=timing,
                    )

            raw_results = await asyncio.gather(
                *[pipeline_single(technique_id) for technique_id in techniques],
                return_exceptions=True,
            )
            final_compositions = [
                result for result in raw_results if result is not None and not isinstance(result, Exception)
            ]
        else:
            _, _, original_b64 = stage1.prepare_image_payload(image_bytes)
            stage1_compositions, _, _, _ = await stage1.analyze_parallel(image_bytes, techniques)
            image_requests = [
                ImageRequest(
                    technique_id=composition.technique,
                    image_prompt=composition.image_prompt or "",
                    original_image_b64=original_b64,
                )
                for composition in stage1_compositions
                if composition.image_prompt
            ]
            image_results, _ = await stage2.generate_parallel(image_requests)
            image_map = {
                result.technique_id: result.image_base64
                for result in image_results
                if result.success and result.image_base64
            }
            image_time_map = {
                result.technique_id: result.response_time_ms
                for result in image_results
            }
            final_compositions = [
                composition.model_copy(
                    update={
                        "image_base64": image_map.get(composition.technique),
                        "timing": {
                            "stage1_ms": round(composition.response_time_ms or 0.0, 2),
                            "stage2_ms": round(image_time_map.get(composition.technique, 0.0), 2),
                            "total_ms": round(
                                (composition.response_time_ms or 0.0) +
                                image_time_map.get(composition.technique, 0.0),
                                2,
                            ),
                        },
                    }
                )
                for composition in stage1_compositions
            ]
            for composition in final_compositions:
                timing = composition.timing or {}
                print(
                    f"  🧩 [{composition.technique}] "
                    f"s1={timing.get('stage1_ms', 0.0):.0f}ms, "
                    f"s2={timing.get('stage2_ms', 0.0):.0f}ms, "
                    f"total={timing.get('total_ms', 0.0):.0f}ms",
                    flush=True,
                )

        final_compositions = _diversify_compositions(final_compositions)
        total_time_ms = (time.perf_counter() - overall_start) * 1000
        print(
            f"🏁 两阶段分析完成 total={total_time_ms:.0f}ms "
            f"applicable={len(final_compositions)}/{len(techniques)} "
            f"timeouts(s1={timeout_stats['stage1_timeouts']}, "
            f"s2={timeout_stats['stage2_timeouts']}) "
            f"s2_failures={timeout_stats['stage2_failures']}",
            flush=True,
        )
        return AnalysisResponse(
            success=True,
            total_techniques=len(techniques),
            applicable_count=len(final_compositions),
            total_time_ms=total_time_ms,
            compositions=final_compositions,
        )
    except Exception as error:
        print(f"❌ 分析失败: {error}")
        return AnalysisResponse(
            success=False,
            message=str(error),
            compositions=[],
        )


@router.post("/composition_analyze_stream")
async def analyze_composition_stream(
    image: UploadFile = File(..., description="要分析的原始图片（JPEG/PNG）"),
):
    if not image.content_type or not image.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail=f"不支持的文件类型: {image.content_type}")

    stage1 = get_stage1_service()
    if not getattr(stage1, "supports_prompt_streaming_pipeline", False):
        raise HTTPException(status_code=400, detail=f"当前 Stage1 provider `{stage1.provider_name}` 不支持流式 prompt 前置")

    image_bytes = await image.read()
    techniques = list(TECHNIQUE_CONFIGS.keys())

    async def event_generator():
        async for event in stage1.analyze_stream(image_bytes, techniques=techniques):
            yield f"data: {json.dumps(event, ensure_ascii=False)}\n\n"
        yield "data: [DONE]\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@router.get("/health", response_model=HealthResponse)
async def health_check():
    return HealthResponse(
        status="healthy",
        model=get_stage1_model_name(),
        techniques=list(TECHNIQUE_CONFIGS.keys()),
    )
