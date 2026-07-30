#!/usr/bin/env python3
"""Verify the authenticated R14 conversation-list route without exposing credentials."""
from __future__ import annotations

import argparse
import json
import os
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlsplit
from urllib.request import Request, urlopen


def validate_base_url(value: str, allow_loopback: bool) -> str:
    base = value.rstrip("/")
    parsed = urlsplit(base)
    public = (
        parsed.scheme == "https"
        and parsed.hostname == "api.orbexa.cc"
        and parsed.port in (None, 443)
    )
    loopback = (
        allow_loopback
        and parsed.scheme == "http"
        and parsed.hostname == "127.0.0.1"
        and parsed.port is not None
    )
    if not (public or loopback) or parsed.username or parsed.password \
            or parsed.query or parsed.fragment:
        raise RuntimeError("R14 conversation gate only accepts canonical public API or explicit loopback")
    return base


def verify(api_base_url: str, access_token: str, timeout: int, allow_loopback: bool) -> dict[str, object]:
    base = validate_base_url(api_base_url, allow_loopback)
    if not access_token or access_token.count(".") != 2:
        raise RuntimeError("R14 access token is missing or malformed")
    query = urlencode({"page": 1, "pageSize": 20, "sort": "updatedAt:desc"})
    request = Request(
        f"{base}/api/v1/conversations?{query}",
        headers={
            "Authorization": f"Bearer {access_token}",
            "User-Agent": "hhy-r14-public-conversation-gate/1",
        },
    )
    try:
        with urlopen(request, timeout=timeout) as response:
            status = int(response.status)
            payload = json.loads(response.read().decode("utf-8"))
    except HTTPError as exc:
        raise RuntimeError(f"R14 conversation route returned HTTP {exc.code}") from exc
    except (URLError, TimeoutError, json.JSONDecodeError) as exc:
        raise RuntimeError("R14 conversation route failed safely") from exc
    data = payload.get("data") if isinstance(payload, dict) else None
    items = data.get("items") if isinstance(data, dict) else None
    if status != 200 or payload.get("success") is not True \
            or not isinstance(items, list):
        raise RuntimeError("R14 conversation route did not return the frozen success envelope")
    request_id = payload.get("requestId")
    if not isinstance(request_id, str) or len(request_id) < 8:
        raise RuntimeError("R14 conversation route did not return a requestId")
    return {
        "status": "PASS",
        "api_base_url": base,
        "conversation_http": status,
        "conversation_count": len(items),
        "request_id": request_id,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api-base-url", required=True)
    parser.add_argument("--timeout", type=int, default=20)
    parser.add_argument("--allow-loopback", action="store_true")
    args = parser.parse_args()
    try:
        result = verify(
            args.api_base_url,
            os.environ.get("HHY_R14_ACCESS_TOKEN", ""),
            args.timeout,
            args.allow_loopback,
        )
    except RuntimeError as exc:
        print(f"R14_PUBLIC_CONVERSATIONS_FAILED {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
