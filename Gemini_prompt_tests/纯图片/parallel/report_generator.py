"""
并行测评报告生成器

从测试结果生成 Markdown 格式的对比分析报告
针对并行构图方案测试优化
"""

import json
from pathlib import Path
from datetime import datetime
from typing import Any


BASE_DIR = Path(__file__).parent
RESULTS_DIR = BASE_DIR / "results"


def generate_report(
    result_dir: str | Path,
    output_path: str | Path | None = None
) -> str:
    """
    根据测试结果生成 Markdown 报告
    
    Args:
        result_dir: 结果目录（包含 summary.json）
        output_path: 输出报告路径
        
    Returns:
        报告内容
    """
    result_dir = Path(result_dir)
    summary_path = result_dir / "summary.json"
    
    if not summary_path.exists():
        raise FileNotFoundError(f"未找到 summary.json: {summary_path}")
    
    with open(summary_path, "r", encoding="utf-8") as f:
        summary = json.load(f)
    
    # 判断是 parallel sync 还是 batch 结果
    is_parallel = "runs" in summary
    
    if is_parallel:
        report = _generate_parallel_report(summary, result_dir)
    else:
        report = _generate_batch_report(summary, result_dir)
    
    if output_path is None:
        output_path = result_dir / "report.md"
    
    with open(output_path, "w", encoding="utf-8") as f:
        f.write(report)
    
    print(f"✅ 报告已生成: {output_path}")
    return report


def _generate_parallel_report(summary: dict, result_dir: Path) -> str:
    """生成并行同步测评报告"""
    runs = summary.get("runs", [])
    techniques = summary.get("techniques", [])
    timestamp = summary.get("timestamp", "unknown")
    model_name = summary.get("model_name", "N/A")
    
    lines = [
        "# 并行构图测评报告",
        "",
        f"> 生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        f"> 测试时间: {timestamp}",
        f"> **模型**: `{model_name}`",
        f"> 输入图像: `{summary.get('input_image', 'N/A')}`",
        "",
        "---",
        "",
        "## 📊 测评概览",
        "",
        f"- **构图技术数**: {len(techniques)}",
        f"- **运行次数**: {len(runs)}",
        "",
    ]
    
    # 构图技术列表
    lines.append("**测试的构图技术**:")
    for tech in techniques:
        lines.append(f"- `{tech}`")
    
    lines.extend(["", "---", "", "## ⏱️ 并发性能分析", ""])
    
    if runs:
        # 计算平均并发耗时
        total_times = [r["total_time_ms"] for r in runs]
        avg_total = sum(total_times) / len(total_times)
        min_total = min(total_times)
        max_total = max(total_times)
        
        lines.extend([
            "| 指标 | 值 |",
            "|------|-----|",
            f"| 平均并发耗时 | **{avg_total:.0f}ms** |",
            f"| 最快并发耗时 | {min_total:.0f}ms |",
            f"| 最慢并发耗时 | {max_total:.0f}ms |",
            "",
        ])
        
        # 按技术统计
        lines.extend(["### 按构图技术统计", ""])
        tech_stats: dict[str, dict] = {}
        
        for run in runs:
            for result in run.get("results", []):
                tech_id = result.get("technique_id", "unknown")
                if tech_id not in tech_stats:
                    tech_stats[tech_id] = {
                        "times": [],
                        "successes": 0,
                        "applicable": 0,
                        "total": 0
                    }
                
                tech_stats[tech_id]["total"] += 1
                if result.get("success"):
                    tech_stats[tech_id]["successes"] += 1
                    tech_stats[tech_id]["times"].append(result.get("response_time_ms", 0))
                if result.get("is_applicable") is True:
                    tech_stats[tech_id]["applicable"] += 1
        
        lines.extend([
            "| 构图技术 | 平均响应(ms) | 成功率 | 适用次数 |",
            "|----------|-------------|--------|----------|"
        ])
        
        for tech_id, stats in sorted(tech_stats.items()):
            avg_time = sum(stats["times"]) / len(stats["times"]) if stats["times"] else 0
            success_rate = stats["successes"] / stats["total"] * 100 if stats["total"] > 0 else 0
            lines.append(
                f"| `{tech_id}` | {avg_time:.0f} | {success_rate:.0f}% | {stats['applicable']}/{stats['total']} |"
            )
    
    # 详细结果
    lines.extend(["", "---", "", "## 📝 详细结果", ""])
    
    for run_idx, run in enumerate(runs):
        lines.extend([
            f"### 运行 {run_idx + 1}",
            "",
            f"- 时间: {run.get('timestamp', 'N/A')}",
            f"- 并发耗时: **{run.get('total_time_ms', 0):.0f}ms**",
            f"- 成功: {run.get('successful_techniques', 0)}/{run.get('total_techniques', 0)}",
            f"- 适用: {run.get('applicable_techniques', 0)}/{run.get('total_techniques', 0)}",
            ""
        ])
        
        for result in run.get("results", []):
            tech_id = result.get("technique_id", "unknown")
            success = result.get("success", False)
            status = "✅" if success else "❌"
            applicable = result.get("is_applicable")
            applicable_str = "适用" if applicable is True else ("不适用" if applicable is False else "未知")
            
            lines.append(f"- {status} **{tech_id}**: {result.get('response_time_ms', 0):.0f}ms [{applicable_str}]")
            
            if result.get("images"):
                lines.append(f"  - 生成图片: {len(result['images'])} 张")
        
        lines.append("")
    
    # 推荐构图
    lines.extend(["---", "", "## 🎯 推荐构图", ""])
    
    applicable_techs = []
    for run in runs:
        for result in run.get("results", []):
            if result.get("is_applicable") is True and result.get("technique_id") not in applicable_techs:
                applicable_techs.append(result.get("technique_id"))
    
    if applicable_techs:
        lines.append("以下构图技术在测试中被判定为**适用**:")
        for tech in applicable_techs:
            lines.append(f"- ✓ `{tech}`")
    else:
        lines.append("*本次测试中未发现适用的构图技术*")
    
    return "\n".join(lines)


def _generate_batch_report(summary: dict, result_dir: Path) -> str:
    """生成批量测评报告"""
    results = summary.get("results", [])
    
    lines = [
        "# 批量构图测评报告",
        "",
        f"> 生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        f"> 作业名称: `{summary.get('display_name', 'N/A')}`",
        f"> 状态: {summary.get('status', 'N/A')}",
        "",
        "---",
        "",
        "## 📊 测评概览",
        "",
        f"- 总技术数: {summary.get('total_techniques', 0)}",
        f"- 成功: {summary.get('successful_techniques', 0)}",
        f"- 适用: {summary.get('applicable_techniques', 0)}",
        "",
        "---",
        "",
        "## 📝 各技术结果",
        "",
    ]
    
    for r in results:
        tech_id = r.get("technique_id", "unknown")
        success = r.get("success", False)
        status = "✅" if success else "❌"
        applicable = r.get("is_applicable")
        
        lines.append(f"### {status} {tech_id}")
        lines.append("")
        
        if success:
            applicable_str = "适用" if applicable is True else ("不适用" if applicable is False else "未知")
            lines.append(f"**适用性**: {applicable_str}")
            
            texts = r.get("texts", [])
            if texts:
                preview = texts[0][:300] + "..." if len(texts[0]) > 300 else texts[0]
                lines.extend(["", "**输出预览:**", "```", preview, "```"])
            
            images = r.get("images", [])
            if images:
                lines.append(f"\n**生成图片数**: {len(images)}")
        else:
            error = r.get("error", "Unknown error")
            lines.append(f"**错误**: {error}")
        
        lines.append("")
    
    return "\n".join(lines)


def find_latest_result_dir() -> Path | None:
    """查找最新的结果目录"""
    if not RESULTS_DIR.exists():
        return None
    
    dirs = [d for d in RESULTS_DIR.iterdir() if d.is_dir() and (d / "summary.json").exists()]
    if not dirs:
        return None
    
    return max(dirs, key=lambda d: d.stat().st_mtime)


if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description="生成并行测评报告")
    parser.add_argument("--input", "-i", help="结果目录路径（默认使用最新）")
    parser.add_argument("--output", "-o", help="输出报告路径")
    
    args = parser.parse_args()
    
    if args.input:
        result_dir = Path(args.input)
    else:
        result_dir = find_latest_result_dir()
        if not result_dir:
            print("❌ 未找到结果目录，请先运行测试")
            exit(1)
        print(f"📂 使用最新结果目录: {result_dir}")
    
    report = generate_report(result_dir, args.output)
    print("\n" + "=" * 60)
    print(report)
