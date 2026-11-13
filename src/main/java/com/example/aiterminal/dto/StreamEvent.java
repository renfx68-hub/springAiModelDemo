package com.example.aiterminal.dto;

/** SSE 事件数据；不同事件类型只填充相关字段。 */
public record StreamEvent(String type, String content, String code, String message) {

    public static StreamEvent delta(String content) {
        return new StreamEvent("delta", content, null, null);
    }

    public static StreamEvent done() {
        return new StreamEvent("done", null, null, null);
    }

    public static StreamEvent error(String code, String message) {
        return new StreamEvent("error", null, code, message);
    }
}
