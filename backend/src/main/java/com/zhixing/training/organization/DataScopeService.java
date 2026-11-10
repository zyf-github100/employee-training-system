package com.zhixing.training.organization;

import com.zhixing.training.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class DataScopeService {
    private final JdbcTemplate jdbc;
    public DataScopeService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public record Scope(long actorId, boolean superAdmin, String kind, List<Long> departmentIds) {
        public boolean all() { return "ALL".equals(kind); }
        // The only interpolated values are typed database IDs, never request strings.
        // Aliases are constants supplied by repositories.
        public String userCondition(String alias) {
            if(all()) return "1=1";
            if("SELF".equals(kind)) return alias+".id="+actorId;
            return departmentIds.isEmpty()?"1=0":alias+".department_id IN ("+ids(departmentIds)+")";
        }
        public boolean containsDepartment(Long id) { return all() || id!=null && departmentIds.contains(id); }
    }
    public Scope resolve(long actor) {
        return jdbc.query("SELECT role_code,status,activated,super_admin,data_scope,department_id FROM sys_user WHERE id=?",(rs,row)->{
            if(!"ADMIN".equals(rs.getString("role_code")) || !"ACTIVE".equals(rs.getString("status")) || !rs.getBoolean("activated")) throw denied();
            boolean superAdmin=rs.getBoolean("super_admin");
            String kind=superAdmin?"ALL":rs.getString("data_scope");
            Long department=(Long)rs.getObject("department_id");
            List<Long> departments=switch(kind) {
                case "CUSTOM" -> jdbc.queryForList("SELECT department_id FROM user_scope_department WHERE user_id=? ORDER BY department_id",Long.class,actor);
                case "DEPARTMENT_TREE" -> department==null?List.of():descendants(department);
                case "DEPARTMENT" -> department==null?List.of():List.of(department);
                default -> List.of();
            };
            if(!Set.of("SELF","DEPARTMENT","DEPARTMENT_TREE","CUSTOM","ALL").contains(kind)) throw denied();
            return new Scope(actor,superAdmin,kind,departments);
        },actor).stream().findFirst().orElseThrow(DataScopeService::denied);
    }
    public List<Long> descendants(long department) {
        return jdbc.queryForList("WITH RECURSIVE tree AS (SELECT id FROM org_department WHERE id=? UNION ALL SELECT d.id FROM org_department d JOIN tree t ON d.parent_id=t.id) SELECT id FROM tree",Long.class,department);
    }
    public void lockMutations() { jdbc.queryForList("SELECT id FROM authorization_guard WHERE id=1 FOR UPDATE",Integer.class); }
    public void requireSuper(long actor) { if(!resolve(actor).superAdmin()) throw denied(); }
    public void requireUser(long actor,long target) {
        Scope scope=resolve(actor);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user u WHERE u.id=? AND "+scope.userCondition("u"),Integer.class,target)!=1) throw denied();
    }
    public void requireContentOwner(long actor,String table,long id) {
        if(!Set.of("training_plan","exam_paper","course").contains(table)) throw new IllegalArgumentException("Invalid content table");
        if(!resolve(actor).all() && jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE id=? AND created_by=?",Integer.class,id,actor)!=1) throw denied();
    }
    private static String ids(List<Long> values) { return values.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")); }
    public static ApiException denied() { return new ApiException(HttpStatus.FORBIDDEN,"DATA_SCOPE_DENIED","目标不在当前账号的管理范围内"); }
}
