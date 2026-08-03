-- R17 payment, callback replay protection, exception, and reconciliation invariants.
SET search_path TO hhy, public;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM hhy.payment_transactions
    WHERE btrim(gateway) = '' OR btrim(status) = '' OR amount IS NULL OR amount < 0
  ) THEN
    RAISE EXCEPTION 'R17_DIRTY_PAYMENT_TRANSACTION_INVALID' USING ERRCODE = '23514';
  END IF;
  IF EXISTS (
    SELECT 1 FROM hhy.payment_callbacks
    WHERE btrim(gateway) = '' OR payload_cipher IS NULL
  ) THEN
    RAISE EXCEPTION 'R17_DIRTY_PAYMENT_CALLBACK_INVALID' USING ERRCODE = '23514';
  END IF;
  IF EXISTS (
    SELECT 1 FROM hhy.payment_exception_orders
    WHERE btrim(status) = '' OR type IS NULL OR btrim(type) = '' OR version < 0
  ) THEN
    RAISE EXCEPTION 'R17_DIRTY_PAYMENT_EXCEPTION_INVALID' USING ERRCODE = '23514';
  END IF;
  IF EXISTS (
    SELECT 1 FROM hhy.payment_reconciliation_records
    WHERE gateway IS NULL OR btrim(gateway) = ''
  ) THEN
    RAISE EXCEPTION 'R17_DIRTY_PAYMENT_RECONCILIATION_INVALID' USING ERRCODE = '23514';
  END IF;
END;
$$;

ALTER TABLE hhy.payment_transactions
  ADD COLUMN currency varchar(3) DEFAULT 'CNY' NOT NULL,
  ADD COLUMN paid_at timestamptz,
  ADD COLUMN reconciliation_status varchar(64) DEFAULT 'NOT_RECONCILED' NOT NULL,
  ADD COLUMN version bigint DEFAULT 0 NOT NULL,
  ADD COLUMN return_url varchar(2048),
  ADD COLUMN idempotency_key varchar(128),
  ADD COLUMN request_hash varchar(64),
  ADD COLUMN legacy_without_idempotency boolean DEFAULT false NOT NULL;

UPDATE hhy.payment_transactions SET legacy_without_idempotency = true;

ALTER TABLE hhy.payment_transactions
  ALTER COLUMN amount SET NOT NULL,
  ADD CONSTRAINT ck_r17_payment_amount CHECK (amount >= 0),
  ADD CONSTRAINT ck_r17_payment_currency CHECK (currency ~ '^[A-Z]{3}$'),
  ADD CONSTRAINT ck_r17_payment_version CHECK (version >= 0),
  ADD CONSTRAINT ck_r17_payment_required_text CHECK (
    btrim(gateway) <> '' AND btrim(status) <> '' AND btrim(reconciliation_status) <> ''
  ),
  ADD CONSTRAINT ck_r17_payment_paid_evidence CHECK (
    status <> 'PAID' OR paid_at IS NOT NULL
  ),
  ADD CONSTRAINT ck_r17_payment_idempotency CHECK (
    (legacy_without_idempotency AND idempotency_key IS NULL AND request_hash IS NULL)
    OR
    (NOT legacy_without_idempotency AND idempotency_key IS NOT NULL
      AND length(idempotency_key) BETWEEN 16 AND 128
      AND request_hash ~ '^[0-9a-f]{64}$')
  );

CREATE UNIQUE INDEX uq_r17_payment_user_idempotency
  ON hhy.payment_transactions(order_id, idempotency_key)
  WHERE legacy_without_idempotency = false;
CREATE INDEX ix_r17_payment_order_created
  ON hhy.payment_transactions(order_id, created_at DESC, id DESC);

ALTER TABLE hhy.payment_callbacks
  ALTER COLUMN payload_cipher TYPE text,
  ADD COLUMN notification_id varchar(64),
  ADD COLUMN event_type varchar(128),
  ADD COLUMN order_no varchar(255),
  ADD COLUMN amount_cent bigint,
  ADD COLUMN currency varchar(3),
  ADD COLUMN callback_status varchar(64),
  ADD COLUMN payload_sha256 varchar(64),
  ADD COLUMN provider_timestamp timestamptz,
  ADD COLUMN provider_nonce varchar(128),
  ADD COLUMN processed_at timestamptz,
  ADD COLUMN legacy_without_notification boolean DEFAULT false NOT NULL;

UPDATE hhy.payment_callbacks SET legacy_without_notification = true;

ALTER TABLE hhy.payment_callbacks
  ADD CONSTRAINT ck_r17_callback_required_text CHECK (btrim(gateway) <> ''),
  ADD CONSTRAINT ck_r17_callback_amount CHECK (amount_cent IS NULL OR amount_cent >= 0),
  ADD CONSTRAINT ck_r17_callback_currency CHECK (currency IS NULL OR currency ~ '^[A-Z]{3}$'),
  ADD CONSTRAINT ck_r17_callback_signature CHECK (signature_valid IS NOT NULL),
  ADD CONSTRAINT ck_r17_callback_processing_pair CHECK (
    COALESCE(processed, false) = (processed_at IS NOT NULL)
  ),
  ADD CONSTRAINT ck_r17_callback_identity CHECK (
    legacy_without_notification OR (
      notification_id IS NOT NULL AND btrim(notification_id) <> ''
      AND event_type IS NOT NULL AND btrim(event_type) <> ''
      AND order_no IS NOT NULL AND btrim(order_no) <> ''
      AND callback_status IS NOT NULL AND btrim(callback_status) <> ''
      AND payload_sha256 ~ '^[0-9a-f]{64}$'
      AND provider_timestamp IS NOT NULL
      AND provider_nonce IS NOT NULL AND btrim(provider_nonce) <> ''
    )
  );

CREATE UNIQUE INDEX uq_r17_callback_notification
  ON hhy.payment_callbacks(gateway, notification_id)
  WHERE notification_id IS NOT NULL;
CREATE UNIQUE INDEX uq_r17_callback_nonce
  ON hhy.payment_callbacks(gateway, provider_nonce)
  WHERE provider_nonce IS NOT NULL;
CREATE INDEX ix_r17_callback_order_created
  ON hhy.payment_callbacks(order_no, created_at DESC);

ALTER TABLE hhy.payment_exception_orders
  ALTER COLUMN resolution TYPE varchar(2000),
  ADD COLUMN reason varchar(2000),
  ADD COLUMN resolved_by_admin_id bigint,
  ADD COLUMN resolved_at timestamptz,
  ADD CONSTRAINT ck_r17_exception_required_text CHECK (
    btrim(type) <> '' AND btrim(status) <> ''
  ),
  ADD CONSTRAINT ck_r17_exception_version CHECK (version >= 0),
  ADD CONSTRAINT ck_r17_exception_resolution CHECK (
    (status <> 'RESOLVED' AND resolution IS NULL AND reason IS NULL
      AND resolved_by_admin_id IS NULL AND resolved_at IS NULL)
    OR
    (status = 'RESOLVED' AND resolution IS NOT NULL AND btrim(resolution) <> ''
      AND reason IS NOT NULL AND btrim(reason) <> ''
      AND resolved_by_admin_id IS NOT NULL AND resolved_at IS NOT NULL)
  ),
  ADD CONSTRAINT fk_r17_exception_resolved_admin
    FOREIGN KEY (resolved_by_admin_id) REFERENCES hhy.admin_users(id) ON DELETE RESTRICT;

ALTER TABLE hhy.payment_reconciliation_records
  ADD COLUMN status varchar(64) DEFAULT 'COMPLETED' NOT NULL,
  ADD COLUMN transaction_count bigint DEFAULT 0 NOT NULL,
  ADD COLUMN difference_count bigint DEFAULT 0 NOT NULL,
  ADD COLUMN dry_run boolean DEFAULT false NOT NULL,
  ADD COLUMN initiated_by_admin_id bigint,
  ADD COLUMN completed_at timestamptz DEFAULT now() NOT NULL,
  ADD CONSTRAINT ck_r17_reconciliation_required_text CHECK (
    btrim(gateway) <> '' AND btrim(status) <> ''
  ),
  ADD CONSTRAINT ck_r17_reconciliation_counts CHECK (
    transaction_count >= 0 AND difference_count >= 0
      AND difference_count <= transaction_count
  ),
  ADD CONSTRAINT fk_r17_reconciliation_admin
    FOREIGN KEY (initiated_by_admin_id) REFERENCES hhy.admin_users(id) ON DELETE RESTRICT;

INSERT INTO hhy.admin_permissions(code, resource, action) VALUES
  ('payment.query', 'payment', 'query'),
  ('payment.exception.read', 'payment_exception', 'read'),
  ('payment.exception.resolve', 'payment_exception', 'resolve')
ON CONFLICT (code) DO UPDATE
SET resource = EXCLUDED.resource, action = EXCLUDED.action;

INSERT INTO hhy.admin_role_permissions(role_id, permission_id)
SELECT legacy.role_id, granular.id
FROM hhy.admin_role_permissions legacy
JOIN hhy.admin_permissions old_permission ON old_permission.id = legacy.permission_id
CROSS JOIN hhy.admin_permissions granular
WHERE old_permission.code = 'payment.manage'
  AND granular.code IN ('payment.query', 'payment.exception.read', 'payment.exception.resolve')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO hhy.admin_role_permissions(role_id, permission_id)
SELECT role.id, permission.id
FROM hhy.admin_roles role
CROSS JOIN hhy.admin_permissions permission
WHERE role.code = 'SUPER_ADMIN' AND role.status = 'ACTIVE'
  AND permission.code IN (
    'payment.read', 'payment.manage', 'payment.reconcile', 'payment.query',
    'payment.exception.read', 'payment.exception.resolve'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;
