# 并行构图测评报告

> 生成时间: 2026-02-02 21:04:30
> 测试时间: 20260202_210416
> **模型**: `gemini-2.5-flash-image`
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
| 平均并发耗时 | **14057ms** |
| 最快并发耗时 | 14057ms |
| 最慢并发耗时 | 14057ms |

### 按构图技术统计

| 构图技术 | 平均响应(ms) | 成功率 | 适用次数 |
|----------|-------------|--------|----------|
| `center_composition` | 12389 | 100% | 1/1 |
| `diagonal_composition` | 5239 | 100% | 0/1 |
| `foreground_framing` | 3800 | 100% | 0/1 |
| `leading_lines` | 14042 | 100% | 1/1 |
| `rule_of_thirds` | 12666 | 100% | 1/1 |

---

## 📝 详细结果

### 运行 1

- 时间: 2026-02-02T21:04:16.030106
- 并发耗时: **14057ms**
- 成功: 5/5
- 适用: 3/5

- ✅ **rule_of_thirds**: 12666ms [适用]
  - 生成图片: 1 张
- ✅ **center_composition**: 12389ms [适用]
  - 生成图片: 1 张
- ✅ **leading_lines**: 14042ms [适用]
  - 生成图片: 1 张
- ✅ **foreground_framing**: 3800ms [不适用]
- ✅ **diagonal_composition**: 5239ms [不适用]

---

## 🎯 推荐构图

以下构图技术在测试中被判定为**适用**:
- ✓ `rule_of_thirds`
- ✓ `center_composition`
- ✓ `leading_lines`