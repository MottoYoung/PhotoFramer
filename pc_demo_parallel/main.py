"""
辅助拍摄 App 后端服务 (v3.1 - 并行化版本)
FastAPI 应用入口
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from routers import composition_router
from config import MODEL_NAME, TECHNIQUE_CONFIGS

# 创建 FastAPI 应用
app = FastAPI(
    title="📷 PhotoFramer API v3.1",
    description="""
## 辅助拍摄 App 后端服务 (并行化版本)

基于 Gemini AI 的智能构图分析 API，使用 **asyncio 并行化** 同时请求 5 种构图技术，
大幅缩短响应时间。

### 核心特性

- 🚀 **并行请求**：5 种构图方案并发分析，响应时间接近单个请求
- 📸 **智能分析**：自动判断每种构图技术是否适用
- 🎯 **操作指令**：生成 Shift / Zoom / View-change 操作步骤
- 🖼️ **参考图像**：生成优化后的目标构图图片

### 支持的构图技术

1. **三分构图** (rule_of_thirds)
2. **中心构图** (center_composition)
3. **引导线构图** (leading_lines)
4. **前景/框架构图** (foreground_framing)
5. **对角线构图** (diagonal_composition)

### 使用流程

1. 调用 `POST /api/v1/composition/analyze` 上传图片
2. 获取适用的构图优化方案（1-5 个）
3. 根据操作指令引导用户调整相机位置
4. 完成构图后拍摄

### 技术栈

- **AI Model**: Gemini 2.5 Flash Image
- **Framework**: FastAPI + Uvicorn + asyncio
""",
    version="3.1.0",
    docs_url="/docs",
    redoc_url="/redoc",
)

# 配置 CORS（允许跨域请求）
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 生产环境应限制具体域名
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 注册路由
app.include_router(composition_router)


@app.get("/", tags=["Root"])
async def root():
    """
    根路径 - 返回服务信息
    """
    return {
        "service": "PhotoFramer API",
        "version": "3.1.0",
        "model": MODEL_NAME,
        "techniques": list(TECHNIQUE_CONFIGS.keys()),
        "docs": "/docs",
        "health": "/api/v1/composition/health",
    }


@app.on_event("startup")
async def startup_event():
    """服务启动时执行"""
    print("=" * 60)
    print("🚀 PhotoFramer API v3.1 (并行化版本) 启动")
    print(f"   模型: {MODEL_NAME}")
    print(f"   构图技术: {', '.join(TECHNIQUE_CONFIGS.keys())}")
    print("   文档: http://localhost:8000/docs")
    print("=" * 60)


@app.on_event("shutdown")
async def shutdown_event():
    """服务关闭时执行"""
    print("👋 PhotoFramer API v3.1 服务关闭")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8000,
        reload=True,
    )
