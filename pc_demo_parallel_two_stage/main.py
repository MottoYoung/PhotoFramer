"""
辅助拍摄 App 后端服务 (Two-Stage v1.1)
FastAPI 应用入口
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from routers import composition_router, image_router
from config import MODEL_NAME, TECHNIQUE_CONFIGS

# 创建 FastAPI 应用
app = FastAPI(
    title="📷 PhotoFramer API - Two-Stage",
    description="""
## 辅助拍摄 App 后端服务（两阶段架构）

### Stage 1：LLM 构图分析（Qwen3.5-flash）
- `POST /composition_analyze` — 并行分析5种构图，返回完整 JSON
- `POST /composition_analyze_stream` — SSE 流式推送，`prompt_ready` 事件最先到达

### Stage 2：图像生成（Qwen-image-2.0）
- `POST /image_generate` — 接收 `qwen_image_prompt`，并行生成参考图（base64返回）

### 推荐调用流程
```
客户端 → POST /composition/analyze/stream  (SSE)
            ↓ 收到 prompt_ready 事件
         → POST /image/generate             (立即触发，无需等待 Stage 1 全部完成)
```
""",
    version="1.1.0",
    docs_url="/docs",
    redoc_url="/redoc",
)

# 配置 CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 注册路由
app.include_router(composition_router)
app.include_router(image_router)


@app.get("/", tags=["Root"])
async def root():
    return {
        "service": "PhotoFramer API - Two-Stage",
        "version": "1.1.0",
        "stage1_model": MODEL_NAME,
        "stage2_model": "qwen-image-2.0-2026-03-03",
        "techniques": list(TECHNIQUE_CONFIGS.keys()),
        "docs": "/docs",
        "health": "/health",
        "analyze": "/composition_analyze",
        "analyze_stream": "/composition_analyze_stream",
        "image_generate": "/image_generate",
    }


@app.on_event("startup")
async def startup_event():
    print("=" * 60)
    print("🚀 PhotoFramer API v1.1 (Two-Stage) 启动")
    print(f"   Stage 1 模型: {MODEL_NAME}")
    print(f"   Stage 2 模型: qwen-image-2.0-2026-03-03")
    print(f"   构图技术: {', '.join(TECHNIQUE_CONFIGS.keys())}")
    print("   文档: http://localhost:8000/docs")
    print("=" * 60)


@app.on_event("shutdown")
async def shutdown_event():
    print("👋 PhotoFramer API Two-Stage 服务关闭")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8000,
        reload=True,
    )
