package com.zhixing.training.organization;

import com.zhixing.training.api.*;
import com.zhixing.training.auth.SessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.util.*;
import static com.zhixing.training.organization.OrganizationDtos.*;

@Service
public class OrganizationService {
    private final JdbcTemplate jdbc;
    private final DataScopeService scopes;
    private final PasswordEncoder passwords;
    private final SessionRepository sessions;
    public OrganizationService(JdbcTemplate jdbc,DataScopeService scopes,PasswordEncoder passwords,SessionRepository sessions) {
        this.jdbc=jdbc;this.scopes=scopes;this.passwords=passwords;this.sessions=sessions;
    }
    public AccessView access(long actor) { var s=scopes.resolve(actor);return new AccessView(s.superAdmin(),s.kind(),s.departmentIds()); }
    public List<Department> departments(long actor) {
        var scope=scopes.resolve(actor);
        return jdbc.query("SELECT d.*,u.name leader_name,u.employee_no leader_employee_no FROM org_department d LEFT JOIN sys_user u ON u.id=d.leader_user_id ORDER BY d.sort_no,d.id",(rs,row)->new Department(rs.getLong("id"),(Long)rs.getObject("parent_id"),rs.getString("code"),rs.getString("name"),rs.getString("status"),rs.getInt("sort_no"),rs.getLong("version"),(Long)rs.getObject("leader_user_id"),rs.getString("leader_name"),rs.getString("leader_employee_no")))
            .stream().filter(d->scope.containsDepartment(d.id())).toList();
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public long saveDepartment(long actor,Long id,DepartmentRequest b) {
        scopes.lockMutations();scopes.requireSuper(actor);
        if(id!=null && count("SELECT COUNT(*) FROM org_department WHERE id=? AND version=?",id,b.version())!=1) throw conflict();
        if(count("SELECT COUNT(*) FROM org_department WHERE code=? AND id<>?",b.code(),id==null?0:id)>0) throw bad("部门编码已存在");
        if(b.parentId()!=null) {
            if(Objects.equals(id,b.parentId()) || id!=null && scopes.descendants(id).contains(b.parentId())) throw bad("不能将部门移动到自身或下级部门");
            if(count("SELECT COUNT(*) FROM org_department WHERE id=?"+("ACTIVE".equals(b.status())?" AND status='ACTIVE'":""),b.parentId())!=1) throw bad("上级部门不存在或已停用");
        }
        if(id!=null && "DISABLED".equals(b.status())) {
            if(count("SELECT COUNT(*) FROM org_department WHERE parent_id=? AND status='ACTIVE'",id)>0 || count("SELECT COUNT(*) FROM sys_user WHERE department_id=? AND status='ACTIVE'",id)>0) throw bad("请先停用或迁移该部门的有效子部门及在职人员");
        }
        if(id==null) {
            var key=new GeneratedKeyHolder();
            jdbc.update(c->{var p=c.prepareStatement("INSERT INTO org_department(parent_id,code,name,status,sort_no) VALUES(?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);p.setObject(1,b.parentId());p.setString(2,b.code().trim().toUpperCase(Locale.ROOT));p.setString(3,b.name().trim());p.setString(4,b.status());p.setInt(5,b.sortNo());return p;},key);
            return key.getKey().longValue();
        }
        jdbc.update("UPDATE org_department SET parent_id=?,code=?,name=?,status=?,sort_no=?,version=version+1 WHERE id=?",b.parentId(),b.code().trim().toUpperCase(Locale.ROOT),b.name().trim(),b.status(),b.sortNo(),id);
        return id;
    }
    public PageResult<Employee> employees(long actor,int pageNo,int pageSize,String keyword,Long departmentId,String status,String role) {
        if(pageNo<1 || pageNo>100000 || pageSize<1 || pageSize>100) throw bad("分页参数不合法");
        var s=scopes.resolve(actor);
        StringBuilder where=new StringBuilder(" WHERE "+s.userCondition("u"));var args=new ArrayList<Object>();
        if(keyword!=null&&!keyword.isBlank()){where.append(" AND (u.employee_no LIKE ? OR u.name LIKE ?)");args.add("%"+keyword.trim()+"%");args.add("%"+keyword.trim()+"%");}
        if(departmentId!=null){where.append(" AND u.department_id=?");args.add(departmentId);}
        if(status!=null&&!status.isBlank()){where.append(" AND u.status=?");args.add(status);}
        if(role!=null&&!role.isBlank()){where.append(" AND u.role_code=?");args.add(role);}
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM sys_user u"+where,Long.class,args.toArray());
        args.add(pageSize);args.add((pageNo-1)*pageSize);
        var list=jdbc.query(SELECT+where+" ORDER BY u.id DESC LIMIT ? OFFSET ?",(rs,row)->employee(rs),args.toArray());
        return new PageResult<>(list,pageNo,pageSize,total);
    }
    public Employee employee(long actor,long id) { scopes.requireUser(actor,id);return find(id); }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void setLeader(long actor,long department,LeaderRequest body) {
        scopes.lockMutations();scopes.requireSuper(actor);
        if(count("SELECT COUNT(*) FROM org_department WHERE id=? AND version=?",department,body.version())!=1)throw conflict();
        if(body.leaderUserId()!=null) {
            if(count("SELECT COUNT(*) FROM org_department WHERE id=? AND status='ACTIVE'",department)!=1)throw bad("停用部门不能设置负责人");
            if(count("SELECT COUNT(*) FROM sys_user WHERE id=? AND department_id=? AND status='ACTIVE'",body.leaderUserId(),department)!=1)throw bad("负责人必须是本部门的启用账号");
        }
        jdbc.update("UPDATE org_department SET leader_user_id=?,version=version+1 WHERE id=?",body.leaderUserId(),department);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Employee saveEmployee(long actor,Long id,EmployeeRequest b) {
        scopes.lockMutations();var scope=scopes.resolve(actor);
        Employee old=null;
        if(id!=null) {scopes.requireUser(actor,id);lockUser(id);old=find(id);if(old.version()!=b.version())throw conflict();}
        if(!scope.superAdmin() && (old!=null && !"EMPLOYEE".equals(old.roleCode()) || !"EMPLOYEE".equals(b.roleCode()) || b.superAdmin() || !"SELF".equals(b.dataScope()) || b.customDepartmentIds()!=null&&!b.customDepartmentIds().isEmpty())) throw DataScopeService.denied();
        if(!scope.containsDepartment(b.departmentId())) throw DataScopeService.denied();
        if(count("SELECT COUNT(*) FROM org_department WHERE id=? AND status='ACTIVE'",b.departmentId())!=1) throw bad("请选择启用的部门");
        if("EMPLOYEE".equals(b.roleCode()) && (b.superAdmin() || !"SELF".equals(b.dataScope()))) throw bad("员工账号的数据范围必须为本人");
        if(b.superAdmin() && !"ALL".equals(b.dataScope())) throw bad("超级管理员的数据范围必须为全部");
        List<Long> custom=b.customDepartmentIds()==null?List.of():b.customDepartmentIds().stream().distinct().sorted().toList();
        if(!"CUSTOM".equals(b.dataScope())&&!custom.isEmpty()) throw bad("非自定义范围不能指定部门");
        if("CUSTOM".equals(b.dataScope())&&custom.isEmpty()) throw bad("请至少选择一个授权部门");
        for(long d:custom) if(count("SELECT COUNT(*) FROM org_department WHERE id=? AND status='ACTIVE'",d)!=1) throw bad("授权部门不存在或已停用");
        String no=b.employeeNo().trim().toUpperCase(Locale.ROOT);
        if(old!=null&&!old.employeeNo().equalsIgnoreCase(no)) throw bad("已有账号的工号不能更改");
        if(count("SELECT COUNT(*) FROM sys_user WHERE employee_no=? AND id<>?",no,id==null?0:id)>0) throw new ApiException(HttpStatus.CONFLICT,"EMPLOYEE_NO_EXISTS","工号已存在");
        if(old!=null && b.activated() && count("SELECT COUNT(*) FROM sys_user WHERE id=? AND password_hash=?",id,com.zhixing.training.auth.AccountSetupService.PENDING_PASSWORD)>0) throw bad("请先生成激活链接，由员工设置密码后激活账号");
        if(old!=null&&old.superAdmin()&&old.activated()&&"ACTIVE".equals(old.status())&&(!b.superAdmin()||!b.activated()||!"ACTIVE".equals(b.status()))) {
            if(count("SELECT COUNT(*) FROM sys_user WHERE super_admin=TRUE AND role_code='ADMIN' AND status='ACTIVE' AND activated=TRUE")<=1) throw new ApiException(HttpStatus.CONFLICT,"LAST_SUPER_ADMIN","必须保留至少一个启用的超级管理员");
        }
        if(id==null) {
            validatePassword(b.initialPassword());String hash=passwords.encode(b.initialPassword());var key=new GeneratedKeyHolder();
            jdbc.update(c->{var p=c.prepareStatement("INSERT INTO sys_user(employee_no,password_hash,name,department_id) VALUES(?,?,?,?)",Statement.RETURN_GENERATED_KEYS);p.setString(1,no);p.setString(2,hash);p.setString(3,b.name().trim());p.setLong(4,b.departmentId());return p;},key);
            id=key.getKey().longValue();
            jdbc.update("UPDATE sys_user SET must_change_password=TRUE,password_changed_at=NULL WHERE id=?", id);
        }
        jdbc.update("UPDATE sys_user SET name=?,department_id=?,job_title=?,email=?,phone=?,role_code=?,super_admin=?,data_scope=?,status=?,activated=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",b.name().trim(),b.departmentId(),b.jobTitle(),b.email(),b.phone(),b.roleCode(),b.superAdmin(),b.dataScope(),b.status(),b.activated(),id);
        clearInvalidLeadership(id);
        jdbc.update("DELETE FROM user_scope_department WHERE user_id=?",id);
        for(long d:custom) jdbc.update("INSERT INTO user_scope_department(user_id,department_id) VALUES(?,?)",id,d);
        if(old!=null && (!Objects.equals(old.departmentId(),b.departmentId()) || !old.roleCode().equals(b.roleCode()) || old.superAdmin()!=b.superAdmin() || !old.dataScope().equals(b.dataScope()) || !old.customDepartmentIds().equals(custom) || !old.status().equals(b.status()) || old.activated()!=b.activated())) sessions.revokeAll(id);
        return find(id);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Employee unlock(long actor,long id,long version) {
        scopes.lockMutations();scopes.requireUser(actor,id);lockUser(id);var old=find(id);
        if(!scopes.resolve(actor).superAdmin()&&!"EMPLOYEE".equals(old.roleCode()))throw DataScopeService.denied();
        if(old.version()!=version)throw conflict();
        jdbc.update("UPDATE sys_user SET locked_until=NULL,failed_login_count=0,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",id);
        return find(id);
    }
    private void lockUser(long id) { jdbc.queryForList("SELECT id FROM sys_user WHERE id=? FOR UPDATE",Long.class,id); }
    // Caller holds authorization_guard inside a transaction, including batch changes.
    public int clearInvalidLeadership(long id) {
        return jdbc.update("UPDATE org_department d JOIN sys_user u ON u.id=d.leader_user_id SET d.leader_user_id=NULL,d.version=d.version+1 WHERE u.id=? AND (u.status<>'ACTIVE' OR u.department_id IS NULL OR u.department_id<>d.id)",id);
    }
    private static final String SELECT="SELECT u.*,d.name department_name FROM sys_user u LEFT JOIN org_department d ON d.id=u.department_id";
    private Employee find(long id) { return jdbc.query(SELECT+" WHERE u.id=?",(rs,row)->employee(rs),id).stream().findFirst().orElseThrow(DataScopeService::denied); }
    private Employee employee(java.sql.ResultSet rs)throws java.sql.SQLException {
        var locked=rs.getTimestamp("locked_until");long id=rs.getLong("id");
        return new Employee(id,rs.getString("employee_no"),rs.getString("name"),(Long)rs.getObject("department_id"),rs.getString("department_name"),rs.getString("job_title"),rs.getString("email"),rs.getString("phone"),rs.getString("role_code"),rs.getBoolean("super_admin"),rs.getString("data_scope"),jdbc.queryForList("SELECT department_id FROM user_scope_department WHERE user_id=? ORDER BY department_id",Long.class,id),rs.getString("status"),rs.getBoolean("activated"),locked==null?null:locked.toInstant(),rs.getLong("version"));
    }
    private void validatePassword(String p) { if(p==null || p.length()<8 || p.getBytes(StandardCharsets.UTF_8).length>72 || !p.matches("(?s).*[A-Za-z].*") || !p.matches("(?s).*[0-9].*")) throw bad("初始密码须为 8 至 72 字节，并包含字母和数字"); }
    private int count(String sql,Object...args){return jdbc.queryForObject(sql,Integer.class,args);}
    private static ApiException bad(String m){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ORGANIZATION",m);}
    private static ApiException conflict(){return new ApiException(HttpStatus.CONFLICT,"VERSION_CONFLICT","记录已被修改，请刷新后重试");}
}
