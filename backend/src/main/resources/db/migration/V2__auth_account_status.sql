-- V2__auth_account_status.sql — Phase 2: authentication & RBAC
-- Non-destructive: only adds the account status column and
-- normalizes existing emails to lowercase.

-- Normalize existing emails (emails are compared case-insensitively).
UPDATE app_user SET email = LOWER(email) WHERE email IS NOT NULL;

ALTER TABLE app_user
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE', 'SUSPENDED'));
