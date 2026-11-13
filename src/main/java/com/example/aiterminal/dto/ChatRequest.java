package com.example.aiterminal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 流式聊天请求。 */
public record ChatRequest(
        @NotBlank String sessionId,
        @NotBlank @Size(max = 20000) String message) {
}
