-- Align the R15 access implementation with the frozen baseline tables and permissions.
SET search_path TO hhy, public;

ALTER TABLE hhy.notifications
  ADD COLUMN status varchar(32) DEFAULT 'UNREAD' NOT NULL,
  ADD COLUMN version bigint DEFAULT 0 NOT NULL;

UPDATE hhy.notifications
SET status = 'READ'
WHERE read_at IS NOT NULL;

ALTER TABLE hhy.notifications
  ADD CONSTRAINT ck_r15_notification_read_state CHECK (
    (status = 'UNREAD' AND read_at IS NULL)
    OR (status = 'READ' AND read_at IS NOT NULL)
  ),
  ADD CONSTRAINT ck_r15_notification_version CHECK (version >= 0);

ALTER TABLE hhy.support_tickets
  ADD COLUMN close_reason text,
  ADD COLUMN version bigint DEFAULT 0 NOT NULL,
  ADD CONSTRAINT ck_r15_support_ticket_version CHECK (version >= 0);

ALTER TABLE hhy.chat_reports
  ADD COLUMN decision varchar(32),
  ADD COLUMN decision_reason text,
  ADD CONSTRAINT ck_r15_chat_report_decision CHECK (
    (decision IS NULL AND decision_reason IS NULL)
    OR (status IN ('APPROVED', 'REJECTED', 'ESCALATED')
        AND decision IN ('APPROVE', 'REJECT', 'ESCALATE')
        AND decision_reason IS NOT NULL AND btrim(decision_reason) <> '')
  );

CREATE INDEX idx_r15_notifications_user_status
  ON hhy.notifications(user_id, status, created_at DESC, id DESC);
CREATE INDEX idx_r15_announcements_status
  ON hhy.announcements(status, created_at DESC, id DESC);
CREATE INDEX idx_r15_cms_articles_status
  ON hhy.cms_articles(status, updated_at DESC, id DESC);
CREATE INDEX idx_r15_support_tickets_user_status
  ON hhy.support_tickets(user_id, status, updated_at DESC, id DESC);
CREATE INDEX idx_r15_chat_reports_status
  ON hhy.chat_reports(status, updated_at DESC, id DESC);

INSERT INTO hhy.admin_permissions(code, resource, action) VALUES
  ('report.decide', 'report', 'decide'),
  ('appeal.decide', 'appeal', 'decide'),
  ('chat.report.read', 'chat', 'report.read'),
  ('chat.report.decide', 'chat', 'report.decide'),
  ('support.read', 'support', 'read'),
  ('support.assign', 'support', 'assign'),
  ('support.reply', 'support', 'reply'),
  ('support.close', 'support', 'close')
ON CONFLICT (code) DO UPDATE
SET resource = EXCLUDED.resource,
    action = EXCLUDED.action;

-- Existing broad managers retain the corresponding granular R15 capabilities.
INSERT INTO hhy.admin_role_permissions(role_id, permission_id)
SELECT legacy.role_id, granular.id
FROM hhy.admin_role_permissions legacy
JOIN hhy.admin_permissions legacy_permission
  ON legacy_permission.id = legacy.permission_id
CROSS JOIN hhy.admin_permissions granular
WHERE (
    legacy_permission.code = 'review.manage'
    AND granular.code IN ('report.decide', 'appeal.decide')
  ) OR (
    legacy_permission.code = 'chat.report.manage'
    AND granular.code IN ('chat.report.read', 'chat.report.decide')
  ) OR (
    legacy_permission.code = 'support.manage'
    AND granular.code IN ('support.read', 'support.assign', 'support.reply', 'support.close')
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO hhy.admin_role_permissions(role_id, permission_id)
SELECT role.id, permission.id
FROM hhy.admin_roles role
CROSS JOIN hhy.admin_permissions permission
WHERE role.code = 'SUPER_ADMIN'
  AND role.status = 'ACTIVE'
  AND permission.code IN (
    'report.decide', 'appeal.decide', 'chat.report.read', 'chat.report.decide',
    'support.read', 'support.assign', 'support.reply', 'support.close'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;

DO $$
BEGIN
  IF (SELECT count(*) FROM hhy.admin_permissions WHERE code IN (
      'report.decide', 'appeal.decide', 'chat.report.read', 'chat.report.decide',
      'support.read', 'support.assign', 'support.reply', 'support.close'
  )) <> 8 THEN
    RAISE EXCEPTION 'R15_GRANULAR_PERMISSIONS_INCOMPLETE'
      USING ERRCODE = '23514';
  END IF;
END;
$$;
