"""
Stage 2 图像生成路由（provider 化）。
"""
from fastapi import APIRouter, HTTPException

from schemas.image import ImageGenerationRequest, ImageGenerationResponse
from services import get_stage2_service

router = APIRouter(tags=["Image Generation"])


@router.post("/image_generate", response_model=ImageGenerationResponse)
async def generate_images(body: ImageGenerationRequest):
    if not body.requests:
        raise HTTPException(status_code=400, detail="requests 列表不能为空")

    try:
        stage2 = get_stage2_service()
        results, total_time_ms = await stage2.generate_parallel(body.requests)
        successful_count = sum(1 for result in results if result.success)
        return ImageGenerationResponse(
            success=True,
            total=len(body.requests),
            successful_count=successful_count,
            total_time_ms=total_time_ms,
            results=results,
        )
    except Exception as error:
        print(f"❌ 生图失败: {error}")
        return ImageGenerationResponse(
            success=False,
            message=str(error),
            total=len(body.requests),
            results=[],
        )
