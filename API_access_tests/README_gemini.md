# Gemini Imagen API 图生图测试指南

## 📋 功能说明

本测试脚本用于PhotoFramer项目第一阶段的图像生成功能验证，主要实现：

1. **文生图**：基于文本提示词生成图片
2. **图生图**：基于参考图片生成优化构图的图片
3. **PhotoFramer提示词集成**：使用论文中的构图优化提示词

## 🔧 环境配置

### 1. 安装依赖

```bash
pip install google-genai Pillow
```

### 2. 获取API密钥

1. 访问 [Google AI Studio](https://aistudio.google.com/app/apikey)
2. 创建或复制你的API Key
3. 设置环境变量：

```bash
# macOS/Linux
export GEMINI_API_KEY="your-api-key-here"

# 或者直接在代码中修改 GEMINI_API_KEY 变量
```

## 🚀 使用方法

### 基础运行

```bash
cd /Users/mottoyoung/学习/毕设/PhotoFramer/pc_demo/api_tests
python gemini_picgen_test.py
```

### 目录结构

```
api_tests/
├── gemini_picgen_test.py    # 主测试脚本
├── test_images/
│   ├── input/               # 放置输入图片（用于图生图测试）
│   └── output/              # 生成的图片输出目录
```

## 📝 代码结构

### 核心函数

1. **`generate_from_text_only()`**
   - 纯文生图模式
   - 适用于基础功能测试

2. **`generate_improved_composition()`**
   - 图生图模式
   - 基于参考图生成优化构图

3. **`save_generated_images()`**
   - 保存生成的图片到本地

### PhotoFramer提示词

```python
# Shift + Zoom 任务
PROMPT_AUTO_SHIFT_ZOOM = [
    "Refine the composition through shift or zoom-in adjustments.",
    "Improve the composition by combining shift or zoom-in operations.",
    ...
]

# 全自动任务（包含view-change）
PROMPT_FULL_AUTO = [
    "Capture this scene with better composition.",
    "Enhance the composition of this scene.",
    ...
]
```

## 🎯 测试场景

### 测试1: 基础文生图
- 验证API连接和基本功能
- 生成风景照片

### 测试2: 人像构图优化
- 使用PhotoFramer提示词
- 生成人像照片参考构图

### 测试3: 图生图
- 基于输入图片生成优化构图
- **需要在 `test_images/input/` 目录下放置测试图片**

## ⚙️ 配置选项

```python
# 模型选择
MODEL_NAME = "imagen-3.0-generate-001"  # Imagen 3
# MODEL_NAME = "imagen-4.0-generate-001"  # Imagen 4（推荐，支持更多功能）

# 生成参数
num_images = 3          # 生成数量（1-4）
aspect_ratio = "3:4"    # 宽高比 ("1:1", "3:4", "4:3", "9:16", "16:9")
image_size = "1K"       # 图片尺寸 ("1K" 或 "2K"，仅Imagen 4支持）
```

## 📊 输出示例

```
✅ 成功生成 3 张图片

💾 保存生成的图片...
   [1] test2_portrait_20260103_110000_1.png
   [2] test2_portrait_20260103_110000_2.png
   [3] test2_portrait_20260103_110000_3.png

✅ 已保存到目录: test_images/output
```

## ⚠️ 注意事项

1. **图生图支持**
   - Gemini Imagen API的图生图功能主要用于图像编辑（inpaint/outpaint）
   - 如果需要基于参考图生成全新构图，可能需要结合文生图+详细提示词
   - 代码中已包含fallback方案

2. **API限制**
   - 免费额度有限，请合理使用
   - 生成时间约10-30秒，请耐心等待
   - 仅支持英文提示词

3. **图片格式**
   - 支持JPG、PNG格式
   - 推荐使用高质量原图作为参考

## 🔍 故障排查

### 问题1: API Key错误
```
ValueError: 请设置GEMINI_API_KEY环境变量
```
**解决**：检查环境变量或在代码中设置API密钥

### 问题2: 图生图不支持
```
⚠️ 图生图功能可能不支持或需要不同的调用方式
```
**解决**：使用文生图模式，配合详细的构图描述提示词

### 问题3: 依赖缺失
```
ModuleNotFoundError: No module named 'google.genai'
```
**解决**：运行 `pip install google-genai Pillow`

## 📚 参考资料

- [Gemini API 文档](https://ai.google.dev/gemini-api/docs?hl=zh-cn)
- [Imagen 生成图片文档](https://ai.google.dev/gemini-api/docs/imagen?hl=zh-cn)
- PhotoFramer论文提示词（需求分析文档第5.1.2节）
