"""
构图分析 API 路由 (v3.1 统一格式)
"""
from io import BytesIO

from fastapi import APIRouter, File, UploadFile, HTTPException
from PIL import Image

from schemas import AnalysisResponse, HealthResponse
from services import get_gemini_service
from config import MODEL_NAME

router = APIRouter(prefix="/api/v1/composition", tags=["Composition"])


@router.post("/analyze", response_model=AnalysisResponse)
async def analyze_composition(
    image: UploadFile = File(..., description="要分析的原始图片"),
):
    """
    分析上传的图片，返回AI优化的构图建议 (v3.1 统一格式)
    
    - **image**: 上传的图片文件 (JPEG/PNG)
    
    返回包含以下内容：
    - 适用的构图方案数量
    - 1-4 个构图优化方案，每个方案包含：
      - technique: 构图技术ID
      - technique_name: 构图技术中文名称
      - aesthetic_desc: 美学描述
      - steps: 操作步骤列表
      - image_base64: 生成的目标构图图片（Base64 编码）
    """
    # 验证文件类型
    if not image.content_type or not image.content_type.startswith("image/"):
        raise HTTPException(
            status_code=400,
            detail=f"不支持的文件类型: {image.content_type}，请上传图片文件"
        )
    
    try:
        # 读取并转换图片
        image_bytes = await image.read()
        pil_image = Image.open(BytesIO(image_bytes))
        
        # 如果是 RGBA，转换为 RGB
        if pil_image.mode == "RGBA":
            pil_image = pil_image.convert("RGB")
        
        print(f"📷 接收到图片: {image.filename}, 尺寸: {pil_image.size}, 模式: {pil_image.mode}")
        
        # 调用 Gemini 服务
        gemini_service = get_gemini_service()
        compositions, total_time_ms, applicable_count = gemini_service.analyze_composition(
            image=pil_image,
        )
        
        return AnalysisResponse(
            success=True,
            total_techniques=applicable_count,  # 串行版只返回 applicable 的
            applicable_count=applicable_count,
            total_time_ms=total_time_ms,
            compositions=compositions,
        )
        
    except Exception as e:
        print(f"❌ 分析失败: {e}")
        return AnalysisResponse(
            success=False,
            message=str(e),
            compositions=[],
        )


@router.get("/health", response_model=HealthResponse)
async def health_check():
    """健康检查接口"""
    return HealthResponse(
        status="healthy",
        model=MODEL_NAME,
        techniques=["dynamic"],  # 串行版动态返回
    )
