package com.zhixing.training.communication;

import com.zhixing.training.api.*;
import com.zhixing.training.organization.DataScopeService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Statement;
import java.util.*;
import static com.zhixing.training.communication.CommunicationDtos.*;

@Service
public class AnnouncementService {
    private final JdbcTemplate jdbc;
    private final DataScopeService scopes;
    public AnnouncementService(JdbcTemplate jdbc,DataScopeService scopes){this.jdbc=jdbc;this.scopes=scopes;}
    private String select(long actor,boolean admin) {
        String counts=admin?scopes.resolve(actor).userCondition("u"):"1=0";
        return "SELECT a.*,p.name publisher_name,r.read_at,(SELECT COUNT(*) FROM announcement_recipient ar JOIN sys_user u ON u.id=ar.user_id WHERE ar.announcement_id=a.id AND "+counts+") recipient_count,(SELECT COUNT(*) FROM announcement_recipient ar JOIN sys_user u ON u.id=ar.user_id WHERE ar.announcement_id=a.id AND ar.read_at IS NOT NULL AND "+counts+") read_count FROM training_announcement a JOIN sys_user p ON p.id=a.created_by LEFT JOIN announcement_recipient r ON r.announcement_id=a.id AND r.user_id="+actor;
    }
    private String condition(long actor,boolean admin) {
        if(!admin)return "a.status='PUBLISHED' AND r.user_id="+actor;
        return scopes.resolve(actor).all()?"1=1":"a.created_by="+actor;
    }
    private static final org.springframework.jdbc.core.RowMapper<AnnouncementView> MAP=(rs,row)->new AnnouncementView(rs.getLong("id"),rs.getString("title"),rs.getString("category"),rs.getString("content"),rs.getString("status"),rs.getString("publisher_name"),rs.getTimestamp("published_at").toInstant(),rs.getTimestamp("withdrawn_at")==null?null:rs.getTimestamp("withdrawn_at").toInstant(),rs.getTimestamp("read_at")==null?null:rs.getTimestamp("read_at").toInstant(),rs.getInt("recipient_count"),rs.getInt("read_count"),rs.getLong("version"));
    @Transactional(readOnly=true)
    public PageResult<AnnouncementView> list(long actor,boolean admin,int page,int size) {
        FeedbackService.validatePage(page,size);
        String where=" WHERE "+condition(actor,admin),base=select(actor,admin);
        // Counting uses the same visibility join as the page, without materializing content.
        String count="SELECT COUNT(*) FROM training_announcement a LEFT JOIN announcement_recipient r ON r.announcement_id=a.id AND r.user_id=?"+where;
        long total=jdbc.queryForObject(count,Long.class,actor);
        return new PageResult<>(jdbc.query(base+where+" ORDER BY a.id DESC LIMIT ? OFFSET ?",MAP,size,(page-1)*size),page,size,total);
    }
    public long unread(long actor) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM announcement_recipient r JOIN training_announcement a ON a.id=r.announcement_id WHERE r.user_id=? AND r.read_at IS NULL AND a.status='PUBLISHED'",Long.class,actor);
    }
    @Transactional
    public AnnouncementView publish(long actor,AnnouncementRequest body) {
        scopes.lockMutations();var scope=scopes.resolve(actor);
        if(body.employeeIds().isEmpty()||body.employeeIds().size()>5000)throw FeedbackService.bad("公告名单须为 1 至 5000 人");
        var ids=body.employeeIds().stream().distinct().sorted().toList();
        String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
        int count=jdbc.queryForObject("SELECT COUNT(*) FROM sys_user u WHERE u.id IN ("+placeholders+") AND u.role_code='EMPLOYEE' AND u.status='ACTIVE' AND u.activated=TRUE AND "+scope.userCondition("u"),Integer.class,ids.toArray());
        if(count!=ids.size())throw DataScopeService.denied();
        var old=jdbc.queryForList("SELECT id FROM training_announcement WHERE created_by=? AND request_id=?",Long.class,actor,body.requestId().toString());
        if(!old.isEmpty()) {
            var view=get(actor,true,old.get(0));
            var recipients=jdbc.queryForList("SELECT user_id FROM announcement_recipient WHERE announcement_id=? ORDER BY user_id",Long.class,view.id());
            if(!view.title().equals(body.title().trim())||!view.category().equals(body.category())||!view.content().equals(body.content().trim())||!recipients.equals(ids))throw new ApiException(HttpStatus.CONFLICT,"REQUEST_REUSED","发布编号已用于其他公告内容或名单");
            return view;
        }
        var key=new GeneratedKeyHolder();
        jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO training_announcement(request_id,title,category,content,created_by) VALUES(?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);ps.setString(1,body.requestId().toString());ps.setString(2,body.title().trim());ps.setString(3,body.category());ps.setString(4,body.content().trim());ps.setLong(5,actor);return ps;},key);
        long id=key.getKey().longValue();
        jdbc.batchUpdate("INSERT INTO announcement_recipient(announcement_id,user_id) VALUES(?,?)",ids,500,(ps,user)->{ps.setLong(1,id);ps.setLong(2,user);});
        return get(actor,true,id);
    }
    @Transactional
    public AnnouncementView read(long actor,long id) {
        var found=jdbc.queryForList("SELECT a.id FROM training_announcement a JOIN announcement_recipient r ON r.announcement_id=a.id WHERE a.id=? AND r.user_id=? AND a.status='PUBLISHED' FOR UPDATE",Long.class,id,actor);
        if(found.isEmpty())throw missing();
        jdbc.update("UPDATE announcement_recipient SET read_at=COALESCE(read_at,CURRENT_TIMESTAMP(6)) WHERE announcement_id=? AND user_id=?",id,actor);
        return get(actor,false,id);
    }
    @Transactional
    public AnnouncementView withdraw(long actor,long id,long version) {
        scopes.lockMutations();String where=condition(actor,true);
        var found=jdbc.queryForList("SELECT a.id FROM training_announcement a WHERE a.id=? AND "+where+" FOR UPDATE",Long.class,id);
        if(found.isEmpty())throw DataScopeService.denied();
        var view=get(actor,true,id);
        if(view.status().equals("WITHDRAWN"))return view;
        if(view.version()!=version)throw new ApiException(HttpStatus.CONFLICT,"VERSION_CONFLICT","公告已更新，请刷新后重试");
        jdbc.update("UPDATE training_announcement SET status='WITHDRAWN',withdrawn_at=CURRENT_TIMESTAMP(6),version=version+1 WHERE id=?",id);
        return get(actor,true,id);
    }
    private AnnouncementView get(long actor,boolean admin,long id) {
        return jdbc.query(select(actor,admin)+" WHERE a.id=? AND "+condition(actor,admin),MAP,id).stream().findFirst().orElseThrow(AnnouncementService::missing);
    }
    private static ApiException missing(){return new ApiException(HttpStatus.NOT_FOUND,"ANNOUNCEMENT_NOT_FOUND","公告不存在或当前账号无权读取");}
}
