#!/usr/bin/env bash
set -euo pipefail
set +x
export LC_ALL=C

: "${DATABASE_URL:?DATABASE_URL is required}"
: "${HHY_BOOTSTRAP_ADMIN_USERNAME:?HHY_BOOTSTRAP_ADMIN_USERNAME is required}"
: "${HHY_BOOTSTRAP_ADMIN_PASSWORD_HASH:?HHY_BOOTSTRAP_ADMIN_PASSWORD_HASH is required}"

if [[ "${HHY_BOOTSTRAP_ADMIN_CONFIRM:-}" != "YES" ]]; then
  echo "Refusing administrator bootstrap without HHY_BOOTSTRAP_ADMIN_CONFIRM=YES" >&2
  exit 2
fi
if [[ -n "${HHY_BOOTSTRAP_ADMIN_PASSWORD:-}" ]]; then
  echo "Plaintext administrator passwords are forbidden; provide only HHY_BOOTSTRAP_ADMIN_PASSWORD_HASH" >&2
  exit 2
fi
command -v psql >/dev/null 2>&1 || { echo "psql is required" >&2; exit 2; }

bootstrap_username="${HHY_BOOTSTRAP_ADMIN_USERNAME}"
bootstrap_hash="${HHY_BOOTSTRAP_ADMIN_PASSWORD_HASH}"

if (( ${#bootstrap_username} < 3 || ${#bootstrap_username} > 128 )) \
  || [[ ! "${bootstrap_username}" =~ ^[A-Za-z0-9._@-]+$ ]]; then
  echo "Administrator username must be 3-128 safe ASCII characters" >&2
  exit 2
fi
if [[ ! "${bootstrap_hash}" =~ ^\$2[aby]\$1[0-4]\$[./A-Za-z0-9]{53}$ ]]; then
  echo "Administrator password hash must be a pre-generated BCrypt hash with cost 10-14" >&2
  exit 2
fi

psql "${DATABASE_URL}" -X -v ON_ERROR_STOP=1 --single-transaction <<'SQL' >/dev/null
\getenv bootstrap_username HHY_BOOTSTRAP_ADMIN_USERNAME
\getenv bootstrap_hash HHY_BOOTSTRAP_ADMIN_PASSWORD_HASH
SET search_path TO hhy, public;

CREATE TEMP TABLE bootstrap_admin_input(
  username varchar(255) NOT NULL,
  password_hash varchar(128) NOT NULL
) ON COMMIT DROP;
INSERT INTO bootstrap_admin_input(username, password_hash)
VALUES (:'bootstrap_username', :'bootstrap_hash');

DO $$
DECLARE
  v_existing hhy.admin_users%ROWTYPE;
  v_input bootstrap_admin_input%ROWTYPE;
BEGIN
  SELECT * INTO STRICT v_input FROM bootstrap_admin_input;
  SELECT * INTO v_existing
  FROM hhy.admin_users
  WHERE username = v_input.username;

  IF FOUND AND v_existing.password_hash IS DISTINCT FROM v_input.password_hash THEN
    RAISE EXCEPTION 'ADMIN_BOOTSTRAP_CREDENTIAL_CONFLICT username=%', v_input.username
      USING ERRCODE = '23514';
  END IF;
  IF FOUND AND v_existing.status <> 'ACTIVE' THEN
    RAISE EXCEPTION 'ADMIN_BOOTSTRAP_ACCOUNT_NOT_ACTIVE username=%', v_input.username
      USING ERRCODE = '23514';
  END IF;
END;
$$;

INSERT INTO hhy.admin_users(username, password_hash, status)
SELECT username, password_hash, 'ACTIVE'
FROM bootstrap_admin_input
ON CONFLICT (username) DO NOTHING;

INSERT INTO hhy.admin_user_roles(admin_id, role_id)
SELECT admin.id, role.id
FROM hhy.admin_users AS admin
JOIN hhy.admin_roles AS role ON role.code = 'SUPER_ADMIN' AND role.status = 'ACTIVE'
JOIN bootstrap_admin_input AS input ON input.username = admin.username
ON CONFLICT (admin_id, role_id) DO NOTHING;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM hhy.admin_users AS admin
    JOIN hhy.admin_user_roles AS user_role ON user_role.admin_id = admin.id
    JOIN hhy.admin_roles AS role ON role.id = user_role.role_id
    JOIN bootstrap_admin_input AS input ON input.username = admin.username
    WHERE admin.username = input.username
      AND admin.status = 'ACTIVE'
      AND role.code = 'SUPER_ADMIN'
      AND role.status = 'ACTIVE'
  ) THEN
    RAISE EXCEPTION 'ADMIN_BOOTSTRAP_SUPER_ADMIN_BINDING_FAILED'
      USING ERRCODE = '23514';
  END IF;
END;
$$;
SQL

unset bootstrap_hash HHY_BOOTSTRAP_ADMIN_PASSWORD_HASH
echo "BOOTSTRAP_ADMIN_OK role=SUPER_ADMIN"
