# 并行构图测评报告

> 生成时间: 2026-01-31 21:02:54
> 测试时间: 20260131_210224
> **模型**: `gemini-3-pro-image-preview`
> 输入图像: `/Users/mottoyoung/学习/毕设/PhotoFramer/test_images/input/person.jpg`

---

## 📊 测评概览

- **构图技术数**: 5
- **运行次数**: 1

**测试的构图技术**:
- `rule_of_thirds`
- `center_composition`
- `leading_lines`
- `foreground_framing`
- `diagonal_composition`

---

## ⏱️ 并发性能分析

| 指标 | 值 |
|------|-----|
| 平均并发耗时 | **30237ms** |
| 最快并发耗时 | 30237ms |
| 最慢并发耗时 | 30237ms |

### 按构图技术统计

| 构图技术 | 平均响应(ms) | 成功率 | 适用次数 |
|----------|-------------|--------|----------|
| `center_composition` | 30223 | 100% | 1/1 |
| `diagonal_composition` | 9836 | 100% | 0/1 |
| `foreground_framing` | 9122 | 100% | 0/1 |
| `leading_lines` | 24120 | 100% | 1/1 |
| `rule_of_thirds` | 22504 | 100% | 1/1 |

---

## 📝 详细结果

### 运行 1

- 时间: 2026-01-31T21:02:24.510498
- 并发耗时: **30237ms**
- 成功: 5/5
- 适用: 3/5

- ✅ **rule_of_thirds**: 22504ms [适用]
  - 生成图片: 1 张
- ✅ **center_composition**: 30223ms [适用]
  - 生成图片: 1 张
- ✅ **leading_lines**: 24120ms [适用]
  - 生成图片: 1 张
- ✅ **foreground_framing**: 9122ms [不适用]
- ✅ **diagonal_composition**: 9836ms [不适用]

---

## 🎯 推荐构图

以下构图技术在测试中被判定为**适用**:
- ✓ `rule_of_thirds`
- ✓ `center_composition`
- ✓ `leading_lines`