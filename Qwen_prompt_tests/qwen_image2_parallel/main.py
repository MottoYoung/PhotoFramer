"""
Qwen-Image 2.0 并行提示词测评入口

使用方法:
    # 并发同步测试（5种构图并发执行）
    python main.py sync -i test_image.jpg

    # 预览配置
    python main.py config
"""

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from prompts_config import load_prompts_from_yaml, generate_composition_requests
from sync_runner import run_parallel_assessment


def cmd_sync(args):
    print("\n" + "=" * 60)
    print("🚀 Qwen 并发同步测试 - 5 种构图方案并行")
    print("=" * 60)

    if args.prompt:
        print(f"   指定提示词版本: {args.prompt}")

    run_parallel_assessment(
        input_image_path=args.image,
        repeats=args.repeats,
        delay_between_runs=args.delay,
        prompt_id=args.prompt,
        images_per_prompt=args.n,
        negative_prompt=args.negative,
        prompt_extend=not args.no_extend,
        watermark=args.watermark,
        max_concurrency=args.max_concurrency,
        max_rate=args.max_rate,
        download_images=args.download,
    )


def cmd_config(_args):
    print("\n" + "=" * 60)
    print("📋 Qwen 并行提示词配置预览")
    print("=" * 60)

    prompt_sets = load_prompts_from_yaml()
    print(f"\n📝 提示词集合 ({len(prompt_sets)}):")
    for ps in prompt_sets:
        print(f"  - {ps.id}: {ps.description}")
        print(f"    包含 {len(ps.user_prompts)} 种构图技术:")
        for tech_id in ps.user_prompts.keys():
            print(f"      • {tech_id}")

    print("\n🎯 生成请求示例:")
    if prompt_sets:
        requests = generate_composition_requests(prompt_set=prompt_sets[0])
        for req in requests:
            print(f"  - {req.request_id}")


def main():
    parser = argparse.ArgumentParser(
        description="Qwen-Image 2.0 并行提示词测评工具",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  # 并发同步测试（5种构图并行）
  python main.py sync -i test_image.jpg -r 3

  # 预览配置
  python main.py config
        """,
    )

    subparsers = parser.add_subparsers(dest="command", help="子命令")

    sync_parser = subparsers.add_parser("sync", help="并发同步测试")
    sync_parser.add_argument("-i", "--image", required=True, help="输入图像路径")
    sync_parser.add_argument("-p", "--prompt", help="指定提示词版本ID（默认全部）")
    sync_parser.add_argument("-r", "--repeats", type=int, default=1, help="重复运行次数")
    sync_parser.add_argument("-d", "--delay", type=float, default=2.0, help="运行间隔（秒）")
    sync_parser.add_argument("--n", type=int, default=1, help="每个提示词生成图片数量（1-6）")
    sync_parser.add_argument("--negative", default=" ", help="negative_prompt")
    sync_parser.add_argument("--no-extend", action="store_true", help="关闭 prompt_extend")
    sync_parser.add_argument("--watermark", action="store_true", help="输出水印")
    sync_parser.add_argument("--max-concurrency", type=int, default=None, help="最大并发数")
    sync_parser.add_argument("--max-rate", type=float, default=2.0, help="每秒最大请求数（Qwen 限制 2/s，默认 2.0）")
    sync_parser.add_argument("--download", action="store_true", help="下载生成图片到本地")
    sync_parser.set_defaults(func=cmd_sync)

    config_parser = subparsers.add_parser("config", help="预览配置")
    config_parser.set_defaults(func=cmd_config)

    args = parser.parse_args()

    if args.command is None:
        parser.print_help()
        return

    args.func(args)


if __name__ == "__main__":
    main()
