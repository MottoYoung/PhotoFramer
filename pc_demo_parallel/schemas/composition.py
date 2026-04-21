"""
数据模型定义
使用 Pydantic 定义请求和响应的数据结构
"""
from typing import List, Optional
from pydantic import BaseModel, Field


# ==================== 响应模型 ==================== #
class CompositionStep(BaseModel):
    """单步操作指令"""
    step_order: int = Field(..., description="步骤序号")
    action_type: str = Field(
        ...,
        description="操作类型，如 Shift, Level, Zoom, Orbit, RaiseCamera, LowerCamera, Step 等"
    )
    direction: str = Field(
        ...,
        description="操作方向，如 Left, Right, Up, Down, In, Out, Forward, Backward, CW, CCW 等"
    )
    guide_text: str = Field(..., description="中文指导文本")


class ShotSpec(BaseModel):
    """结构化构图目标，供前端做更稳的引导与验证"""
    subject_hint: Optional[str] = Field(None, description="主体提示，如 main subject / person")
    viewpoint_required: bool = Field(default=False, description="是否必须改变机位而不只是平移或变焦")
    target_subject_center: Optional[List[float]] = Field(
        None,
        description="主体在目标参考图中的归一化中心点 [x, y]"
    )
    target_subject_size: Optional[float] = Field(
        None,
        description="主体在目标参考图中的相对尺寸（面积占比或近似比例）"
    )
    camera_move_summary: Optional[str] = Field(None, description="相机移动摘要，用于前端文案/调试")
    validation_notes: Optional[str] = Field(None, description="补充说明")


class CompositionResult(BaseModel):
    """单个构图方案"""
    technique: str = Field(..., description="构图技术ID，如 rule_of_thirds")
    technique_name: str = Field(..., description="构图技术中文名称")
    aesthetic_desc: str = Field(..., description="美学描述（中文）")
    steps: List[CompositionStep] = Field(default_factory=list, description="操作步骤列表")
    shot_spec: Optional[ShotSpec] = Field(None, description="结构化拍摄目标")
    image_base64: Optional[str] = Field(None, description="生成图片的 Base64 编码")
    response_time_ms: Optional[float] = Field(None, description="该方案的响应时间（毫秒）")


class AnalysisResponse(BaseModel):
    """完整的分析响应"""
    success: bool = Field(..., description="请求是否成功")
    message: Optional[str] = Field(None, description="错误消息（如果有）")
    total_techniques: int = Field(default=0, description="请求的构图技术总数")
    applicable_count: int = Field(default=0, description="适用的构图方案数量")
    total_time_ms: float = Field(default=0.0, description="并行请求总耗时（毫秒）")
    compositions: List[CompositionResult] = Field(
        default_factory=list,
        description="构图方案列表（仅包含 is_applicable=true 的结果）"
    )


class HealthResponse(BaseModel):
    """健康检查响应"""
    status: str = Field(default="healthy")
    model: str = Field(..., description="当前使用的模型名称")
    techniques: List[str] = Field(..., description="支持的构图技术列表")
