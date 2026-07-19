-- Seed the first controlled identity consent only when operations has not created one.
DO $$
DECLARE
  agreement_id bigint;
  version_id bigint;
BEGIN
  SELECT id INTO agreement_id
  FROM hhy.agreements
  WHERE code = 'IDENTITY_VERIFICATION';

  IF agreement_id IS NULL THEN
    INSERT INTO hhy.agreements(code, current_version_id)
    VALUES ('IDENTITY_VERIFICATION', 0)
    RETURNING id INTO agreement_id;

    INSERT INTO hhy.agreement_versions(
      agreement_id, version, content, effective_at)
    VALUES (
      agreement_id,
      2026072001,
      $consent$为完成实名认证，您同意合伙云在身份核验所必需的范围内处理您主动提交的真实姓名、身份证号码及活体影像信息，并将必要信息提供给受托的实名认证服务机构进行核验。

上述信息仅用于确认账号使用者身份、保障账号与交易安全、履行法律法规要求及处理实名认证相关申诉。合伙云将采取访问控制、加密存储和安全审计等措施保护相关信息，不会将其用于与实名认证无关的用途。

请确认您提交的是本人真实、有效的信息，并由本人完成活体检测。提交即表示您已阅读、理解并同意本说明；如您不同意，请勿继续实名认证。$consent$,
      TIMESTAMPTZ '2026-07-20 00:00:00+08')
    RETURNING id INTO version_id;

    UPDATE hhy.agreements
    SET current_version_id = version_id, updated_at = clock_timestamp()
    WHERE id = agreement_id;
  END IF;
END $$;
