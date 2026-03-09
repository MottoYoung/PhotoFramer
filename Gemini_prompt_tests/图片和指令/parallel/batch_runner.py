"""
批量测试运行器

使用 Gemini Batch API 批量测试 5 种构图方案。
适用于成本敏感的质量筛选阶段。
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
    ParallelPromptSet, ModelConfig, CompositionRequest,
    generate_composition_requests, load_config
)


# ==================== 配置 ==================== #
GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY")
# GEMINI_BASE_URL = os.environ.get("GEMINI_BASE_URL", "https://api.newapi.pro/v1beta")

MODEL_NAME = "gemini-2.5-flash-image"

BASE_DIR = Path(__file__).parent
RESULTS_DIR = BASE_DIR / "results"


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
    """初始化 Gemini 客户端"""
    if not GEMINI_API_KEY:
        raise ValueError("请设置 GEMINI_API_KEY 环境变量")
    return genai.Client(api_key=GEMINI_API_KEY)


def prepare_batch_requests(
    requests: list[CompositionRequest],
    input_image_path: str | Path,
) -> list[dict]:
    """
    为 Batch API 准备请求列表
    
    Args:
        requests: 构图请求列表
        input_image_path: 输入图像路径
        
    Returns:
        JSONL 格式的请求列表
    """
    with open(input_image_path, "rb") as f:
        image_data = base64.b64encode(f.read()).decode("utf-8")
    
    suffix = Path(input_image_path).suffix.lower()
    mime_map = {".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".png": "image/png"}
    mime_type = mime_map.get(suffix, "image/jpeg")
    
    batch_requests = []
    for req in requests:
        batch_req = {
            "key": req.technique_id,
            "request": {
                "contents": [
                    {
                        "parts": [
                            {"text": req.user_prompt},
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
                    "parts": [{"text": req.system_instruction}]
                },
                "generationConfig": {
                    "temperature": req.model_config.temperature,
                    "topK": req.model_config.top_k,
                    "topP": req.model_config.top_p,
                    "responseModalities": ["TEXT", "IMAGE"]
                }
            }
        }
        batch_requests.append(batch_req)
    
    return batch_requests


def create_batch_job(
    client: genai.Client,
    requests: list[CompositionRequest],
    input_image_path: str | Path,
    job_name: str | None = None,
) -> BatchJobInfo:
    """
    创建批处理作业
    """
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    display_name = job_name or f"parallel_batch_{timestamp}"
    
    print(f"📦 准备批处理请求...")
    batch_requests = prepare_batch_requests(requests, input_image_path)
    print(f"   共 {len(batch_requests)} 种构图技术")
    
    # 创建 JSONL 文件
    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    jsonl_path = RESULTS_DIR / f"{display_name}.jsonl"
    
    with open(jsonl_path, "w", encoding="utf-8") as f:
        for req in batch_requests:
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
        created_at=datetime.now()
    )


def wait_for_job(
    client: genai.Client,
    job_info: BatchJobInfo,
    poll_interval: int = 30,
    timeout: int = 86400
) -> BatchJobInfo:
    """等待批处理作业完成"""
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
    """下载并解析批处理结果"""
    if not job_info.result_file:
        raise ValueError("作业无结果文件")
    
    output_dir = Path(output_dir) if output_dir else RESULTS_DIR / job_info.display_name
    output_dir.mkdir(parents=True, exist_ok=True)
    
    print(f"\n📥 下载结果文件...")
    file_content = client.files.download(file=job_info.result_file)
    content_str = file_content.decode("utf-8")
    
    results = []
    for i, line in enumerate(content_str.splitlines()):
        if not line.strip():
            continue
        
        parsed = json.loads(line)
        technique_id = parsed.get("key", f"unknown_{i}")
        case_dir = output_dir / technique_id
        case_dir.mkdir(exist_ok=True)
        
        result = {
            "technique_id": technique_id,
            "success": False,
            "is_applicable": None,
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
                        txt_path = case_dir / f"text_{j}.txt"
                        with open(txt_path, "w", encoding="utf-8") as f:
                            f.write(part["text"])
                        
                        # 尝试解析 is_applicable
                        try:
                            import re
                            json_match = re.search(r'\{.*\}', part["text"], re.DOTALL)
                            if json_match:
                                parsed_json = json.loads(json_match.group(0))
                                if result["is_applicable"] is None:
                                    result["is_applicable"] = parsed_json.get("is_applicable")
                        except:
                            pass
                    
                    elif "inlineData" in part:
                        img_data = base64.b64decode(part["inlineData"]["data"])
                        img_path = case_dir / f"image_{j}.png"
                        with open(img_path, "wb") as f:
                            f.write(img_data)
                        result["images"].append(str(img_path))
        
        results.append(result)
        
        with open(case_dir / "result.json", "w", encoding="utf-8") as f:
            json.dump(result, f, ensure_ascii=False, indent=2)
    
    # 保存汇总
    summary = {
        "job_name": job_info.job_name,
        "display_name": job_info.display_name,
        "created_at": job_info.created_at.isoformat(),
        "completed_at": job_info.completed_at.isoformat() if job_info.completed_at else None,
        "status": job_info.status,
        "total_techniques": len(results),
        "successful_techniques": sum(1 for r in results if r["success"]),
        "applicable_techniques": sum(1 for r in results if r["is_applicable"] is True),
        "results": results
    }
    
    summary_path = output_dir / "summary.json"
    with open(summary_path, "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    
    print(f"✅ 结果已保存到: {output_dir}")
    print(f"   成功: {summary['successful_techniques']}, 适用: {summary['applicable_techniques']}")
    
    return output_dir


def run_batch_assessment(
    input_image_path: str | Path,
    wait: bool = True,
    prompt_id: str | None = None
) -> BatchJobInfo:
    """
    运行批量测评
    
    Args:
        input_image_path: 输入图像路径
        wait: 是否等待作业完成
        prompt_id: 指定提示词版本ID（默认第一个）
    """
    client = init_client()
    requests = generate_composition_requests(prompt_id=prompt_id)
    
    print(f"\n{'='*60}")
    print(f"🎯 批量测评 - 5 种构图方案 (Batch API)")
    print(f"{'='*60}")
    print(f"   构图技术数: {len(requests)}")
    print(f"   输入图像: {input_image_path}")
    
    job_info = create_batch_job(client, requests, input_image_path)
    
    if wait:
        job_info = wait_for_job(client, job_info)
        
        if job_info.status == "JOB_STATE_SUCCEEDED":
            download_results(client, job_info)
    
    return job_info


if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description="批量测评 5 种构图方案 (Batch API)")
    parser.add_argument("--image", "-i", required=True, help="输入图像路径")
    parser.add_argument("--no-wait", action="store_true", help="不等待作业完成")
    
    args = parser.parse_args()
    
    job_info = run_batch_assessment(
        input_image_path=args.image,
        wait=not args.no_wait
    )
    
    print(f"\n作业信息: {job_info}")
