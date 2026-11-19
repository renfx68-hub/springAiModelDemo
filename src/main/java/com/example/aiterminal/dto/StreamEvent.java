package com.example.aiterminal.dto;

import java.util.List;

/** SSE 事件数据；不同事件类型只填充相关字段。 */
public record StreamEvent(String type, String content, String code, String message, List<Source> sources) {

    public static StreamEvent delta(String content) {
        return new StreamEvent("delta", content, null, null, null);
    }

    public static StreamEvent sources(List<Source> sources) {
        return new StreamEvent("sources", null, null, null, sources);
    }

    public static StreamEvent notice(String message) {
        return new StreamEvent("notice", null, null, message, null);
    }

    public static StreamEvent done() {
        return new StreamEvent("done", null, null, null, null);
    }

    public static StreamEvent error(String code, String message) {
        return new StreamEvent("error", null, code, message, null);
    }

    /** 可安全展示给用户的网页来源，不包含原始接口响应。 */
    public record Source(String title, String url, String media, String publishDate, String content) {
    }
}
