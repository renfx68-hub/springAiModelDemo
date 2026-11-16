package com.example.aiterminal.controller;

import com.example.aiterminal.dto.SessionRequest;
import com.example.aiterminal.dto.SessionResponse;
import com.example.aiterminal.service.SessionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 会话 CRUD 与消息历史接口；userId 由登录拦截器注入。 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping
    public List<SessionResponse> list(@RequestAttribute("userId") String userId) {
        return sessionService.list(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse create(@RequestAttribute("userId") String userId,
            @Valid @RequestBody SessionRequest request) {
        return sessionService.create(request.title(), userId);
    }

    @GetMapping("/{id}")
    public SessionResponse get(@RequestAttribute("userId") String userId, @PathVariable("id") String id) {
        return sessionService.get(id, userId);
    }

    @PatchMapping("/{id}")
    public SessionResponse rename(@RequestAttribute("userId") String userId, @PathVariable("id") String id,
            @Valid @RequestBody SessionRequest request) {
        return sessionService.rename(id, request.title(), userId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute("userId") String userId, @PathVariable("id") String id) {
        sessionService.delete(id, userId);
    }
}
