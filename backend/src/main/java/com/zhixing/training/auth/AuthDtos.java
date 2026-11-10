package com.zhixing.training.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AuthDtos {
    private AuthDtos() { }
    public record LoginRequest(@NotBlank @Size(max=32) String employeeNo, @NotBlank @Size(max=72) String password) { }
    public record UserView(long id, String employeeNo, String name, String departmentName, String jobTitle, String email, String phone, String roleCode, boolean mustChangePassword, long version) {
        public static UserView from(UserRecord u) { return new UserView(u.id(),u.employeeNo(),u.name(),u.departmentName(),u.jobTitle(),u.email(),u.phone(),u.roleCode(),u.mustChangePassword(),u.version()); }
    }
    public record LoginResponse(String accessToken, long expiresIn, UserView user) { }
    public record ProfileRequest(@NotBlank @Size(max=40) String name, @Email @Size(max=120) String email,
                                 @Pattern(regexp="^$|^[0-9+()\\- ]{6,30}$", message="手机号格式不合法") String phone,
                                 @Size(max=80) String jobTitle, long version) { }
    public record PasswordRequest(@NotBlank @Size(max=72) String currentPassword, @NotBlank @Size(min=8,max=72) String newPassword) { }
    public record SessionView(String id, String expiresAt) { }
}
