"""
数据模型定义 (v3.1 统一格式)
使用 Pydantic 定义请求和响应的数据结构
"""
from typing import List, Optional
from pydantic import BaseModel, Field


# ==================== 请求模型 ==================== #
class AnalyzeRequest(BaseModel):
    """构图分析请求参数（非文件部分）"""
    aspect_ratio: Optional[str] = Field(
        default=None,
        description="生成图片的宽高比，如 '16:9', '4:3', '1:1'"
    )


# ==================== 响应模型 ==================== #
class CompositionStep(BaseModel):
    """单步操作指令"""
    step_order: int = Field(..., description="步骤序号")
    action_type: str = Field(..., description="操作类型，如 Shift, Zoom, View-change, Rotate-CW 等")
    direction: str = Field(..., description="操作方向，如 Left, Right, In, Out, High-angle 等")
    guide_text: str = Field(..., description="中文指导文本")


class CompositionResult(BaseModel):
    """单个构图方案 (v3.1 格式)"""
    technique: str = Field(..., description="构图技术ID，如 rule_of_thirds")
    technique_name: str = Field(..., description="构图技术中文名称")
    aesthetic_desc: str = Field(..., description="美学描述（中文）")
    steps: List[CompositionStep] = Field(default_factory=list, description="操作步骤列表")
    image_base64: Optional[str] = Field(None, description="生成图片的 Base64 编码")
    response_time_ms: Optional[float] = Field(None, description="响应时间（毫秒）")


class AnalysisResponse(BaseModel):
    """完整的分析响应 (v3.1 格式)"""
    success: bool = Field(..., description="请求是否成功")
    message: Optional[str] = Field(None, description="错误消息（如果有）")
    total_techniques: int = Field(default=0, description="请求的构图技术总数")
    applicable_count: int = Field(default=0, description="适用的构图方案数量")
    total_time_ms: float = Field(default=0.0, description="请求总耗时（毫秒）")
    compositions: List[CompositionResult] = Field(
        default_factory=list,
        description="构图方案列表"
    )


class HealthResponse(BaseModel):
    """健康检查响应"""
    status: str = Field(default="healthy")
    model: str = Field(..., description="当前使用的模型名称")
    techniques: List[str] = Field(default_factory=list, description="支持的构图技术列表")
