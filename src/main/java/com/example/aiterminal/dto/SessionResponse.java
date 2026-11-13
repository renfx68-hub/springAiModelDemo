package com.example.aiterminal.dto;

import java.time.Instant;
import java.util.List;

/** 会话列表与详情返回结构。 */
public record SessionResponse(
        String id, String title, Instant createdAt, Instant updatedAt, List<MessageResponse> messages) {
}
