from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "infra/nginx/ws.orbexa.cc.conf"


class R14RealtimeProxyTest(unittest.TestCase):
    def test_template_forwards_websocket_upgrade_and_frozen_protocol_header(self) -> None:
        config = CONFIG.read_text(encoding="utf-8")
        for marker in (
            "server_name ws.orbexa.cc;",
            "location = /ws",
            "proxy_http_version 1.1;",
            "proxy_set_header Upgrade $http_upgrade;",
            "proxy_set_header Connection $hhy_ws_connection;",
            "proxy_set_header Sec-WebSocket-Protocol $http_sec_websocket_protocol;",
            "proxy_buffering off;",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, config)

    def test_access_log_never_records_protocol_credential_header(self) -> None:
        config = CONFIG.read_text(encoding="utf-8")
        log_format = re.search(
            r"log_format\s+hhy_ws_safe\s+(.*?);", config, flags=re.DOTALL
        )
        self.assertIsNotNone(log_format)
        normalized = log_format.group(1).lower()
        self.assertNotIn("sec_websocket_protocol", normalized)
        self.assertNotIn("request_headers", normalized)
        self.assertIn("access_log /var/log/nginx/ws.orbexa.cc.access.log hhy_ws_safe;", config)

    def test_template_does_not_claim_activation(self) -> None:
        config = CONFIG.read_text(encoding="utf-8")
        self.assertIn("deployment template only", config)
        self.assertNotIn("ACTIVATED=PASS", config)


if __name__ == "__main__":
    unittest.main()
