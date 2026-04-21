"""
并行化 Gemini API 服务
核心逻辑：使用 asyncio 并发调用 5 种构图方案
"""
import asyncio
import re
import json
import time
import base64
from io import BytesIO
from typing import List, Optional, Tuple, Dict, Any
from dataclasses import dataclass, field
from datetime import datetime

from google import genai
from google.genai import types
from PIL import Image

from config import (
    GEMINI_API_KEY,
    MODEL_NAME,
    SYSTEM_INSTRUCTION,
    TECHNIQUE_CONFIGS,
    ENABLE_ORBIT_ACTION,
    ENABLE_STEP_ACTION,
    MODEL_TEMPERATURE,
    MODEL_TOP_K,
    MODEL_TOP_P,
    IMAGE_SIZE,
    MODEL_MAX_TOKENS,
    THINKING_LEVEL
)
from schemas import CompositionStep, CompositionResult, ShotSpec


@dataclass
class SingleTechniqueResult:
    """单个构图技术的调用结果（内部使用）"""
    technique_id: str
    technique_name: str
    start_time: str
    end_time: str
    response_time_ms: float
    success: bool
    is_applicable: bool = False
    error_message: Optional[str] = None
    aesthetic_desc: str = ""
    steps: List[Dict[str, Any]] = field(default_factory=list)
    shot_spec: Optional[Dict[str, Any]] = None
    image_base64: Optional[str] = None


class GeminiService:
    """并行化 Gemini API 服务类"""
    
    def __init__(self):
        """初始化 Gemini 客户端"""
        if not GEMINI_API_KEY:
            raise ValueError(
                "请设置 GEMINI_API_KEY 环境变量或在 config.py 中填写 API 密钥\n"
                "获取方式：https://aistudio.google.com/app/apikey"
            )
        self.client = genai.Client(api_key=GEMINI_API_KEY)
        print("✅ Gemini 客户端初始化成功")

    def _parse_json_from_text(self, text: str) -> Optional[Dict]:
        """从响应文本中解析 JSON"""
        # 尝试提取 ```json ... ``` 块
        json_match = re.search(r'```json\s*(.*?)\s*```', text, re.DOTALL)
        if json_match:
            try:
                return json.loads(json_match.group(1))
            except json.JSONDecodeError:
                pass
        
        # 尝试直接解析
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            pass
        
        # 尝试查找 { ... } 结构
        brace_match = re.search(r'\{.*\}', text, re.DOTALL)
        if brace_match:
            try:
                return json.loads(brace_match.group(0))
            except json.JSONDecodeError:
                pass
        
        return None
    
    def _image_to_base64(self, image_bytes: bytes, mime_type: str) -> str:
        """将图片字节转换为 Base64 data URL"""
        b64_str = base64.b64encode(image_bytes).decode('utf-8')
        return f"data:{mime_type};base64,{b64_str}"

    def _sanitize_composition_result(
        self,
        technique_id: str,
        result: SingleTechniqueResult
    ) -> SingleTechniqueResult:
        """
        服务端质量兜底：
        - Orbit 当前设备侧验证仍不稳定，先保守过滤
        - 对角线构图禁止依赖旋转手机/故意倾斜画面
        """
        if not result.is_applicable:
            return result

        normalized_steps = []
        for step in result.steps:
            action_type = str(step.get("action_type", "")).strip().lower()
            direction = str(step.get("direction", "")).strip().lower()
            normalized_steps.append((action_type, direction))

        has_orbit = any(action_type == "orbit" for action_type, _ in normalized_steps)
        has_step = any(action_type == "step" for action_type, _ in normalized_steps)
        has_roll = any(
            action_type == "level" or direction in {"cw", "ccw", "rotate-cw", "rotate-ccw"}
            for action_type, direction in normalized_steps
        )

        if has_orbit and not ENABLE_ORBIT_ACTION:
            result.is_applicable = False
            result.image_base64 = None
            result.steps = []
            result.shot_spec = None
            result.aesthetic_desc = "当前设备暂不稳定支持绕拍引导"
            return result

        if has_step and not ENABLE_STEP_ACTION:
            result.is_applicable = False
            result.image_base64 = None
            result.steps = []
            result.shot_spec = None
            result.aesthetic_desc = "当前设备暂不稳定支持前后移动引导"
            return result

        if technique_id == "diagonal_composition" and has_roll:
            result.is_applicable = False
            result.image_base64 = None
            result.steps = []
            result.shot_spec = None
            result.aesthetic_desc = "对角线构图不能靠歪斜画面强行实现"
            return result

        return result
    
    async def _call_single_technique(
        self,
        image_bytes: bytes,
        technique_id: str,
    ) -> SingleTechniqueResult:
        """
        异步调用单个构图技术的 Gemini API
        
        使用 asyncio.to_thread 包装同步 SDK 调用
        每个任务独立加载图像，避免竞态条件
        """
        technique_config = TECHNIQUE_CONFIGS[technique_id]
        start_time = datetime.now()
        start_ts = time.perf_counter()
        
        result = SingleTechniqueResult(
            technique_id=technique_id,
            technique_name=technique_config.name,
            start_time=start_time.isoformat(),
            end_time="",
            response_time_ms=0,
            success=False
        )
        
        try:
            # 每个任务独立加载图像
            image = Image.open(BytesIO(image_bytes))
            if image.mode == "RGBA":
                image = image.convert("RGB")
            
            contents = [technique_config.user_prompt, image]
            config = types.GenerateContentConfig(
                response_modalities=["Text", "Image"],
                system_instruction=SYSTEM_INSTRUCTION,
                temperature=MODEL_TEMPERATURE,
                top_k=MODEL_TOP_K,
                top_p=MODEL_TOP_P
                # maxOutputTokens=MODEL_MAX_TOKENS,
                # image_config=types.ImageConfig(
                # image_size=IMAGE_SIZE
                # )
            )
            
            # 使用 asyncio.to_thread 将同步调用转为异步
            response = await asyncio.to_thread(
                self.client.models.generate_content,
                model=MODEL_NAME,
                contents=contents,
                config=config
            )
            
           


            
            end_ts = time.perf_counter()
            end_time = datetime.now()
            
            result.end_time = end_time.isoformat()
            result.response_time_ms = (end_ts - start_ts) * 1000
            result.success = True
            
            # 解析响应
            parsed_json = None
            if hasattr(response, "candidates") and response.candidates:
                for part in response.candidates[0].content.parts:
                    # 文本部分 - 提取 JSON
                    if hasattr(part, "text") and part.text:
                        if parsed_json is None:
                            parsed_json = self._parse_json_from_text(part.text)
                    
                    # 图片部分 - 转为 Base64
                    elif hasattr(part, "inline_data") and part.inline_data:
                        result.image_base64 = self._image_to_base64(
                            part.inline_data.data,
                            part.inline_data.mime_type
                        )
            
            # 提取 JSON 中的关键字段
            if parsed_json:
                result.is_applicable = parsed_json.get("is_applicable", False)
                
                composition_data = parsed_json.get("composition_data", {})
                if composition_data:
                    result.aesthetic_desc = (
                        parsed_json.get("aesthetic_analysis")
                        or composition_data.get("core_reasoning")
                        or composition_data.get("aesthetic_desc")
                        or ""
                    )
                    result.steps = composition_data.get("steps", [])
                    result.shot_spec = composition_data.get("shot_spec")

                result = self._sanitize_composition_result(technique_id, result)

        except Exception as e:
            end_ts = time.perf_counter()
            end_time = datetime.now()
            result.end_time = end_time.isoformat()
            result.response_time_ms = (end_ts - start_ts) * 1000
            result.error_message = str(e)
            print(f"❌ {technique_id} 调用失败: {e}")
        
        return result
    
    async def analyze_composition_parallel(
        self,
        image_bytes: bytes,
    ) -> Tuple[List[CompositionResult], float, int, int]:
        """
        并行分析 5 种构图方案
        
        Args:
            image_bytes: 图片二进制数据
            
        Returns:
            (compositions, total_time_ms, total_techniques, applicable_count)
        """
        start_ts = time.perf_counter()
        
        # 创建所有异步任务
        techniques = list(TECHNIQUE_CONFIGS.keys())
        tasks = [
            asyncio.create_task(
                self._call_single_technique(image_bytes, technique_id)
            )
            for technique_id in techniques
        ]
        
        # 并发执行所有请求
        results = await asyncio.gather(*tasks, return_exceptions=True)
        
        end_ts = time.perf_counter()
        total_time_ms = (end_ts - start_ts) * 1000
        
        # 处理结果，只保留 is_applicable=True 的方案
        compositions: List[CompositionResult] = []
        successful_count = 0
        applicable_count = 0
        
        for r in results:
            if isinstance(r, Exception):
                print(f"⚠️ 任务异常: {r}")
                continue
            
            if r.success:
                successful_count += 1
                has_image = "✓" if r.image_base64 else "✗"
                print(f"✓ {r.technique_id}: {r.response_time_ms:.0f}ms, applicable={r.is_applicable}, image={has_image}")
                
                if r.is_applicable and r.image_base64:
                    applicable_count += 1
                    
                    # 转换 steps
                    steps = [
                        CompositionStep(
                            step_order=step.get("step_order", i + 1),
                            action_type=step.get("action_type", "Shift"),
                            direction=step.get("direction", ""),
                            guide_text=step.get("guide_text", "")
                        )
                        for i, step in enumerate(r.steps)
                    ]
                    shot_spec = None
                    if r.shot_spec:
                        try:
                            shot_spec = ShotSpec(**r.shot_spec)
                        except Exception as error:
                            print(f"⚠️ {r.technique_id} shot_spec 解析失败: {error}")
                    
                    compositions.append(CompositionResult(
                        technique=r.technique_id,
                        technique_name=r.technique_name,
                        aesthetic_desc=r.aesthetic_desc,
                        steps=steps,
                        shot_spec=shot_spec,
                        image_base64=r.image_base64,
                        response_time_ms=r.response_time_ms
                    ))
            else:
                print(f"✗ {r.technique_id}: {r.error_message}")
        
        print(f"\n📊 统计: 成功 {successful_count}/{len(techniques)}, 适用 {applicable_count}/{len(techniques)}")
        print(f"⏱️  并行总耗时: {total_time_ms:.0f}ms")
        
        return compositions, total_time_ms, len(techniques), applicable_count


# 全局服务实例（延迟初始化）
_gemini_service: Optional[GeminiService] = None


def get_gemini_service() -> GeminiService:
    """获取 Gemini 服务单例"""
    global _gemini_service
    if _gemini_service is None:
        _gemini_service = GeminiService()
    return _gemini_service
