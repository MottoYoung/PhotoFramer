"""
构图分析 API 路由
"""
import asyncio
import json
import time
from datetime import datetime

from fastapi import APIRouter, File, UploadFile, HTTPException
from fastapi.responses import StreamingResponse

from schemas import AnalysisResponse, HealthResponse
from schemas.composition import CompositionResult
from schemas.image import ImageRequest
from services import get_qwen_service
from services.image_service import get_image_service
from config import MODEL_NAME, TECHNIQUE_CONFIGS

router = APIRouter(tags=["Composition"])


@router.post("/composition_analyze", response_model=AnalysisResponse)
async def analyze_composition(
    image: UploadFile = File(..., description="要分析的原始图片（JPEG/PNG）"),
):
    """
    最优两阶段流水线：每条技术链路 S1 流式检测到 prompt 后立即触发 S2，两者并发运行。

    **时序**（每条链路独立）：
    ```
    S1 流式输出 ──── [prompt 就绪] ──── S1 继续获取 steps/desc
                          ↓ 立即
                         S2 开始生图 ─────────────────────── S2 完成
    ```
    总耗时 ≈ max(S1 全量时间, prompt就绪时间 + S2时间)
    """
    if not image.content_type or not image.content_type.startswith("image/"):
        raise HTTPException(
            status_code=400,
            detail=f"不支持的文件类型: {image.content_type}，请上传图片文件",
        )
    try:
        image_bytes = await image.read()
        overall_start = time.perf_counter()
        techniques = list(TECHNIQUE_CONFIGS.keys())

        print(f"\n{'='*60}")
        print(f"📷 接收到图片: {image.filename}, 大小: {len(image_bytes)} bytes")
        print(f"🚀 启动 {len(techniques)} 条 S1→S2 最优流水线...")
        print(f"{'='*60}")

        qwen_service = get_qwen_service()
        image_service = get_image_service()
        loop = asyncio.get_running_loop()

        # 一次性处理图片，所有技术链路共用（避免重复编码）
        img_bytes_processed, mime = qwen_service._prepare_image(image_bytes)
        image_data_url = qwen_service._image_to_base64_url(img_bytes_processed, mime)
        original_b64 = image_data_url  # Stage 2 原图引导（图生图），与 image_data_url 同一份

        async def pipeline_single(technique_id: str) -> CompositionResult | None:
            """
            单技术最优流水线：
            1. 启动 S1 流式调用（后台线程）
            2. 同步等待 prompt_ready_event（S1 流检测到 prompt 时触发）
            3. S2 立即开始，与 S1 尾部（steps/desc）并发运行
            4. 两者都完成后合并结果
            """
            prompt_ready_event = asyncio.Event()
            prompt_ref = [None]  # 线程安全的可变容器
            start_time = datetime.now()
            start_ts = time.perf_counter()

            # 启动 S1 流式调用（在线程池中运行，检测到 prompt 后 set event）
            s1_task = asyncio.create_task(
                asyncio.to_thread(
                    qwen_service._stream_call_pipeline_sync,
                    image_data_url, technique_id, loop,
                    prompt_ready_event, prompt_ref,
                )
            )

            # S2：等待 prompt 就绪后立即开始，与 S1 尾部并发
            async def run_s2():
                await prompt_ready_event.wait()
                prompt = prompt_ref[0]
                if not prompt:
                    return None, None  # 不适用
                return await image_service.generate_single_with_timing(ImageRequest(
                    technique_id=technique_id,
                    qwen_image_prompt=prompt,
                    original_image_b64=original_b64,
                ))

            s2_task = asyncio.create_task(run_s2())

            # 并发等待 S1 完成（获取完整 JSON）和 S2 完成（获取 image_base64）
            s1_payload, s2_payload = await asyncio.gather(
                s1_task, s2_task, return_exceptions=True
            )

            if isinstance(s1_payload, Exception) or not s1_payload:
                print(f"  ❌ [{technique_id}] S1 异常: {s1_payload}", flush=True)
                return None

            s1_full_content, stage1_timing = s1_payload

            img_result = None
            stage2_timing = None
            if isinstance(s2_payload, Exception):
                print(f"  ❌ [{technique_id}] S2 异常: {s2_payload}", flush=True)
            elif s2_payload is not None:
                img_result, stage2_timing = s2_payload

            # 解析完整 S1 JSON（steps, aesthetic_desc 等）
            end_ts = time.perf_counter()
            s1_result = qwen_service._parse_single_result(
                technique_id, s1_full_content,
                start_ts, end_ts, start_time, datetime.now(),
                timing=stage1_timing,
            )

            if not s1_result.is_applicable or not prompt_ref[0]:
                print(f"  ⏭️  [{technique_id}] 不适用，跳过", flush=True)
                return None

            comp = qwen_service._build_composition_result(s1_result)
            if not comp:
                return None

            image_b64 = (
                img_result.image_base64
                if img_result and not isinstance(img_result, Exception) and img_result.success
                else None
            )

            total_chain_ms = (end_ts - start_ts) * 1000
            print(f"  ✅ [{technique_id}] 链路完成 {total_chain_ms:.0f}ms", flush=True)

            return CompositionResult(
                technique=s1_result.technique_id,
                technique_name=s1_result.technique_name,
                aesthetic_desc=s1_result.aesthetic_desc,
                steps=comp.steps,
                image_base64=image_b64,
                qwen_image_prompt=prompt_ref[0],  # 保留供调试
                response_time_ms=s1_result.response_time_ms,
            )

        # 5 条流水线并发运行
        raw_results = await asyncio.gather(
            *[pipeline_single(tid) for tid in techniques],
            return_exceptions=True,
        )

        final_compositions = [
            r for r in raw_results
            if r is not None and not isinstance(r, Exception)
        ]
        applicable_count = len(final_compositions)
        overall_time_ms = (time.perf_counter() - overall_start) * 1000

        print(f"\n📊 完成：适用 {applicable_count}/{len(techniques)}")
        print(f"⏱️  两阶段总耗时: {overall_time_ms:.0f}ms")

        return AnalysisResponse(
            success=True,
            total_techniques=len(techniques),
            applicable_count=applicable_count,
            total_time_ms=overall_time_ms,
            compositions=final_compositions,
        )

    except Exception as e:
        print(f"❌ 分析失败: {e}")
        return AnalysisResponse(
            success=False,
            message=str(e),
            compositions=[],
        )


@router.post("/composition_analyze_stream")
async def analyze_composition_stream(
    image: UploadFile = File(..., description="要分析的原始图片（JPEG/PNG）"),
):
    """
    Stage 1 流式推送（SSE），实时返回 prompt_ready / technique_complete 事件。
    """
    if not image.content_type or not image.content_type.startswith("image/"):
        raise HTTPException(
            status_code=400,
            detail=f"不支持的文件类型: {image.content_type}，请上传图片文件",
        )

    image_bytes = await image.read()
    techniques = list(TECHNIQUE_CONFIGS.keys())
    print(f"\n{'='*60}")
    print(f"📷 接收到图片: {image.filename}, 大小: {len(image_bytes)} bytes")
    print(f"🌊 开始流式并行分析 {len(techniques)} 种构图技术...")
    print(f"{'='*60}")

    qwen_service = get_qwen_service()

    async def event_generator():
        async for event in qwen_service.analyze_composition_stream(
            image_bytes,
            techniques=techniques,
        ):
            yield f"data: {json.dumps(event, ensure_ascii=False)}\n\n"
        yield "data: [DONE]\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@router.get("/health", response_model=HealthResponse)
async def health_check():
    """健康检查接口"""
    return HealthResponse(
        status="healthy",
        model=MODEL_NAME,
        techniques=list(TECHNIQUE_CONFIGS.keys()),
    )
