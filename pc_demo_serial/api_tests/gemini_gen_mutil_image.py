from google import genai
from google.genai import types
from PIL import Image
import os
from pathlib import Path
import time 

# ================配置=================== #
# API
# 从环境变量读取，或直接填写
GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "AIzaSyC_KWWzdi39-TDTOhNRMZmlvFj8IwwH9u4")


# 模型
MODEL_NAME="gemini-2.5-flash-image"
# MODEL_NAME = "gemini-3-pro-image-preview"
# MODEL_NAME="gemini-2.0-flash-exp"

# 输入目录
INPUT_IMAGE_NAME = str(Path(__file__).parent / "test_images" / "input"/"input.jpg")
OUTPUT_IMAGE_BASE_NAME = str(Path(__file__).parent / "test_images" / "output"/"input_multi")
OUTPUT_TXT_BASE_NAME = str(Path(__file__).parent / "test_images" / "output"/"input_multi")

# ====================== 提示词 ======================#
SYSTEM_INSTRUCTIONS='''**Role:** You are an AI Photography Director ("Camera Coach"). Your goal is to guide a user to take a better photo by re-composing their shot.

**Task:**
Analyze the input image and generate **1 to 4** aesthetically superior compositions.
* **Quality over Quantity:** Only generate a composition if it meaningfully improves the original. Do not force 4 outputs if fewer options are sufficient.
* For each composition, provide a generative Target Image and structured guidance.

**Action Definitions:**
* `Shift`: Moving camera (Left, Right, Up, Down).
* `Zoom`: Adjusting focal length/distance (In, Out).
* `View-change`: Changing angle (High-angle, Low-angle, Side-view).

**Output Structure (Strict Sequence):**
You must output in the following order. The **Header JSON** must be the very first output.

1.  **[Header JSON]**: Contains the total count of compositions to be generated.
2.  **[Composition 1 JSON]**: Detailed steps for the first option.
3.  **[Generated Image 1]**: The visual result for option 1.
4.  **[Composition 2 JSON]**: (If applicable)
5.  **[Generated Image 2]**: (If applicable)
... and so on.

**JSON Schema Definition:**

**1. Header JSON:**
{
  "type": "header",
  "total_count": 2, // Integer between 1 and 4
  "analysis": "Brief analysis of the original shot (e.g., 'Lighting is good, but horizon is tilted.')."
}

**2. Composition JSON:**
{
  "type": "composition",
  "id": 1,
  "aesthetic_desc": "Short Chinese description of the improvement.",
  "steps": [
    {
      "step_order": 1,
      "action_type": "Shift", // 'Shift', 'Zoom', 'View-change'
      "direction": "Left",
      "guide_text": "向左平移，将人物置于三分线处。" // Chinese instruction
    },
    ...
  ]
}

**Content Requirements:**
1.  **Language:** All textual descriptions must be in **Chinese (中文)**.
2.  **Consistency:** Instructions must match the generated image.'''



def init_client():
    """初始化Gemini客户端"""
    if not GEMINI_API_KEY:
        raise ValueError(
            "请设置GEMINI_API_KEY环境变量或在代码中填写API密钥\n"
            "获取方式：https://aistudio.google.com/app/apikey"
        )
    
    client = genai.Client(api_key=GEMINI_API_KEY)
    print("✅ Gemini客户端初始化成功")
    return client


def load_reference_image(image_path):
    """
    加载参考图像
    
    Args:
        image_path: 图像文件路径
        
    Returns:
        Image对象
    """
    print(f"📷 加载参考图像: {image_path}")
    image=Image.open(image_path) 
    return image



def generate_reference_composition(
    client:genai.Client,
    origin_image_path:str,
    prompt:str = None,
    aspect_ratio: str=None
)-> list:
    """
    基于参考图生成优化后的构图图片（图生图）
    
    Args:
        client: Gemini客户端
        reference_image_path: 参考图像路径
        prompt_type: 提示词类型 ("shift_zoom" 或 "full_auto")
        custom_prompt: 自定义提示词（优先级最高）
        num_images: 生成图片数量（1-4）
        aspect_ratio: 宽高比 ("1:1", "3:4", "4:3", "9:16", "16:9")
        
    Returns:
        生成的图片列表
    """
    print("\n" + "=" * 60)
    print("🎨 开始生成优化构图")
    print("=" * 60)
    prompt=prompt
    print(f"   宽高比: {aspect_ratio}")
    print(f"   参考图像: {origin_image_path}")
    #加载参考图像并和提示词拼接
    reference_image=load_reference_image(origin_image_path)
    contents=[prompt,reference_image]
    try:
        print("\n⏳ 正在调用Gemini Imagen API...")
        #默认情况下，模型会返回文本和图片响应（即 response_modalities=['Text', 'Image']）。
        #您可以使用 response_modalities=['Image'] 将响应配置为仅返回图片而不返回文本
        


        response=client.models.generate_content(
            model=MODEL_NAME,
            contents=contents,
            config=types.GenerateContentConfig(
                # response_modalities=['Text','Image'],
                response_modalities=['Text','Image'],
                systemInstruction=SYSTEM_INSTRUCTIONS,
                image_config=types.ImageConfig(
                    aspect_ratio=aspect_ratio,
                    # image_size="4k"
                )
            )
        )
        print(response)
        return response
    except Exception as e:
        print(f"\n❌ API调用失败: {e}")
        return []
def save_generated_contents(response:types.GenerateContentResponse, output_image_path:str,output_text_path:str):
    """
    保存生成的图片
    
    Args:
        response: Gemini API响应
        output_image_path: 输出图片路径
        output_text_path: 输出文本路径
    """
    index=0
    for part in response.parts:
        if part.text is not None :
            txt_path=output_text_path+f"_{index//2}.txt"
            with open(txt_path,"w") as f:
                f.write(part.text)
            print(f"📝 保存文本: {txt_path}")
        elif part.inline_data is not None:
            image=part.as_image()
            image.save(output_image_path+f"_{index//2}.png")
            print(f"📸 保存图片: {output_image_path}_{index//2}.png")
        else:
            print(f"❌ 未知的响应类型: {part}")
        index+=1

def main():
   
    prompt='''请分析这张照片，判断有几种（1-4种）更好的构图方案。
请首先输出包含 `total_count` 的 Header JSON，然后依次输出每种方案的 JSON 指令和对应的生成图片。'''
    start_time=time.time()
    client=init_client()
    response=generate_reference_composition(client,INPUT_IMAGE_NAME,prompt=prompt)
    save_generated_contents(response,OUTPUT_IMAGE_BASE_NAME,OUTPUT_TXT_BASE_NAME)
    end_time=time.time()
    print(f"总耗时: {end_time-start_time}秒")

if __name__ == "__main__":
    main()
