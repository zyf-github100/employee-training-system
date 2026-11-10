package com.zhixing.training.organization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.training.api.ApiException;
import com.zhixing.training.auth.AuthService;
import com.zhixing.training.auth.SessionRepository;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.zhixing.training.api.PageResult;
import java.util.*;
import static com.zhixing.training.organization.OrganizationBatchDtos.*;

@Service
public class OrganizationBatchService {
    private final JdbcTemplate jdbc;
    private final DataScopeService scopes;
    private final OrganizationService organization;
    private final SessionRepository sessions;
    private final ObjectMapper json;
    private final Validator validator;

    public OrganizationBatchService(JdbcTemplate jdbc, DataScopeService scopes, OrganizationService organization,
                                    SessionRepository sessions, ObjectMapper json, Validator validator) {
        this.jdbc=jdbc; this.scopes=scopes; this.organization=organization;
        this.sessions=sessions; this.json=json; this.validator=validator;
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Result transfer(long actor, TransferRequest body) {
        validate(body);
        var targets=targets(body.targets());
        String title=Objects.toString(body.jobTitle(), "").trim();
        String payload=encode(List.of("TRANSFER", targets, body.departmentId(), body.updateJobTitle(), body.updateJobTitle()?title:""));
        String requestId=UUID.fromString(body.requestId()).toString();
        scopes.lockMutations(); var scope=scopes.resolve(actor);
        var previous=previous(actor, requestId, payload);
        if(previous!=null) return previous;
        if(!scope.containsDepartment(body.departmentId())) throw DataScopeService.denied();
        requireActiveDepartment(body.departmentId());
        var employees=employees(actor, targets, scope);
        var before = new ArrayList<>(employees);
        int changed=0, cleared=0;
        for(var employee:employees) {
            String nextTitle=body.updateJobTitle()?title:employee.jobTitle();
            if(Objects.equals(employee.departmentId(), body.departmentId()) && Objects.equals(employee.jobTitle(), nextTitle)) continue;
            jdbc.update("UPDATE sys_user SET department_id=?,job_title=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                    body.departmentId(), nextTitle, employee.id());
            cleared+=organization.clearInvalidLeadership(employee.id());
            sessions.revokeAll(employee.id());
            changed++;
        }
        return store(actor, requestId, "TRANSFER", payload, new Result(requestId, targets.size(), changed, cleared), before, snapshot(actor, targets));
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Result roles(long actor, RoleRequest body) {
        validate(body);
        var targets=targets(body.targets());
        var custom=body.customDepartmentIds().stream().distinct().sorted().toList();
        if("EMPLOYEE".equals(body.roleCode()) && !"SELF".equals(body.dataScope())) throw bad("员工的数据范围必须为本人");
        if("CUSTOM".equals(body.dataScope())==custom.isEmpty()) throw bad("自定义范围须指定部门，其他范围不能指定部门");
        String payload=encode(List.of("ROLES", targets, body.roleCode(), body.dataScope(), custom));
        String requestId=UUID.fromString(body.requestId()).toString();
        scopes.lockMutations(); var scope=scopes.resolve(actor);
        if(!scope.superAdmin()) throw DataScopeService.denied();
        var previous=previous(actor, requestId, payload);
        if(previous!=null) return previous;
        for(long department:custom) requireActiveDepartment(department);
        var employees=employees(actor, targets, scope);
        var before = new ArrayList<>(employees);
        int changed=0;
        for(var employee:employees) {
            if(employee.roleCode().equals(body.roleCode()) && employee.dataScope().equals(body.dataScope())
                    && employee.customDepartmentIds().equals(custom)) continue;
            jdbc.update("UPDATE sys_user SET role_code=?,data_scope=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                    body.roleCode(), body.dataScope(), employee.id());
            jdbc.update("DELETE FROM user_scope_department WHERE user_id=?", employee.id());
            for(long department:custom) jdbc.update("INSERT INTO user_scope_department(user_id,department_id) VALUES(?,?)", employee.id(), department);
            sessions.revokeAll(employee.id());
            changed++;
        }
        return store(actor, requestId, "ROLES", payload, new Result(requestId, targets.size(), changed, 0), before, snapshot(actor, targets));
    }

    public PageResult<HistoryView> history(long actor, int pageNo, int pageSize, String operation) {
        if (pageNo < 1 || pageSize < 1 || pageSize > 100) throw bad("分页参数不合法");
        var scope = scopes.resolve(actor);
        String where = scope.superAdmin() ? "WHERE (?='' OR operation=?)" : "WHERE actor_id=? AND (?='' OR operation=?)";
        Object[] countArgs = scope.superAdmin() ? new Object[]{operation, operation} : new Object[]{actor, operation, operation};
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM organization_batch " + where, Long.class, countArgs);
        List<Object> args = new ArrayList<>(Arrays.asList(countArgs));
        args.add(pageSize); args.add((pageNo - 1) * pageSize);
        List<HistoryView> list = jdbc.query("SELECT request_id,actor_id,operation,created_at,result_json,before_json,after_json,rolled_back_at,rollback_result_json FROM organization_batch " + where + " ORDER BY created_at DESC LIMIT ? OFFSET ?", (rs, row) -> historyRow(rs), args.toArray());
        return new PageResult<>(list, pageNo, pageSize, total);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public HistoryView rollback(long actor, String requestId, RollbackRequest body) {
        scopes.lockMutations();
        var row = jdbc.query("SELECT request_id,actor_id,operation,created_at,result_json,before_json,after_json,rolled_back_at,rollback_result_json FROM organization_batch WHERE request_id=? FOR UPDATE", (rs, r) -> historyRow(rs), requestId).stream().findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "批量变更记录不存在"));
        if (row.actorId() != actor && !scopes.resolve(actor).superAdmin()) throw DataScopeService.denied();
        if (row.rolledBackAt() != null) return row;
        if (row.before().isEmpty() || row.after().isEmpty()) throw bad("该记录没有可回滚的变更快照");
        var before = row.before(); var after = row.after();
        if ("ROLES".equals(row.operation())) scopes.requireSuper(actor);
        for (int i = 0; i < before.size(); i++) {
            var old = before.get(i); var expected = after.get(i);
            scopes.requireUser(actor, old.id());
            var current = organization.employee(actor, old.id());
            if (current.version() != expected.version() || current.id() != expected.id()) throw new ApiException(HttpStatus.CONFLICT, "ROLLBACK_CONFLICT", "目标账号已发生其他变更，本次未回滚任何账号");
            if (current.superAdmin() || current.id() == actor) throw bad("批量回滚不包含本人或超级管理员账号");
        }
        int changed = 0;
        for (int i = 0; i < before.size(); i++) {
            var old = before.get(i);
            if ("TRANSFER".equals(row.operation())) {
                jdbc.update("UPDATE sys_user SET department_id=?,job_title=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND version=?", old.departmentId(), old.jobTitle(), old.id(), after.get(i).version());
                organization.clearInvalidLeadership(old.id());
            } else {
                jdbc.update("UPDATE sys_user SET role_code=?,super_admin=?,data_scope=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND version=?", old.roleCode(), old.superAdmin(), old.dataScope(), old.id(), after.get(i).version());
                jdbc.update("DELETE FROM user_scope_department WHERE user_id=?", old.id());
                for (long department : old.customDepartmentIds()) jdbc.update("INSERT INTO user_scope_department(user_id,department_id) VALUES(?,?)", old.id(), department);
            }
            sessions.revokeAll(old.id()); changed++;
        }
        String result = encode(new Result(requestId, before.size(), changed, 0));
        jdbc.update("UPDATE organization_batch SET rolled_back_at=CURRENT_TIMESTAMP(6),rollback_result_json=? WHERE request_id=?", result, requestId);
        return history(requestId, actor);
    }

    // Validate every target while locking rows in a deterministic order, before writing any employee.
    private List<OrganizationDtos.Employee> employees(long actor, List<Target> targets, DataScopeService.Scope scope) {
        var employees=new ArrayList<OrganizationDtos.Employee>();
        for(var target:targets) {
            scopes.requireUser(actor, target.id());
            jdbc.queryForList("SELECT id FROM sys_user WHERE id=? FOR UPDATE", Long.class, target.id());
            var employee=organization.employee(actor, target.id());
            if(employee.superAdmin() || employee.id()==actor) throw bad("批量操作不包含本人或超级管理员，请单独编辑这些账号");
            if(!scope.superAdmin() && !"EMPLOYEE".equals(employee.roleCode())) throw DataScopeService.denied();
            if(employee.version()!=target.version()) throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                    "所选账号已被修改，本次未保存任何变更。请刷新列表后重新选择");
            employees.add(employee);
        }
        return employees;
    }

    private List<Target> targets(List<Target> values) {
        if(values.stream().map(Target::id).distinct().count()!=values.size()) throw bad("批量名单中存在重复账号");
        return values.stream().sorted(Comparator.comparingLong(Target::id)).toList();
    }

    private Result previous(long actor, String id, String payload) {
        return jdbc.query("SELECT actor_id,payload_hash,result_json FROM organization_batch WHERE request_id=?", (rs,row)->{
            if(rs.getLong("actor_id")!=actor || !AuthService.hash(payload).equals(rs.getString("payload_hash")))
                throw new ApiException(HttpStatus.CONFLICT, "BATCH_REQUEST_CONFLICT", "该请求编号已被使用，请刷新后重新提交");
            try { return json.readValue(rs.getString("result_json"), Result.class); }
            catch(Exception e) { throw new IllegalStateException(e); }
        }, id).stream().findFirst().orElse(null);
    }

    private Result store(long actor, String id, String operation, String payload, Result result, Object before, Object after) {
        jdbc.update("INSERT INTO organization_batch(request_id,actor_id,operation,payload_hash,payload_json,result_json,before_json,after_json) VALUES(?,?,?,?,?,?,?,?)",
                id, actor, operation, AuthService.hash(payload), payload, encode(result), encode(before), encode(after));
        return result;
    }

    private List<OrganizationDtos.Employee> snapshot(long actor, List<Target> targets) { return targets.stream().map(target -> organization.employee(actor, target.id())).toList(); }
    private HistoryView historyRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        try {
            return new HistoryView(rs.getString("request_id"), rs.getLong("actor_id"), rs.getString("operation"), rs.getTimestamp("created_at").toInstant(),
                    json.readValue(rs.getString("result_json"), Result.class),
                    rs.getString("before_json") == null ? List.of() : json.readValue(rs.getString("before_json"), new com.fasterxml.jackson.core.type.TypeReference<List<OrganizationDtos.Employee>>() {}),
                    rs.getString("after_json") == null ? List.of() : json.readValue(rs.getString("after_json"), new com.fasterxml.jackson.core.type.TypeReference<List<OrganizationDtos.Employee>>() {}),
                    rs.getTimestamp("rolled_back_at") == null ? null : rs.getTimestamp("rolled_back_at").toInstant(), rs.getString("rollback_result_json"));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private HistoryView history(String requestId, long actor) { return jdbc.query("SELECT request_id,actor_id,operation,created_at,result_json,before_json,after_json,rolled_back_at,rollback_result_json FROM organization_batch WHERE request_id=?", (rs,row)->historyRow(rs), requestId).stream().findFirst().orElseThrow(); }

    private void requireActiveDepartment(long id) {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM org_department WHERE id=? AND status='ACTIVE'", Integer.class, id)!=1)
            throw bad("目标或授权部门不存在或已停用");
    }
    private void validate(Object request) {
        if(request==null || !validator.validate(request).isEmpty()) throw bad("请选择 1–200 个账号，并检查请求编号、版本和目标配置");
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException(e); }
    }
    private static ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BATCH", message); }
}
