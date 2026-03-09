# 并行构图测评报告

> 生成时间: 2026-01-29 21:38:03
> 测试时间: 20260129_213746
> **模型**: `gemini-2.5-flash-image`
> 输入图像: `/Users/mottoyoung/学习/毕设/PhotoFramer/test_images/input/input.jpg`

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
| 平均并发耗时 | **17528ms** |
| 最快并发耗时 | 17528ms |
| 最慢并发耗时 | 17528ms |

### 按构图技术统计

| 构图技术 | 平均响应(ms) | 成功率 | 适用次数 |
|----------|-------------|--------|----------|
| `center_composition` | 15179 | 100% | 1/1 |
| `diagonal_composition` | 17515 | 100% | 1/1 |
| `foreground_framing` | 5355 | 100% | 0/1 |
| `leading_lines` | 4273 | 100% | 0/1 |
| `rule_of_thirds` | 16489 | 100% | 1/1 |

---

## 📝 详细结果

### 运行 1

- 时间: 2026-01-29T21:37:46.144528
- 并发耗时: **17528ms**
- 成功: 5/5
- 适用: 3/5

- ✅ **rule_of_thirds**: 16489ms [适用]
  - 生成图片: 1 张
- ✅ **center_composition**: 15179ms [适用]
  - 生成图片: 1 张
- ✅ **leading_lines**: 4273ms [不适用]
- ✅ **foreground_framing**: 5355ms [不适用]
- ✅ **diagonal_composition**: 17515ms [适用]
  - 生成图片: 1 张

---

## 🎯 推荐构图

以下构图技术在测试中被判定为**适用**:
- ✓ `rule_of_thirds`
- ✓ `center_composition`
- ✓ `diagonal_composition`