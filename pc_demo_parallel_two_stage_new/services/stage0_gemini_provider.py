"""
Gemini Stage 0 provider。
职责：对候选构图技术做软预筛，返回更值得进入 Stage 1 的 technique 列表。
"""
import asyncio
import base64
from io import BytesIO
from typing import List

from google.genai import types
from PIL import Image

from config import (
    ENABLE_STAGE0,
    GEMINI_STAGE0_MODEL,
    STAGE0_MAX_TECHNIQUES,
    STAGE0_SOFT_PREFILTER_PROMPT_TEMPLATE,
    STAGE0_TEMPERATURE,
    STAGE0_TOP_P,
    TECHNIQUE_CONFIGS,
)
from services.common import image_to_data_url, now_perf, parse_json_from_text, prepare_image_bytes
from services.gemini_client_factory import create_gemini_client, describe_gemini_backend


class GeminiStage0Provider:
    provider_name = "gemini"
    model_name = GEMINI_STAGE0_MODEL

    def __init__(self):
        self.client = create_gemini_client()
        print(f"✅ GeminiStage0Provider 初始化成功 [{describe_gemini_backend()}]")

    def prepare_image_payload(self, image_bytes: bytes) -> tuple[bytes, str, str]:
        processed, mime = prepare_image_bytes(image_bytes)
        return processed, mime, image_to_data_url(processed, mime)

    def _image_from_data_url(self, image_data_url: str) -> Image.Image:
        _, b64 = image_data_url.split("base64,", 1)
        image_bytes = base64.b64decode(b64)
        image = Image.open(BytesIO(image_bytes))
        if image.mode == "RGBA":
            image = image.convert("RGB")
        return image

    def _build_config(self) -> types.GenerateContentConfig:
        return types.GenerateContentConfig(
            temperature=STAGE0_TEMPERATURE,
            top_p=STAGE0_TOP_P,
            response_modalities=["TEXT"],
            response_mime_type="application/json",
        )

    def _build_prompt(self, techniques: List[str]) -> str:
        technique_lines = [
            f'- "{technique_id}": {TECHNIQUE_CONFIGS[technique_id].name}'
            for technique_id in techniques
        ]
        return STAGE0_SOFT_PREFILTER_PROMPT_TEMPLATE.format(
            max_techniques=min(STAGE0_MAX_TECHNIQUES, len(techniques)),
            candidate_techniques="\n".join(technique_lines),
        )

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

        image = self._image_from_data_url(image_data_url)
        limit = min(STAGE0_MAX_TECHNIQUES, len(techniques))
        start_ts = now_perf()
        print(
            f"  🚀 [stage0-gemini:{self.model_name}] request start "
            f"candidates={len(techniques)} limit={limit}",
            flush=True,
        )
        response = self.client.models.generate_content(
            model=self.model_name,
            contents=[self._build_prompt(techniques), image],
            config=self._build_config(),
        )
        elapsed_ms = (now_perf() - start_ts) * 1000
        print(
            f"  📥 [stage0-gemini:{self.model_name}] response received {elapsed_ms:.0f}ms",
            flush=True,
        )
        text = getattr(response, "text", None) or ""
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
                f"  🔎 [stage0-gemini:{self.model_name}] {elapsed_ms:.0f}ms "
                f"selected={selected}",
                flush=True,
            )
            return selected
        except Exception as error:
            elapsed_ms = (now_perf() - start_ts) * 1000
            print(
                f"  ⚠️ [stage0-gemini:{self.model_name}] {elapsed_ms:.0f}ms "
                f"failed={type(error).__name__}: {error}",
                flush=True,
            )
            return techniques
