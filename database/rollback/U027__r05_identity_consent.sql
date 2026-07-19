DO $$
DECLARE
  seed_agreement_id bigint;
  seed_version_id bigint;
BEGIN
  SELECT a.id, v.id INTO seed_agreement_id, seed_version_id
  FROM hhy.agreements a
  JOIN hhy.agreement_versions v ON v.id = a.current_version_id
  WHERE a.code = 'IDENTITY_VERIFICATION'
    AND v.version = 2026072001;

  IF seed_version_id IS NOT NULL THEN
    IF EXISTS (
      SELECT 1
      FROM hhy.user_agreement_acceptances acceptance
      WHERE acceptance.version_id = seed_version_id
    ) THEN
      RAISE EXCEPTION 'R05_IDENTITY_CONSENT_HAS_ACCEPTANCES';
    END IF;
    DELETE FROM hhy.agreement_versions WHERE id = seed_version_id;
    DELETE FROM hhy.agreements WHERE id = seed_agreement_id;
  END IF;
END $$;
