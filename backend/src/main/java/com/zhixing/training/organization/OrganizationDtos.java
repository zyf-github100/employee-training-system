package com.zhixing.training.organization;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class OrganizationDtos {
    private OrganizationDtos() {}
    public record Department(long id, Long parentId, String code, String name, String status, int sortNo, long version,
                             Long leaderUserId, String leaderName, String leaderEmployeeNo) {}
    public record LeaderRequest(@Min(1) Long leaderUserId, @Min(0) long version) {}
    public record DepartmentRequest(Long parentId,
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,40}") String code,
        @NotBlank @Size(max=80) String name,
        @NotBlank @Pattern(regexp="ACTIVE|DISABLED") String status,
        @Min(0) @Max(100000) int sortNo, @Min(0) long version) {}
    public record Employee(long id,String employeeNo,String name,Long departmentId,String departmentName,
        String jobTitle,String email,String phone,String roleCode,boolean superAdmin,String dataScope,
        List<Long> customDepartmentIds,String status,boolean activated,Instant lockedUntil,long version) {}
    public record EmployeeRequest(
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{2,32}") String employeeNo,
        @NotBlank @Size(max=40) String name,@NotNull @Min(1) Long departmentId,
        @Size(max=80) String jobTitle,@Email @Size(max=120) String email,
        @Pattern(regexp="^$|^[0-9+()\\- ]{6,30}$") String phone,
        @NotBlank @Pattern(regexp="ADMIN|EMPLOYEE") String roleCode,boolean superAdmin,
        @NotBlank @Pattern(regexp="SELF|DEPARTMENT|DEPARTMENT_TREE|CUSTOM|ALL") String dataScope,
        @Size(max=5000) List<@NotNull @Min(1) Long> customDepartmentIds,
        @NotBlank @Pattern(regexp="ACTIVE|DISABLED") String status,boolean activated,
        @Size(max=72) String initialPassword,@Min(0) long version) {}
    public record VersionRequest(@Min(0) long version) {}
    public record AccessView(boolean superAdmin,String dataScope,List<Long> departmentIds) {}
}
