package com.example.aiterminal.controller;

import com.example.aiterminal.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;
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
            @Size(max = 60) String nickname,
            @Size(max = 6) String code) {
    }

    public record LoginRequest(
            @NotBlank @Email(message = "邮箱格式不正确") String email,
            @NotBlank String password) {
    }

    public record CodeRequest(
            @NotBlank @Email(message = "邮箱格式不正确") String email,
            String purpose) {
    }

    public record ResetRequest(
            @NotBlank @Email(message = "邮箱格式不正确") String email,
            @NotBlank @Size(min = 6, max = 6, message = "验证码为 6 位数字") String code,
            @NotBlank @Size(min = 8, max = 72, message = "密码长度需 8~72 位") String newPassword) {
    }

    /** 前端据此决定是否展示验证码输入与"忘记密码"。 */
    @GetMapping("/capabilities")
    public Map<String, Object> capabilities() {
        return Map.of("mailEnabled", authService.mailEnabled(), "devMode", authService.mailDevMode());
    }

    /** 发送验证码（purpose: register / reset）；开发模式在响应里回传 devCode。 */
    @PostMapping("/code")
    public Map<String, Object> sendCode(@Valid @RequestBody CodeRequest request) {
        String devCode = authService.requestCode(request.email(), request.purpose());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sent", true);
        body.put("devCode", devCode);
        return body;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthService.AuthResult register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request.email(), request.password(), request.nickname(), request.code());
    }

    @PostMapping("/login")
    public AuthService.AuthResult login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody ResetRequest request) {
        authService.resetPassword(request.email(), request.code(), request.newPassword());
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
