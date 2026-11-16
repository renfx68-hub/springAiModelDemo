package com.example.aiterminal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 账号体系配置：Token 签名密钥与有效期。生产环境务必用 APP_AUTH_SECRET 覆盖默认值。 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthSettings(
        String secret,
        long tokenTtlHours) {

    public AuthSettings {
        if (secret == null || secret.isBlank()) {
            secret = "latiao-dev-secret-change-me";
        }
        if (tokenTtlHours == 0) {
            tokenTtlHours = 168;
        }
    }
}
