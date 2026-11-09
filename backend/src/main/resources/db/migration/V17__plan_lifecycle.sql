-- 培训计划支持终止与延期：终止只改变计划状态，不删除分配、进度或已签发证书。
ALTER TABLE training_plan
  ADD COLUMN terminated_at TIMESTAMP NULL,
  ADD COLUMN termination_reason VARCHAR(500) NULL;
