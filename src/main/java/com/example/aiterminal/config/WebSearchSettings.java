package com.example.aiterminal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 联网搜索配置：默认使用智谱 web_search 接口，复用模型服务地址与 API Key。 */
@ConfigurationProperties(prefix = "app.websearch")
public record WebSearchSettings(
        Boolean enabled,
        String searchEngine,
        Integer resultCount,
        String searchPath) {

    public WebSearchSettings {
        if (enabled == null) {
            enabled = Boolean.FALSE;
        }
        if (searchEngine == null || searchEngine.isBlank()) {
            searchEngine = "search_std";
        }
        if (resultCount == null) {
            resultCount = 5;
        }
        resultCount = Math.max(1, Math.min(10, resultCount));
        if (searchPath == null || searchPath.isBlank()) {
            searchPath = "/web_search";
        }
    }
}
