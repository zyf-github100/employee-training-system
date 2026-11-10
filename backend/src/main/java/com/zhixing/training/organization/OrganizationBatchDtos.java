package com.zhixing.training.organization;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class OrganizationBatchDtos {
    private OrganizationBatchDtos() {}
    public static final String UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    public record Target(@Min(1) long id, @Min(0) long version) {}
    public record TransferRequest(
            @NotBlank @Pattern(regexp=UUID_PATTERN) String requestId,
            @NotEmpty @Size(max=200) List<@NotNull @Valid Target> targets,
            @NotNull @Min(1) Long departmentId,
            boolean updateJobTitle, @Size(max=80) String jobTitle) {}
    public record RoleRequest(
            @NotBlank @Pattern(regexp=UUID_PATTERN) String requestId,
            @NotEmpty @Size(max=200) List<@NotNull @Valid Target> targets,
            @NotBlank @Pattern(regexp="EMPLOYEE|ADMIN") String roleCode,
            @NotBlank @Pattern(regexp="SELF|DEPARTMENT|DEPARTMENT_TREE|CUSTOM|ALL") String dataScope,
            @NotNull @Size(max=5000) List<@NotNull @Min(1) Long> customDepartmentIds) {}
    public record Result(String requestId, int requested, int changed, int clearedLeaders) {}
    public record HistoryView(String requestId, long actorId, String operation, Instant createdAt,
                              Result result, List<OrganizationDtos.Employee> before, List<OrganizationDtos.Employee> after,
                              Instant rolledBackAt, String rollbackResult) {}
    public record RollbackRequest(@Size(max=200) String reason) {}
}
