package com.example.aiterminal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 账号体系配置：总开关、Token 签名密钥与有效期、验证码有效期与重发间隔。 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthSettings(
        boolean enabled,
        String secret,
        long tokenTtlHours,
        long codeTtlMinutes,
        long codeResendSeconds) {

    public AuthSettings {
        if (secret == null || secret.isBlank()) {
            secret = "latiao-dev-secret-change-me";
        }
        if (tokenTtlHours == 0) {
            tokenTtlHours = 168;
        }
        if (codeTtlMinutes == 0) {
            codeTtlMinutes = 10;
        }
        if (codeResendSeconds == 0) {
            codeResendSeconds = 60;
        }
    }
}
