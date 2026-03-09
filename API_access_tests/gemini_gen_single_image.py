from google import genai
from google.genai import types
from PIL import Image
import os
from pathlib import Path
import time

# ================配置=================== #
# API
# 从环境变量读取，或直接填写
GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY")


# 模型
MODEL_NAME="gemini-2.5-flash-image"
# MODEL_NAME = "gemini-3-pro-image-preview"
# MODEL_NAME="gemini-2.0-flash-exp"

# 输入目录
INPUT_IMAGE_NAME = Path(__file__).parent / "test_images" / "input"/"person.jpg"
OUTPUT_IMAGE_NAME = Path(__file__).parent / "test_images" / "output"/"person_single.png"
OUTPUT_TXT_NAME = Path(__file__).parent / "test_images" / "output"/"person_single.txt"

# ====================== 提示词 ======================#
#自动shift/zoom提示词
PROMPT_AUTO_SHIFT_ZOOM = [
    "Refine the composition through shift or zoom-in adjustments.",
    "Improve the composition by combining shift or zoom-in operations.",
    "Enhance the image composition with coordinated shift or zoom-in refinement.",
    "Adjust the framing through shift or zoom-in to achieve a better composition.",
    "Make the composition more appealing using shift or zoom-in adjustments.",
]
#全自动提示词
PROMPT_FULL_AUTO = [
    "Capture this scene with better composition.And describe the changes in 20 words or less.",
    "Enhance the composition of this scene.And describe the changes in 20 words or less.",
    "Refine the composition to make this scene more visually pleasing.And describe the changes in 20 words or less.",
    "Adjust the image to improve its composition.And describe the changes in 20 words or less.",
    "Reframe this scene to achieve a more pleasing visual composition.And describe the changes in 20 words or less.",
    "Refine the framing through shift, zoom-in, or view change.And describe the changes in 20 words or less.",
    "Improve the composition through shift, zoom-in, or viewpoint adjustment.And describe the changes in 20 words or less.",
]
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
    prompt_type: str="full_auto",
    custom_prompt:str = None,
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

    #选择提示词
    if custom_prompt:
        prompt=custom_prompt
        print(f"📝 使用自定义提示词")
    elif prompt_type=="shift_zoom":
        prompt=PROMPT_AUTO_SHIFT_ZOOM[0]
        print(f"📝 使用Shift/Zoom提示词")
    else:
        prompt=PROMPT_FULL_AUTO[0]
        print(f"📝 使用全自动提示词")
    print(f"   Prompt: {prompt}")
    print(f"   宽高比: {aspect_ratio}")
    
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
                response_modalities=['Text','Image'],
                image_config=types.ImageConfig(
                    aspect_ratio=aspect_ratio,
                    # image_size="4k"
                )
            )
        )
        return response
    except Exception as e:
        print(f"\n❌ API调用失败: {e}")
        return []
def save_generated_contents(response:types.GenerateContentResponse, output_image_path:Path,output_text_path:Path):
    """
    保存生成的图片
    
    Args:
        response: Gemini API响应
        output_image_path: 输出图片路径
        output_text_path: 输出文本路径
    """
    for part in response.parts:
        if part.text is not None :
            txt_path=output_text_path
            with open(txt_path,"w") as f:
                f.write(part.text)
            print(f"📝 保存文本: {txt_path}")
        elif part.inline_data is not None:
            image=part.as_image()
            image.save(output_image_path)
            print(f"📸 保存图片: {output_image_path}")
        else:
            print(f"❌ 未知的响应类型: {part}")

def main():
    custom_prompt="你是一个顶尖AI构图助手,你擅长分析用户上传的照片并给出你的理想构图以供用户参考,\
        对于你的理想构图,有以下要求\
        1.你可以通过移动,旋转,视角变换等操作来拍摄出你理想的构图;\
        2.你的理想构图你的理想构图可以有1-4张,如果有多张理想构图,你需要把他们拼接在一起;\
        3.拼接的图像之间要留有宽度为5像素,颜色为#00FF00的空隙,其余地方不应添加该颜色的元素;\
        4.要确保理想构图与原图之间的一致性,并且不同理想构图之间要有差异性、避免重复,做到宁缺毋滥,宁缺毋重复;\
        5.每张理想构图要有简要的文字说明,20字以内;\
        6.你应该在一次请求种给出图片和文字说明,二者缺一不可,但需是分离的,即图片上不能有文字说明"
    client=init_client()
    start_time=time.time()
    response=generate_reference_composition(client,INPUT_IMAGE_NAME,prompt_type="full_auto",custom_prompt=custom_prompt)
    save_generated_contents(response,OUTPUT_IMAGE_NAME,OUTPUT_TXT_NAME)
    end_time=time.time()
    print(f"总耗时: {end_time-start_time}秒")
if __name__ == "__main__":
    main()
