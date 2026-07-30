-- Align persisted R16 SKU facts with the already frozen OpenAPI bounds.
SET search_path TO hhy, public;

CREATE OR REPLACE FUNCTION hhy.r16_benefits_valid(p_benefits jsonb)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
AS $$
  SELECT COALESCE(
    jsonb_typeof(p_benefits) = 'array'
    AND jsonb_array_length(p_benefits) <= 100
    AND NOT EXISTS (
      SELECT 1
      FROM jsonb_array_elements(p_benefits) AS benefit(value)
      WHERE jsonb_typeof(benefit.value) <> 'object'
         OR NOT benefit.value ?& ARRAY['benefitCode', 'name', 'value']
         OR benefit.value - ARRAY['benefitCode', 'name', 'value', 'unit']::text[] <> '{}'::jsonb
         OR jsonb_typeof(benefit.value->'benefitCode') <> 'string'
         OR char_length(btrim(benefit.value->>'benefitCode')) NOT BETWEEN 1 AND 64
         OR jsonb_typeof(benefit.value->'name') <> 'string'
         OR char_length(btrim(benefit.value->>'name')) NOT BETWEEN 1 AND 255
         OR (
           benefit.value ? 'unit'
           AND (
             jsonb_typeof(benefit.value->'unit') <> 'string'
             OR char_length(benefit.value->>'unit') > 64
           )
         )
    ),
    false
  );
$$;

ALTER TABLE hhy.product_skus
  DROP CONSTRAINT ck_r16_product_skus_duration,
  ADD CONSTRAINT ck_r16_product_skus_duration CHECK (
    duration_days IS NULL OR duration_days >= 0
  );

COMMENT ON FUNCTION hhy.r16_benefits_valid(jsonb) IS
  'R16 frozen BenefitResource validator: 100 items, name 255, unit 64';
COMMENT ON CONSTRAINT ck_r16_product_skus_duration ON hhy.product_skus IS
  'R16 frozen durationDays contract: null or nonnegative';
