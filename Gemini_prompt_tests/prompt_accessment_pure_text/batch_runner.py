"""
批量测试运行器 - 纯文本模态版本

使用 Gemini Batch API 以 50% 成本批量测试所有提示词+模型配置组合。
只返回文本（JSON指令），不生成图片。
"""

import json
import time
import base64
import os
from pathlib import Path
from datetime import datetime
from dataclasses import dataclass, field

from google import genai
from google.genai import types
from PIL import Image

from prompts_config import (
    PromptConfig, ModelConfig, TestCase,
    generate_test_matrix, PROMPT_VERSIONS, MODEL_CONFIGS
)


# ==================== 配置 ==================== #
# 【原Gemini官方API配置】
# GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "")

# 【中转API配置】
# 使用 Bearer Token 鉴权格式: Authorization: Bearer sk-xxxxxx
GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "sk-MgVuquDqVte3C4PlsXoKm7wxdhcnYV0IsWYtTX5NEJbdEGQ2")  # 中转API的key，格式如 sk-xxxxxx
GEMINI_BASE_URL = os.environ.get("GEMINI_BASE_URL", "https://yinli.one/v1beta/models/gemini-2.5-flash-image:generateContent")  # 中转API地址

# 纯文本模式使用更快的flash模型
MODEL_NAME = "gemini-2.0-flash"

# 目录配置
BASE_DIR = Path(__file__).parent
RESULTS_DIR = BASE_DIR / "results"
INPUT_IMAGES_DIR = BASE_DIR / "test_images" / "input"


@dataclass
class BatchJobInfo:
    """批处理作业信息"""
    job_name: str
    display_name: str
    created_at: datetime
    status: str = "PENDING"
    completed_at: datetime | None = None
    result_file: str | None = None
    error: str | None = None


def init_client() -> genai.Client:
    """
    初始化 Gemini 客户端
    
    支持两种模式：
    1. 官方API：仅设置 GEMINI_API_KEY，不设置 GEMINI_BASE_URL
    2. 中转API：同时设置 GEMINI_API_KEY 和 GEMINI_BASE_URL
    
    中转API使用 Bearer Token 鉴权格式: Authorization: Bearer sk-xxxxxx
    """
    if not GEMINI_API_KEY:
        raise ValueError(
            "请设置 GEMINI_API_KEY 环境变量\n"
            "中转API格式: sk-xxxxxx\n"
            "官方API获取: https://aistudio.google.com/app/apikey"
        )
    
    # 【原Gemini官方API客户端初始化 - 已注释】
    # return genai.Client(api_key=GEMINI_API_KEY)
    
    # 【中转API客户端初始化】
    # 使用 http_options 配置自定义 base URL 和鉴权头
    if GEMINI_BASE_URL:
        # 中转API模式：使用自定义 base URL
        client = genai.Client(
            api_key=GEMINI_API_KEY,
            http_options={
                "base_url": GEMINI_BASE_URL,
                # Bearer Token 鉴权由 SDK 自动处理，api_key 会被转换为 Authorization: Bearer {api_key}
            }
        )
        print(f"📡 使用中转API: {GEMINI_BASE_URL}")
    else:
        # 官方API模式
        client = genai.Client(api_key=GEMINI_API_KEY)
        print("📡 使用Gemini官方API")
    
    return client


def prepare_batch_requests(
    test_cases: list[TestCase],
    input_image_path: str | Path,
) -> list[dict]:
    """
    为 Batch API 准备请求列表
    
    Args:
        test_cases: 测试用例列表
        input_image_path: 输入图像路径
        
    Returns:
        JSONL 格式的请求列表
    """
    # 读取图像并编码为 base64
    with open(input_image_path, "rb") as f:
        image_data = base64.b64encode(f.read()).decode("utf-8")
    
    # 获取图像 MIME 类型
    suffix = Path(input_image_path).suffix.lower()
    mime_map = {".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".png": "image/png"}
    mime_type = mime_map.get(suffix, "image/jpeg")
    
    requests = []
    for case in test_cases:
        request = {
            "key": case.case_id,
            "request": {
                "contents": [
                    {
                        "parts": [
                            {"text": case.prompt.user_prompt},
                            {
                                "inlineData": {
                                    "mimeType": mime_type,
                                    "data": image_data
                                }
                            }
                        ],
                        "role": "user"
                    }
                ],
                "systemInstruction": {
                    "parts": [{"text": case.prompt.system_instruction}]
                },
                "generationConfig": {
                    "temperature": case.model_config.temperature,
                    "topK": case.model_config.top_k,
                    "topP": case.model_config.top_p,
                    "responseModalities": ["TEXT"]  # 纯文本模态
                }
            }
        }
        requests.append(request)
    
    return requests


def create_batch_job(
    client: genai.Client,
    test_cases: list[TestCase],
    input_image_path: str | Path,
    job_name: str | None = None,
) -> BatchJobInfo:
    """
    创建批处理作业
    
    Args:
        client: Gemini 客户端
        test_cases: 测试用例列表
        input_image_path: 输入图像路径
        job_name: 作业名称（可选）
        
    Returns:
        BatchJobInfo 对象
    """
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    display_name = job_name or f"prompt_assessment_{timestamp}"
    
    print(f"📦 准备批处理请求...")
    requests = prepare_batch_requests(test_cases, input_image_path)
    print(f"   共 {len(requests)} 个测试用例")
    
    # 创建 JSONL 文件
    jsonl_path = RESULTS_DIR / f"{display_name}.jsonl"
    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    
    with open(jsonl_path, "w", encoding="utf-8") as f:
        for req in requests:
            f.write(json.dumps(req, ensure_ascii=False) + "\n")
    
    print(f"📤 上传请求文件: {jsonl_path}")
    uploaded_file = client.files.upload(
        file=str(jsonl_path),
        config=types.UploadFileConfig(
            display_name=display_name,
            mime_type="application/jsonl"
        )
    )
    print(f"   文件已上传: {uploaded_file.name}")
    
    print(f"🚀 创建批处理作业...")
    batch_job = client.batches.create(
        model=MODEL_NAME,
        src=uploaded_file.name,
        config={"display_name": display_name}
    )
    
    print(f"✅ 批处理作业已创建: {batch_job.name}")
    
    return BatchJobInfo(
        job_name=batch_job.name,
        display_name=display_name,
        created_at=datetime.now(),
        status="PENDING"
    )


def wait_for_job(
    client: genai.Client,
    job_info: BatchJobInfo,
    poll_interval: int = 30,
    timeout: int = 86400  # 24小时
) -> BatchJobInfo:
    """
    等待批处理作业完成
    
    Args:
        client: Gemini 客户端
        job_info: 作业信息
        poll_interval: 轮询间隔（秒）
        timeout: 超时时间（秒）
        
    Returns:
        更新后的 BatchJobInfo
    """
    completed_states = {
        "JOB_STATE_SUCCEEDED",
        "JOB_STATE_FAILED", 
        "JOB_STATE_CANCELLED",
        "JOB_STATE_EXPIRED"
    }
    
    start_time = time.time()
    print(f"\n⏳ 等待作业完成: {job_info.job_name}")
    
    while True:
        batch_job = client.batches.get(name=job_info.job_name)
        current_state = batch_job.state.name
        
        elapsed = int(time.time() - start_time)
        print(f"   [{elapsed}s] 状态: {current_state}")
        
        if current_state in completed_states:
            job_info.status = current_state
            job_info.completed_at = datetime.now()
            
            if current_state == "JOB_STATE_SUCCEEDED":
                job_info.result_file = batch_job.dest.file_name
                print(f"✅ 作业完成，结果文件: {job_info.result_file}")
            else:
                job_info.error = str(getattr(batch_job, "error", "Unknown error"))
                print(f"❌ 作业失败: {job_info.error}")
            
            return job_info
        
        if time.time() - start_time > timeout:
            job_info.status = "TIMEOUT"
            job_info.error = f"作业超时（>{timeout}秒）"
            return job_info
        
        time.sleep(poll_interval)


def download_results(
    client: genai.Client,
    job_info: BatchJobInfo,
    output_dir: str | Path | None = None
) -> Path:
    """
    下载并解析批处理结果
    
    Args:
        client: Gemini 客户端
        job_info: 作业信息
        output_dir: 输出目录
        
    Returns:
        结果目录路径
    """
    if not job_info.result_file:
        raise ValueError("作业无结果文件")
    
    output_dir = Path(output_dir) if output_dir else RESULTS_DIR / job_info.display_name
    output_dir.mkdir(parents=True, exist_ok=True)
    
    print(f"\n📥 下载结果文件...")
    file_content = client.files.download(file=job_info.result_file)
    content_str = file_content.decode("utf-8")
    
    # 解析每行结果
    results = []
    for i, line in enumerate(content_str.splitlines()):
        if not line.strip():
            continue
        
        parsed = json.loads(line)
        case_id = parsed.get("key", f"unknown_{i}")
        case_dir = output_dir / case_id
        case_dir.mkdir(exist_ok=True)
        
        result = {
            "case_id": case_id,
            "success": False,
            "texts": [],
            "images": [],
            "error": None
        }
        
        if "error" in parsed:
            result["error"] = parsed["error"]
        elif "response" in parsed and parsed["response"]:
            result["success"] = True
            response = parsed["response"]
            
            if "candidates" in response and response["candidates"]:
                parts = response["candidates"][0].get("content", {}).get("parts", [])
                
                for j, part in enumerate(parts):
                    if "text" in part:
                        result["texts"].append(part["text"])
                        # 保存文本
                        txt_path = case_dir / f"text_{j}.txt"
                        with open(txt_path, "w", encoding="utf-8") as f:
                            f.write(part["text"])
                    
                    elif "inlineData" in part:
                        img_data = base64.b64decode(part["inlineData"]["data"])
                        img_path = case_dir / f"image_{j}.png"
                        with open(img_path, "wb") as f:
                            f.write(img_data)
                        result["images"].append(str(img_path))
        
        results.append(result)
        
        # 保存单个结果的元数据
        with open(case_dir / "result.json", "w", encoding="utf-8") as f:
            json.dump(result, f, ensure_ascii=False, indent=2)
    
    # 保存汇总
    summary = {
        "job_name": job_info.job_name,
        "display_name": job_info.display_name,
        "created_at": job_info.created_at.isoformat(),
        "completed_at": job_info.completed_at.isoformat() if job_info.completed_at else None,
        "status": job_info.status,
        "total_cases": len(results),
        "successful_cases": sum(1 for r in results if r["success"]),
        "failed_cases": sum(1 for r in results if not r["success"]),
        "results": results
    }
    
    summary_path = output_dir / "summary.json"
    with open(summary_path, "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    
    print(f"✅ 结果已保存到: {output_dir}")
    print(f"   成功: {summary['successful_cases']}, 失败: {summary['failed_cases']}")
    
    return output_dir


def run_batch_assessment(
    input_image_path: str | Path,
    prompts: list[PromptConfig] | None = None,
    model_configs: list[ModelConfig] | None = None,
    wait: bool = True,
    prompt_id: str | None = None
) -> BatchJobInfo:
    """
    运行批量测评（阶段1）
    
    Args:
        input_image_path: 输入图像路径
        prompts: 提示词列表（默认使用预定义）
        model_configs: 模型配置列表（默认使用预定义）
        wait: 是否等待作业完成
        prompt_id: 指定提示词版本ID（默认全部）
        
    Returns:
        BatchJobInfo 对象
    """
    client = init_client()
    
    # 生成测试用例
    test_cases = list(generate_test_matrix(prompts, model_configs))
    
    # 按 prompt_id 过滤
    if prompt_id:
        test_cases = [tc for tc in test_cases if tc.prompt.id == prompt_id]
        if not test_cases:
            raise ValueError(f"未找到提示词版本: {prompt_id}")
    
    print(f"\n{'='*60}")
    print(f"🎯 批量测评 - 纯文本模态")
    print(f"{'='*60}")
    print(f"   测试用例数: {len(test_cases)}")
    print(f"   输入图像: {input_image_path}")
    
    # 创建批处理作业
    job_info = create_batch_job(client, test_cases, input_image_path)
    
    if wait:
        job_info = wait_for_job(client, job_info)
        
        if job_info.status == "JOB_STATE_SUCCEEDED":
            download_results(client, job_info)
    
    return job_info


if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description="批量测评提示词（阶段1：质量筛选）")
    parser.add_argument("--image", "-i", required=True, help="输入图像路径")
    parser.add_argument("--no-wait", action="store_true", help="不等待作业完成")
    
    args = parser.parse_args()
    
    job_info = run_batch_assessment(
        input_image_path=args.image,
        wait=not args.no_wait
    )
    
    print(f"\n作业信息: {job_info}")
