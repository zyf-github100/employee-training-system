-- 学习时长由服务端按心跳间隔累计，客户端只上报播放位置与是否在学。
-- 历史已完成记录保留 completed_at，不回退；历史进度从 0 秒开始累计。
ALTER TABLE study_progress
  ADD COLUMN watched_seconds INT NOT NULL DEFAULT 0,
  ADD COLUMN last_reported_at TIMESTAMP NULL;

UPDATE study_progress SET last_reported_at = updated_at WHERE last_reported_at IS NULL;
