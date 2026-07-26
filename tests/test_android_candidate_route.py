from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class AndroidCandidateRouteTest(unittest.TestCase):
    def setUp(self) -> None:
        self.script = (
            ROOT / "scripts/switch_android_candidate_route.sh"
        ).read_text(encoding="utf-8")
        self.workflow = (
            ROOT / ".github/workflows/android-quality-gate.yml"
        ).read_text(encoding="utf-8")

    def test_route_switch_is_exact_guarded_and_rollback_capable(self) -> None:
        self.assertIn('HHY_CANDIDATE_ROUTE_CONFIRM" != "YES"', self.script)
        self.assertIn(
            "/www/server/panel/vhost/nginx/api.orbexa.cc.conf",
            self.script,
        )
        self.assertIn("^hhy-r(0[6-9]|[12][0-9]|3[0-2])-ci-candidate-", self.script)
        self.assertIn('docker port "$HHY_CANDIDATE_CONTAINER" 8080/tcp', self.script)
        self.assertIn('curl --fail --silent --show-error --max-time 20 "$local_probe_url"', self.script)
        self.assertIn('cp --preserve=mode,ownership,timestamps "$nginx_config" "$backup"', self.script)
        self.assertIn("nginx -t", self.script)
        self.assertIn("systemctl reload nginx", self.script)
        self.assertIn("rollback()", self.script)
        self.assertIn("trap rollback ERR", self.script)

    def test_public_probe_must_be_observed_in_target_container(self) -> None:
        self.assertIn(
            'public_probe_url="https://api.orbexa.cc/public-api/v1/platform/status"',
            self.script,
        )
        self.assertIn('--header "X-Request-ID: ${sent_request_id}"', self.script)
        self.assertIn('--dump-header "$response_headers"', self.script)
        self.assertIn('tolower($0) ~ /^x-request-id:[[:space:]]*', self.script)
        self.assertIn(
            '[[ ! "$response_request_id" =~ ^[A-Za-z0-9_-]{8,64}$ ]]',
            self.script,
        )
        self.assertIn(
            "Public response did not provide a valid backend X-Request-Id",
            self.script,
        )
        self.assertIn("for probe_attempt in {1..10}; do", self.script)
        self.assertIn("break 2", self.script)
        self.assertIn('docker logs --since "$probe_since" "$HHY_CANDIDATE_CONTAINER"', self.script)
        self.assertIn('grep -F "\\"requestId\\":\\"${response_request_id}\\"" >/dev/null', self.script)
        self.assertIn('trap cleanup_headers EXIT', self.script)
        self.assertIn(
            "Public request did not reach the intended candidate container",
            self.script,
        )

    def test_bootstrap_preflight_runs_before_heavy_android_setup(self) -> None:
        bootstrap = self.workflow.index(
            "- name: Request one-time Staging CI bootstrap code",
            self.workflow.index("  emulator:"),
        )
        setup_java = self.workflow.index(
            "- uses: actions/setup-java@v4",
            bootstrap,
        )
        setup_android = self.workflow.index(
            "- uses: android-actions/setup-android@v3",
            bootstrap,
        )
        enable_kvm = self.workflow.index(
            "- name: Enable KVM for the Android emulator",
            bootstrap,
        )
        self.assertLess(bootstrap, setup_java)
        self.assertLess(bootstrap, setup_android)
        self.assertLess(bootstrap, enable_kvm)
        self.assertEqual(
            1,
            self.workflow.count("- name: Request one-time Staging CI bootstrap code"),
        )

    def test_bootstrap_diagnostics_identify_stage_without_leaking_body(self) -> None:
        self.assertIn("GitHub OIDC token endpoint returned HTTP ${oidc_status}", self.workflow)
        self.assertIn("Staging bootstrap endpoint returned HTTP ${bootstrap_status}", self.workflow)
        self.assertIn(
            "response body is intentionally suppressed",
            self.workflow,
        )
        self.assertIn(
            "response body and bearer token are intentionally suppressed",
            self.workflow,
        )
        self.assertIn('trap \'rm -f "$oidc_file" "$bootstrap_file"\' EXIT', self.workflow)
        self.assertNotIn('<<<"$oidc_response"', self.workflow)
        self.assertNotIn('<<<"$bootstrap_response"', self.workflow)


if __name__ == "__main__":
    unittest.main()
