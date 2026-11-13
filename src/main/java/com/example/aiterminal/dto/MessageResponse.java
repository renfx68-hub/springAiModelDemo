package com.example.aiterminal.dto;

import java.time.Instant;

/** 历史消息返回结构；imageUrl 为用户消息附带的图片访问路径。 */
public record MessageResponse(Long id, String role, String content, String imageUrl, Instant createdAt) {
}
