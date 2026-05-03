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
    TECHNIQUE_CONFIGS,
    get_stage1_model_name,
)
from schemas import AnalysisResponse, HealthResponse
from schemas.composition import CompositionResult
from schemas.image import ImageRequest
from services import get_stage1_service, get_stage2_service
from services.common import to_composition_result

router = APIRouter(tags=["Composition"])


@router.post("/composition_analyze", response_model=AnalysisResponse)
async def analyze_composition(
    image: UploadFile = File(..., description="要分析的原始图片（JPEG/PNG）"),
):
    if not image.content_type or not image.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail=f"不支持的文件类型: {image.content_type}")

    try:
        image_bytes = await image.read()
        overall_start = time.perf_counter()
        requested_techniques = list(TECHNIQUE_CONFIGS.keys())
        stage1 = get_stage1_service()
        stage2 = get_stage2_service()
        techniques = requested_techniques

        if getattr(stage1, "supports_technique_prefilter", False):
            selected = await stage1.prefilter_techniques(image_bytes, requested_techniques)
            if selected:
                techniques = selected

        print(f"\n{'=' * 60}")
        print(f"📷 接收到图片: {image.filename}, 大小: {len(image_bytes)} bytes")
        print(
            f"🚀 启动两阶段分析 "
            f"[stage1={stage1.provider_name}:{stage1.model_name}] "
            f"[stage2={stage2.provider_name}:{stage2.model_name}]"
        )
        if techniques != requested_techniques:
            print(
                f"🎯 预筛后详细分析 {len(techniques)}/{len(requested_techniques)} 个构图: {techniques}",
                flush=True,
            )
        print(f"{'=' * 60}")

        if getattr(stage1, "supports_prompt_streaming_pipeline", False):
            loop = asyncio.get_running_loop()
            _, _, image_data_url = stage1.prepare_image_payload(image_bytes)
            original_b64 = image_data_url

            async def pipeline_single(technique_id: str) -> CompositionResult | None:
                prompt_ready_event = asyncio.Event()
                prompt_ref = [None]
                start_time = datetime.now()
                start_ts = time.perf_counter()

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

                async def run_s2():
                    await prompt_ready_event.wait()
                    prompt = prompt_ref[0]
                    if not prompt:
                        return None, None
                    return await stage2.generate_single_with_timing(
                        ImageRequest(
                            technique_id=technique_id,
                            image_prompt=prompt,
                            original_image_b64=original_b64,
                        )
                    )

                s2_task = asyncio.create_task(run_s2())
                s1_payload, s2_payload = await asyncio.gather(s1_task, s2_task, return_exceptions=True)

                if isinstance(s1_payload, Exception) or not s1_payload:
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
                    print(f"  ⏭️  [{technique_id}] 不适用，跳过", flush=True)
                    return None

                image_b64 = None
                stage2_ms = None
                if not isinstance(s2_payload, Exception) and s2_payload is not None:
                    image_result, _ = s2_payload
                    if image_result and image_result.success:
                        image_b64 = image_result.image_base64
                    if image_result:
                        stage2_ms = image_result.response_time_ms
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

        total_time_ms = (time.perf_counter() - overall_start) * 1000
        print(
            f"🏁 两阶段分析完成 total={total_time_ms:.0f}ms "
            f"applicable={len(final_compositions)}/{len(techniques)}",
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
