package com.example.aiterminal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 美股行情连接器配置（默认使用腾讯财经公开行情接口，无需密钥）。 */
@ConfigurationProperties(prefix = "app.market")
public record MarketSettings(
        Boolean enabled,
        String baseUrl) {

    public MarketSettings {
        if (enabled == null) {
            enabled = Boolean.TRUE;
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://qt.gtimg.cn";
        }
    }
}
