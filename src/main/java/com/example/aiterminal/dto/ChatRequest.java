package com.example.aiterminal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 流式聊天请求；imageBase64 可选（允许带 data:image/...;base64, 前缀），
 * professional=true 时切换严谨的专业模式（关闭辣条人设），
 * legal=true 时切换法律模式（结构化法律信息 + 免责声明，优先级高于 professional）。
 */
public record ChatRequest(
        @NotBlank String sessionId,
        @NotBlank @Size(max = 20000) String message,
        @Size(max = 12_000_000, message = "图片过大，请压缩后重试") String imageBase64,
        Boolean professional,
        Boolean legal) {
}
