package com.zhixing.training.organization;

import com.zhixing.training.api.ApiResponse;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.net.URI;
import java.util.*;

@RestController @RequestMapping("/api/v1/admin/employee-imports")
public class EmployeeImportController {
    private final EmployeeImportService imports;
    private final String publicUrl;
    private final List<String> origins;
    public EmployeeImportController(EmployeeImportService imports,Environment env){
        this.imports=imports;
        origins=Arrays.stream(env.getProperty("zhixing.cors-origins","http://127.0.0.1:5173").split(",")).map(String::trim).filter(s->!s.isEmpty()).toList();
        publicUrl=env.getProperty("zhixing.public-web-url","").replaceAll("/+$","");
        if(!publicUrl.isBlank()) {var uri=URI.create(publicUrl);if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)throw new IllegalArgumentException("TRAINING_PUBLIC_WEB_URL must be a valid HTTP(S) web URL");}
    }
    @GetMapping("/template") public ResponseEntity<byte[]> template(@AuthenticationPrincipal Jwt jwt){return file("employee-template.xlsx",imports.template(actor(jwt)));}
    @GetMapping public ApiResponse<List<EmployeeImportDtos.Summary>> recent(@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(imports.recent(actor(jwt)));}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ApiResponse<EmployeeImportDtos.Preview> preview(@AuthenticationPrincipal Jwt jwt,@RequestParam("file") MultipartFile file){return ApiResponse.ok(imports.preview(actor(jwt),file));}
    @GetMapping("/{id}") public ApiResponse<EmployeeImportDtos.Preview> read(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="50") int pageSize){return ApiResponse.ok(imports.read(actor(jwt),id,pageNo,pageSize));}
    @PostMapping("/{id}/commit") public ApiResponse<EmployeeImportDtos.Preview> commit(@AuthenticationPrincipal Jwt jwt,@PathVariable String id){return ApiResponse.ok(imports.commit(actor(jwt),id));}
    @GetMapping("/{id}/report") public ResponseEntity<byte[]> report(@AuthenticationPrincipal Jwt jwt,@PathVariable String id){return file("employee-validation.xlsx",imports.report(actor(jwt),id));}
    @PostMapping("/{id}/activation-links") public ResponseEntity<byte[]> links(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,@RequestHeader(value="Origin",required=false) String origin){
        String base=!publicUrl.isBlank()?publicUrl:origins.contains(origin)?origin:origins.get(0);
        return file("employee-activation.xlsx",imports.activationList(actor(jwt),id,base));
    }
    private static long actor(Jwt jwt){return Long.parseLong(jwt.getSubject());}
    private ResponseEntity<byte[]> file(String name,byte[] bytes){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+name+"\"").contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(bytes);}
}
