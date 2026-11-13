package com.example.aiterminal.service;

import com.example.aiterminal.config.ImageSettings;
import com.example.aiterminal.dto.MessageResponse;
import com.example.aiterminal.exception.ApiException;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 文生图服务：调用 OpenAI 兼容的 images/generations 接口（默认智谱 CogView），
 * 将生成结果下载并保存到本地，作为一条助手消息落入会话。
 */
@Service
public class ImageGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ImageGenerationService.class);

    private final RestClient restClient;
    private final ImageStorageService imageStorage;
    private final SessionService sessionService;
    private final ImageSettings settings;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final String apiKey;

    public ImageGenerationService(
            @Value("${spring.ai.openai.base-url:}") String baseUrl,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            ImageStorageService imageStorage,
            SessionService sessionService,
            ImageSettings settings,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.imageStorage = imageStorage;
        this.sessionService = sessionService;
        this.settings = settings;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(120_000);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl == null ? "" : baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + this.apiKey)
                .requestFactory(factory)
                .build();
    }

    /** 生成图片并把「用户提问 + 助手图片」两条消息落库，返回助手消息。 */
    public MessageResponse generate(String sessionId, String prompt) {
        if (apiKey.isBlank() || "missing".equalsIgnoreCase(apiKey)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "尚未配置模型 API Key，请设置 OPENAI_API_KEY 后重启服务。");
        }
        sessionService.requireSession(sessionId);
        String remoteUrl = requestGeneration(prompt);
        byte[] bytes = download(remoteUrl);
        String imageUrl = imageStorage.save(bytes, detectMime(remoteUrl));
        sessionService.appendMessage(sessionId, "user", prompt);
        return sessionService.appendMessage(sessionId, "assistant", "", imageUrl);
    }

    private String requestGeneration(String prompt) {
        String raw;
        try {
            raw = restClient.post()
                    .uri(settings.generationsPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new GenerationRequest(settings.model(), prompt))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException exception) {
            log.warn("图片生成接口失败：{} {}", exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "图片生成失败（HTTP " + exception.getStatusCode().value() + "），请稍后重试");
        } catch (RuntimeException exception) {
            log.warn("图片生成请求异常", exception);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "图片生成失败，请稍后重试");
        }
        GenerationResponse response;
        try {
            response = objectMapper.readValue(raw, GenerationResponse.class);
        } catch (Exception exception) {
            log.warn("图片生成响应解析失败：{}", abbreviate(raw));
            throw new ApiException(HttpStatus.BAD_GATEWAY, "图片生成响应异常，请稍后重试");
        }
        if (response == null || response.data() == null || response.data().isEmpty()
                || response.data().get(0).url() == null || response.data().get(0).url().isBlank()) {
            log.warn("图片生成响应缺少图片 URL：{}", abbreviate(raw));
            throw new ApiException(HttpStatus.BAD_GATEWAY, "图片生成服务未返回图片，请稍后重试");
        }
        return response.data().get(0).url();
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "(空响应)";
        }
        return text.length() <= 600 ? text : text.substring(0, 600) + "...(截断)";
    }

    private byte[] download(String url) {
        try {
            byte[] bytes = restClient.get().uri(URI.create(url)).retrieve().body(byte[].class);
            if (bytes == null || bytes.length == 0) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "生成结果下载为空，请重试");
            }
            return bytes;
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("下载生成图片失败: {}", url, exception);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "生成结果下载失败，请重试");
        }
    }

    private static String detectMime(String url) {
        String lower = url.toLowerCase();
        if (lower.contains(".jpg") || lower.contains(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.contains(".webp")) {
            return "image/webp";
        }
        return "image/png";
    }

    record GenerationRequest(String model, String prompt) {
    }

    record GenerationItem(String url) {
    }

    record GenerationResponse(List<GenerationItem> data) {
    }
}
