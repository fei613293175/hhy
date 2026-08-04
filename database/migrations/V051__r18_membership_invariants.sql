-- R18 Pro membership, immutable benefits, and remaining-value upgrade invariants.
SET search_path TO hhy, public;

-- Existing rows must already contain product facts. The migration refuses
-- ambiguous legacy data instead of inventing membership or monetary values.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM hhy.membership_plans
    WHERE btrim(code) = '' OR name IS NULL OR btrim(name) = ''
       OR public_badge IS NULL OR btrim(public_badge) = ''
  ) OR EXISTS (
    SELECT 1 FROM hhy.membership_plans GROUP BY code HAVING count(*) > 1
  ) THEN
    RAISE EXCEPTION 'R18_DIRTY_UPGRADE_MEMBERSHIP_PLAN_INVALID'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1 FROM hhy.membership_skus
    WHERE term_type NOT IN ('MONTH', 'QUARTER', 'YEAR')
       OR term_seconds IS NULL
       OR term_seconds !~ '^[0-9]+$'
       OR term_seconds::numeric <= 0
       OR term_seconds::numeric > 9223372036854775807
  ) OR EXISTS (
    SELECT 1 FROM hhy.membership_skus GROUP BY sku_id HAVING count(*) > 1
  ) OR EXISTS (
    SELECT 1 FROM hhy.membership_skus membership_sku
    LEFT JOIN hhy.membership_plans plan ON plan.id = membership_sku.plan_id
    WHERE plan.id IS NULL
  ) THEN
    RAISE EXCEPTION 'R18_DIRTY_UPGRADE_MEMBERSHIP_SKU_INVALID'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1 FROM hhy.membership_benefits WHERE btrim(code) = ''
  ) OR EXISTS (
    SELECT 1 FROM hhy.membership_benefits GROUP BY code HAVING count(*) > 1
  ) OR EXISTS (
    SELECT 1 FROM hhy.membership_sku_benefits
    WHERE value_json IS NULL OR value_json = 'null'::jsonb
  ) OR EXISTS (
    SELECT 1 FROM hhy.membership_sku_benefits
    GROUP BY membership_sku_id, benefit_id HAVING count(*) > 1
  ) THEN
    RAISE EXCEPTION 'R18_DIRTY_UPGRADE_MEMBERSHIP_BENEFIT_INVALID'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1 FROM hhy.user_memberships
    WHERE starts_at IS NULL OR ends_at IS NULL OR ends_at <= starts_at
       OR status NOT IN ('PENDING', 'ACTIVE', 'EXPIRED', 'CANCELLED')
       OR version < 0
  ) OR EXISTS (
    SELECT 1 FROM hhy.user_memberships GROUP BY user_id HAVING count(*) > 1
  ) OR EXISTS (
    SELECT 1 FROM hhy.user_memberships membership
    LEFT JOIN hhy.membership_plans plan ON plan.id = membership.plan_id
    WHERE plan.id IS NULL
  ) THEN
    RAISE EXCEPTION 'R18_DIRTY_UPGRADE_USER_MEMBERSHIP_INVALID'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1 FROM hhy.membership_entitlement_segments
    WHERE type NOT IN ('PAID', 'GIFT')
       OR paid_amount IS NULL OR paid_amount < 0
       OR consumed IS NULL OR consumed < 0 OR consumed > paid_amount
       OR starts_at IS NULL OR ends_at IS NULL OR ends_at <= starts_at
       OR (type = 'PAID' AND source_order_id IS NULL)
       OR (type = 'GIFT' AND (source_order_id IS NOT NULL OR paid_amount <> 0))
  ) OR EXISTS (
    SELECT 1
    FROM hhy.membership_entitlement_segments segment
    WHERE segment.type = 'PAID' AND (
      SELECT count(DISTINCT membership_sku.id)
      FROM hhy.order_items item
      JOIN hhy.membership_skus membership_sku ON membership_sku.sku_id = item.sku_id
      WHERE item.order_id = segment.source_order_id
    ) <> 1
  ) THEN
    RAISE EXCEPTION 'R18_DIRTY_UPGRADE_MEMBERSHIP_SEGMENT_INVALID'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1 FROM hhy.membership_benefit_snapshots
    WHERE NOT hhy.r16_benefits_valid(benefits_json)
  ) OR EXISTS (
    SELECT 1 FROM hhy.membership_benefit_snapshots GROUP BY order_id HAVING count(*) > 1
  ) THEN
    RAISE EXCEPTION 'R18_DIRTY_UPGRADE_MEMBERSHIP_SNAPSHOT_INVALID'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1 FROM hhy.membership_upgrade_quotes
    WHERE target_sku_id IS NULL OR remaining_value IS NULL OR remaining_value < 0
       OR payable IS NULL OR payable < 0 OR expires_at IS NULL OR expires_at <= created_at
  ) THEN
    RAISE EXCEPTION 'R18_DIRTY_UPGRADE_MEMBERSHIP_QUOTE_INVALID'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1 FROM hhy.membership_value_conversions
    WHERE source_segment_id IS NULL OR converted_value IS NULL OR converted_value < 0
       OR extra_seconds IS NULL OR extra_seconds !~ '^[0-9]+$'
       OR extra_seconds::numeric > 9223372036854775807
  ) OR EXISTS (
    SELECT 1 FROM hhy.membership_value_conversions
    GROUP BY upgrade_order_id, source_segment_id HAVING count(*) > 1
  ) THEN
    RAISE EXCEPTION 'R18_DIRTY_UPGRADE_MEMBERSHIP_CONVERSION_INVALID'
      USING ERRCODE = '23514';
  END IF;
END;
$$;

ALTER TABLE hhy.membership_plans
  ADD COLUMN status varchar(64) DEFAULT 'ACTIVE' NOT NULL,
  ADD COLUMN version bigint DEFAULT 0 NOT NULL,
  ALTER COLUMN name SET NOT NULL,
  ALTER COLUMN public_badge SET NOT NULL,
  ADD CONSTRAINT ck_r18_membership_plan_identity CHECK (
    btrim(code) <> '' AND btrim(name) <> '' AND btrim(public_badge) <> ''
  ),
  ADD CONSTRAINT ck_r18_membership_plan_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
  ADD CONSTRAINT ck_r18_membership_plan_version CHECK (version >= 0),
  ADD CONSTRAINT uq_r18_membership_plan_code UNIQUE (code);

ALTER TABLE hhy.membership_skus
  ADD COLUMN version bigint DEFAULT 0 NOT NULL,
  ALTER COLUMN term_seconds TYPE bigint USING term_seconds::bigint,
  ALTER COLUMN term_type SET NOT NULL,
  ALTER COLUMN term_seconds SET NOT NULL,
  ADD CONSTRAINT fk_r18_membership_sku_plan FOREIGN KEY (plan_id)
    REFERENCES hhy.membership_plans(id) NOT VALID,
  ADD CONSTRAINT ck_r18_membership_sku_term CHECK (
    term_type IN ('MONTH', 'QUARTER', 'YEAR') AND term_seconds > 0
  ),
  ADD CONSTRAINT ck_r18_membership_sku_version CHECK (version >= 0),
  ADD CONSTRAINT uq_r18_membership_sku_product UNIQUE (sku_id);

ALTER TABLE hhy.membership_benefits
  ADD CONSTRAINT uq_r18_membership_benefit_code UNIQUE (code),
  ADD CONSTRAINT ck_r18_membership_benefit_identity CHECK (btrim(code) <> '');

ALTER TABLE hhy.membership_sku_benefits
  ALTER COLUMN value_json SET NOT NULL,
  ADD CONSTRAINT fk_r18_membership_sku_benefit_sku FOREIGN KEY (membership_sku_id)
    REFERENCES hhy.membership_skus(id) ON DELETE CASCADE NOT VALID,
  ADD CONSTRAINT fk_r18_membership_sku_benefit_definition FOREIGN KEY (benefit_id)
    REFERENCES hhy.membership_benefits(id) NOT VALID,
  ADD CONSTRAINT uq_r18_membership_sku_benefit UNIQUE (membership_sku_id, benefit_id),
  ADD CONSTRAINT ck_r18_membership_sku_benefit_value CHECK (value_json <> 'null'::jsonb);

ALTER TABLE hhy.user_memberships
  ALTER COLUMN starts_at SET NOT NULL,
  ALTER COLUMN ends_at SET NOT NULL,
  ADD CONSTRAINT fk_r18_user_membership_plan FOREIGN KEY (plan_id)
    REFERENCES hhy.membership_plans(id) NOT VALID,
  ADD CONSTRAINT uq_r18_user_current_membership UNIQUE (user_id),
  ADD CONSTRAINT ck_r18_user_membership_status CHECK (
    status IN ('PENDING', 'ACTIVE', 'EXPIRED', 'CANCELLED')
  ),
  ADD CONSTRAINT ck_r18_user_membership_time CHECK (ends_at > starts_at),
  ADD CONSTRAINT ck_r18_user_membership_version CHECK (version >= 0);

ALTER TABLE hhy.membership_entitlement_segments
  ADD COLUMN membership_sku_id bigint,
  ADD COLUMN version bigint DEFAULT 0 NOT NULL;

UPDATE hhy.membership_entitlement_segments segment
SET membership_sku_id = resolved.membership_sku_id
FROM (
  SELECT segment_id, min(membership_sku_id) AS membership_sku_id
  FROM (
    SELECT segment.id AS segment_id, membership_sku.id AS membership_sku_id
    FROM hhy.membership_entitlement_segments segment
    JOIN hhy.order_items item ON item.order_id = segment.source_order_id
    JOIN hhy.membership_skus membership_sku ON membership_sku.sku_id = item.sku_id
    WHERE segment.type = 'PAID'
  ) matches
  GROUP BY segment_id
) resolved
WHERE segment.id = resolved.segment_id;

ALTER TABLE hhy.membership_entitlement_segments
  ALTER COLUMN type SET NOT NULL,
  ALTER COLUMN paid_amount SET DEFAULT 0,
  ALTER COLUMN paid_amount SET NOT NULL,
  ALTER COLUMN starts_at SET NOT NULL,
  ALTER COLUMN ends_at SET NOT NULL,
  ALTER COLUMN consumed SET DEFAULT 0,
  ALTER COLUMN consumed SET NOT NULL,
  ADD CONSTRAINT fk_r18_membership_segment_order FOREIGN KEY (source_order_id)
    REFERENCES hhy.orders(id) NOT VALID,
  ADD CONSTRAINT fk_r18_membership_segment_sku FOREIGN KEY (membership_sku_id)
    REFERENCES hhy.membership_skus(id) NOT VALID,
  ADD CONSTRAINT ck_r18_membership_segment_type CHECK (type IN ('PAID', 'GIFT')),
  ADD CONSTRAINT ck_r18_membership_segment_time CHECK (ends_at > starts_at),
  ADD CONSTRAINT ck_r18_membership_segment_value CHECK (
    paid_amount >= 0 AND consumed >= 0 AND consumed <= paid_amount
  ),
  ADD CONSTRAINT ck_r18_membership_segment_source CHECK (
    (type = 'PAID' AND source_order_id IS NOT NULL AND membership_sku_id IS NOT NULL)
    OR (type = 'GIFT' AND source_order_id IS NULL AND paid_amount = 0)
  ),
  ADD CONSTRAINT ck_r18_membership_segment_version CHECK (version >= 0);

CREATE INDEX ix_r18_membership_segments_user_expiry
  ON hhy.membership_entitlement_segments(user_id, ends_at DESC);

ALTER TABLE hhy.membership_benefit_snapshots
  ADD COLUMN snapshot_version bigint DEFAULT 1 NOT NULL,
  ADD CONSTRAINT uq_r18_membership_snapshot_order UNIQUE (order_id),
  ADD CONSTRAINT ck_r18_membership_snapshot_content CHECK (
    hhy.r16_benefits_valid(benefits_json) AND snapshot_version >= 1
  );

CREATE TRIGGER trg_r18_membership_benefit_snapshots_immutable
BEFORE UPDATE OR DELETE ON hhy.membership_benefit_snapshots
FOR EACH ROW EXECUTE FUNCTION hhy.prevent_immutable_mutation();

ALTER TABLE hhy.membership_upgrade_quotes
  ADD COLUMN status varchar(64) DEFAULT 'OPEN' NOT NULL,
  ADD COLUMN membership_version bigint DEFAULT 0 NOT NULL,
  ADD COLUMN idempotency_key varchar(128),
  ADD COLUMN request_hash varchar(64),
  ADD COLUMN version bigint DEFAULT 0 NOT NULL,
  ALTER COLUMN target_sku_id SET NOT NULL,
  ALTER COLUMN remaining_value SET NOT NULL,
  ALTER COLUMN payable SET NOT NULL,
  ALTER COLUMN expires_at SET NOT NULL,
  ADD CONSTRAINT fk_r18_membership_quote_target FOREIGN KEY (target_sku_id)
    REFERENCES hhy.membership_skus(id) NOT VALID,
  ADD CONSTRAINT ck_r18_membership_quote_amounts CHECK (
    remaining_value >= 0 AND payable >= 0 AND expires_at > created_at
  ),
  ADD CONSTRAINT ck_r18_membership_quote_status CHECK (
    status IN ('OPEN', 'CONSUMED', 'EXPIRED', 'CANCELLED')
  ),
  ADD CONSTRAINT ck_r18_membership_quote_versions CHECK (
    membership_version >= 0 AND version >= 0
  ),
  ADD CONSTRAINT ck_r18_membership_quote_idempotency CHECK (
    (idempotency_key IS NULL AND request_hash IS NULL)
    OR (
      char_length(idempotency_key) BETWEEN 16 AND 128
      AND request_hash ~ '^[0-9a-f]{64}$'
    )
  );

CREATE UNIQUE INDEX uq_r18_membership_quote_idempotency
  ON hhy.membership_upgrade_quotes(user_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;

CREATE INDEX ix_r18_membership_quote_expiry
  ON hhy.membership_upgrade_quotes(status, expires_at);

ALTER TABLE hhy.membership_value_conversions
  ALTER COLUMN source_segment_id SET NOT NULL,
  ALTER COLUMN converted_value SET NOT NULL,
  ALTER COLUMN extra_seconds TYPE bigint USING extra_seconds::bigint,
  ALTER COLUMN extra_seconds SET NOT NULL,
  ADD CONSTRAINT fk_r18_membership_conversion_order FOREIGN KEY (upgrade_order_id)
    REFERENCES hhy.orders(id) NOT VALID,
  ADD CONSTRAINT fk_r18_membership_conversion_segment FOREIGN KEY (source_segment_id)
    REFERENCES hhy.membership_entitlement_segments(id) NOT VALID,
  ADD CONSTRAINT uq_r18_membership_conversion_segment UNIQUE (upgrade_order_id, source_segment_id),
  ADD CONSTRAINT ck_r18_membership_conversion_values CHECK (
    converted_value >= 0 AND extra_seconds >= 0
  );

CREATE TRIGGER trg_r18_membership_value_conversions_immutable
BEFORE UPDATE OR DELETE ON hhy.membership_value_conversions
FOR EACH ROW EXECUTE FUNCTION hhy.prevent_immutable_mutation();

-- Preserve status transitions in the existing immutable transactional Outbox;
-- the frozen 198-table catalog does not permit a second history table.
CREATE TRIGGER trg_r18_user_memberships_status_history
AFTER UPDATE OF status ON hhy.user_memberships
FOR EACH ROW EXECUTE FUNCTION hhy.record_platform_status_history();

CREATE OR REPLACE FUNCTION hhy.fulfill_r18_membership_order()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_membership_sku_id bigint;
  v_plan_id bigint;
  v_product_sku_id bigint;
  v_term_seconds bigint;
  v_target_price bigint;
  v_membership_id bigint;
  v_membership_version bigint;
  v_membership_starts_at timestamptz;
  v_membership_ends_at timestamptz;
  v_membership_status varchar(64);
  v_segment_start timestamptz;
  v_segment_end timestamptz;
  v_extra_seconds bigint := 0;
  v_gift_seconds bigint := 0;
  v_converted bigint;
  v_conversion_seconds bigint;
  v_snapshot_count integer;
  v_segment record;
BEGIN
  IF NEW.status <> 'PAID'
     OR NEW.biz_type NOT IN ('MEMBERSHIP', 'MEMBERSHIP_UPGRADE') THEN
    RETURN NEW;
  END IF;

  IF NEW.biz_type = 'MEMBERSHIP' THEN
    v_membership_sku_id := NEW.biz_id;
  ELSE
    SELECT quote.target_sku_id INTO v_membership_sku_id
    FROM hhy.membership_upgrade_quotes quote
    WHERE quote.id = NEW.biz_id AND quote.user_id = NEW.user_id
      AND quote.status = 'CONSUMED';
  END IF;

  SELECT membership_sku.plan_id,membership_sku.sku_id,membership_sku.term_seconds,
         product_sku.price_cent
  INTO v_plan_id,v_product_sku_id,v_term_seconds,v_target_price
  FROM hhy.membership_skus membership_sku
  JOIN hhy.product_skus product_sku ON product_sku.id=membership_sku.sku_id
  WHERE membership_sku.id=v_membership_sku_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'R18_MEMBERSHIP_FULFILLMENT_SKU_MISSING order_id=%', NEW.id
      USING ERRCODE = '23514';
  END IF;

  SELECT count(*) INTO v_snapshot_count
  FROM hhy.membership_benefit_snapshots WHERE order_id=NEW.id;
  IF v_snapshot_count <> 1 THEN
    RAISE EXCEPTION 'R18_MEMBERSHIP_FULFILLMENT_SNAPSHOT_MISSING order_id=%', NEW.id
      USING ERRCODE = '23514';
  END IF;

  SELECT id,version,starts_at,ends_at,status
  INTO v_membership_id,v_membership_version,v_membership_starts_at,
       v_membership_ends_at,v_membership_status
  FROM hhy.user_memberships WHERE user_id=NEW.user_id FOR UPDATE;

  IF NEW.biz_type = 'MEMBERSHIP_UPGRADE' THEN
    IF v_membership_id IS NULL OR v_target_price <= 0 THEN
      RAISE EXCEPTION 'R18_MEMBERSHIP_UPGRADE_STATE_INVALID order_id=%', NEW.id
        USING ERRCODE = '23514';
    END IF;
    FOR v_segment IN
      SELECT id,paid_amount-consumed AS remaining
      FROM hhy.membership_entitlement_segments
      WHERE user_id=NEW.user_id AND type='PAID'
        AND ends_at>clock_timestamp() AND paid_amount>consumed
      ORDER BY id FOR UPDATE
    LOOP
      v_converted := v_segment.remaining;
      v_conversion_seconds := floor(
        v_converted::numeric * v_term_seconds::numeric / v_target_price::numeric
      )::bigint;
      UPDATE hhy.membership_entitlement_segments
      SET consumed=paid_amount,version=version+1 WHERE id=v_segment.id;
      INSERT INTO hhy.membership_value_conversions(
        upgrade_order_id,source_segment_id,converted_value,extra_seconds)
      VALUES (NEW.id,v_segment.id,v_converted,v_conversion_seconds);
      v_extra_seconds := v_extra_seconds + v_conversion_seconds;
    END LOOP;
    SELECT COALESCE(sum(extract(epoch FROM (
      ends_at - greatest(starts_at,clock_timestamp())
    ))),0)::bigint INTO v_gift_seconds
    FROM hhy.membership_entitlement_segments
    WHERE user_id=NEW.user_id AND type='GIFT' AND ends_at>clock_timestamp();
    v_segment_start := clock_timestamp();
    v_segment_end := v_segment_start + make_interval(
      secs => v_term_seconds + v_extra_seconds + v_gift_seconds);
  ELSE
    v_segment_start := CASE
      WHEN v_membership_id IS NOT NULL
       AND v_membership_status='ACTIVE'
       AND v_membership_ends_at>clock_timestamp()
      THEN v_membership_ends_at ELSE clock_timestamp() END;
    v_segment_end := v_segment_start + make_interval(secs => v_term_seconds);
  END IF;

  IF v_membership_id IS NULL THEN
    INSERT INTO hhy.user_memberships(
      user_id,plan_id,status,starts_at,ends_at,version)
    VALUES (NEW.user_id,v_plan_id,'ACTIVE',clock_timestamp(),v_segment_end,0)
    RETURNING id INTO v_membership_id;
  ELSE
    UPDATE hhy.user_memberships
    SET plan_id=v_plan_id,status='ACTIVE',
        starts_at=CASE
          WHEN NEW.biz_type='MEMBERSHIP_UPGRADE' OR status<>'ACTIVE'
          THEN clock_timestamp() ELSE starts_at END,
        ends_at=v_segment_end,version=version+1
    WHERE id=v_membership_id AND version=v_membership_version;
    IF NOT FOUND THEN
      RAISE EXCEPTION 'R18_MEMBERSHIP_FULFILLMENT_VERSION_CONFLICT order_id=%', NEW.id
        USING ERRCODE = '40001';
    END IF;
  END IF;

  INSERT INTO hhy.membership_entitlement_segments(
    user_id,source_order_id,membership_sku_id,type,paid_amount,
    starts_at,ends_at,consumed,version)
  VALUES (
    NEW.user_id,NEW.id,v_membership_sku_id,'PAID',v_target_price,
    v_segment_start,v_segment_end,0,0
  );

  UPDATE hhy.orders SET status='FULFILLING',version=version+1 WHERE id=NEW.id;
  UPDATE hhy.orders SET status='COMPLETED',version=version+1 WHERE id=NEW.id;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_r18_membership_order_fulfillment
AFTER UPDATE OF status ON hhy.orders
FOR EACH ROW EXECUTE FUNCTION hhy.fulfill_r18_membership_order();

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM hhy.admin_permissions WHERE code = 'membership.manage'
  ) THEN
    RAISE EXCEPTION 'R18_MEMBERSHIP_MANAGE_PERMISSION_MISSING'
      USING ERRCODE = '23514';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM hhy.admin_roles
    WHERE code = 'SUPER_ADMIN' AND status = 'ACTIVE'
  ) THEN
    RAISE EXCEPTION 'R18_SUPER_ADMIN_ROLE_MISSING_OR_INACTIVE'
      USING ERRCODE = '23514';
  END IF;
END;
$$;

INSERT INTO hhy.admin_permissions(code, resource, action) VALUES
  ('membership.read', 'membership', 'read'),
  ('membership.write', 'membership', 'write'),
  ('membership.grant', 'membership', 'grant')
ON CONFLICT (code) DO UPDATE
SET resource = EXCLUDED.resource,
    action = EXCLUDED.action;

INSERT INTO hhy.admin_role_permissions(role_id, permission_id)
SELECT legacy.role_id, granular.id
FROM hhy.admin_role_permissions legacy
JOIN hhy.admin_permissions legacy_permission
  ON legacy_permission.id = legacy.permission_id
CROSS JOIN hhy.admin_permissions granular
WHERE legacy_permission.code = 'membership.manage'
  AND granular.code IN ('membership.read', 'membership.write', 'membership.grant')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO hhy.admin_role_permissions(role_id, permission_id)
SELECT role.id, permission.id
FROM hhy.admin_roles role
CROSS JOIN hhy.admin_permissions permission
WHERE role.code = 'SUPER_ADMIN'
  AND role.status = 'ACTIVE'
  AND permission.code IN ('membership.read', 'membership.write', 'membership.grant')
ON CONFLICT (role_id, permission_id) DO NOTHING;

DO $$
BEGIN
  IF (SELECT count(*) FROM hhy.admin_permissions
      WHERE code IN ('membership.read', 'membership.write', 'membership.grant')
        AND resource = 'membership') <> 3 THEN
    RAISE EXCEPTION 'R18_MEMBERSHIP_GRANULAR_PERMISSIONS_INCOMPLETE'
      USING ERRCODE = '23514';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM hhy.admin_role_permissions legacy
    JOIN hhy.admin_permissions legacy_permission
      ON legacy_permission.id = legacy.permission_id
    WHERE legacy_permission.code = 'membership.manage'
      AND EXISTS (
        SELECT 1
        FROM hhy.admin_permissions granular
        WHERE granular.code IN ('membership.read', 'membership.write', 'membership.grant')
          AND NOT EXISTS (
            SELECT 1
            FROM hhy.admin_role_permissions mapped
            WHERE mapped.role_id = legacy.role_id
              AND mapped.permission_id = granular.id
          )
      )
  ) THEN
    RAISE EXCEPTION 'R18_MEMBERSHIP_MANAGER_PERMISSION_MAPPING_INCOMPLETE'
      USING ERRCODE = '23514';
  END IF;
END;
$$;
