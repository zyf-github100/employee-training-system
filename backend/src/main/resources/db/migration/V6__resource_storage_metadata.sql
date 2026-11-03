ALTER TABLE course_resource
    ADD COLUMN original_name VARCHAR(255) NULL,
    ADD COLUMN media_type VARCHAR(120) NULL,
    ADD COLUMN storage_key VARCHAR(255) NULL,
    ADD COLUMN sha256 CHAR(64) NULL;
