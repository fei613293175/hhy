INSERT INTO hhy.app_release_channels (id, code, environment, download_domain, status) VALUES
  (1, 'official', 'TEST', 'https://downloads.example.test', 'ACTIVE'),
  (2, 'official', 'PROD', 'https://downloads.example.com/', 'ACTIVE'),
  (3, 'beta', 'TEST', 'https://beta-downloads.example.test', 'ACTIVE'),
  (4, 'disabled', 'TEST', 'https://disabled.example.test', 'DISABLED');

INSERT INTO hhy.app_build_artifacts (id, object_key, sha256) VALUES
  (10, '/apps/official-test-1.2.3.apk', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'),
  (11, 'apps/official-test-future.apk', 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'),
  (20, 'apps/official-prod-2.2.0.apk', 'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc'),
  (30, 'apps/beta-test-1.3.0.apk', 'dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd');

INSERT INTO hhy.app_release_records (
  id, artifact_id, channel_id, version_name, version_code, update_type, status,
  published_at, min_supported_version_code, release_notes
) VALUES
  (100, 10, 1, '1.2.3', 120300, 'OPTIONAL', 'PUBLISHED',
   '2026-01-01T00:00:00Z', 120000, '测试环境稳定版'),
  (101, 11, 1, '9.9.9', 999999, 'FORCED', 'PUBLISHED',
   '2099-01-01T00:00:00Z', 999000, '未来版本不可提前命中'),
  (200, 20, 2, '2.2.0', 4294967296, 'FORCED', 'PUBLISHED',
   '2026-02-01T00:00:00Z', 4000000000, '生产环境稳定版'),
  (300, 30, 3, '1.3.0-beta', 130000, 'OPTIONAL', 'PUBLISHED',
   '2026-03-01T00:00:00Z', 120000, '测试渠道预览版');
