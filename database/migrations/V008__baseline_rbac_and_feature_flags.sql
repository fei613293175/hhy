-- Idempotent baseline RBAC. No default administrator credential is created.
SET search_path TO hhy, public;

INSERT INTO hhy.admin_roles(code,name,status) VALUES ('SUPER_ADMIN','超级管理员','ACTIVE'),('AUDITOR','审计员','ACTIVE'),('OPERATOR','运营人员','ACTIVE') ON CONFLICT(code) DO NOTHING;

INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('dashboard.read','dashboard','read') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('dashboard.manage','dashboard','manage') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('user.read','user','read') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('user.manage','user','manage') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('identity.read','identity','read') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('identity.manage','identity','manage') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('content.read','content','read') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('content.manage','content','manage') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('review.manage','review','manage') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('redpacket.read','redpacket','read') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('redpacket.manage','redpacket','manage') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('product.manage','product','manage') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('order.read','order','read') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('order.manage','order','manage') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('payment.read','payment','read') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('payment.manage','payment','manage') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('payment.reconcile','payment','reconcile') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('reward.read','reward','read') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('reward.manage','reward','manage') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('withdrawal.read','withdrawal','read') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('withdrawal.manage','withdrawal','manage') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('membership.manage','membership','manage') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('prop.manage','prop','manage') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('referral.read','referral','read') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('referral.manage','referral','manage') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('task.manage','task','manage') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('chat.report.read','chat','report.read') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('chat.report.manage','chat','report.manage') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('support.manage','support','manage') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('cms.manage','cms','manage') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('app.release.read','app','release.read') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('app.release.manage','app','release.manage') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('risk.manage','risk','manage') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('config.manage','config','manage') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('rbac.manage','rbac','manage') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('audit.read','audit','read') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('audit.manage','audit','manage') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('job.manage','job','manage') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.admin_permissions(code,resource,action) VALUES ('app.build.manage','app','build.manage') ON CONFLICT(code) DO NOTHING;
INSERT INTO hhy.feature_flags(key,enabled,rollout_json) VALUES ('registration.enabled',true,'{}'::jsonb),('publishing.enabled',true,'{}'::jsonb),('redpacket.enabled',false,'{}'::jsonb),('withdrawal.enabled',false,'{}'::jsonb) ON CONFLICT(key) DO NOTHING;
-- 首个管理员必须通过 scripts/bootstrap-admin.sh 从环境变量生成强哈希，禁止在迁移中写入默认密码。
