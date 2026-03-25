"""
Stage 2 图像生成 API 路由
"""
from fastapi import APIRouter, HTTPException

from schemas.image import ImageGenerationRequest, ImageGenerationResponse, ImageResult
from services.image_service import get_image_service

router = APIRouter(prefix="/api/v1/image", tags=["Image Generation (Stage 2)"])


@router.post("/generate", response_model=ImageGenerationResponse)
async def generate_images(body: ImageGenerationRequest):
    """
    Stage 2：接收一批构图提示词，并行调用 Qwen-image-2.0 生成参考图。

    ### 典型调用时机
    客户端在 Stage 1 `/analyze/stream` 收到 `prompt_ready` 事件后，
    立即用该 `qwen_image_prompt` 调用本接口，无需等待 Stage 1 全部完成。

    ### 请求体示例
    ```json
    {
      "requests": [
        {
          "technique_id": "rule_of_thirds",
          "qwen_image_prompt": "Two people at night, rule-of-thirds...",
          "original_image_b64": "data:image/jpeg;base64,..."
        }
      ]
    }
    ```

    ### 响应
    - `results[].image_base64`：生成图片的 base64 data URL，可直接在前端 `<img src>` 使用
    - `results[].success`：是否生图成功
    """
    if not body.requests:
        raise HTTPException(status_code=400, detail="requests 列表不能为空")

    try:
        image_service = get_image_service()
        image_results, total_time_ms = await image_service.generate_parallel(body.requests)

        successful_count = sum(1 for r in image_results if r.success)

        return ImageGenerationResponse(
            success=True,
            total=len(body.requests),
            successful_count=successful_count,
            total_time_ms=total_time_ms,
            results=image_results,
        )

    except Exception as e:
        print(f"❌ 生图失败: {e}")
        return ImageGenerationResponse(
            success=False,
            message=str(e),
            total=len(body.requests),
            results=[],
        )
