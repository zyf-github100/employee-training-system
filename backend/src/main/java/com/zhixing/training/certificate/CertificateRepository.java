package com.zhixing.training.certificate;

import com.zhixing.training.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.Year;
import java.util.List;

@Repository
public class CertificateRepository {
    private final JdbcTemplate jdbc;
    private final com.zhixing.training.organization.DataScopeService scopes;
    public CertificateRepository(JdbcTemplate jdbc,com.zhixing.training.organization.DataScopeService scopes) { this.jdbc = jdbc; this.scopes=scopes; }

    public List<CertificateDtos.CertificateView> employee(long userId) {
        return jdbc.query(sql(" AND tc.user_id=?"), (rs,row) -> view(rs), userId);
    }
    public List<CertificateDtos.CertificateView> admin(long actor) {
        return jdbc.query(sql(" AND "+scopes.resolve(actor).userCondition("u")), (rs,row) -> view(rs));
    }
    public CertificateDtos.CertificateView issue(long planId,long userId) {
        Integer completed=jdbc.queryForObject("SELECT COUNT(*) FROM training_assignment WHERE plan_id=? AND user_id=? AND status='COMPLETED'",Integer.class,planId,userId);
        if(completed==null||completed!=1) throw new ApiException(HttpStatus.BAD_REQUEST,"PLAN_NOT_COMPLETED","培训计划尚未完成，不能签发证书");
        String no="ZHX-"+Year.now().getValue()+"-"+planId+"-"+userId;
        jdbc.update("INSERT INTO training_certificate(certificate_no,plan_id,user_id,title) SELECT ?,tp.id,?,CONCAT(tp.title,'结业证书') FROM training_plan tp WHERE tp.id=? ON DUPLICATE KEY UPDATE certificate_no=VALUES(certificate_no)",no,userId,planId);
        return jdbc.query(sql(" AND tc.plan_id=? AND tc.user_id=?"),(rs,row)->view(rs),planId,userId).stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"CERTIFICATE_NOT_FOUND","证书不存在"));
    }
    private String sql(String filter){return "SELECT tc.id,tc.certificate_no,tc.plan_id,tp.title plan_title,u.name recipient_name,u.employee_no,tc.title,tc.issued_at,tc.revoked_at,CASE WHEN tc.revoked_at IS NULL THEN 'VALID' ELSE 'REVOKED' END status FROM training_certificate tc JOIN training_plan tp ON tp.id=tc.plan_id JOIN sys_user u ON u.id=tc.user_id WHERE 1=1"+filter+" ORDER BY tc.issued_at DESC,tc.id DESC";}
    private CertificateDtos.CertificateView view(java.sql.ResultSet rs)throws java.sql.SQLException{return new CertificateDtos.CertificateView(rs.getLong("id"),rs.getString("certificate_no"),rs.getLong("plan_id"),rs.getString("plan_title"),rs.getString("recipient_name"),rs.getString("employee_no"),rs.getString("title"),instant(rs.getTimestamp("issued_at")),instant(rs.getTimestamp("revoked_at")),rs.getString("status"));}
    private Instant instant(Timestamp value){return value==null?null:value.toInstant();}
}
