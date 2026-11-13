package com.example.aiterminal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 聊天提示词与生成参数，集中从配置读取以便按部署环境调整。 */
@ConfigurationProperties(prefix = "app.chat")
public record ChatSettings(
        String systemPrompt,
        int maxHistoryTurns,
        double temperature,
        int maxTokens,
        double topP) {
}
