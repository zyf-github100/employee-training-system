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
    private final CertificateLinks links;
    public CertificateRepository(JdbcTemplate jdbc,com.zhixing.training.organization.DataScopeService scopes,CertificateLinks links) { this.jdbc = jdbc; this.scopes=scopes; this.links=links; }

    private static final String BASE="SELECT tc.*,rv.name revoked_by_name,CASE WHEN tc.revoked_at IS NULL THEN 'VALID' ELSE 'REVOKED' END status FROM training_certificate tc JOIN sys_user u ON u.id=tc.user_id LEFT JOIN sys_user rv ON rv.id=tc.revoked_by WHERE 1=1";
    private static final String ORDER=" ORDER BY tc.issued_at DESC,tc.id DESC";
    private static final java.security.SecureRandom RANDOM=new java.security.SecureRandom();
    public List<CertificateDtos.CertificateView> employee(long userId) {
        return jdbc.query(BASE+" AND tc.user_id=?"+ORDER,(rs,row)->view(rs),userId);
    }
    public List<CertificateDtos.CertificateView> admin(long actor) {
        return jdbc.query(BASE+" AND "+scopes.resolve(actor).userCondition("u")+ORDER,(rs,row)->view(rs));
    }
    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public com.zhixing.training.api.PageResult<CertificateDtos.CertificateView> page(long actor,boolean admin,int page,int size,String keyword,String status) {
        if(page<1||page>100000||size<1||size>100||keyword.length()>80||!List.of("","VALID","REVOKED").contains(status))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","证书查询参数不合法");
        String scope=admin?scopes.resolve(actor).userCondition("u"):"tc.user_id="+actor;
        String where=" AND "+scope+(status.isEmpty()?"":" AND tc.revoked_at IS "+("VALID".equals(status)?"NULL":"NOT NULL"));
        where+=" AND (?='' OR tc.certificate_no LIKE ? OR tc.recipient_name_snapshot LIKE ? OR tc.employee_no_snapshot LIKE ? OR tc.plan_title_snapshot LIKE ?)";
        String q=keyword.isBlank()?"":"%"+keyword.trim()+"%";
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM training_certificate tc JOIN sys_user u ON u.id=tc.user_id WHERE 1=1"+where,Long.class,q,q,q,q,q);
        var list=jdbc.query(BASE+where+ORDER+" LIMIT ? OFFSET ?",(rs,row)->view(rs),q,q,q,q,q,size,(page-1)*size);
        return new com.zhixing.training.api.PageResult<>(list,page,size,total);
    }
    public CertificateDtos.CertificateView authorized(long actor,boolean admin,long id) {
        String scope=admin?scopes.resolve(actor).userCondition("u"):"tc.user_id="+actor;
        return jdbc.query(BASE+" AND tc.id=? AND "+scope,(rs,row)->view(rs),id).stream().findFirst().orElseThrow(CertificateRepository::missing);
    }
    @org.springframework.transaction.annotation.Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CertificateDtos.CertificateView issue(long planId,long userId) {
        Integer completed=jdbc.queryForObject("SELECT COUNT(*) FROM training_assignment WHERE plan_id=? AND user_id=? AND status='COMPLETED'",Integer.class,planId,userId);
        if(completed==null||completed!=1)throw new ApiException(HttpStatus.BAD_REQUEST,"PLAN_NOT_COMPLETED","培训计划尚未完成，不能签发证书");
        String no="ZHX-"+Year.now(java.time.ZoneOffset.UTC).getValue()+"-"+planId+"-"+userId;
        byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);String code=java.util.HexFormat.of().formatHex(bytes);
        jdbc.update("INSERT INTO training_certificate(certificate_no,plan_id,user_id,title,recipient_name_snapshot,employee_no_snapshot,plan_title_snapshot,issuer_name,completed_at_snapshot,verification_code) SELECT ?,tp.id,u.id,CONCAT(tp.title,'结业证书'),u.name,u.employee_no,tp.title,p.company_name,COALESCE(ta.completed_at,CURRENT_TIMESTAMP),? FROM training_assignment ta JOIN training_plan tp ON tp.id=ta.plan_id JOIN sys_user u ON u.id=ta.user_id JOIN platform_setting p ON p.id=1 WHERE ta.plan_id=? AND ta.user_id=? AND ta.status='COMPLETED' ON DUPLICATE KEY UPDATE id=training_certificate.id",no,code,planId,userId);
        return jdbc.query(BASE+" AND tc.plan_id=? AND tc.user_id=?",(rs,row)->view(rs),planId,userId).stream().findFirst().orElseThrow(CertificateRepository::missing);
    }
    @org.springframework.transaction.annotation.Transactional
    public CertificateDtos.CertificateView revoke(long actor,long id,CertificateDtos.RevokeRequest body) {
        if(body.reason().isBlank())throw new ApiException(HttpStatus.BAD_REQUEST,"REASON_REQUIRED","请填写撤销原因");
        scopes.lockMutations();String scope=scopes.resolve(actor).userCondition("u");
        var found=jdbc.queryForList("SELECT tc.id FROM training_certificate tc JOIN sys_user u ON u.id=tc.user_id WHERE tc.id=? AND "+scope+" FOR UPDATE",Long.class,id);
        if(found.isEmpty())throw com.zhixing.training.organization.DataScopeService.denied();
        var old=authorized(actor,true,id);
        if(old.revokedAt()!=null) {
            if(body.reason().trim().equals(old.revocationReason()))return old;
            throw new ApiException(HttpStatus.CONFLICT,"CERTIFICATE_REVOKED","证书已撤销，不能覆盖原撤销记录");
        }
        if(old.version()!=body.version())throw new ApiException(HttpStatus.CONFLICT,"VERSION_CONFLICT","证书状态已更新，请刷新后重试");
        jdbc.update("UPDATE training_certificate SET revoked_at=CURRENT_TIMESTAMP,revoked_by=?,revocation_reason=?,version=version+1 WHERE id=?",actor,body.reason().trim(),id);
        return authorized(actor,true,id);
    }
    public CertificateDtos.VerificationView verify(String code) {
        if(code==null||!code.matches("[a-fA-F0-9]{64}"))throw missingVerification();
        var cert=jdbc.query(BASE+" AND tc.verification_code=?",(rs,row)->view(rs),code.toLowerCase(java.util.Locale.ROOT)).stream().findFirst().orElseThrow(CertificateRepository::missingVerification);
        String no=cert.employeeNo();String masked=no.length()<=4?"*".repeat(no.length()):"****"+no.substring(no.length()-4);
        return new CertificateDtos.VerificationView(cert.certificateNo(),cert.recipientName(),masked,cert.planTitle(),cert.issuerName(),cert.completedAt(),cert.issuedAt(),cert.status(),cert.revokedAt(),cert.legacySnapshot(),Instant.now());
    }
    private static ApiException missing(){return new ApiException(HttpStatus.NOT_FOUND,"CERTIFICATE_NOT_FOUND","证书不存在或当前账号无权读取");}
    private static ApiException missingVerification(){return new ApiException(HttpStatus.NOT_FOUND,"VERIFICATION_NOT_FOUND","未找到对应证书，请检查验真码或重新扫描原证书二维码");}
    private CertificateDtos.CertificateView view(java.sql.ResultSet rs)throws java.sql.SQLException{return new CertificateDtos.CertificateView(rs.getLong("id"),rs.getString("certificate_no"),rs.getLong("plan_id"),rs.getString("plan_title_snapshot"),rs.getString("recipient_name_snapshot"),rs.getString("employee_no_snapshot"),rs.getString("title"),instant(rs.getTimestamp("issued_at")),instant(rs.getTimestamp("revoked_at")),rs.getString("status"),rs.getString("issuer_name"),instant(rs.getTimestamp("completed_at_snapshot")),rs.getString("verification_code"),rs.getLong("version"),rs.getString("revocation_reason"),rs.getString("revoked_by_name"),"LEGACY".equals(rs.getString("snapshot_origin")),links.optionalVerification(rs.getString("verification_code")));}
    private Instant instant(Timestamp value){return value==null?null:value.toInstant();}
}
