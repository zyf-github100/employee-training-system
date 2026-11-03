package com.zhixing.training.organization;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class EmployeeImportDtos {
    private EmployeeImportDtos() {}
    public record Fields(
        @NotBlank(message="工号不能为空") @Pattern(regexp="[A-Za-z0-9_-]{2,32}",message="工号须为 2–32 位字母、数字、下划线或连字符") String employeeNo,
        @NotBlank(message="姓名不能为空") @Size(max=40,message="姓名不能超过 40 字") String name,
        @NotBlank(message="部门编码不能为空") @Pattern(regexp="[A-Za-z0-9_-]{1,40}",message="部门编码格式不正确") String departmentCode,
        @Size(max=80,message="岗位不能超过 80 字") String jobTitle,
        @Email(message="邮箱格式不正确") @Size(max=120,message="邮箱不能超过 120 字") String email,
        @Pattern(regexp="^$|^[0-9+()\\- ]{6,30}$",message="电话须为 6–30 位数字或电话符号") String phone) {}
    public record Row(int rowNumber, Fields fields, List<String> errors) {}
    public record Preview(String id,String status,int totalRows,int validRows,int errorRows,
        Instant expiresAt,Instant committedAt,int pageNo,int pageSize,List<Row> rows) {}
    public record Summary(String id,String status,int totalRows,Instant createdAt,Instant expiresAt,Instant committedAt) {}
}
