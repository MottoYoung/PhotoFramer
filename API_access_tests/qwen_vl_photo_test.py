# -*- coding: utf-8 -*-
"""
Qwen-VL 多模态模型 构图分析测试
输出可视化引导元素，供Android端绘制
"""
import os
import json
import base64
from pathlib import Path

os.environ["DASHSCOPE_API_KEY"] = "sk-0a1e1f2f2de542d68b1b812f8d07edcb"

from openai import OpenAI

# 初始化客户端
client = OpenAI(
    api_key=os.getenv("DASHSCOPE_API_KEY"),
    base_url="https://dashscope.aliyuncs.com/compatible-mode/v1",
)

# 构图分析专用Prompt - 符合需求文档规范
SYSTEM_PROMPT = """你是一位世界顶级的摄影导师，专注于实时取景构图指导。

**核心任务**：分析用户的实时取景画面，识别主体，评估构图，并输出可视化引导元素供手机屏幕绘制。

**分析流程**：
1. **识别主体**：找出画面中最显著的主体（人物、建筑、物体、风景元素等），确定其边界框
2. **评估构图**：运用摄影法则判断构图质量
   - 三分法：主体是否在三分线交点附近
   - 黄金分割：重要元素是否位于黄金分割点
   - 居中构图：对称元素是否居中
   - 引导线：是否有线条引导视线
   - 地平线：是否水平，位置是否恰当
3. **生成引导**：计算用户应如何调整手机位置/角度来改善构图

**坐标系统**（重要！）：
- 使用归一化坐标，范围 0.0 到 1.0
- 原点 (0,0) 在图像**左上角**
- x 轴向右增长，y 轴向下增长
- 坐标格式：[y1, x1, y2, x2] 即 [top, left, bottom, right]

**输出规则**：
1. 必须且只能返回符合给定 JSON Schema 的 JSON 对象
2. 不要包含 markdown 代码块标记
3. 所有 coords 值必须在 0.0-1.0 范围内
4. feedback 必须简洁（不超过15个中文字符），适合手机屏幕显示"""

ANALYSIS_PROMPT = """分析这张实时取景画面，按以下JSON格式输出构图建议：

{
  "score": <1-10整数，当前构图评分>,
  "feedback": "<简短建议，不超过15字，如'向右平移'或'主体太小'>",
  "elements": [
    {
      "type": "<rect|arrow|line|point>",
      "color": "<green|red|yellow|white>",
      "coords": [<y1>, <x1>, <y2>, <x2>],
      "label": "<可选，图形旁的简短说明>"
    }
  ]
}

**图形类型说明**：
- rect：矩形框，用于标记主体位置。coords = [top, left, bottom, right]
- arrow：箭头，指示移动方向。coords = [start_y, start_x, end_y, end_x]
- line：直线，用于标记地平线或引导线。coords = [start_y, start_x, end_y, end_x]
- point：兴趣点。coords = [y, x, 0, 0]（后两位填0）

**颜色含义**：
- green：正确/建议位置
- yellow：当前主体位置（需要调整）
- red：问题区域（需要避开或修正）
- white：辅助参考线

**示例**（主体偏左需要右移）：
{
  "score": 5,
  "feedback": "向右平移，让主体居中",
  "elements": [
    {"type": "rect", "color": "yellow", "coords": [0.2, 0.05, 0.8, 0.35], "label": "当前位置"},
    {"type": "arrow", "color": "green", "coords": [0.5, 0.35, 0.5, 0.55], "label": "向右移"}
  ]
}

**要求**：
- 只输出JSON，不要任何其他文字
- elements数组最多包含4个元素
- 评分标准：1-3差，4-6中等，7-8良好，9-10优秀

请分析当前取景："""


def analyze_image_from_url(image_url: str):
    """使用URL方式分析图片构图"""
    print(f"\n🔄 正在分析图片构图...")
    print("（等待模型响应，约10-30秒）\n")
    
    try:
        response = client.chat.completions.create(
            model="qwen-vl-max",
            messages=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": ANALYSIS_PROMPT},
                        {"type": "image_url", "image_url": {"url": image_url}}
                    ]
                }
            ],
            temperature=0.3,
            max_tokens=1500,
        )
        
        content = response.choices[0].message.content
        print("✅ 模型返回:")
        print("-" * 50)
        
        # 提取JSON
        import re
        json_match = re.search(r'\{[\s\S]*\}', content)
        if json_match:
            result = json.loads(json_match.group())
            print(json.dumps(result, ensure_ascii=False, indent=2))
            
            # 打印摘要
            print("\n" + "=" * 50)
            print("📊 构图分析摘要:")
            print("=" * 50)
            print(f"  构图评分: {result.get('score', 'N/A')}/10")
            print(f"  建议: {result.get('feedback', 'N/A')}")
            
            elements = result.get('elements', [])
            if elements:
                print(f"\n  📐 可视化引导元素 ({len(elements)}个):")
                for i, elem in enumerate(elements, 1):
                    elem_type = elem.get('type', '?')
                    color = elem.get('color', '?')
                    coords = elem.get('coords', [])
                    label = elem.get('label', '')
                    
                    # 格式化坐标显示
                    coords_str = f"[{', '.join(f'{c:.2f}' for c in coords)}]" if coords else "[]"
                    print(f"    {i}. [{color}] {elem_type}: {coords_str}")
                    if label:
                        print(f"       └─ {label}")
            
            return result
        else:
            print(content)
            print("\n⚠️ 无法解析JSON")
            
    except Exception as e:
        print(f"❌ 调用失败: {e}")
        return None


def analyze_local_image(file_path: str):
    """分析本地图片"""
    print(f"\n📷 读取本地图片: {file_path}")
    
    with open(file_path, "rb") as f:
        image_data = f.read()
    
    media_type = "image/png" if file_path.lower().endswith(".png") else "image/jpeg"
    image_base64 = base64.b64encode(image_data).decode()
    image_url = f"data:{media_type};base64,{image_base64}"
    
    return analyze_image_from_url(image_url)





def main():
    """主函数"""
    print("=" * 60)
    print("PhotoFramer - 构图分析测试")
    print("=" * 60)
    
    # 查找或创建本地测试图片
    test_dir = Path(__file__).parent / "test_images"
    test_dir.mkdir(exist_ok=True)
    
    images = list(test_dir.glob("*.jpg")) + list(test_dir.glob("*.png"))+list(test_dir.glob("*.jpeg"))
    
    if not images:
        print("\n📷 未找到测试图片")
        return 
  
    
    if images:
        for img in images:  # 只测试第一张
            print(f"\n测试图片: {img.name}")
            analyze_local_image(str(img))
    else:
        print("\n⚠️ 请手动添加测试图片到 api_tests/test_images/ 目录")
        print("或安装Pillow以自动生成测试图片: pip install Pillow")


if __name__ == "__main__":
    main()
