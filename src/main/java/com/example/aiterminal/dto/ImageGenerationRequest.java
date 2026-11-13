package com.example.aiterminal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 文生图请求。 */
public record ImageGenerationRequest(
        @NotBlank String sessionId,
        @NotBlank @Size(max = 2000) String prompt) {
}
