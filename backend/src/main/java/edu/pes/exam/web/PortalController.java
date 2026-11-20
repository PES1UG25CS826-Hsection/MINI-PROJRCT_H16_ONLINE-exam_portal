package edu.pes.exam.web;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class PortalController {
    private final PortalService portal;
    public PortalController(PortalService portal) { this.portal = portal; }

    @PostMapping("/auth/register")
    public Map<String,Object> register(@RequestBody PortalService.RegisterRequest request) { return portal.register(request); }
    @PostMapping("/auth/login")
    public Map<String,Object> login(@RequestBody PortalService.LoginRequest request) { return portal.login(request); }

    @GetMapping("/student/exams")
    @PreAuthorize("hasRole('STUDENT')")
    public Map<String,Object> exams(Authentication auth) { return Map.of("groups",portal.exams(userId(auth))); }

    @PostMapping("/exams/{examId}/attempts")
    @PreAuthorize("hasRole('STUDENT')")
    public Map<String,Object> start(@PathVariable UUID examId,Authentication auth) { return portal.start(userId(auth),examId); }

    @GetMapping("/attempts/{attemptId}")
    @PreAuthorize("hasRole('STUDENT')")
    public Map<String,Object> attempt(@PathVariable UUID attemptId,Authentication auth) { return portal.attempt(userId(auth),attemptId); }

    @PutMapping("/attempts/{attemptId}/answers/{questionId}")
    @PreAuthorize("hasRole('STUDENT')")
    public Map<String,Object> saveAnswer(@PathVariable UUID attemptId,@PathVariable UUID questionId,
            @RequestBody PortalService.AnswerRequest request,Authentication auth) {
        return portal.saveAnswer(userId(auth),attemptId,questionId,request);
    }

    @PostMapping("/attempts/{attemptId}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public Map<String,Object> submit(@PathVariable UUID attemptId,Authentication auth) { return portal.submit(userId(auth),attemptId); }

    @GetMapping("/attempts/{attemptId}/result")
    @PreAuthorize("hasRole('STUDENT')")
    public Map<String,Object> result(@PathVariable UUID attemptId,Authentication auth) { return portal.result(userId(auth),attemptId); }

    private UUID userId(Authentication auth) {
        if(auth==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in to continue");
        return auth.getPrincipal() instanceof edu.pes.exam.security.PortalPrincipal p ? p.id() : (UUID) auth.getPrincipal();
    }
}
