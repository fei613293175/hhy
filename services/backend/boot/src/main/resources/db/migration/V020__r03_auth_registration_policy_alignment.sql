-- Align the active R02/R03 authentication values with the frozen product specification.
-- Only repair the original erroneous seed so an explicit operator override is never overwritten.
UPDATE hhy.system_configs
SET value_json = '20'::jsonb,
    version = version + 1
WHERE key = 'auth.password.max_length'
  AND scope = 'GLOBAL'
  AND value_json = '72'::jsonb;
