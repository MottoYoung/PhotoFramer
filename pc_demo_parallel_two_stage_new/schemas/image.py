"""
Stage 2 图像生成数据模型
"""
from typing import List, Optional
from pydantic import BaseModel, Field, AliasChoices


class ImageRequest(BaseModel):
    """单条生图请求"""
    technique_id: str = Field(..., description="构图技术ID，与 Stage 1 对应")
    image_prompt: str = Field(
        ...,
        validation_alias=AliasChoices("image_prompt", "qwen_image_prompt"),
        serialization_alias="image_prompt",
        description="Stage 1 输出的图像生成提示词"
    )
    original_image_b64: Optional[str] = Field(
        None,
        description="原始照片的 base64 data URL（可选，传入则用于图生图引导）"
    )


class ImageResult(BaseModel):
    """单条生图结果"""
    technique_id: str = Field(..., description="构图技术ID")
    success: bool = Field(..., description="是否成功")
    image_base64: Optional[str] = Field(None, description="生成图片的 base64 data URL")
    error_message: Optional[str] = Field(None, description="错误信息")
    response_time_ms: float = Field(default=0.0, description="响应时间（毫秒）")


class ImageGenerationRequest(BaseModel):
    """POST /image_generate 请求体"""
    requests: List[ImageRequest] = Field(..., description="生图请求列表")


class ImageGenerationResponse(BaseModel):
    """POST /image_generate 响应"""
    success: bool = Field(..., description="整体是否成功")
    message: Optional[str] = Field(None, description="错误消息（整体失败时）")
    total: int = Field(default=0, description="请求总数")
    successful_count: int = Field(default=0, description="成功生图数量")
    total_time_ms: float = Field(default=0.0, description="并行生图总耗时（毫秒）")
    results: List[ImageResult] = Field(default_factory=list, description="各技术的生图结果")
