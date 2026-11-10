package com.zhixing.training.auth;

import com.zhixing.training.api.ApiException;
import com.zhixing.training.organization.DataScopeService;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class AccountSetupService {
    public static final String PENDING_PASSWORD="!pending-account-setup";
    private final JdbcTemplate jdbc;private final DataScopeService scopes;private final PasswordEncoder passwords;private final SessionRepository sessions;
    private final SecureRandom random=new SecureRandom();
    public AccountSetupService(JdbcTemplate jdbc,DataScopeService scopes,PasswordEncoder passwords,SessionRepository sessions){this.jdbc=jdbc;this.scopes=scopes;this.passwords=passwords;this.sessions=sessions;}
    public record Issued(long userId,String employeeNo,String token,String purpose,Instant expiresAt) {}
    public record CompleteRequest(@NotBlank @Size(max=32) String employeeNo,@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token,@NotBlank @Size(min=8,max=72) String newPassword) {}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Issued issue(long actor,long target,long version) {
        scopes.lockMutations();scopes.requireUser(actor,target);
        var user=lock(target);
        if(!scopes.resolve(actor).superAdmin()&&!"EMPLOYEE".equals(user.role))throw DataScopeService.denied();
        if(user.version!=version)throw new ApiException(HttpStatus.CONFLICT,"VERSION_CONFLICT","账号已被修改，请刷新后重试");
        if(!"ACTIVE".equals(user.status))throw new ApiException(HttpStatus.BAD_REQUEST,"ACCOUNT_DISABLED","请先启用账号再生成设置密码链接");
        if(user.superAdmin && user.activated && jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE super_admin=TRUE AND role_code='ADMIN' AND status='ACTIVE' AND activated=TRUE",Integer.class)<=1)
            throw new ApiException(HttpStatus.CONFLICT,"LAST_SUPER_ADMIN","最后一个可用超级管理员请通过个人资料修改密码");
        if(actor==target)throw new ApiException(HttpStatus.BAD_REQUEST,"SELF_RESET_UNSUPPORTED","请通过个人资料修改自己的密码，或请其他管理员协助重置");
        String purpose=user.activated?"RESET":"ACTIVATE";
        byte[] bytes=new byte[32];random.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expires=Instant.now().plus(Duration.ofHours(24));
        jdbc.update("UPDATE sys_user SET activated=FALSE,password_hash=?,failed_login_count=0,locked_until=NULL,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",PENDING_PASSWORD,target);
        sessions.revokeAll(target);
        jdbc.update("INSERT INTO account_setup_token(user_id,token_hash,purpose,issued_by,user_version,expires_at) VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE token_hash=VALUES(token_hash),purpose=VALUES(purpose),issued_by=VALUES(issued_by),user_version=VALUES(user_version),expires_at=VALUES(expires_at),consumed_at=NULL",target,AuthService.hash(token),purpose,actor,version+1,Timestamp.from(expires));
        return new Issued(target,user.employeeNo,token,purpose,expires);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public long complete(CompleteRequest body) {
        AuthService.validatePasswordBytes(body.newPassword());
        if(body.newPassword().length()<8||!body.newPassword().matches("(?s).*[A-Za-z].*")||!body.newPassword().matches("(?s).*[0-9].*"))throw new ApiException(HttpStatus.BAD_REQUEST,"PASSWORD_WEAK","新密码至少 8 位，且需同时包含字母和数字");
        var ids=jdbc.queryForList("SELECT u.id FROM sys_user u JOIN account_setup_token t ON t.user_id=u.id WHERE u.employee_no=? AND t.token_hash=? AND t.consumed_at IS NULL AND t.expires_at>CURRENT_TIMESTAMP",Long.class,body.employeeNo().trim().toUpperCase(Locale.ROOT),AuthService.hash(body.token()));
        if(ids.isEmpty())throw invalid();
        scopes.lockMutations();long id=ids.get(0);var user=lock(id);
        var tokens=jdbc.query("SELECT issued_by,user_version FROM account_setup_token WHERE user_id=? AND token_hash=? AND consumed_at IS NULL AND expires_at>CURRENT_TIMESTAMP",(rs,row)->new long[]{rs.getLong(1),rs.getLong(2)},id,AuthService.hash(body.token()));
        if(tokens.isEmpty()||user.activated||!"ACTIVE".equals(user.status)||user.version!=tokens.get(0)[1])throw invalid();
        try {long issuer=tokens.get(0)[0];scopes.requireUser(issuer,id);if(!scopes.resolve(issuer).superAdmin()&&!"EMPLOYEE".equals(user.role))throw invalid();}
        catch(ApiException e){throw invalid();}
        jdbc.update("UPDATE sys_user SET password_hash=?,activated=TRUE,must_change_password=FALSE,failed_login_count=0,locked_until=NULL,password_changed_at=CURRENT_TIMESTAMP,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",passwords.encode(body.newPassword()),id);
        jdbc.update("UPDATE account_setup_token SET consumed_at=CURRENT_TIMESTAMP WHERE user_id=?",id);sessions.revokeAll(id);return id;
    }
    private Account lock(long id) {return jdbc.query("SELECT * FROM sys_user WHERE id=? FOR UPDATE",(rs,row)->new Account(rs.getString("employee_no"),rs.getString("role_code"),rs.getString("status"),rs.getBoolean("activated"),rs.getBoolean("super_admin"),rs.getLong("version")),id).stream().findFirst().orElseThrow(AccountSetupService::invalid);}
    private record Account(String employeeNo,String role,String status,boolean activated,boolean superAdmin,long version) {}
    private static ApiException invalid(){return new ApiException(HttpStatus.BAD_REQUEST,"SETUP_LINK_INVALID","设置密码链接无效、已使用或已过期，请联系管理员重新生成");}
}
