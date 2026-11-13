package com.example.aiterminal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 文生图配置：模型与生成接口路径，默认智谱 CogView（cogview-3-flash 免费）。 */
@ConfigurationProperties(prefix = "app.image")
public record ImageSettings(
        String model,
        String generationsPath) {

    public ImageSettings {
        if (generationsPath == null || generationsPath.isBlank()) {
            generationsPath = "/images/generations";
        }
    }
}
