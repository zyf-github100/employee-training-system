package com.zhixing.training.organization;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.training.api.ApiException;
import com.zhixing.training.auth.AccountSetupService;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import static com.zhixing.training.organization.EmployeeImportDtos.*;

@Service
public class EmployeeImportService {
    private final JdbcTemplate jdbc;private final DataScopeService scopes;private final OrganizationService organization;
    private final EmployeeWorkbook workbook;private final ObjectMapper json;private final Validator validator;private final AccountSetupService setup;
    public EmployeeImportService(JdbcTemplate jdbc,DataScopeService scopes,OrganizationService organization,EmployeeWorkbook workbook,ObjectMapper json,Validator validator,AccountSetupService setup){this.jdbc=jdbc;this.scopes=scopes;this.organization=organization;this.workbook=workbook;this.json=json;this.validator=validator;this.setup=setup;}
    public byte[] template(long actor){return workbook.template(organization.departments(actor));}
    public Preview preview(long actor,MultipartFile file) {
        scopes.resolve(actor);
        List<Row> rows=validate(actor,workbook.parse(file));String id=UUID.randomUUID().toString();Instant expires=Instant.now().plus(Duration.ofMinutes(30));
        jdbc.update("INSERT INTO employee_import(id,actor_id,status,rows_json,expires_at) VALUES(?,?,'PREVIEW',?,?)",id,actor,encode(rows),Timestamp.from(expires));
        return page(new Job(id,"PREVIEW",rows,expires,null),1,50);
    }
    public List<Summary> recent(long actor) {
        scopes.resolve(actor);
        return jdbc.query("SELECT id,status,JSON_LENGTH(rows_json) row_count,created_at,expires_at,committed_at FROM employee_import WHERE actor_id=? AND (expires_at>CURRENT_TIMESTAMP OR status='COMMITTED') ORDER BY created_at DESC LIMIT 10",(rs,row)->new Summary(rs.getString("id"),rs.getString("status"),rs.getInt("row_count"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("expires_at").toInstant(),instant(rs.getTimestamp("committed_at"))),actor);
    }
    public Preview read(long actor,String id,int page,int size){return page(owned(actor,id),page,size);}
    public byte[] report(long actor,String id){return workbook.report(owned(actor,id).rows);}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Preview commit(long actor,String id) {
        scopes.lockMutations();Job job=owned(actor,id);
        if("COMMITTED".equals(job.status))return page(job,1,50);
        var checked=validate(actor,job.rows);
        if(checked.stream().anyMatch(row->!row.errors().isEmpty())) {
            jdbc.update("UPDATE employee_import SET status='REVIEW_REQUIRED',rows_json=? WHERE id=?",encode(checked),id);
            return page(new Job(id,"REVIEW_REQUIRED",checked,job.expires,null),1,50);
        }
        var departments=availableDepartments(actor);
        jdbc.batchUpdate("INSERT INTO sys_user(employee_no,password_hash,name,department_id,job_title,email,phone,activated,role_code,data_scope) VALUES(?,?,?,?,?,?,?,FALSE,'EMPLOYEE','SELF')",checked,250,(p,row)->{
            var f=row.fields();p.setString(1,f.employeeNo());p.setString(2,AccountSetupService.PENDING_PASSWORD);p.setString(3,f.name());p.setLong(4,departments.get(f.departmentCode()));p.setString(5,f.jobTitle());p.setString(6,f.email());p.setString(7,f.phone());
        });
        jdbc.batchUpdate("INSERT INTO employee_import_user(import_id,user_id,source_row) SELECT ?,id,? FROM sys_user WHERE employee_no=?",checked,250,(p,row)->{p.setString(1,id);p.setInt(2,row.rowNumber());p.setString(3,row.fields().employeeNo());});
        Instant committed=Instant.now();jdbc.update("UPDATE employee_import SET status='COMMITTED',rows_json=?,committed_at=? WHERE id=?",encode(checked),Timestamp.from(committed),id);
        return page(new Job(id,"COMMITTED",checked,job.expires,committed),1,50);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public byte[] activationList(long actor,String id,String base) {
        scopes.lockMutations();Job job=owned(actor,id);
        if(!"COMMITTED".equals(job.status))throw new ApiException(HttpStatus.CONFLICT,"IMPORT_NOT_COMMITTED","请先确认导入");
        var members=jdbc.query("SELECT u.id,u.employee_no,u.name,u.status,u.activated,u.version FROM employee_import_user i JOIN sys_user u ON u.id=i.user_id WHERE i.import_id=? ORDER BY i.source_row",(rs,n)->new Member(rs.getLong("id"),rs.getString("employee_no"),rs.getString("name"),rs.getString("status"),rs.getBoolean("activated"),rs.getLong("version")),id);
        List<List<String>> rows=new ArrayList<>();
        for(var m:members) {
            if(m.activated || !"ACTIVE".equals(m.status)) {rows.add(List.of(m.employeeNo,m.name,m.activated?"已激活，未生成链接":"账号停用，未生成链接","","",""));continue;}
            var token=setup.issue(actor,m.id,m.version);
            // Keep tokens in the URL fragment so they are not sent in HTTP request URLs.
            String path=base+"/#/account-setup?employeeNo="+m.employeeNo+"&token="+token.token();
            rows.add(List.of(m.employeeNo,m.name,"待设置密码",path,token.token(),token.expiresAt().toString()));
        }
        return workbook.activationList(rows);
    }
    private Job owned(long actor,String id) {
        var scope=scopes.resolve(actor);
        Job job=jdbc.query("SELECT * FROM employee_import WHERE id=? AND actor_id=?",(rs,row)->new Job(rs.getString("id"),rs.getString("status"),decode(rs.getString("rows_json")),rs.getTimestamp("expires_at").toInstant(),instant(rs.getTimestamp("committed_at"))),id,actor).stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"IMPORT_NOT_FOUND","导入任务不存在"));
        if(!"COMMITTED".equals(job.status)&&!job.expires.isAfter(Instant.now()))throw new ApiException(HttpStatus.GONE,"IMPORT_EXPIRED","预览已过期，请重新上传校验");
        if("COMMITTED".equals(job.status) && jdbc.queryForObject("SELECT COUNT(*) FROM employee_import_user i JOIN sys_user u ON u.id=i.user_id WHERE i.import_id=? AND NOT ("+scope.userCondition("u")+")",Integer.class,id)>0)throw DataScopeService.denied();
        return job;
    }
    private List<Row> validate(long actor,List<Row> rows) {
        var departments=availableDepartments(actor);
        var counts=rows.stream().collect(Collectors.groupingBy(r->r.fields().employeeNo(),Collectors.counting()));
        Set<String> existing=new HashSet<>();var numbers=rows.stream().map(r->r.fields().employeeNo()).distinct().toList();
        for(int from=0;from<numbers.size();from+=500){var batch=numbers.subList(from,Math.min(from+500,numbers.size()));existing.addAll(jdbc.queryForList("SELECT UPPER(employee_no) FROM sys_user WHERE employee_no IN ("+String.join(",",Collections.nCopies(batch.size(),"?"))+")",String.class,batch.toArray()));}
        List<Row> result=new ArrayList<>();
        for(var row:rows) {
            var f=row.fields();List<String> errors=new ArrayList<>(row.errors());
            validator.validate(f).stream().sorted(Comparator.comparing(v->v.getPropertyPath().toString())).forEach(v->errors.add(v.getMessage()));
            if(counts.get(f.employeeNo())>1)errors.add("文件内工号重复");
            if(existing.contains(f.employeeNo()))errors.add("工号已存在，不能覆盖已有账号");
            if(!departments.containsKey(f.departmentCode()))errors.add("部门不存在、已停用或超出当前授权范围");
            result.add(new Row(row.rowNumber(),f,errors.stream().distinct().toList()));
        }
        return result;
    }
    private Map<String,Long> availableDepartments(long actor){return organization.departments(actor).stream().filter(d->"ACTIVE".equals(d.status())).collect(Collectors.toMap(d->d.code().toUpperCase(Locale.ROOT),OrganizationDtos.Department::id));}
    private Preview page(Job job,int page,int size) {
        if(page<1||page>100000||size<1||size>100)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGE","分页参数不合法");
        int errors=(int)job.rows.stream().filter(r->!r.errors().isEmpty()).count(),from=Math.min((page-1)*size,job.rows.size());
        return new Preview(job.id,job.status,job.rows.size(),job.rows.size()-errors,errors,job.expires,job.committed,page,size,job.rows.subList(from,Math.min(from+size,job.rows.size())));
    }
    private String encode(List<Row> rows){try{return json.writeValueAsString(rows);}catch(Exception e){throw new IllegalStateException(e);}}
    private List<Row> decode(String text){try{return json.readValue(text,new TypeReference<List<Row>>(){});}catch(Exception e){throw new IllegalStateException(e);}}
    private static Instant instant(Timestamp t){return t==null?null:t.toInstant();}
    private record Job(String id,String status,List<Row> rows,Instant expires,Instant committed){}
    private record Member(long id,String employeeNo,String name,String status,boolean activated,long version){}
}
