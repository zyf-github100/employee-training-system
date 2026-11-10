package com.zhixing.training.auth;
import com.zhixing.training.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/admin/employees/{id}/invitations")
public class InvitationDeliveryController {
    private final InvitationDeliveryService service;
    public InvitationDeliveryController(InvitationDeliveryService service){this.service=service;}
    @PostMapping public ApiResponse<InvitationDeliveryService.View> deliver(@PathVariable long id,@RequestParam long version,@Valid @RequestBody InvitationDeliveryService.Request body,@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(service.deliver(Long.parseLong(jwt.getSubject()),id,version,body));}
    @GetMapping("/latest") public ApiResponse<InvitationDeliveryService.View> latest(@PathVariable long id,@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(service.latest(Long.parseLong(jwt.getSubject()),id));}
}
