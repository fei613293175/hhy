-- R05 private identity evidence reuses R04 media objects. Provider object keys
-- must identify a single metadata row inside one activated storage binding.

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM hhy.media_objects
    WHERE storage_binding_id IS NOT NULL AND object_key IS NOT NULL
      AND btrim(object_key) <> ''
    GROUP BY storage_binding_id, object_key
    HAVING count(*) > 1
  ) THEN
    RAISE EXCEPTION 'duplicate storage object keys must be resolved before V028';
  END IF;
END $$;

CREATE UNIQUE INDEX uq_r05_media_binding_object_key
  ON hhy.media_objects (storage_binding_id, object_key)
  WHERE storage_binding_id IS NOT NULL AND object_key IS NOT NULL;
