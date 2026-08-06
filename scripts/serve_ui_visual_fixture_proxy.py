#!/usr/bin/env python3
"""Local-only reverse proxy with frozen UI acceptance fixtures.

This helper is deliberately outside every product build. It forwards Vite assets to an
upstream development server and answers only the existing frozen API paths needed to
render visual-acceptance pages. It refuses non-loopback bind addresses.
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any


REQUEST_ID = "ui-visual-fixture"


R23_CAMPAIGNS = [
    {
        "id": "r23-active-2001", "contentId": "content-r23-2001", "ownerUserId": "owner-r23",
        "status": "ACTIVE", "totalCount": 80, "remainingCount": 63, "amountPerClaimCent": 123,
        "principalCent": 9840, "serviceFeeCent": 0, "startAt": "2026-08-01T08:00:00Z",
        "endAt": "2026-08-31T08:00:00Z", "version": 7,
    },
    {
        "id": "r23-risk-2002", "contentId": "content-r23-2002", "ownerUserId": "owner-r23-risk",
        "status": "PAUSED_BY_RISK", "totalCount": 60, "remainingCount": 59, "amountPerClaimCent": 200,
        "principalCent": 12000, "serviceFeeCent": 0, "startAt": "2026-08-02T08:00:00Z",
        "endAt": "2026-08-30T08:00:00Z", "version": 4,
    },
]


def page(items: list[dict[str, Any]]) -> dict[str, Any]:
    return {
        "items": items,
        "page": {"page": 1, "pageSize": 20, "total": str(len(items)), "hasMore": "false"},
    }


IDENTITIES = [
    {"id": "77", "userId": "42", "status": "MANUAL_REVIEW", "provider": "ALIYUN_MARKET_FACE", "expiresAt": "2026-08-19T08:00:00Z", "version": 3},
    {"id": "78", "userId": "43", "status": "COMPLETED", "provider": "ALIYUN_MARKET_FACE", "expiresAt": "2027-01-12T08:00:00Z", "version": 2},
    {"id": "79", "userId": "44", "status": "REJECTED", "provider": "ALIYUN_MARKET_FACE", "failureCode": "FACE_NOT_MATCH", "expiresAt": "2026-07-30T08:00:00Z", "version": 4},
]

USERS = [
    {"id": "42", "nickname": "验收用户甲", "phoneMasked": "138****5678", "bio": "项目协作账号", "status": "ACTIVE", "identityStatus": "VERIFIED", "membershipStatus": "ACTIVE", "createdAt": "2026-07-18T00:00:00Z", "version": 3},
    {"id": "43", "nickname": "验收用户乙", "phoneMasked": "139****2109", "status": "RESTRICTED", "identityStatus": "MANUAL_REVIEW", "membershipStatus": "ACTIVE", "createdAt": "2026-07-17T00:00:00Z", "version": 5},
    {"id": "44", "nickname": "验收用户丙", "phoneMasked": "136****7812", "status": "FROZEN", "identityStatus": "REJECTED", "membershipStatus": "CANCELLED", "createdAt": "2026-07-16T00:00:00Z", "version": 2},
]

PROVIDERS = [
    {"provider": "sms", "environment": "STAGING", "activeVersion": "sms-v1", "connectionStatus": "ACTIVE", "version": 3},
    {"provider": "storage", "environment": "STAGING", "draftVersion": "storage-v2", "connectionStatus": "VALIDATED", "version": 2},
    {"provider": "payment", "environment": "STAGING", "activeVersion": "payment-v4", "connectionStatus": "CONNECTION_TESTED", "version": 6},
    {"provider": "payout", "environment": "STAGING", "activeVersion": "payout-v1", "draftVersion": "payout-v2", "connectionStatus": "CONNECTION_TESTED", "version": 4},
    {"provider": "identity", "environment": "STAGING", "activeVersion": "identity-v1", "connectionStatus": "CONNECTION_TESTED", "version": 4},
]

DOMAINS = [
    {"code": "api", "environment": "PRODUCTION", "hostname": "api.orbexa.cc", "dnsStatus": "PASSED", "httpsStatus": "PASSED", "certificateStatus": "VALID", "lastVerifiedAt": "2026-07-18T15:40:00Z", "version": 5},
    {"code": "h5", "environment": "PRODUCTION", "hostname": "h5.orbexa.cc", "dnsStatus": "PASSED", "httpsStatus": "PASSED", "certificateStatus": "VALID", "lastVerifiedAt": "2026-07-18T15:35:00Z", "version": 4},
    {"code": "admin", "environment": "STAGING", "hostname": "admin.orbexa.cc", "dnsStatus": "PENDING_USER_DNS", "httpsStatus": "NOT_RUN", "certificateStatus": "PENDING", "version": 2},
]


def api_fixture(path: str, method: str) -> tuple[int, dict[str, Any]] | None:
    parsed = urllib.parse.urlsplit(path)
    route = parsed.path
    if method == "POST" and route == "/admin-api/v1/auth/login":
        return 200, {"data": {"accessToken": "visual-admin-token", "adminUserId": "7", "displayName": "视觉验收员", "expiresAt": "2030-01-01T00:00:00Z", "permissionCodes": ["identity.read", "identity.review", "identity.media.view", "identity.freeze", "user.read", "user.restrict", "user.freeze", "user.security", "config.manage", "domain.read", "domain.write", "domain.verify", "redpacket.read", "redpacket.manage", "redpacket.terminate", "redpacket.finance"], "mfaRequired": "NONE"}, "requestId": REQUEST_ID}
    if method == "GET" and route == "/admin-api/v1/red-packet-campaigns":
        return 200, {"data": page(R23_CAMPAIGNS), "requestId": REQUEST_ID}
    if method == "GET" and route.startswith("/admin-api/v1/red-packet-campaigns/"):
        suffix = route.removeprefix("/admin-api/v1/red-packet-campaigns/")
        campaign_id = suffix.split("/", 1)[0]
        campaign = next((item for item in R23_CAMPAIGNS if item["id"] == campaign_id), R23_CAMPAIGNS[0])
        if suffix.endswith("/sessions"):
            return 200, {"data": page([{"id": "r23-session-1", "campaignId": campaign["id"], "status": "CLAIMED", "requiredSeconds": 20, "accumulatedSeconds": 20, "expiresAt": "2026-08-06T10:01:00Z", "version": 2}]), "requestId": REQUEST_ID}
        if suffix.endswith("/claims"):
            return 200, {"data": page([{"id": "r23-claim-1", "campaignId": campaign["id"], "status": "SETTLED", "amountCent": 123, "amountVersion": 1, "createdAt": "2026-08-06T10:00:20Z", "updatedAt": "2026-08-06T10:00:20Z"}]), "requestId": REQUEST_ID}
        if suffix.endswith("/ledger"):
            return 200, {"data": {"items": [{"id": "r23-ledger-1", "campaignId": campaign["id"], "entryType": "PRINCIPAL", "amountCent": 9840, "balanceAfterCent": 9840, "bizId": "r23-order-1", "createdAt": "2026-08-01T08:00:00Z"}]}, "requestId": REQUEST_ID}
        return 200, {"data": campaign, "requestId": REQUEST_ID}
    if method == "GET" and route == "/admin-api/v1/identities":
        return 200, {"data": page(IDENTITIES), "requestId": REQUEST_ID}
    if method == "GET" and route.startswith("/admin-api/v1/identities/"):
        user_id = route.rsplit("/", 1)[-1]
        item = next((value for value in IDENTITIES if value["userId"] == user_id), IDENTITIES[0])
        return 200, {"data": item, "requestId": REQUEST_ID}
    if method == "GET" and route == "/admin-api/v1/users":
        return 200, {"data": page(USERS), "requestId": REQUEST_ID}
    if method == "GET" and route.startswith("/admin-api/v1/users/"):
        user_id = route.rsplit("/", 1)[-1]
        item = next((value for value in USERS if value["id"] == user_id), USERS[0])
        return 200, {"data": item, "requestId": REQUEST_ID}
    if method == "GET" and route == "/admin-api/v1/provider-configs":
        return 200, {"data": page(PROVIDERS), "requestId": REQUEST_ID}
    if method == "GET" and route.startswith("/admin-api/v1/provider-configs/"):
        provider = route.rsplit("/", 1)[-1]
        base = next((value for value in PROVIDERS if value["provider"] == provider), PROVIDERS[0])
        detail = {**base, "configuredSecrets": [{"key": f"{provider}.credential", "configured": True, "secretRefMasked": "vault://***/cr****"}], "lastTestAt": "2026-07-18T13:50:00Z"}
        return 200, {"data": detail, "requestId": REQUEST_ID}
    if method == "GET" and route == "/admin-api/v1/provider-certificates":
        return 200, {"data": page([{"id": "cert-1", "provider": "payout", "certificateType": "ALIPAY_PRIVATE_KEY", "alias": "主私钥", "fingerprint": "a" * 64, "status": "ACTIVE", "version": 3}]), "requestId": REQUEST_ID}
    if method == "GET" and route == "/admin-api/v1/domains/dns-actions":
        return 200, {"data": page([DOMAINS[2]]), "requestId": REQUEST_ID}
    if method == "GET" and route == "/admin-api/v1/domains":
        return 200, {"data": page(DOMAINS), "requestId": REQUEST_ID}
    if method == "GET" and route == "/public-api/v1/invite/INVITE-R02/registration-config":
        return 200, {"data": page([{"code": "INVITE-R02", "title": "加入合伙云协作空间", "description": "完成安全注册后，即可在 App 中接受邀请并进入对应项目。", "content": [], "version": 7}]), "requestId": REQUEST_ID}
    if method == "POST" and route == "/api/v1/auth/security-challenges":
        return 200, {"data": {"challengeId": "challenge-visual", "challengeType": "IMAGE", "expiresAt": "2026-07-22T06:00:00Z", "imageBase64": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M/wHwAEAQH/69YpWQAAAABJRU5ErkJggg=="}, "requestId": REQUEST_ID}
    return None


class FixtureProxyHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    upstream: str

    def do_GET(self) -> None:  # noqa: N802
        self._handle("GET")

    def do_POST(self) -> None:  # noqa: N802
        self._handle("POST")

    def do_OPTIONS(self) -> None:  # noqa: N802
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Content-Length", "0")
        self.end_headers()

    def _handle(self, method: str) -> None:
        length = int(self.headers.get("Content-Length", "0"))
        request_body = self.rfile.read(length) if length else None
        fixture = api_fixture(self.path, method)
        if fixture is not None:
            status, payload = fixture
            self._send_json(status, payload)
            return
        self._proxy(method, request_body)

    def _send_json(self, status: int, payload: dict[str, Any]) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Request-Id", REQUEST_ID)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _proxy(self, method: str, body: bytes | None) -> None:
        target = urllib.parse.urljoin(f"{self.upstream}/", self.path.lstrip("/"))
        headers = {key: value for key, value in self.headers.items() if key.lower() not in {"host", "connection", "accept-encoding", "content-length"}}
        request = urllib.request.Request(target, data=body, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=15) as response:
                payload = response.read()
                self.send_response(response.status)
                for key, value in response.headers.items():
                    if key.lower() not in {"connection", "transfer-encoding", "content-length", "content-encoding"}:
                        self.send_header(key, value)
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)
        except urllib.error.HTTPError as error:
            payload = error.read()
            self.send_response(error.code)
            self.send_header("Content-Type", error.headers.get("Content-Type", "text/plain"))
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
        except urllib.error.URLError as error:
            self._send_json(502, {"error": {"code": "UI_FIXTURE_UPSTREAM_UNAVAILABLE", "message": str(error.reason)}, "requestId": REQUEST_ID})

    def log_message(self, format_string: str, *args: Any) -> None:
        sys.stdout.write(f"ui-fixture {self.address_string()} {format_string % args}\n")
        sys.stdout.flush()


def main() -> int:
    parser = argparse.ArgumentParser(description="Serve a loopback-only UI fixture reverse proxy")
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--upstream", required=True, help="Vite upstream URL, for example http://127.0.0.1:5173")
    parser.add_argument("--bind", default="127.0.0.1")
    args = parser.parse_args()
    if args.bind not in {"127.0.0.1", "localhost", "::1"}:
        parser.error("visual fixture proxy may bind only to a loopback address")
    upstream = urllib.parse.urlsplit(args.upstream)
    if upstream.scheme not in {"http", "https"} or not upstream.hostname:
        parser.error("--upstream must be an absolute http(s) URL")
    handler = type("BoundFixtureProxyHandler", (FixtureProxyHandler,), {"upstream": args.upstream.rstrip("/")})
    server = ThreadingHTTPServer((args.bind, args.port), handler)
    print(f"UI_VISUAL_FIXTURE_PROXY_READY http://{args.bind}:{args.port} -> {handler.upstream}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
