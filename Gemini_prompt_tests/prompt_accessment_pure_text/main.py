"""
提示词测评主入口

使用方法:
    # 阶段1: 批量测试（质量筛选）
    python main.py batch -i test_images/input/input.jpg
    
    # 阶段2: 同步测试（响应时间测量）
    python main.py sync -i test_images/input/input.jpg -r 3
    
    # 生成报告
    python main.py report
"""

import argparse
import sys
from pathlib import Path

# 添加当前目录到路径
sys.path.insert(0, str(Path(__file__).parent))

from prompts_config import (
    PROMPT_VERSIONS, MODEL_CONFIGS, 
    generate_test_matrix, save_test_matrix, load_prompts_from_yaml
)
from batch_runner import run_batch_assessment
from sync_runner import run_sync_assessment
from report_generator import generate_report, find_latest_result_dir


def cmd_batch(args):
    """执行批量测试（阶段1）"""
    print("\n" + "="*60)
    print("🚀 阶段1: 批量测试 (Batch API)")
    print("="*60)
    
    if args.prompt:
        print(f"   指定提示词版本: {args.prompt}")
    
    job_info = run_batch_assessment(
        input_image_path=args.image,
        wait=not args.no_wait,
        prompt_id=args.prompt
    )
    
    if args.no_wait:
        print(f"\n作业已提交，作业名称: {job_info.job_name}")
        print("使用以下命令查看状态:")
        print(f"  python main.py status --job {job_info.job_name}")


def cmd_sync(args):
    """执行同步测试（阶段2）"""
    print("\n" + "="*60)
    print("⚡ 阶段2: 同步测试 (响应时间测量)")
    print("="*60)
    
    if args.prompt:
        print(f"   指定提示词版本: {args.prompt}")
    
    results = run_sync_assessment(
        input_image_path=args.image,
        repeats=args.repeats,
        delay_between_calls=args.delay,
        prompt_id=args.prompt
    )
    
    # 自动生成报告
    result_dir = find_latest_result_dir()
    if result_dir:
        generate_report(result_dir)


def cmd_report(args):
    """生成测评报告"""
    if args.input:
        result_dir = Path(args.input)
    else:
        result_dir = find_latest_result_dir()
        if not result_dir:
            print("❌ 未找到结果目录，请先运行测试")
            return
    
    report = generate_report(result_dir, args.output)
    
    if not args.quiet:
        print("\n" + "="*60)
        print(report)


def cmd_config(args):
    """预览配置和测试矩阵"""
    print("\n" + "="*60)
    print("📋 测试矩阵预览")
    print("="*60)
    
    cases = list(generate_test_matrix())
    
    print(f"\n提示词版本 ({len(PROMPT_VERSIONS)}):")
    for p in PROMPT_VERSIONS:
        print(f"  - {p.id}: {p.description}")
    
    print(f"\n模型配置 ({len(MODEL_CONFIGS)}):")
    for c in MODEL_CONFIGS:
        print(f"  - {c.id}: temp={c.temperature}, topK={c.top_k}, topP={c.top_p}")
    
    print(f"\n总测试组合数: {len(cases)}")
    
    if args.save:
        output_path = Path(args.save)
        save_test_matrix(output_path)
        print(f"\n✅ 测试矩阵已保存到: {output_path}")


def main():
    parser = argparse.ArgumentParser(
        description="提示词测评工具",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  # 阶段1: 批量测试（质量筛选，成本50%）
  python main.py batch -i test_images/input/input.jpg
  
  # 阶段2: 同步测试（响应时间测量）
  python main.py sync -i test_images/input/input.jpg -r 3
  
  # 生成报告
  python main.py report
  
  # 预览测试矩阵
  python main.py matrix
        """
    )
    
    subparsers = parser.add_subparsers(dest="command", help="子命令")
    
    # batch 命令
    batch_parser = subparsers.add_parser("batch", help="阶段1: 批量测试（质量筛选）")
    batch_parser.add_argument("-i", "--image", required=True, help="输入图像路径")
    batch_parser.add_argument("-p", "--prompt", help="指定提示词版本ID（默认全部）")
    batch_parser.add_argument("--no-wait", action="store_true", help="不等待作业完成")
    batch_parser.set_defaults(func=cmd_batch)
    
    # sync 命令
    sync_parser = subparsers.add_parser("sync", help="阶段2: 同步测试（响应时间测量）")
    sync_parser.add_argument("-i", "--image", required=True, help="输入图像路径")
    sync_parser.add_argument("-p", "--prompt", help="指定提示词版本ID（默认全部）")
    sync_parser.add_argument("-r", "--repeats", type=int, default=3, help="每用例重复次数")
    sync_parser.add_argument("-d", "--delay", type=float, default=5.0, help="调用间隔（秒）")
    sync_parser.set_defaults(func=cmd_sync)
    
    # report 命令
    report_parser = subparsers.add_parser("report", help="生成测评报告")
    report_parser.add_argument("-i", "--input", help="结果目录（默认最新）")
    report_parser.add_argument("-o", "--output", help="输出报告路径")
    report_parser.add_argument("-q", "--quiet", action="store_true", help="不打印报告内容")
    report_parser.set_defaults(func=cmd_report)
    
    # config 命令
    config_parser = subparsers.add_parser("config", help="预览配置")
    config_parser.add_argument("-s", "--save", help="保存到JSON文件")
    config_parser.set_defaults(func=cmd_config)
    
    args = parser.parse_args()
    
    if args.command is None:
        parser.print_help()
        return
    
    args.func(args)


if __name__ == "__main__":
    main()
