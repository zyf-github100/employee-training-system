package com.zhixing.training.audit;

import com.zhixing.training.api.*;
import com.zhixing.training.organization.DataScopeService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.ArrayList;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;private final DataScopeService scopes;
    public AuditService(JdbcTemplate jdbc,DataScopeService scopes){this.jdbc=jdbc;this.scopes=scopes;}
    public record Entry(long id,Long actorId,String actorName,String action,String target,int httpStatus,String outcome,Instant occurredAt) {}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void record(Long actor,String action,String target,int status) {
        String name=actor==null?null:jdbc.query("SELECT name FROM sys_user WHERE id=?",(rs,row)->rs.getString(1),actor).stream().findFirst().orElse(null);
        jdbc.update("INSERT INTO audit_log(actor_id,actor_name,action,target,http_status,outcome) VALUES(?,?,?,?,?,?)",actor,name,action,target.substring(0,Math.min(250,target.length())),status,status<400?"SUCCESS":status==401||status==403?"DENIED":"FAILED");
    }
    public PageResult<Entry> list(long actor,int page,int size,String outcome) {
        scopes.requireSuper(actor);
        if(page<1 || page>100000 || size<1 || size>100) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGE","分页参数不合法");
        var args=new ArrayList<Object>();String where="";
        if(outcome!=null&&!outcome.isBlank()){where=" WHERE outcome=?";args.add(outcome);}
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM audit_log"+where,Long.class,args.toArray());
        args.add(size);args.add((page-1)*size);
        return new PageResult<>(jdbc.query("SELECT * FROM audit_log"+where+" ORDER BY id DESC LIMIT ? OFFSET ?",(rs,row)->new Entry(rs.getLong("id"),(Long)rs.getObject("actor_id"),rs.getString("actor_name"),rs.getString("action"),rs.getString("target"),rs.getInt("http_status"),rs.getString("outcome"),rs.getTimestamp("occurred_at").toInstant()),args.toArray()),page,size,total);
    }
}
