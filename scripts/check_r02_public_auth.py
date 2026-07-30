#!/usr/bin/env python3
"""Verify the anonymous R02 auth endpoints required by the Android APK."""
from __future__ import annotations

import argparse
import base64
import binascii
import json
import ssl
import struct
import sys
import uuid
import zlib
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen


def request_json(
        request: Request, timeout: int, *, allow_http_error: bool = False,
) -> tuple[int, dict[str, object]]:
    try:
        with urlopen(request, timeout=timeout, context=ssl.create_default_context()) as response:
            status = int(response.status)
            payload = json.loads(response.read().decode("utf-8"))
    except HTTPError as exc:
        if not allow_http_error:
            raise RuntimeError(f"{request.method} {request.full_url} returned HTTP {exc.code}") from exc
        status = int(exc.code)
        try:
            payload = json.loads(exc.read().decode("utf-8"))
        except json.JSONDecodeError as decode_error:
            raise RuntimeError(f"{request.method} {request.full_url} returned invalid JSON") from decode_error
    except (URLError, TimeoutError, json.JSONDecodeError) as exc:
        raise RuntimeError(f"{request.method} {request.full_url} failed safely") from exc
    if not isinstance(payload, dict):
        raise RuntimeError(f"{request.method} {request.full_url} returned a non-object body")
    return status, payload


DIGIT_GLYPHS = (
    ("11111", "10001", "10011", "10101", "11001", "10001", "11111"),
    ("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
    ("11110", "00001", "00001", "11110", "10000", "10000", "11111"),
    ("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
    ("10010", "10010", "10010", "11111", "00010", "00010", "00010"),
    ("11111", "10000", "10000", "11110", "00001", "00001", "11110"),
    ("01111", "10000", "10000", "11110", "10001", "10001", "01110"),
    ("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
    ("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
    ("01110", "10001", "10001", "01111", "00001", "00001", "11110"),
)


def challenge_answer(image: bytes) -> str:
    chunks: list[bytes] = []
    offset = 8
    while offset + 12 <= len(image):
        length = struct.unpack(">I", image[offset:offset + 4])[0]
        kind = image[offset + 4:offset + 8]
        data = image[offset + 8:offset + 8 + length]
        if kind == b"IDAT":
            chunks.append(data)
        offset += 12 + length
    raw = zlib.decompress(b"".join(chunks))
    row_size = 1 + 160 * 3
    if len(raw) != row_size * 56 or any(raw[row * row_size] != 0 for row in range(56)):
        raise RuntimeError("security-challenges PNG uses an unsupported raster layout")
    answer = []
    for index in range(4):
        glyph = []
        for row in range(7):
            bits = []
            for column in range(5):
                x = 24 + index * 29 + column * 4 + 2
                y = 14 + row * 4 + 2
                pixel = y * row_size + 1 + x * 3
                bits.append("1" if sum(raw[pixel:pixel + 3]) < 300 else "0")
            glyph.append("".join(bits))
        try:
            answer.append(str(DIGIT_GLYPHS.index(tuple(glyph))))
        except ValueError as exc:
            raise RuntimeError("security-challenges PNG contains an unknown digit glyph") from exc
    return "".join(answer)


def verify(api_base_url: str, timeout: int) -> dict[str, object]:
    base = api_base_url.rstrip("/")
    parsed = urlsplit(base)
    if parsed.scheme != "https" or parsed.hostname != "api.orbexa.cc" or parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise RuntimeError("R02 public auth gate only accepts canonical https://api.orbexa.cc")

    registration_status, registration = request_json(
        Request(f"{base}/api/v1/auth/registration-config", headers={"User-Agent": "hhy-r02-public-gate/1"}),
        timeout,
    )
    nonce = f"r02-public-gate-{uuid.uuid4()}"
    challenge_body = json.dumps({
        "scene": "LOGIN",
        "clientNonce": nonce,
        "deviceFingerprint": "release-gate",
    }).encode("utf-8")
    challenge_status, challenge = request_json(
        Request(
            f"{base}/api/v1/auth/security-challenges",
            data=challenge_body,
            method="POST",
            headers={
                "Content-Type": "application/json",
                "User-Agent": "hhy-r02-public-gate/1",
                "X-Idempotency-Key": nonce,
            },
        ),
        timeout,
    )
    if registration_status != 200 or registration.get("success") is not True:
        raise RuntimeError("registration-config did not return the frozen success envelope")
    challenge_data = challenge.get("data")
    if challenge_status != 200 or challenge.get("success") is not True or not isinstance(challenge_data, dict) or not challenge_data.get("challengeId"):
        raise RuntimeError("security-challenges did not return a usable frozen success envelope")
    image_base64 = challenge_data.get("imageBase64")
    if not isinstance(image_base64, str):
        raise RuntimeError("security-challenges did not return a raster challenge image")
    try:
        image = base64.b64decode(image_base64, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise RuntimeError("security-challenges returned invalid imageBase64") from exc
    if len(image) < 24 or image[:8] != b"\x89PNG\r\n\x1a\n":
        raise RuntimeError("security-challenges challenge image is not PNG")
    width, height = struct.unpack(">II", image[16:24])
    if (width, height) != (160, 56):
        raise RuntimeError(f"security-challenges challenge PNG has unexpected dimensions {width}x{height}")
    answer = challenge_answer(image)

    def login(challenge_id: str, proof: str) -> tuple[int, dict[str, object]]:
        body = json.dumps({
            "phone": "19900000001",
            "password": "ValidPass99",
            "challengeId": challenge_id,
            "challengeProof": proof,
            "device": {
                "deviceFingerprint": "release-gate",
                "model": "release-gate",
                "platform": "ANDROID",
                "osVersion": "1",
                "appVersion": "gate",
            },
        }).encode("utf-8")
        key = f"r02-login-gate-{uuid.uuid4()}"
        return request_json(Request(
            f"{base}/api/v1/auth/password/login",
            data=body,
            method="POST",
            headers={
                "Content-Type": "application/json",
                "User-Agent": "hhy-r02-public-gate/1",
                "X-Idempotency-Key": key,
            },
        ), timeout, allow_http_error=True)

    correct_status, correct_payload = login(str(challenge_data["challengeId"]), answer)
    correct_error = correct_payload.get("error")
    if correct_status != 401 or not isinstance(correct_error, dict) \
            or correct_error.get("code") != "COMMON-401-UNAUTHENTICATED":
        raise RuntimeError("correct security challenge did not continue to credential validation")

    wrong_nonce = f"r02-public-gate-wrong-{uuid.uuid4()}"
    wrong_status, wrong_challenge = request_json(Request(
        f"{base}/api/v1/auth/security-challenges",
        data=json.dumps({
            "scene": "LOGIN",
            "clientNonce": wrong_nonce,
            "deviceFingerprint": "release-gate",
        }).encode("utf-8"),
        method="POST",
        headers={
            "Content-Type": "application/json",
            "User-Agent": "hhy-r02-public-gate/1",
            "X-Idempotency-Key": wrong_nonce,
        },
    ), timeout)
    wrong_data = wrong_challenge.get("data")
    if wrong_status != 200 or not isinstance(wrong_data, dict):
        raise RuntimeError("second security challenge could not be created")
    wrong_proof = "0000" if answer != "0000" else "1111"
    rejected_status, rejected_payload = login(str(wrong_data["challengeId"]), wrong_proof)
    rejected_error = rejected_payload.get("error")
    if rejected_status != 422 or not isinstance(rejected_error, dict) \
            or rejected_error.get("code") != "AUTH-422-SECURITY_CHALLENGE_INVALID":
        raise RuntimeError("wrong security challenge did not use the dedicated error code")
    return {
        "status": "PASS",
        "api_base_url": base,
        "registration_config_http": registration_status,
        "security_challenge_http": challenge_status,
        "registration_request_id": registration.get("requestId"),
        "challenge_request_id": challenge.get("requestId"),
        "challenge_image": f"PNG {width}x{height}",
        "correct_challenge_continues_to_credentials": "PASS",
        "wrong_challenge_error_code": "AUTH-422-SECURITY_CHALLENGE_INVALID",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api-base-url", required=True)
    parser.add_argument("--timeout", type=int, default=20)
    args = parser.parse_args()
    try:
        result = verify(args.api_base_url, args.timeout)
    except RuntimeError as exc:
        print(f"R02_PUBLIC_AUTH_FAILED {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
