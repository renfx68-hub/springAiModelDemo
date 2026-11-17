package com.example.aiterminal.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 重新生成请求：删除最后一条回答并按相同上下文重新流式生成；
 * professional / legal 与 /api/chat/stream 同义。
 */
public record ChatRegenerateRequest(
        @NotBlank String sessionId,
        Boolean professional,
        Boolean legal) {
}
