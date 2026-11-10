package com.zhixing.training.auth;

import java.time.Instant;

public record UserRecord(long id, String employeeNo, String passwordHash, String name, Long departmentId,
                         String departmentName, String jobTitle, String email, String phone, String roleCode,
                         String status, boolean activated, int failedLoginCount, Instant lockedUntil,
                         boolean mustChangePassword, long version) {
    public boolean active() { return "ACTIVE".equals(status) && activated; }
}
