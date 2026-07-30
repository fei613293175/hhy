#!/usr/bin/env bash
set -euo pipefail

if [[ "${HHY_R14_CANDIDATE_FIXTURE_CONFIRM:-}" != "YES" ]]; then
  echo "Set HHY_R14_CANDIDATE_FIXTURE_CONFIRM=YES for the isolated R14 candidate fixture" >&2
  exit 2
fi

backend=${HHY_CANDIDATE_CONTAINER:?HHY_CANDIDATE_CONTAINER is required}
postgres=${HHY_CANDIDATE_DATABASE_CONTAINER:?HHY_CANDIDATE_DATABASE_CONTAINER is required}
database_user=${HHY_CANDIDATE_DATABASE_USER:?HHY_CANDIDATE_DATABASE_USER is required}
database_name=${HHY_CANDIDATE_DATABASE_NAME:?HHY_CANDIDATE_DATABASE_NAME is required}
manifest=${HHY_R14_TEST_APK_MANIFEST:-artifacts/apk/R14/APK_MANIFEST.yaml}

[[ "$backend" =~ ^hhy-r14-ci-candidate-[a-z0-9]{8,40}$ ]] || {
  echo "Unsafe R14 candidate backend container name" >&2
  exit 2
}
[[ "$postgres" == "hhy-r14-staging-postgres-1" ]] || {
  echo "Unsafe R14 candidate PostgreSQL container name" >&2
  exit 2
}
[[ "$database_user" =~ ^[A-Za-z_][A-Za-z0-9_]{0,62}$ ]] || {
  echo "Unsafe R14 candidate database user" >&2
  exit 2
}
[[ "$database_name" == "hhy_r14_smoke" ]] || {
  echo "Unsafe R14 candidate database name" >&2
  exit 2
}
test -f "$manifest"
docker inspect "$backend" "$postgres" >/dev/null
[[ "$(docker exec "$backend" printenv SPRING_PROFILES_ACTIVE)" == "staging" ]]
[[ "$(docker inspect --format '{{index .Config.Labels "hhy.release"}}' "$backend")" == "R14" ]]

manifest_value() {
  local key=$1
  awk -v key="$key" '
    index($0, key ":") == 1 {
      sub(/^[^:]+:[[:space:]]*/, "")
      gsub(/^[[:space:]"'\'' ]+|[[:space:]"'\'' ]+$/, "")
      print
      exit
    }
  ' "$manifest"
}

release=$(manifest_value release)
apk_file=$(manifest_value apk_file)
version_name=$(manifest_value version_name)
version_code=$(manifest_value version_code)
source_commit=$(manifest_value commit)
apk_sha256=$(manifest_value sha256)
size_bytes=$(manifest_value size_bytes)
signing_fingerprint=$(manifest_value signing_fingerprint)
built_at=$(manifest_value built_at)
download_url=$(manifest_value download_url)
download_domain=https://download.orbexa.cc
object_key=${download_url#${download_domain}/}

[[ "$release" == "R14" ]]
[[ "$apk_file" =~ ^hhy-r14-[a-f0-9]{7}-debug\.apk$ ]]
[[ "$version_name" =~ ^[0-9]+\.[0-9]+\.[0-9]+-debug$ ]]
[[ "$version_code" =~ ^[1-9][0-9]*$ ]]
[[ "$source_commit" =~ ^[a-f0-9]{40}$ ]]
[[ "$apk_sha256" =~ ^[a-f0-9]{64}$ ]]
[[ "$size_bytes" =~ ^[1-9][0-9]*$ ]]
[[ "$signing_fingerprint" =~ ^[a-f0-9]{64}$ ]]
[[ "$built_at" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]]
[[ "$download_url" == "${download_domain}/r14-artifacts/${apk_file}" ]]
[[ "$object_key" == "r14-artifacts/${apk_file}" ]]

fixture_result="$(docker exec -i "$postgres" psql -X -qAt \
  -U "$database_user" -d "$database_name" -v ON_ERROR_STOP=1 \
  -v apk_file="$apk_file" \
  -v version_name="$version_name" \
  -v version_code="$version_code" \
  -v source_commit="$source_commit" \
  -v apk_sha256="$apk_sha256" \
  -v size_bytes="$size_bytes" \
  -v signing_fingerprint="$signing_fingerprint" \
  -v built_at="$built_at" \
  -v download_domain="$download_domain" \
  -v object_key="$object_key" <<'SQL'
BEGIN;
SELECT pg_advisory_xact_lock(709014);

CREATE TEMP TABLE hhy_r14_candidate_manifest AS
SELECT :'apk_file'::text AS apk_file,
       :'version_name'::text AS version_name,
       :'version_code'::bigint AS version_code,
       :'source_commit'::text AS source_commit,
       :'apk_sha256'::text AS apk_sha256,
       :'size_bytes'::bigint AS size_bytes,
       :'signing_fingerprint'::text AS signing_fingerprint,
       :'built_at'::timestamptz AS built_at,
       :'download_domain'::text AS download_domain,
       :'object_key'::text AS object_key;

DO $fixture$
DECLARE
  manifest_row record;
  fixture_profile_id bigint;
  fixture_job_id bigint;
  fixture_artifact_id bigint;
  fixture_channel_id bigint;
  fixture_release_id bigint;
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM hhy.flyway_schema_history WHERE version='044' AND success
  ) THEN
    RAISE EXCEPTION 'R14 candidate fixture requires Flyway V044';
  END IF;
  SELECT * INTO STRICT manifest_row FROM hhy_r14_candidate_manifest;
  IF manifest_row.built_at > clock_timestamp() THEN
    RAISE EXCEPTION 'R14 TEST_APK build time is in the future';
  END IF;

  IF (SELECT count(*) FROM hhy.app_build_profiles
      WHERE name='r14-candidate-startup-policy' AND environment='STAGING') > 1 THEN
    RAISE EXCEPTION 'R14 candidate startup build profile is duplicated';
  END IF;
  SELECT id INTO fixture_profile_id FROM hhy.app_build_profiles
   WHERE name='r14-candidate-startup-policy' AND environment='STAGING';
  IF fixture_profile_id IS NULL THEN
    INSERT INTO hhy.app_build_profiles(
      name,environment,display_name,gradle_params,allowed_refs,status,version
    ) VALUES (
      'r14-candidate-startup-policy','STAGING','R14候选启动版本策略',
      'fixture-only','refs/fixtures/r14/startup-policy','ACTIVE',0
    ) RETURNING id INTO fixture_profile_id;
  ELSIF EXISTS (
    SELECT 1 FROM hhy.app_build_profiles WHERE id=fixture_profile_id AND (
      display_name IS DISTINCT FROM 'R14候选启动版本策略'
      OR gradle_params IS DISTINCT FROM 'fixture-only'
      OR allowed_refs IS DISTINCT FROM 'refs/fixtures/r14/startup-policy'
      OR status IS DISTINCT FROM 'ACTIVE'
    )
  ) THEN
    RAISE EXCEPTION 'R14 candidate startup build profile drifted';
  END IF;

  IF (SELECT count(*) FROM hhy.app_build_jobs
      WHERE profile_id=fixture_profile_id AND triggered_by='r14-candidate-startup-policy'
        AND version_code=manifest_row.version_code) > 1 THEN
    RAISE EXCEPTION 'R14 candidate startup build job is duplicated';
  END IF;
  SELECT id INTO fixture_job_id FROM hhy.app_build_jobs
   WHERE profile_id=fixture_profile_id AND triggered_by='r14-candidate-startup-policy'
     AND version_code=manifest_row.version_code;
  IF fixture_job_id IS NULL THEN
    INSERT INTO hhy.app_build_jobs(
      profile_id,git_ref,commit_sha,version_name,version_code,status,triggered_by,
      started_at,finished_at
    ) VALUES (
      fixture_profile_id,'refs/fixtures/r14/startup-policy',manifest_row.source_commit,
      manifest_row.version_name,manifest_row.version_code,'SUCCEEDED',
      'r14-candidate-startup-policy',manifest_row.built_at,manifest_row.built_at
    ) RETURNING id INTO fixture_job_id;
  ELSIF EXISTS (
    SELECT 1 FROM hhy.app_build_jobs WHERE id=fixture_job_id AND (
      commit_sha IS DISTINCT FROM manifest_row.source_commit
      OR version_name IS DISTINCT FROM manifest_row.version_name
      OR status IS DISTINCT FROM 'SUCCEEDED'
    )
  ) THEN
    RAISE EXCEPTION 'R14 candidate startup build job drifted';
  END IF;

  IF (SELECT count(*) FROM hhy.app_build_artifacts
      WHERE job_id=fixture_job_id AND artifact_type='APK') > 1 THEN
    RAISE EXCEPTION 'R14 candidate startup APK artifact is duplicated';
  END IF;
  SELECT id INTO fixture_artifact_id FROM hhy.app_build_artifacts
   WHERE job_id=fixture_job_id AND artifact_type='APK';
  IF fixture_artifact_id IS NULL THEN
    INSERT INTO hhy.app_build_artifacts(
      job_id,artifact_type,object_key,file_size,sha256,signing_fingerprint,retention_until
    ) VALUES (
      fixture_job_id,'APK',manifest_row.object_key,manifest_row.size_bytes,
      manifest_row.apk_sha256,manifest_row.signing_fingerprint,
      timestamptz '2099-12-31 23:59:59+00'
    ) RETURNING id INTO fixture_artifact_id;
  ELSIF EXISTS (
    SELECT 1 FROM hhy.app_build_artifacts WHERE id=fixture_artifact_id AND (
      object_key IS DISTINCT FROM manifest_row.object_key
      OR file_size IS DISTINCT FROM manifest_row.size_bytes
      OR sha256 IS DISTINCT FROM manifest_row.apk_sha256
      OR signing_fingerprint IS DISTINCT FROM manifest_row.signing_fingerprint
    )
  ) THEN
    RAISE EXCEPTION 'R14 candidate startup APK artifact drifted';
  END IF;

  IF (SELECT count(*) FROM hhy.app_release_channels
      WHERE code='official' AND environment='STAGING') > 1 THEN
    RAISE EXCEPTION 'R14 official STAGING release channel is duplicated';
  END IF;
  SELECT id INTO fixture_channel_id FROM hhy.app_release_channels
   WHERE code='official' AND environment='STAGING';
  IF fixture_channel_id IS NULL THEN
    INSERT INTO hhy.app_release_channels(
      code,environment,download_domain,update_policy,status
    ) VALUES (
      'official','STAGING',manifest_row.download_domain,
      'R14_CANDIDATE_STARTUP_GATE','ACTIVE'
    ) RETURNING id INTO fixture_channel_id;
  ELSIF EXISTS (
    SELECT 1 FROM hhy.app_release_channels WHERE id=fixture_channel_id AND (
      download_domain IS DISTINCT FROM manifest_row.download_domain
      OR update_policy IS DISTINCT FROM 'R14_CANDIDATE_STARTUP_GATE'
      OR status IS DISTINCT FROM 'ACTIVE'
    )
  ) THEN
    RAISE EXCEPTION 'R14 official STAGING release channel drifted';
  END IF;

  IF (SELECT count(*) FROM hhy.app_release_records
      WHERE channel_id=fixture_channel_id AND version_code=manifest_row.version_code) > 1 THEN
    RAISE EXCEPTION 'R14 candidate startup release record is duplicated';
  END IF;
  SELECT id INTO fixture_release_id FROM hhy.app_release_records
   WHERE channel_id=fixture_channel_id AND version_code=manifest_row.version_code;
  IF fixture_release_id IS NULL THEN
    INSERT INTO hhy.app_release_records(
      artifact_id,channel_id,version_name,version_code,update_type,status,published_at,
      min_supported_version_code,release_notes
    ) VALUES (
      fixture_artifact_id,fixture_channel_id,manifest_row.version_name,manifest_row.version_code,
      'NONE','PUBLISHED',manifest_row.built_at,manifest_row.version_code,
      'R14候选启动版本策略'
    ) RETURNING id INTO fixture_release_id;
  ELSIF EXISTS (
    SELECT 1 FROM hhy.app_release_records WHERE id=fixture_release_id AND (
      artifact_id IS DISTINCT FROM fixture_artifact_id
      OR version_name IS DISTINCT FROM manifest_row.version_name
      OR update_type IS DISTINCT FROM 'NONE'
      OR status IS DISTINCT FROM 'PUBLISHED'
      OR published_at IS DISTINCT FROM manifest_row.built_at
      OR min_supported_version_code IS DISTINCT FROM manifest_row.version_code
      OR release_notes IS DISTINCT FROM 'R14候选启动版本策略'
    )
  ) THEN
    RAISE EXCEPTION 'R14 candidate startup release record drifted';
  END IF;
END
$fixture$;

COMMIT;

SELECT 'R14_CANDIDATE_STARTUP_POLICY_OK|release=' || count(DISTINCT release_record.id)
       || '|version=' || min(release_record.version_code)::text
FROM hhy.app_release_channels channel
JOIN hhy.app_release_records release_record ON release_record.channel_id=channel.id
JOIN hhy.app_build_artifacts artifact ON artifact.id=release_record.artifact_id
JOIN hhy_r14_candidate_manifest manifest_row
  ON release_record.version_code=manifest_row.version_code
 AND release_record.version_name=manifest_row.version_name
 AND artifact.object_key=manifest_row.object_key
 AND artifact.sha256=manifest_row.apk_sha256
WHERE channel.code='official' AND channel.environment='STAGING'
  AND channel.status='ACTIVE' AND release_record.status='PUBLISHED'
  AND release_record.update_type='NONE'
  AND release_record.published_at<=clock_timestamp()
HAVING count(DISTINCT release_record.id)=1;
SQL

)"
fixture_result="$(printf '%s\n' "$fixture_result" | awk 'NF { print }')"

expected_result="R14_CANDIDATE_STARTUP_POLICY_OK|release=1|version=${version_code}"
if [[ "$fixture_result" != "$expected_result" ]]; then
  echo "R14 candidate startup policy proof is incomplete" >&2
  exit 2
fi
printf '%s\n' "$fixture_result"
