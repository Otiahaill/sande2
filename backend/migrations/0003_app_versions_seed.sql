-- Migration 0003: Initial app version seed
-- Seeds the app_versions table for minimum version enforcement

INSERT INTO app_versions (id, version_code, version_name, min_supported_version_code, platform, release_notes, download_url, is_mandatory, released_at, created_at)
VALUES ('app_v1_android', 1, '1.0.0', 1, 'android', 'Initial release', NULL, 1, strftime('%s', 'now'), strftime('%s', 'now'));

INSERT INTO app_versions (id, version_code, version_name, min_supported_version_code, platform, release_notes, download_url, is_mandatory, released_at, created_at)
VALUES ('app_v1_android_2', 2, '1.1.0', 1, 'android', 'Security hardening and sync hardening', NULL, 0, strftime('%s', 'now'), strftime('%s', 'now'));
