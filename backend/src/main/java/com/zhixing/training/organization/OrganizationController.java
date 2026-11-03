package com.zhixing.training.organization;

import com.zhixing.training.api.*;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.zhixing.training.organization.OrganizationDtos.*;

@RestController
@RequestMapping("/api/v1/admin")
public class OrganizationController {
    private final OrganizationService service;
    public OrganizationController(OrganizationService service){this.service=service;}
    @GetMapping("/access") public ApiResponse<AccessView> access(@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(service.access(actor(jwt)));}
    @GetMapping("/departments/tree") public ApiResponse<List<Department>> departments(@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(service.departments(actor(jwt)));}
    @PostMapping("/departments") public ApiResponse<Long> createDepartment(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody DepartmentRequest b){return ApiResponse.ok(service.saveDepartment(actor(jwt),null,b));}
    @PutMapping("/departments/{id}") public ApiResponse<Long> updateDepartment(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody DepartmentRequest b){return ApiResponse.ok(service.saveDepartment(actor(jwt),id,b));}
    @PutMapping("/departments/{id}/leader") public ApiResponse<Void> leader(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody LeaderRequest b){service.setLeader(actor(jwt),id,b);return ApiResponse.ok();}
    @GetMapping("/employees") public ApiResponse<PageResult<Employee>> employees(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String keyword,@RequestParam(required=false) Long departmentId,@RequestParam(required=false) String status,@RequestParam(required=false) String role){return ApiResponse.ok(service.employees(actor(jwt),pageNo,pageSize,keyword,departmentId,status,role));}
    @GetMapping("/employees/{id}") public ApiResponse<Employee> employee(@AuthenticationPrincipal Jwt jwt,@PathVariable long id){return ApiResponse.ok(service.employee(actor(jwt),id));}
    @PostMapping("/employees") public ApiResponse<Employee> createEmployee(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody EmployeeRequest b){return ApiResponse.ok(service.saveEmployee(actor(jwt),null,b));}
    @PutMapping("/employees/{id}") public ApiResponse<Employee> updateEmployee(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody EmployeeRequest b){return ApiResponse.ok(service.saveEmployee(actor(jwt),id,b));}
    @PostMapping("/employees/{id}/unlock") public ApiResponse<Employee> unlock(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody VersionRequest b){return ApiResponse.ok(service.unlock(actor(jwt),id,b.version()));}
    private static long actor(Jwt jwt){return Long.parseLong(jwt.getSubject());}
}
