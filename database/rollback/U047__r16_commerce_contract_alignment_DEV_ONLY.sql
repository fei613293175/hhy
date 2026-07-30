-- DEV/TEST only. Refuse to erase facts that are valid only under V047.
SET search_path TO hhy, public;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM hhy.product_skus WHERE duration_days = 0
  ) OR EXISTS (
    SELECT 1
    FROM hhy.product_skus sku
    CROSS JOIN LATERAL jsonb_array_elements(sku.benefits_json) AS benefit(value)
    WHERE char_length(btrim(benefit.value->>'name')) > 120
       OR char_length(benefit.value->>'unit') > 32
  ) THEN
    RAISE EXCEPTION 'R16_V047_ROLLBACK_NEW_CONTRACT_FACTS_PRESENT'
      USING ERRCODE = '23514';
  END IF;
END;
$$;

ALTER TABLE hhy.product_skus
  DROP CONSTRAINT ck_r16_product_skus_duration,
  ADD CONSTRAINT ck_r16_product_skus_duration CHECK (
    duration_days IS NULL OR duration_days > 0
  );

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
         OR char_length(btrim(benefit.value->>'name')) NOT BETWEEN 1 AND 120
         OR (
           benefit.value ? 'unit'
           AND (
             jsonb_typeof(benefit.value->'unit') <> 'string'
             OR char_length(benefit.value->>'unit') > 32
           )
         )
    ),
    false
  );
$$;
