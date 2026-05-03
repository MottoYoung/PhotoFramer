# PhotoFramer Backend - Two-Stage New

一个可插拔的两阶段后端：

- Stage 1：构图分析与步骤生成
- Stage 2：参考构图图生成

支持自由组合：

- `qwen -> qwen`
- `qwen -> gemini`
- `gemini -> qwen`
- `gemini -> gemini`

默认配置现在是：

- `gemini -> gemini`

这样默认不会撞上 Qwen 官方速率限制；Qwen 保留为按需切换的可选 provider。

并且 Gemini 侧会按模型能力自动适配配置：

- 文本模型与图像模型分开处理
- `image_size` 只对支持 `image_config` 的模型下发
- `512 / 1K / 2K / 4K` 只对支持 imageSize 的 Gemini 图片模型下发
- `max_output_tokens` 只对支持的模型下发
- `thinking_config` 只对支持 thinking 的模型下发

## 目录特点

- 新目录，不影响旧的 `pc_demo_parallel_two_stage`
- Qwen Stage 1 保留了 `prompt` 前置流式能力
- Gemini Stage 1 改成“只做 JSON 分析”
- Stage 2 统一消费 `image_prompt`

## 环境变量

```bash
export STAGE1_PROVIDER=gemini
export STAGE2_PROVIDER=gemini

export DASHSCOPE_API_KEY=sk-xxx
export GEMINI_API_KEY=xxx
```

如果你想走国内 Gemini 兼容中转，可改为：

```bash
export USE_GEMINI_DOMESTIC_API=true
export GEMINI_DOMESTIC_BASE_URL=https://docs.newapi.pro/
export GEMINI_DOMESTIC_API_KEY=sk-xxx
export GEMINI_DOMESTIC_API_VERSION=v1beta
```

注意：

- `GEMINI_DOMESTIC_BASE_URL` 填根地址或 API 根地址即可
- 不要填成 `.../v1beta/models/{model}:generateContent` 这种完整请求路径
- 开启国内模式后，Gemini provider 会走 `Authorization: Bearer <key>` 方式鉴权

可选模型变量：

```bash
export QWEN_STAGE1_MODEL=qwen3.5-flash-2026-02-23
export QWEN_STAGE2_MODEL=qwen-image-2.0-2026-03-03
export GEMINI_STAGE1_MODEL=gemini-2.5-flash
export GEMINI_STAGE2_MODEL=gemini-2.5-flash-image
```

## 运行

```bash
pip install -r requirements.txt
python main.py
```

## 部署地址

当前公网服务地址：

```text
http://aicrop.312237.xyz
```

## 接口

### `POST /composition_analyze`

统一两阶段入口。

- 如果 Stage 1 支持 prompt 前置流式（当前 Qwen、Gemini 都支持），会自动走流水线：
  - prompt 一出来就触发 Stage 2
- 如果未来切到某个不支持流式前置的 provider，则会先完成分析，再触发 Stage 2

### `POST /composition_analyze_stream`

仅当 Stage 1 provider 支持流式 prompt 前置时可用。

当前：

- `qwen`：支持
- `gemini`：支持

### `POST /image_generate`

直接调用 Stage 2，根据当前 `STAGE2_PROVIDER` 生图。

### `GET /health`

健康检查接口。

## 响应特性

返回的 `compositions[]` 统一包含：

- `steps`
- `shot_spec`
- `image_prompt`
- `image_base64`

这样前端不需要关心当前是 Qwen 还是 Gemini。
