"""
Gemini API 服务封装 (v3.1 统一格式)
核心逻辑：调用 Gemini API 生成构图建议
"""
import re
import json
import base64
import time
from typing import Tuple, List, Optional, Dict, Any

from google import genai
from google.genai import types
from PIL import Image

from config import (
    GEMINI_API_KEY,
    MODEL_NAME,
    SYSTEM_INSTRUCTIONS,
    DEFAULT_PROMPT,
    MODEL_TEMPERATURE,
    MODEL_TOP_K,
    MODEL_TOP_P,
)
from schemas import CompositionStep, CompositionResult


class GeminiService:
    """Gemini API 服务类 (v3.1 统一格式)"""
    
    def __init__(self):
        """初始化 Gemini 客户端"""
        if not GEMINI_API_KEY:
            raise ValueError(
                "请设置 GEMINI_API_KEY 环境变量或在 config.py 中填写 API 密钥\n"
                "获取方式：https://aistudio.google.com/app/apikey"
            )
        self.client = genai.Client(api_key=GEMINI_API_KEY)
        print("✅ Gemini 客户端初始化成功")
    
    def analyze_composition(
        self,
        image: Image.Image,
        prompt: Optional[str] = None,
    ) -> Tuple[List[CompositionResult], float, int]:
        """
        分析图片并生成构图建议 (v3.1 统一格式)
        
        Args:
            image: PIL Image 对象
            prompt: 自定义提示词（可选，默认使用 DEFAULT_PROMPT）
            
        Returns:
            (compositions, total_time_ms, applicable_count)
        """
        start_time = time.perf_counter()
        
        # 使用提供的 prompt 或默认 prompt
        final_prompt = prompt if prompt else DEFAULT_PROMPT
        
        print(f"📝 使用提示词: {final_prompt[:50]}...")
        
        # 构建请求内容
        contents = [final_prompt, image]
        
        try:
            print("⏳ 正在调用 Gemini API...")
            
            response = self.client.models.generate_content(
                model=MODEL_NAME,
                contents=contents,
                config=types.GenerateContentConfig(
                    response_modalities=['Text', 'Image'],
                    system_instruction=SYSTEM_INSTRUCTIONS,
                    temperature=MODEL_TEMPERATURE,
                    top_k=MODEL_TOP_K,
                    top_p=MODEL_TOP_P,
                )
            )
            
            end_time = time.perf_counter()
            total_time_ms = (end_time - start_time) * 1000
            
            print("✅ API 调用成功，开始解析响应...")
            compositions = self._parse_response(response)
            applicable_count = len(compositions)
            
            return compositions, total_time_ms, applicable_count
            
        except Exception as e:
            print(f"❌ API 调用失败: {e}")
            raise
    
    def _parse_response(
        self, 
        response: types.GenerateContentResponse
    ) -> List[CompositionResult]:
        """
        解析 Gemini 混合响应（v3.1 统一格式）
        
        Args:
            response: Gemini API 响应对象
            
        Returns:
            compositions: 解析后的构图方案列表
        """
        compositions: List[Dict[str, Any]] = []
        current_composition: Optional[Dict[str, Any]] = None
        
        # 遍历所有 parts
        for part in response.candidates[0].content.parts:
            
            # 情况 1: 文本 Part（包含 JSON）
            if hasattr(part, 'text') and part.text:
                parsed_json = self._parse_json_from_text(part.text)
                
                if parsed_json:
                    is_applicable = parsed_json.get("is_applicable", False)
                    
                    if is_applicable:
                        technique = parsed_json.get("technique", f"composition_{len(compositions)+1}")
                        technique_name = parsed_json.get("technique_name", "未知构图")
                        
                        composition_data = parsed_json.get("composition_data", {})
                        aesthetic_desc = composition_data.get("aesthetic_desc", "")
                        
                        # 解析步骤
                        steps = [
                            CompositionStep(
                                step_order=step.get("step_order", i + 1),
                                action_type=step.get("action_type", "Shift"),
                                direction=step.get("direction", ""),
                                guide_text=step.get("guide_text", "")
                            )
                            for i, step in enumerate(composition_data.get("steps", []))
                        ]
                        
                        current_composition = {
                            "technique": technique,
                            "technique_name": technique_name,
                            "aesthetic_desc": aesthetic_desc,
                            "steps": steps,
                            "image_base64": None,
                        }
                        compositions.append(current_composition)
                        print(f"✅ 解析 {technique_name}: {aesthetic_desc[:30]}...")
            
            # 情况 2: 图片 Part
            elif hasattr(part, 'inline_data') and part.inline_data:
                print(f"🖼️ 接收到图片数据 (大小: {len(part.inline_data.data)} bytes)")
                
                # 将图片转为 Base64
                image_base64 = self._image_to_base64(part.inline_data.data, part.inline_data.mime_type)
                
                # 绑定到最近的 composition
                if current_composition is not None:
                    current_composition["image_base64"] = image_base64
                else:
                    print("⚠️ 警告：收到图片但没有对应的指令 JSON")
        
        # 转换为 Pydantic 模型
        composition_results = [
            CompositionResult(
                technique=comp["technique"],
                technique_name=comp["technique_name"],
                aesthetic_desc=comp["aesthetic_desc"],
                steps=comp["steps"],
                image_base64=comp["image_base64"]
            )
            for comp in compositions
        ]
        
        return composition_results
    
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


# 全局服务实例（延迟初始化）
_gemini_service: Optional[GeminiService] = None


def get_gemini_service() -> GeminiService:
    """获取 Gemini 服务单例"""
    global _gemini_service
    if _gemini_service is None:
        _gemini_service = GeminiService()
    return _gemini_service
