"""三阶段构图分析响应模型。"""
from typing import List, Optional
from pydantic import BaseModel, Field, AliasChoices


# ==================== 响应模型 ==================== #
class CompositionStep(BaseModel):
    """单步操作指令"""
    step_order: int = Field(..., description="步骤序号")
    action_type: str = Field(..., description="操作类型，如 Shift, Zoom, View-change")
    direction: str = Field(..., description="操作方向，如 Left, Right, In, Out, High-angle 等")
    guide_text: str = Field(..., description="中文指导文本")


class ShotSpec(BaseModel):
    """弱几何先验，供前端闭环验证使用。"""
    subject_hint: Optional[str] = Field(None, description="主体提示")
    viewpoint_required: bool = Field(default=False, description="是否必须改变机位")
    target_subject_center: Optional[List[float]] = Field(
        None,
        description="主体中心归一化坐标 [x, y]"
    )
    target_subject_size: Optional[float] = Field(
        None,
        description="主体长边相对画面短边的归一化比例"
    )
    camera_move_summary: Optional[str] = Field(None, description="机位变化摘要")
    validation_notes: Optional[str] = Field(None, description="验证注意事项")


class CompositionResult(BaseModel):
    """单个构图方案"""
    technique: str = Field(..., description="构图技术ID，如 rule_of_thirds")
    technique_name: str = Field(..., description="构图技术中文名称")
    aesthetic_desc: str = Field(..., description="美学描述与分析理由（中文）")
    steps: List[CompositionStep] = Field(default_factory=list, description="操作步骤列表")
    shot_spec: Optional[ShotSpec] = Field(
        None, description="弱几何目标，供前端验证闭环使用"
    )
    image_base64: Optional[str] = Field(
        None, description="生成的参考构图图片（base64 data URL），前端直接解码显示"
    )
    image_prompt: Optional[str] = Field(
        default=None,
        validation_alias=AliasChoices("image_prompt", "qwen_image_prompt"),
        serialization_alias="image_prompt",
        description="Stage 2 图像提示词（调试/流式场景可见）"
    )
    timing: Optional[dict] = Field(
        None,
        description="链路时延信息，如 prompt_ready_ms / stage1_ms / stage2_ms / total_ms"
    )
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
