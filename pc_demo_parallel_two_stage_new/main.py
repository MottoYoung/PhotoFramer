"""
辅助拍摄 App 后端服务（provider 化三阶段版）
"""
import asyncio

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from config import (
    GEMINI_STAGE1_FORCE_MINIMAL_THINKING,
    GEMINI_STAGE1_THINKING_BUDGET,
    GEMINI_STAGE1_THINKING_LEVEL,
    STAGE0_PROVIDER,
    QWEN_STAGE1_ENABLE_THINKING,
    STAGE1_PROVIDER,
    STAGE2_PROVIDER,
    TECHNIQUE_CONFIGS,
    get_stage0_model_name,
    get_stage1_model_name,
    get_stage2_model_name,
)
from routers import composition_router, image_router
from services.gemini_client_factory import (
    is_official_gemini_api_enabled,
    probe_gemini_proxy_connectivity,
)

app = FastAPI(
    title="PhotoFramer API - Two-Stage New",
    description="""
支持可插拔的三阶段组合：

- Stage 0（软预筛）: `qwen` / `gemini`
- Stage 1（构图分析）: `qwen` / `gemini`
- Stage 2（参考图生成）: `qwen` / `gemini`

推荐通过环境变量切换：

```bash
export STAGE0_PROVIDER=qwen
export STAGE1_PROVIDER=qwen
export STAGE2_PROVIDER=gemini
```
""",
    version="2.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(composition_router)
app.include_router(image_router)


@app.get("/", tags=["Root"])
async def root():
    return {
        "service": "PhotoFramer API - Two-Stage New",
        "version": "2.0.0",
        "stage0_provider": STAGE0_PROVIDER,
        "stage0_model": get_stage0_model_name(),
        "stage1_provider": STAGE1_PROVIDER,
        "stage1_model": get_stage1_model_name(),
        "stage2_provider": STAGE2_PROVIDER,
        "stage2_model": get_stage2_model_name(),
        "techniques": list(TECHNIQUE_CONFIGS.keys()),
        "docs": "/docs",
    }


@app.on_event("startup")
async def startup_event():
    print("=" * 60)
    print("🚀 PhotoFramer API v2.0 (Three-Stage Flow) 启动")
    print(f"   Stage 0: {STAGE0_PROVIDER} / {get_stage0_model_name()}")
    print(f"   Stage 1: {STAGE1_PROVIDER} / {get_stage1_model_name()}")
    print(f"   Stage 2: {STAGE2_PROVIDER} / {get_stage2_model_name()}")
    print(
        "   Stage 1 thinking: "
        f"qwen_enable={QWEN_STAGE1_ENABLE_THINKING}, "
        f"gemini_minimal={GEMINI_STAGE1_FORCE_MINIMAL_THINKING}, "
        f"gemini_budget={GEMINI_STAGE1_THINKING_BUDGET}, "
        f"gemini_level={GEMINI_STAGE1_THINKING_LEVEL}"
    )
    print(f"   构图技术: {', '.join(TECHNIQUE_CONFIGS.keys())}")
    if (
        STAGE0_PROVIDER == "gemini"
        or STAGE1_PROVIDER == "gemini"
        or STAGE2_PROVIDER == "gemini"
    ) and is_official_gemini_api_enabled():
        ok, message = await asyncio.to_thread(probe_gemini_proxy_connectivity)
        prefix = "   ✅ Gemini 代理自检:" if ok else "   ❌ Gemini 代理自检:"
        print(f"{prefix} {message}")
    print("   文档: http://localhost:8100/docs")
    print("=" * 60)


@app.on_event("shutdown")
async def shutdown_event():
    print("👋 PhotoFramer API Three-Stage Flow 服务关闭")


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8100,
        reload=True,
    )
