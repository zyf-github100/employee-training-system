ALTER TABLE training_certificate
    MODIFY title VARCHAR(200) NOT NULL,
    DROP FOREIGN KEY fk_certificate_user,
    ADD CONSTRAINT fk_certificate_recipient FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE RESTRICT,
    ADD COLUMN recipient_name_snapshot VARCHAR(80) NULL,
    ADD COLUMN employee_no_snapshot VARCHAR(80) NULL,
    ADD COLUMN plan_title_snapshot VARCHAR(160) NULL,
    ADD COLUMN issuer_name VARCHAR(80) NULL,
    ADD COLUMN completed_at_snapshot TIMESTAMP NULL,
    ADD COLUMN verification_code CHAR(64) NULL,
    ADD COLUMN revocation_reason VARCHAR(500) NULL,
    ADD COLUMN revoked_by BIGINT NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN snapshot_origin VARCHAR(16) NOT NULL DEFAULT 'LEGACY',
    ADD CONSTRAINT fk_certificate_revoker FOREIGN KEY (revoked_by) REFERENCES sys_user(id);

UPDATE training_certificate tc
JOIN sys_user u ON u.id=tc.user_id
JOIN training_plan tp ON tp.id=tc.plan_id
JOIN platform_setting p ON p.id=1
LEFT JOIN training_assignment ta ON ta.plan_id=tc.plan_id AND ta.user_id=tc.user_id
SET tc.recipient_name_snapshot=u.name,tc.employee_no_snapshot=u.employee_no,
    tc.plan_title_snapshot=tp.title,tc.issuer_name=p.company_name,
    tc.completed_at_snapshot=COALESCE(ta.completed_at,tc.issued_at),
    tc.verification_code=LOWER(HEX(RANDOM_BYTES(32)));

ALTER TABLE training_certificate
    MODIFY recipient_name_snapshot VARCHAR(80) NOT NULL,
    MODIFY employee_no_snapshot VARCHAR(80) NOT NULL,
    MODIFY plan_title_snapshot VARCHAR(160) NOT NULL,
    MODIFY issuer_name VARCHAR(80) NOT NULL,
    MODIFY completed_at_snapshot TIMESTAMP NOT NULL,
    MODIFY verification_code CHAR(64) NOT NULL,
    ALTER COLUMN snapshot_origin SET DEFAULT 'ISSUANCE',
    ADD UNIQUE KEY uk_certificate_verification(verification_code),
    ADD INDEX idx_certificate_status(revoked_at,id);
