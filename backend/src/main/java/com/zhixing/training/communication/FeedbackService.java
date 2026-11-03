package com.zhixing.training.communication;

import com.zhixing.training.api.*;
import com.zhixing.training.organization.DataScopeService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Statement;
import java.util.List;
import static com.zhixing.training.communication.CommunicationDtos.*;

@Service
public class FeedbackService {
    private final JdbcTemplate jdbc;
    private final DataScopeService scopes;
    public FeedbackService(JdbcTemplate jdbc,DataScopeService scopes){this.jdbc=jdbc;this.scopes=scopes;}
    private static final String SELECT="SELECT f.*,u.employee_no,u.name author_name,d.name department_name,r.name replied_by_name FROM training_feedback f JOIN sys_user u ON u.id=f.user_id LEFT JOIN org_department d ON d.id=u.department_id LEFT JOIN sys_user r ON r.id=f.replied_by ";
    private static final RowMapper<FeedbackView> MAP=(rs,row)->new FeedbackView(rs.getLong("id"),rs.getString("title"),rs.getString("feedback_type"),rs.getString("content"),rs.getString("status"),rs.getLong("user_id"),rs.getString("employee_no"),rs.getString("author_name"),rs.getString("department_name"),rs.getString("reply"),rs.getString("replied_by_name"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("replied_at")==null?null:rs.getTimestamp("replied_at").toInstant(),rs.getLong("version"));
    @Transactional(readOnly=true)
    public PageResult<FeedbackView> list(long actor,boolean admin,int page,int size,String status,String keyword) {
        validatePage(page,size);
        if(!List.of("","PENDING","REPLIED").contains(status)||keyword.length()>80)throw bad("筛选条件不合法");
        String condition=admin?scopes.resolve(actor).userCondition("u"):"u.id="+actor;
        String where=" WHERE "+condition+" AND (?='' OR f.status=?) AND (?='' OR f.title LIKE ? OR f.content LIKE ?)";
        String q=keyword.isBlank()?"":"%"+keyword.trim()+"%";
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM training_feedback f JOIN sys_user u ON u.id=f.user_id"+where,Long.class,status,status,q,q,q);
        var rows=jdbc.query(SELECT+where+" ORDER BY f.id DESC LIMIT ? OFFSET ?",MAP,status,status,q,q,q,size,(page-1)*size);
        return new PageResult<>(rows,page,size,total);
    }
    @Transactional
    public FeedbackView create(long actor,FeedbackRequest body) {
        // Serialize this account's retries without blocking submissions by other employees.
        var active=jdbc.queryForObject("SELECT status='ACTIVE' AND activated=TRUE FROM sys_user WHERE id=? FOR UPDATE",Boolean.class,actor);
        if(!Boolean.TRUE.equals(active))throw DataScopeService.denied();
        var existing=jdbc.query(SELECT+" WHERE f.user_id=? AND f.request_id=?",MAP,actor,body.requestId().toString());
        if(!existing.isEmpty()) {
            var old=existing.get(0);
            if(!old.title().equals(body.title().trim())||!old.content().equals(body.content().trim())||!old.type().equals(body.type()))throw new ApiException(HttpStatus.CONFLICT,"REQUEST_REUSED","该提交编号已用于其他内容，请重新提交");
            return old;
        }
        var key=new GeneratedKeyHolder();
        jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO training_feedback(user_id,request_id,title,feedback_type,content) VALUES(?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);ps.setLong(1,actor);ps.setString(2,body.requestId().toString());ps.setString(3,body.title().trim());ps.setString(4,body.type());ps.setString(5,body.content().trim());return ps;},key);
        return get(key.getKey().longValue());
    }
    @Transactional
    public FeedbackView reply(long actor,long id,ReplyRequest body) {
        scopes.lockMutations();String scope=scopes.resolve(actor).userCondition("u");
        var versions=jdbc.queryForList("SELECT f.version FROM training_feedback f JOIN sys_user u ON u.id=f.user_id WHERE f.id=? AND "+scope+" FOR UPDATE",Long.class,id);
        if(versions.isEmpty())throw DataScopeService.denied();
        if(versions.get(0)!=body.version())throw new ApiException(HttpStatus.CONFLICT,"VERSION_CONFLICT","反馈已有新回复，请重新读取后再编辑");
        jdbc.update("UPDATE training_feedback SET reply=?,status='REPLIED',replied_by=?,replied_at=CURRENT_TIMESTAMP(6),version=version+1 WHERE id=?",body.reply().trim(),actor,id);
        return get(id);
    }
    private FeedbackView get(long id){return jdbc.queryForObject(SELECT+" WHERE f.id=?",MAP,id);}
    static void validatePage(int page,int size){if(page<1||page>100000||size<1||size>100)throw bad("分页参数不合法");}
    static ApiException bad(String message){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST",message);}
}
