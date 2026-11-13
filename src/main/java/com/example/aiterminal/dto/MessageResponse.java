package com.example.aiterminal.dto;

import java.time.Instant;

/** 历史消息返回结构。 */
public record MessageResponse(Long id, String role, String content, Instant createdAt) {
}
