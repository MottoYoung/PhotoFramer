"""
构图分析 API 路由
"""
from io import BytesIO

from fastapi import APIRouter, File, UploadFile, HTTPException

from schemas import AnalysisResponse, HealthResponse
from services import get_gemini_service
from config import MODEL_NAME, TECHNIQUE_CONFIGS

router = APIRouter(prefix="/api/v1/composition", tags=["Composition"])


@router.post("/analyze", response_model=AnalysisResponse)
async def analyze_composition(
    image: UploadFile = File(..., description="要分析的原始图片"),
):
    """
    并行分析上传的图片，返回 AI 优化的构图建议
    
    - **image**: 上传的图片文件 (JPEG/PNG)
    
    返回包含以下内容：
    - 并行请求总耗时
    - 适用的构图方案数量
    - 构图方案列表，每个方案包含：
      - technique: 构图技术ID
      - technique_name: 构图技术名称
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
        # 读取图片
        image_bytes = await image.read()
        
        print(f"\n{'='*60}")
        print(f"📷 接收到图片: {image.filename}, 大小: {len(image_bytes)} bytes")
        print(f"🚀 开始并行分析 {len(TECHNIQUE_CONFIGS)} 种构图技术...")
        print(f"{'='*60}")
        
        # 调用并行化 Gemini 服务
        gemini_service = get_gemini_service()
        compositions, total_time_ms, total_techniques, applicable_count = \
            await gemini_service.analyze_composition_parallel(image_bytes)
        
        return AnalysisResponse(
            success=True,
            total_techniques=total_techniques,
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
    """
    健康检查接口
    用于检查服务是否正常运行
    """
    return HealthResponse(
        status="healthy",
        model=MODEL_NAME,
        techniques=list(TECHNIQUE_CONFIGS.keys()),
    )
