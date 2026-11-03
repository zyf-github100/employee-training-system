CREATE TABLE platform_setting (
    id INT PRIMARY KEY,
    company_name VARCHAR(80) NOT NULL,
    contact_name VARCHAR(40) NOT NULL,
    contact_email VARCHAR(100) NOT NULL DEFAULT '',
    unread_reminders BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (updated_by) REFERENCES sys_user(id)
);
INSERT INTO platform_setting(id,company_name,contact_name) VALUES(1,'知行企业学习平台','培训管理员');

CREATE TABLE training_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    request_id CHAR(36) NOT NULL,
    title VARCHAR(80) NOT NULL,
    feedback_type VARCHAR(20) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    reply VARCHAR(2000) NULL,
    replied_by BIGINT NULL,
    replied_at TIMESTAMP(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_feedback_request(user_id,request_id),
    INDEX idx_feedback_author(user_id,id),
    INDEX idx_feedback_status(status,id),
    FOREIGN KEY (user_id) REFERENCES sys_user(id),
    FOREIGN KEY (replied_by) REFERENCES sys_user(id)
);

CREATE TABLE training_announcement (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id CHAR(36) NOT NULL,
    title VARCHAR(80) NOT NULL,
    category VARCHAR(20) NOT NULL,
    content VARCHAR(3000) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    created_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    published_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    withdrawn_at TIMESTAMP(6) NULL,
    UNIQUE KEY uk_announcement_request(created_by,request_id),
    INDEX idx_announcement_author(created_by,id),
    FOREIGN KEY (created_by) REFERENCES sys_user(id)
);
CREATE TABLE announcement_recipient (
    announcement_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    read_at TIMESTAMP(6) NULL,
    PRIMARY KEY (announcement_id,user_id),
    INDEX idx_announcement_recipient(user_id,announcement_id),
    FOREIGN KEY (announcement_id) REFERENCES training_announcement(id),
    FOREIGN KEY (user_id) REFERENCES sys_user(id)
);
