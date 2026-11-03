package com.zhixing.training.communication;

import com.zhixing.training.api.*;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import static com.zhixing.training.communication.CommunicationDtos.*;

@RestController
@RequestMapping("/api/v1")
public class CommunicationController {
    private final FeedbackService feedback;
    private final AnnouncementService announcements;
    private final PlatformService platform;
    public CommunicationController(FeedbackService feedback,AnnouncementService announcements,PlatformService platform){this.feedback=feedback;this.announcements=announcements;this.platform=platform;}
    @GetMapping("/employee/feedback") public ApiResponse<PageResult<FeedbackView>> mine(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="") String keyword){return ApiResponse.ok(feedback.list(user(jwt),false,pageNo,pageSize,status,keyword));}
    @PostMapping("/employee/feedback") public ApiResponse<FeedbackView> create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody FeedbackRequest body){return ApiResponse.ok(feedback.create(user(jwt),body));}
    @GetMapping("/admin/feedback") public ApiResponse<PageResult<FeedbackView>> feedback(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="") String keyword){return ApiResponse.ok(feedback.list(user(jwt),true,pageNo,pageSize,status,keyword));}
    @PutMapping("/admin/feedback/{id}/reply") public ApiResponse<FeedbackView> reply(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody ReplyRequest body){return ApiResponse.ok(feedback.reply(user(jwt),id,body));}
    @GetMapping("/employee/announcements") public ApiResponse<PageResult<AnnouncementView>> notices(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize){return ApiResponse.ok(announcements.list(user(jwt),false,pageNo,pageSize));}
    @GetMapping("/employee/announcements/unread-count") public ApiResponse<Long> unread(@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(announcements.unread(user(jwt)));}
    @PostMapping("/employee/announcements/{id}/read") public ApiResponse<AnnouncementView> read(@AuthenticationPrincipal Jwt jwt,@PathVariable long id){return ApiResponse.ok(announcements.read(user(jwt),id));}
    @GetMapping("/admin/announcements") public ApiResponse<PageResult<AnnouncementView>> announcements(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize){return ApiResponse.ok(announcements.list(user(jwt),true,pageNo,pageSize));}
    @PostMapping("/admin/announcements") public ApiResponse<AnnouncementView> publish(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody AnnouncementRequest body){return ApiResponse.ok(announcements.publish(user(jwt),body));}
    @PostMapping("/admin/announcements/{id}/withdraw") public ApiResponse<AnnouncementView> withdraw(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody VersionRequest body){return ApiResponse.ok(announcements.withdraw(user(jwt),id,body.version()));}
    @GetMapping({"/employee/platform","/admin/settings"}) public ApiResponse<PlatformView> platform(){return ApiResponse.ok(platform.read());}
    @PutMapping("/admin/settings") public ApiResponse<PlatformView> settings(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody PlatformRequest body){return ApiResponse.ok(platform.save(user(jwt),body));}
    private static long user(Jwt jwt){return Long.parseLong(jwt.getSubject());}
}
