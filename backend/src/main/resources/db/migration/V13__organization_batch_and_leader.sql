ALTER TABLE org_department ADD COLUMN leader_user_id BIGINT NULL,
    ADD CONSTRAINT fk_department_leader FOREIGN KEY (leader_user_id) REFERENCES sys_user(id);

CREATE TABLE organization_batch (
    request_id CHAR(36) PRIMARY KEY,
    actor_id BIGINT NOT NULL,
    operation VARCHAR(16) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    payload_json MEDIUMTEXT NOT NULL,
    result_json TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (actor_id) REFERENCES sys_user(id),
    INDEX idx_organization_batch_actor (actor_id, created_at)
);
