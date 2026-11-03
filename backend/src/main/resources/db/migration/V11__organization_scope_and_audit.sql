ALTER TABLE org_department
    ADD COLUMN parent_id BIGINT NULL,
    ADD COLUMN sort_no INT NOT NULL DEFAULT 0,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT fk_department_parent FOREIGN KEY (parent_id) REFERENCES org_department(id);

ALTER TABLE sys_user
    ADD COLUMN super_admin BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN data_scope VARCHAR(24) NOT NULL DEFAULT 'SELF';

-- Existing administrators already had full access. Preserve that privilege on upgrade.
UPDATE sys_user SET super_admin=TRUE,data_scope='ALL' WHERE role_code='ADMIN';

CREATE TABLE user_scope_department (
    user_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    PRIMARY KEY(user_id,department_id),
    FOREIGN KEY(user_id) REFERENCES sys_user(id),
    FOREIGN KEY(department_id) REFERENCES org_department(id)
);

-- Serialize organization, permission and roster mutations, including last-admin checks.
CREATE TABLE authorization_guard (id INT PRIMARY KEY);
INSERT INTO authorization_guard(id) VALUES(1);

CREATE TABLE audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_id BIGINT NULL,
    actor_name VARCHAR(80) NULL,
    action VARCHAR(12) NOT NULL,
    target VARCHAR(250) NOT NULL,
    http_status INT NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_audit_time(occurred_at,id),
    INDEX idx_audit_actor(actor_id,id)
);
