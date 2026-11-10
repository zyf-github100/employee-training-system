package com.zhixing.training.auth;

import com.zhixing.training.api.ApiException;
import com.zhixing.training.organization.DataScopeService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
public class InvitationDeliveryService {
    private final JdbcTemplate jdbc; private final AccountSetupService setup; private final DataScopeService scopes;
    public InvitationDeliveryService(JdbcTemplate jdbc,AccountSetupService setup,DataScopeService scopes){this.jdbc=jdbc;this.setup=setup;this.scopes=scopes;}
    public record Request(@NotBlank @Pattern(regexp="EMAIL|SMS") String channel){ }
    public record View(long deliveryId,long userId,String employeeNo,String channel,String destination,String status,String setupToken,Instant expiresAt,Instant createdAt){ }
    @Transactional
    public View deliver(long actor,long userId,long version,Request body){
        scopes.requireUser(actor,userId);var target=jdbc.query("SELECT employee_no,email,phone,status,activated,version FROM sys_user WHERE id=?",(rs,row)->new Target(rs.getString("employee_no"),rs.getString("email"),rs.getString("phone"),rs.getString("status"),rs.getBoolean("activated"),rs.getLong("version")),userId).stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"EMPLOYEE_NOT_FOUND","员工不存在"));
        if(target.version()!=version)throw new ApiException(HttpStatus.CONFLICT,"VERSION_CONFLICT","员工资料已更新，请刷新后重试");
        String channel=body.channel().trim().toUpperCase();String destination="EMAIL".equals(channel)?target.email():target.phone();
        if(destination==null||destination.isBlank())throw new ApiException(HttpStatus.BAD_REQUEST,"INVITATION_DESTINATION_MISSING","该员工没有可用的邮箱或手机号");
        var issued=setup.issue(actor,userId,version);
        jdbc.update("UPDATE invitation_delivery SET status='CANCELLED',updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND status IN ('PENDING','QUEUED')",userId);
        jdbc.update("INSERT INTO invitation_delivery(user_id,channel,destination,setup_token_hash,status,attempt_count) VALUES(?,?,?,?,'QUEUED',1)",userId,channel,destination,AuthService.hash(issued.token()));
        long id=jdbc.queryForObject("SELECT id FROM invitation_delivery WHERE user_id=? AND setup_token_hash=?",Long.class,userId,AuthService.hash(issued.token()));
        jdbc.update("UPDATE invitation_delivery SET channel=?,destination=?,status='QUEUED',attempt_count=1, sent_at=NULL, updated_at=CURRENT_TIMESTAMP WHERE id=?",channel,destination,id);
        return view(id,issued.token());
    }
    public View latest(long actor,long userId){scopes.requireUser(actor,userId);return jdbc.query("SELECT d.id,d.user_id,u.employee_no,d.channel,d.destination,d.status,d.created_at,t.expires_at FROM invitation_delivery d JOIN sys_user u ON u.id=d.user_id LEFT JOIN account_setup_token t ON t.user_id=d.user_id WHERE d.user_id=? ORDER BY d.id DESC LIMIT 1",(rs,row)->new View(rs.getLong("id"),rs.getLong("user_id"),rs.getString("employee_no"),rs.getString("channel"),rs.getString("destination"),rs.getString("status"),null,rs.getTimestamp("expires_at")==null?null:rs.getTimestamp("expires_at").toInstant(),rs.getTimestamp("created_at").toInstant()),userId).stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"INVITATION_NOT_FOUND","没有邀请投递记录"));}
    private View view(long id,String token){return jdbc.query("SELECT d.id,d.user_id,u.employee_no,d.channel,d.destination,d.status,d.created_at,t.expires_at FROM invitation_delivery d JOIN sys_user u ON u.id=d.user_id JOIN account_setup_token t ON t.user_id=d.user_id WHERE d.id=?",(rs,row)->new View(rs.getLong("id"),rs.getLong("user_id"),rs.getString("employee_no"),rs.getString("channel"),rs.getString("destination"),rs.getString("status"),token,rs.getTimestamp("expires_at").toInstant(),rs.getTimestamp("created_at").toInstant()),id).stream().findFirst().orElseThrow();}
    private record Target(String employeeNo,String email,String phone,String status,boolean activated,long version){}
}
