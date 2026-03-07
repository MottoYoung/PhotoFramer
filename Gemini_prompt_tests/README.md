# Gemini 提示词测评工具集

本目录包含 7 套提示词测评工具，用于测试和比较不同提示词策略在辅助摄影构图指导场景下的效果。

## 📁 目录结构

| 目录 | 架构 | 输出模态 | 说明 |
|-----|------|---------|------|
| `prompt_accessment_paraller_p_t` | **并行** | 图+文 | 5种构图方案并发请求，分而治之策略 |
| `prompt_accessment_paraller_p_t_pure_image` | **并行** | 纯图 | 仅返回构图目标图片 |
| `prompt_accessment_paraller_p_t_pure_text` | **并行** | 纯文 | 仅返回 JSON 文本指导 |
| `prompt_accessment_paraller_p_t_pinjie` | **并行** | 图+文 | 多种构图方案拼接后返回 |
| `prompt_accessment_pic_mix_text` | 串行 | 图+文 | 图文混合输入测试 |
| `prompt_accessment_pure_image` | 串行 | 图+文 | 纯图像输入测试 |
| `prompt_accessment_pure_text` | 串行 | 图+文 | 纯文本输入测试 |

## 🎯 核心区别

### 1. 并行架构 vs 串行架构

**并行架构 - 请求级并行** (3 个 `paraller` 目录)
- 采用 "分而治之" 策略：统一 System Instruction + 5 个独立 User Prompt
- 5 种构图技术（三分法、中心构图、引导线、前景框架、对角线）**并发请求**
- 每个请求返回 1 种构图方案，5 个请求同时执行
- 适合快速获取多种构图方案

**并行架构 - 输出级并行** (`prompt_accessment_paraller_p_t_pinjie`)
- 采用 "拼接返回" 策略：单个请求让模型一次性返回多种构图方案
- 模型在一次响应中生成所有构图建议
- 减少 API 调用次数，适合构图方案数量较少的场景

**串行架构** (其他 3 个目录)
- 采用 "测试矩阵" 策略：多个提示词版本 × 多个模型配置
- 顺序执行每个测试用例
- 适合系统性对比评估

### 2. 输出模态差异

| 输出模态 | 目录 | 特点 |
|---------|------|-----|
| **图+文** | `paraller_p_t`, `p_t_pinjie`, `pic_mix_text` 等 | 返回 JSON 指导 + 目标构图图片 |
| **纯图** | `paraller_p_t_pure_image` | 仅返回构图目标图片，不返回文本 |
| **纯文** | `paraller_p_t_pure_text` | 仅返回 JSON 指导，不生成图片 |

## 🚀 通用命令

所有目录均支持以下统一命令：

```bash
# 查看配置和可用提示词版本
python main.py config

# 同步测试（实时响应）
python main.py sync -i <图像路径> [-p <提示词版本>] [-r <重复次数>] [-d <间隔秒数>]

# 批量测试（Batch API，成本 50%）
python main.py batch -i <图像路径> [-p <提示词版本>]

# 生成测评报告
python main.py report
```

### 参数说明

| 参数 | 说明 | 默认值 |
|-----|------|-------|
| `-i, --image` | 输入图像路径 | 必填 |
| `-p, --prompt` | 指定提示词版本 ID | 全部版本 |
| `-r, --repeats` | 每用例重复次数 | 1~3 |
| `-d, --delay` | 调用间隔（秒） | 2.0~5.0 |

## 📋 使用示例

### 示例 1：并行测试（请求级并行）

```bash
cd prompt_accessment_paraller_p_t

# 查看可用配置
python main.py config

# 使用 Gemini 版提示词测试，重复 3 次
python main.py sync -i test.jpg -p parallel_v1_gemini -r 3

# 使用 GPT 版提示词测试
python main.py sync -i test.jpg -p parallel_v1_gpt -r 3
```

### 示例 2：串行对比测试

```bash
cd prompt_accessment_pic_mix_text

# 查看测试矩阵
python main.py config

# 测试特定提示词版本，重复 5 次
python main.py sync -i test.jpg -p v1_base -r 5

# 测试所有版本
python main.py sync -i test.jpg -r 3
```

### 示例 3：批量测试（成本优化）

```bash
# 使用 Batch API 进行大规模测试（成本降低 50%）
python main.py batch -i test.jpg

# 不等待完成，后台运行
python main.py batch -i test.jpg --no-wait
```

## 📊 输出结果

测试结果保存在各目录的 `results/` 文件夹中：

```
results/
├── sync_20260131_221500/     # 同步测试结果
│   ├── summary.json          # 汇总数据
│   ├── case_001.json         # 单用例结果
│   └── ...
├── batch_20260131_220000/    # 批量测试结果
│   └── ...
└── report.md                 # 生成的测评报告
```

## ⚙️ 环境配置

### 依赖安装

```bash
pip install google-generativeai pyyaml pillow
```

### 环境变量

```bash
# Gemini API Key
export GEMINI_API_KEY="your-api-key"

# 或使用中转 API
export GEMINI_API_KEY="your-relay-key"
export GEMINI_BASE_URL="https://your-relay-url/v1beta"
```

## 🔧 配置文件

每个目录包含 `config.yaml` 用于定义：

- **提示词版本**：System Instruction + User Prompt
- **模型配置**：temperature, top_k, top_p
- **输出模式**：JSON Schema 约束

## 📈 选择建议

| 场景 | 推荐目录 |
|-----|---------|
| 快速获取多种构图方案 | `prompt_accessment_paraller_p_t` |
| 系统性对比不同提示词效果 | `prompt_accessment_pic_mix_text` |
| 测试纯图像理解能力 | `prompt_accessment_pure_image` |
| 测试纯文本指导效果 | `prompt_accessment_pure_text` |
| 拼接式提示词实验 | `prompt_accessment_paraller_p_t_pinjie` |
