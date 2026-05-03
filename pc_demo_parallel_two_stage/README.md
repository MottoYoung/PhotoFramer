# PhotoFramer Backend - Two-Stage LLM Stage (v1.0)

基于 asyncio 并行化的 **Qwen3.5-flash** 构图分析后端（Stage 1）。

## 两阶段架构说明

```
用户图片
   │
   ▼
【Stage 1: 本项目】────────────── Qwen3.5-flash (快速 LLM)
   │  并行分析 5 种构图技术
   │  输出: steps + qwen_image_prompt
   │
   ▼
【Stage 2: 待建】─────────────── Qwen-image-2.0 (图像生成)
      输入: qwen_image_prompt
      输出: 目标构图效果图
```

## 快速开始

```bash
# 安装依赖
pip install -r requirements.txt

# 设置 API Key
export DASHSCOPE_API_KEY=sk-xxx

# 启动服务
python main.py
```

服务将在 http://localhost:8000 启动，API 文档: http://localhost:8000/docs

## API 接口

### POST /composition_analyze

上传图片，并行调用 Qwen3.5-flash 分析 5 种构图技术，返回适用的方案。

**请求**: `multipart/form-data`
- `image`: 图片文件 (JPEG/PNG)

**响应**:
```json
{
  "success": true,
  "total_techniques": 5,
  "applicable_count": 3,
  "total_time_ms": 5000.0,
  "compositions": [
    {
      "technique": "rule_of_thirds",
      "technique_name": "三分构图",
      "aesthetic_desc": "将人物置于右侧三分线，增强画面平衡感...",
      "steps": [
        {
          "step_order": 1,
          "action_type": "Shift",
          "direction": "Right",
          "guide_text": "向右平移相机，使主体对齐右侧三分线"
        }
      ],
      "qwen_image_prompt": "A photograph with rule-of-thirds composition, subject placed on the right third-line...",
      "response_time_ms": 3200.0
    }
  ]
}
```

### POST /composition_analyze_stream

SSE 流式接口，检测到 `qwen_image_prompt` 后会尽早推送事件。

### POST /image_generate

Stage 2 生图接口。

### GET /health

健康检查接口。

## 并行化机制

```
请求图片
   │
   ▼
asyncio.gather() ──┬── 三分构图 ──┐
                   ├── 中心构图 ──┼── 聚合结果 → 返回
                   ├── 引导线 ────┤
                   ├── 前景框架 ──┤
                   └── 对角线 ────┘
每个任务: asyncio.to_thread(stream_call_sync)
```

## 环境变量

| 变量 | 说明 | 获取方式 |
|------|------|------|
| `DASHSCOPE_API_KEY` | 阿里云 DashScope API Key | [控制台](https://dashscope.console.aliyun.com/apiKey) |
