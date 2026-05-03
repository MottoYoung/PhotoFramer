"""
Gemini Client 工厂。

支持两种模式：
1. 官方 Gemini API（GEMINI_API_KEY）
2. 国内 Gemini 兼容中转（Authorization: Bearer + 自定义 base_url）
"""
import os
from contextlib import contextmanager

from google import genai
from google.genai import types

from config import (
    GEMINI_API_KEY,
    GEMINI_DOMESTIC_API_KEY,
    GEMINI_DOMESTIC_API_VERSION,
    GEMINI_DOMESTIC_BASE_URL,
    USE_GEMINI_DOMESTIC_API,
)


def _normalize_base_url(url: str) -> str:
    return url.strip().rstrip("/") + "/"


def _mask_token(token: str | None) -> str:
    if not token:
        return "<missing>"
    stripped = token.strip()
    if len(stripped) <= 8:
        return stripped
    return f"{stripped[:4]}...{stripped[-4:]}"


@contextmanager
def _without_official_gemini_env():
    """
    国内代理模式下，避免 google-genai SDK 自动读取官方环境变量并注入 x-goog-api-key。
    """
    keys = ("GEMINI_API_KEY", "GOOGLE_API_KEY")
    original = {key: os.environ.get(key) for key in keys}
    try:
        for key in keys:
            os.environ.pop(key, None)
        yield
    finally:
        for key, value in original.items():
            if value is not None:
                os.environ[key] = value


def _build_domestic_http_options() -> types.HttpOptions:
    return types.HttpOptions(
        base_url=_normalize_base_url(GEMINI_DOMESTIC_BASE_URL),
        api_version=GEMINI_DOMESTIC_API_VERSION or "v1beta",
        headers={
            "Authorization": f"Bearer {GEMINI_DOMESTIC_API_KEY.strip()}",
        },
    )


def _strip_sdk_api_key_header(client: genai.Client) -> genai.Client:
    headers = getattr(client._api_client._http_options, "headers", None)
    if isinstance(headers, dict) and "x-goog-api-key" in headers:
        headers.pop("x-goog-api-key", None)
    return client


def create_gemini_client() -> genai.Client:
    if USE_GEMINI_DOMESTIC_API:
        if not GEMINI_DOMESTIC_BASE_URL:
            raise ValueError("请设置 GEMINI_DOMESTIC_BASE_URL 环境变量")
        if not GEMINI_DOMESTIC_API_KEY:
            raise ValueError("请设置 GEMINI_DOMESTIC_API_KEY 环境变量")
        official_env_present = any(
            os.environ.get(key) for key in ("GEMINI_API_KEY", "GOOGLE_API_KEY")
        )
        print(
            "🔐 Gemini 国内代理模式启用 "
            f"[base={_normalize_base_url(GEMINI_DOMESTIC_BASE_URL)}] "
            f"[api_version={GEMINI_DOMESTIC_API_VERSION or 'v1beta'}] "
            f"[bearer={_mask_token(GEMINI_DOMESTIC_API_KEY)}] "
            f"[official_env_present={official_env_present}]",
            flush=True,
        )
        with _without_official_gemini_env():
            client = genai.Client(
                api_key=GEMINI_DOMESTIC_API_KEY.strip(),
                http_options=_build_domestic_http_options()
            )
        client = _strip_sdk_api_key_header(client)
        print(
            f"🔐 Gemini 国内代理请求头已清理 [x-goog-api-key stripped={'x-goog-api-key' not in client._api_client._http_options.headers}]",
            flush=True,
        )
        return client

    if not GEMINI_API_KEY:
        raise ValueError("请设置 GEMINI_API_KEY 环境变量")
    print(
        "🔐 Gemini 官方模式启用 "
        f"[api_key={_mask_token(GEMINI_API_KEY)}]",
        flush=True,
    )
    return genai.Client(api_key=GEMINI_API_KEY)


def describe_gemini_backend() -> str:
    if USE_GEMINI_DOMESTIC_API:
        base = GEMINI_DOMESTIC_BASE_URL.strip() if GEMINI_DOMESTIC_BASE_URL else "<missing>"
        version = GEMINI_DOMESTIC_API_VERSION or "v1beta"
        return f"domestic-proxy {base} [{version}]"
    return "official Gemini API"
