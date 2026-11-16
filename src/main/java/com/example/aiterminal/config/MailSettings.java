package com.example.aiterminal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 邮件服务配置（默认 QQ 邮箱：smtp.qq.com:465 SSL）。
 * 注意 QQ/163 的 password 是「授权码」而非登录密码；未配置时验证码功能自动关闭，注册保持免验证码。
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailSettings(
        Boolean enabled,
        Boolean devMode,
        String host,
        Integer port,
        String username,
        String password,
        String from,
        Boolean ssl) {

    public MailSettings {
        if (enabled == null) {
            enabled = Boolean.FALSE;
        }
        if (devMode == null) {
            devMode = Boolean.FALSE;
        }
        if (host == null || host.isBlank()) {
            host = "smtp.qq.com";
        }
        if (port == null || port <= 0) {
            port = 465;
        }
        if (username == null) {
            username = "";
        }
        if (password == null) {
            password = "";
        }
        if (from == null || from.isBlank()) {
            from = username;
        }
        if (ssl == null) {
            ssl = Boolean.TRUE;
        }
    }
}
