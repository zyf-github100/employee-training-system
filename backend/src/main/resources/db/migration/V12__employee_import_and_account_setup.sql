CREATE TABLE employee_import (
    id CHAR(36) PRIMARY KEY,
    actor_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    rows_json MEDIUMTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at TIMESTAMP(6) NOT NULL,
    committed_at TIMESTAMP(6) NULL,
    FOREIGN KEY(actor_id) REFERENCES sys_user(id),
    INDEX idx_import_actor(actor_id,created_at)
);
CREATE TABLE employee_import_user (
    import_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    source_row INT NOT NULL,
    PRIMARY KEY(import_id,user_id),
    FOREIGN KEY(import_id) REFERENCES employee_import(id),
    FOREIGN KEY(user_id) REFERENCES sys_user(id)
);
CREATE TABLE account_setup_token (
    user_id BIGINT PRIMARY KEY,
    token_hash CHAR(64) NOT NULL UNIQUE,
    purpose VARCHAR(16) NOT NULL,
    issued_by BIGINT NOT NULL,
    user_version BIGINT NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    consumed_at TIMESTAMP(6) NULL,
    FOREIGN KEY(user_id) REFERENCES sys_user(id),
    FOREIGN KEY(issued_by) REFERENCES sys_user(id)
);
