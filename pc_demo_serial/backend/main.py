"""
辅助拍摄 App 后端服务
FastAPI 应用入口
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from routers import composition_router
from config import MODEL_NAME

# 创建 FastAPI 应用
app = FastAPI(
    title="📷 PhotoFramer API",
    description="""
## 辅助拍摄 App 后端服务

基于 Gemini AI 的智能构图分析 API，为 Android 客户端提供：

- 📸 **图片分析**：上传原始图片，获取 AI 构图建议
- 🎯 **操作指令**：生成 Shift / Zoom / View-change 操作步骤
- 🖼️ **参考图像**：生成优化后的目标构图图片

### 使用流程

1. 调用 `/api/v1/composition/analyze` 上传图片
2. 获取 1-4 个构图优化方案
3. 根据操作指令引导用户调整相机位置
4. 完成构图后拍摄

### 技术栈

- **AI Model**: Gemini 2.5 Flash Image
- **Framework**: FastAPI + Uvicorn
""",
    version="1.0.0",
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
        "version": "1.0.0",
        "model": MODEL_NAME,
        "docs": "/docs",
        "health": "/api/v1/composition/health",
    }


@app.on_event("startup")
async def startup_event():
    """服务启动时执行"""
    print("=" * 60)
    print("🚀 PhotoFramer API 服务启动")
    print(f"   模型: {MODEL_NAME}")
    print("   文档: http://localhost:8000/docs")
    print("=" * 60)


@app.on_event("shutdown")
async def shutdown_event():
    """服务关闭时执行"""
    print("👋 PhotoFramer API 服务关闭")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8000,
        reload=True,
    )
