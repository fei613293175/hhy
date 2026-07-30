-- R07 search, hot-term, and contact-access invariants.
SET search_path TO hhy, public;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE connamespace = 'hhy'::regnamespace
      AND conname = 'uq_idempotency_records_scope_idem_key'
  ) THEN
    RAISE EXCEPTION 'R07_IDEMPOTENCY_PREREQUISITE_MISSING' USING ERRCODE = '55000';
  END IF;

  IF EXISTS (
    SELECT lower(btrim(keyword))
    FROM hhy.hot_search_terms
    GROUP BY lower(btrim(keyword))
    HAVING count(*) > 1
  ) THEN
    RAISE EXCEPTION 'R07_DUPLICATE_HOT_SEARCH_TERMS_REQUIRE_REVIEW' USING ERRCODE = '23505';
  END IF;
END;
$$;

ALTER TABLE hhy.search_histories
  ADD CONSTRAINT ck_r07_search_history_keyword
    CHECK (
      keyword IS NOT NULL
      AND btrim(keyword) <> ''
      AND char_length(btrim(keyword)) <= 100
    ) NOT VALID;
CREATE INDEX ix_r07_search_history_user_recent
  ON hhy.search_histories (user_id, created_at DESC, id DESC);

ALTER TABLE hhy.hot_search_terms
  ALTER COLUMN weight SET DEFAULT 0,
  ADD CONSTRAINT ck_r07_hot_keyword_weight
    CHECK (
      btrim(keyword) <> ''
      AND COALESCE(weight, 0) >= 0
    ) NOT VALID,
  ADD CONSTRAINT ck_r07_hot_schedule
    CHECK (
      starts_at IS NULL OR ends_at IS NULL OR ends_at > starts_at
    ) NOT VALID;
CREATE UNIQUE INDEX uq_r07_hot_keyword
  ON hhy.hot_search_terms (lower(btrim(keyword)));
CREATE INDEX ix_r07_hot_active_rank
  ON hhy.hot_search_terms (enabled, weight DESC, starts_at, ends_at, id);

ALTER TABLE hhy.content_contact_access_logs
  ADD CONSTRAINT ck_r07_contact_access_complete
    CHECK (
      content_id IS NOT NULL
      AND channel IS NOT NULL AND btrim(channel) <> ''
      AND action IS NOT NULL AND btrim(action) <> ''
    ) NOT VALID;
CREATE INDEX ix_r07_contact_access_recent
  ON hhy.content_contact_access_logs (user_id, content_id, created_at DESC, id DESC);
CREATE TRIGGER trg_r07_contact_access_immutable
  BEFORE UPDATE OR DELETE ON hhy.content_contact_access_logs
  FOR EACH ROW EXECUTE FUNCTION hhy.prevent_immutable_mutation();
