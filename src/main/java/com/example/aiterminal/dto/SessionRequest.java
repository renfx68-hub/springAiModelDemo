package com.example.aiterminal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建或重命名会话使用的请求体。 */
public record SessionRequest(@NotBlank @Size(max = 200) String title) {
}
