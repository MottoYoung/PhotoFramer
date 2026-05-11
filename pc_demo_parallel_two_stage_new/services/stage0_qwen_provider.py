"""
Qwen Stage 0 provider。
职责：对候选构图技术做软预筛，返回更值得进入 Stage 1 的 technique 列表。
"""
import asyncio
from typing import List

from openai import OpenAI

from config import (
    DASHSCOPE_API_KEY,
    DASHSCOPE_BASE_URL,
    ENABLE_STAGE0,
    QWEN_STAGE0_ENABLE_THINKING,
    QWEN_STAGE0_MODEL,
    QWEN_STAGE0_THINKING_BUDGET,
    STAGE0_MAX_TECHNIQUES,
    STAGE0_SOFT_PREFILTER_PROMPT_TEMPLATE,
    STAGE0_TEMPERATURE,
    STAGE0_TOP_P,
    TECHNIQUE_CONFIGS,
)
from services.common import image_to_data_url, now_perf, parse_json_from_text, prepare_image_bytes


class QwenStage0Provider:
    provider_name = "qwen"
    model_name = QWEN_STAGE0_MODEL

    def __init__(self):
        if not DASHSCOPE_API_KEY:
            raise ValueError("请设置 DASHSCOPE_API_KEY 环境变量")
        self.client = OpenAI(
            api_key=DASHSCOPE_API_KEY,
            base_url=DASHSCOPE_BASE_URL,
        )
        print("✅ QwenStage0Provider 初始化成功")

    def prepare_image_payload(self, image_bytes: bytes) -> tuple[bytes, str, str]:
        processed, mime = prepare_image_bytes(image_bytes)
        return processed, mime, image_to_data_url(processed, mime)

    def _build_prompt(self, techniques: List[str]) -> str:
        technique_lines = [
            f'- "{technique_id}": {TECHNIQUE_CONFIGS[technique_id].name}'
            for technique_id in techniques
        ]
        prompt = STAGE0_SOFT_PREFILTER_PROMPT_TEMPLATE.format(
            max_techniques=min(STAGE0_MAX_TECHNIQUES, len(techniques)),
            candidate_techniques="\n".join(technique_lines),
        )
        if not QWEN_STAGE0_ENABLE_THINKING:
            prompt = prompt.rstrip() + "\n/no_think"
        return prompt

    def _build_messages(self, image_data_url: str, techniques: List[str]) -> list:
        return [
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": self._build_prompt(techniques)},
                    {"type": "image_url", "image_url": {"url": image_data_url}},
                ],
            }
        ]

    def _build_extra_body(self) -> dict:
        return {
            "enable_thinking": QWEN_STAGE0_ENABLE_THINKING,
            "thinking_budget": QWEN_STAGE0_THINKING_BUDGET,
        }

    def _normalize_selection(self, techniques: List[str], selected: List[str]) -> List[str]:
        limit = min(STAGE0_MAX_TECHNIQUES, len(techniques))
        normalized: List[str] = []
        for technique_id in selected:
            if (
                isinstance(technique_id, str)
                and technique_id in techniques
                and technique_id not in normalized
            ):
                normalized.append(technique_id)
            if len(normalized) >= limit:
                break
        return normalized[:limit]

    def _prefilter_call_sync(self, image_data_url: str, techniques: List[str]) -> List[str]:
        if not ENABLE_STAGE0 or len(techniques) <= 1:
            return techniques

        limit = min(STAGE0_MAX_TECHNIQUES, len(techniques))
        start_ts = now_perf()
        print(
            f"  🚀 [stage0-qwen:{self.model_name}] request start "
            f"candidates={len(techniques)} limit={limit}",
            flush=True,
        )
        response = self.client.chat.completions.create(
            model=self.model_name,
            messages=self._build_messages(image_data_url, techniques),
            temperature=STAGE0_TEMPERATURE,
            top_p=STAGE0_TOP_P,
            extra_body=self._build_extra_body(),
        )
        elapsed_ms = (now_perf() - start_ts) * 1000
        print(
            f"  📥 [stage0-qwen:{self.model_name}] response received {elapsed_ms:.0f}ms",
            flush=True,
        )
        text = response.choices[0].message.content or ""
        parsed = parse_json_from_text(text)
        if not isinstance(parsed, dict):
            return techniques
        raw_selected = parsed.get("selected")
        if not isinstance(raw_selected, list):
            return techniques
        normalized = self._normalize_selection(techniques, raw_selected)
        if raw_selected and not normalized:
            return techniques
        return normalized

    async def select_techniques(self, image_bytes: bytes, techniques: List[str]) -> List[str]:
        if not ENABLE_STAGE0 or len(techniques) <= 1:
            return techniques

        start_ts = now_perf()
        _, _, image_data_url = self.prepare_image_payload(image_bytes)
        try:
            selected = await asyncio.to_thread(
                self._prefilter_call_sync,
                image_data_url,
                techniques,
            )
            elapsed_ms = (now_perf() - start_ts) * 1000
            print(
                f"  🔎 [stage0-qwen:{self.model_name}] {elapsed_ms:.0f}ms "
                f"selected={selected}",
                flush=True,
            )
            return selected
        except Exception as error:
            elapsed_ms = (now_perf() - start_ts) * 1000
            print(
                f"  ⚠️ [stage0-qwen:{self.model_name}] {elapsed_ms:.0f}ms "
                f"failed={type(error).__name__}: {error}",
                flush=True,
            )
            return techniques
