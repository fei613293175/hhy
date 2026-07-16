-- V1.2.2 operational governance rollback (destructive; non-production only).
SET search_path TO hhy, public;
DROP TABLE IF EXISTS hhy.export_download_logs CASCADE;
DROP TABLE IF EXISTS hhy.export_job_files CASCADE;
DROP TABLE IF EXISTS hhy.export_jobs CASCADE;
DROP TABLE IF EXISTS hhy.notification_delivery_attempts CASCADE;
DROP TABLE IF EXISTS hhy.risk_rule_simulations CASCADE;
DROP TABLE IF EXISTS hhy.accounting_reversal_requests CASCADE;
DROP TABLE IF EXISTS hhy.admin_login_logs CASCADE;
DROP TABLE IF EXISTS hhy.admin_recovery_codes CASCADE;
DROP TABLE IF EXISTS hhy.admin_mfa_methods CASCADE;
DROP TABLE IF EXISTS hhy.admin_sessions CASCADE;
