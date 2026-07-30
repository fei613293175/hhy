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
        self.r14_fixture = (
            ROOT / "scripts/prepare_r14_candidate_fixture.sh"
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
        self.assertIn("HHY_CANDIDATE_REGISTRATION_INVITE_CODE is required", self.script)
        self.assertIn('printenv SPRING_PROFILES_ACTIVE', self.script)
        self.assertIn('!= "staging"', self.script)
        self.assertIn('local_readiness_url="http://${HHY_TARGET_UPSTREAM}/api/v1/auth/invite-codes/validate"', self.script)
        self.assertIn('public_readiness_url="https://api.orbexa.cc/api/v1/auth/invite-codes/validate"', self.script)
        self.assertIn('"$local_readiness_url" >/dev/null', self.script)
        self.assertIn('"$public_readiness_url" >/dev/null', self.script)
        self.assertIn('cp --preserve=mode,ownership,timestamps "$nginx_config" "$backup"', self.script)
        self.assertIn("nginx -t", self.script)
        self.assertIn("systemctl reload nginx", self.script)
        self.assertIn("rollback()", self.script)
        self.assertIn("trap rollback ERR", self.script)
        self.assertIn("HHY_OWNER_TEST_UPSTREAM is required", self.script)
        self.assertIn("hhy-owner-test-postgres-data", self.script)
        self.assertIn("HHY_CI_AUTOMATION_ENABLED", self.script)
        self.assertIn('owner_test_route_lease_minutes="${HHY_OWNER_TEST_ROUTE_LEASE_MINUTES:-45}"', self.script)
        self.assertIn("candidate-route-lease.env", self.script)
        self.assertIn("systemd-run --quiet --collect", self.script)
        self.assertIn("--on-active=\"${owner_test_route_lease_minutes}m\"", self.script)
        self.assertIn("scripts/restore_android_candidate_route.sh", self.script)

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

    def test_r14_probe_is_authenticated_and_excludes_invite_readiness(self) -> None:
        self.assertIn("HHY_CANDIDATE_ACCESS_TOKEN is required", self.script)
        self.assertIn("HHY_EXPECTED_CANDIDATE_IMAGE_ID is required", self.script)
        self.assertIn("HHY_EXPECTED_SOURCE_COMMIT is required", self.script)
        self.assertIn('^[a-f0-9]{40}$', self.script)
        self.assertIn('repository_commit="$(git rev-parse --verify HEAD)"', self.script)
        self.assertIn('git status --porcelain --untracked-files=normal', self.script)
        self.assertIn(
            '{{index .Config.Labels "hhy.source_commit"}}',
            self.script,
        )
        self.assertIn(
            'container_source_commit" != "$HHY_EXPECTED_SOURCE_COMMIT',
            self.script,
        )
        self.assertIn(
            "Candidate container source commit does not match the frozen repository commit",
            self.script,
        )
        self.assertIn(
            "Candidate image does not match the frozen R14 image ID",
            self.script,
        )
        self.assertIn("Candidate release label is not R14", self.script)
        self.assertIn("HHY_CANDIDATE_NETWORK is required", self.script)
        self.assertIn("Candidate container is not attached to the exact R14 staging network", self.script)
        self.assertIn("Owner-test backend is not attached to the persistent network", self.script)
        self.assertIn("Candidate database binding does not match the exact R14 smoke database", self.script)
        self.assertIn("Candidate MFA secret volume must be writable", self.script)
        self.assertIn('{{range .Mounts}}{{if eq .Destination "/var/lib/hhy/secrets"}}{{.RW}}', self.script)
        self.assertIn(
            "Candidate database has not reached Flyway V044",
            self.script,
        )
        self.assertIn("HHY_R14_CANDIDATE_FIXTURE_CONFIRM is required", self.script)
        self.assertIn("bash scripts/prepare_r14_candidate_fixture.sh", self.script)
        self.assertLess(
            self.script.index("bash scripts/prepare_r14_candidate_fixture.sh"),
            self.script.index('nginx_config="/www/server/panel/vhost/nginx/api.orbexa.cc.conf"'),
        )
        self.assertIn(
            'local_startup_version_url="http://${HHY_TARGET_UPSTREAM}/public-api/v1/app/version-check"',
            self.script,
        )
        self.assertIn(
            'public_startup_version_url="https://api.orbexa.cc/public-api/v1/app/version-check"',
            self.script,
        )
        self.assertEqual(2, self.script.count('--data "$startup_version_payload"'))
        self.assertIn(
            'public_readiness_url="https://api.orbexa.cc/api/v1/conversations?',
            self.script,
        )
        self.assertIn(
            '--header "Authorization: Bearer ${HHY_CANDIDATE_ACCESS_TOKEN}"',
            self.script,
        )
        r14_guard = self.script[
            self.script.index('if [[ "$probe_mode" == "R14_CONVERSATIONS" ]]'):
            self.script.index('nginx_config=')
        ]
        self.assertNotIn("invite-codes", r14_guard)

    def test_r14_startup_policy_fixture_is_manifest_bound_and_idempotent(self) -> None:
        self.assertIn('HHY_R14_CANDIDATE_FIXTURE_CONFIRM:-}" != "YES"', self.r14_fixture)
        self.assertIn("artifacts/apk/R14/APK_MANIFEST.yaml", self.r14_fixture)
        self.assertIn("SELECT pg_advisory_xact_lock(709014)", self.r14_fixture)
        self.assertIn("fixture_profile_id bigint", self.r14_fixture)
        self.assertIn("fixture_job_id bigint", self.r14_fixture)
        self.assertIn("fixture_artifact_id bigint", self.r14_fixture)
        self.assertIn("fixture_channel_id bigint", self.r14_fixture)
        self.assertIn("fixture_release_id bigint", self.r14_fixture)
        self.assertNotIn("WHERE profile_id=profile_id", self.r14_fixture)
        self.assertNotIn("WHERE job_id=job_id", self.r14_fixture)
        self.assertNotIn("WHERE channel_id=channel_id", self.r14_fixture)
        self.assertIn("R14_CANDIDATE_STARTUP_POLICY_OK|release=1|version=", self.r14_fixture)
        self.assertIn("awk 'NF { print }'", self.r14_fixture)
        self.assertIn('[[ "$fixture_result" != "$expected_result" ]]', self.r14_fixture)

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
