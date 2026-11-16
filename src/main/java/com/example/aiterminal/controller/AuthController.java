package com.example.aiterminal.controller;

import com.example.aiterminal.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 邮箱注册登录接口；token 由前端存储，请求头 Bearer 携带。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record RegisterRequest(
            @NotBlank @Email(message = "邮箱格式不正确") @Size(max = 200) String email,
            @NotBlank @Size(min = 8, max = 72, message = "密码长度需 8~72 位") String password,
            @Size(max = 60) String nickname) {
    }

    public record LoginRequest(
            @NotBlank @Email(message = "邮箱格式不正确") String email,
            @NotBlank String password) {
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthService.AuthResult register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request.email(), request.password(), request.nickname());
    }

    @PostMapping("/login")
    public AuthService.AuthResult login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @GetMapping("/me")
    public AuthService.UserView me(@RequestAttribute("userId") String userId) {
        return authService.me(userId);
    }

    /** 无状态 token：登出由前端丢弃凭证完成，保留接口以便将来做服务端吊销。 */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        // 无状态会话，无需服务端处理
    }
}
