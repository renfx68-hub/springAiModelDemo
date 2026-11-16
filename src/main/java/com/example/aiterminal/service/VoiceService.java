package com.example.aiterminal.service;

import com.example.aiterminal.config.VoiceSettings;
import com.example.aiterminal.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 语音服务：调用 OpenAI 兼容的 audio 接口（默认智谱）。
 * transcribe 走 /audio/transcriptions（multipart 上传音频，返回文本）；
 * speak 走 /audio/speech（JSON 入参，返回音频字节流）。
 */
@Service
public class VoiceService {

    private static final Logger log = LoggerFactory.getLogger(VoiceService.class);
    private static final long MAX_AUDIO_BYTES = 20L * 1024 * 1024;

    private final RestClient restClient;
    private final VoiceSettings settings;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final String apiKey;

    public VoiceService(
            @Value("${spring.ai.openai.base-url:}") String baseUrl,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            VoiceSettings settings,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
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

    /** 语音转文字；audio 为 WAV/MP3 等厂商支持的音频字节。 */
    public String transcribe(byte[] audio, String filename) {
        requireKey();
        if (audio == null || audio.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "音频内容为空");
        }
        if (audio.length > MAX_AUDIO_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "音频过大，请录制 60 秒以内");
        }
        String raw;
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("model", settings.asrModel());
            body.add("file", new ByteArrayResource(audio) {
                @Override
                public String getFilename() {
                    return filename == null || filename.isBlank() ? "voice.wav" : filename;
                }
            });
            raw = restClient.post()
                    .uri(settings.transcriptionsPath())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException exception) {
            log.warn("语音转写接口失败：{} {}", exception.getStatusCode(), exception.getResponseBodyAsString());
            throw providerFailure(exception, "语音识别");
        } catch (RuntimeException exception) {
            log.warn("语音转写请求异常", exception);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "语音识别失败，请稍后重试");
        }
        try {
            JsonNode node = objectMapper.readTree(raw == null ? "" : raw);
            String text = node.path("text").asText("");
            if (text.isBlank()) {
                log.warn("语音转写响应缺少 text：{}", abbreviate(raw));
                throw new ApiException(HttpStatus.BAD_GATEWAY, "没有识别到语音内容");
            }
            return text;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("语音转写响应解析失败：{}", abbreviate(raw));
            throw new ApiException(HttpStatus.BAD_GATEWAY, "语音识别响应异常，请稍后重试");
        }
    }

    /** 文字转语音，返回音频字节与响应的媒体类型。 */
    public SpeakAudio speak(String text) {
        requireKey();
        try {
            ResponseEntity<byte[]> entity = restClient.post()
                    .uri(settings.speechPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", settings.ttsModel(), "input", text, "voice", settings.voice()))
                    .retrieve()
                    .toEntity(byte[].class);
            byte[] data = entity.getBody();
            if (data == null || data.length == 0) {
                log.warn("语音合成返回空音频");
                throw new ApiException(HttpStatus.BAD_GATEWAY, "语音合成失败，请稍后重试");
            }
            MediaType type = entity.getHeaders().getContentType();
            return new SpeakAudio(data, type == null ? MediaType.parseMediaType("audio/wav") : type);
        } catch (ApiException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            log.warn("语音合成接口失败：{} {}", exception.getStatusCode(), exception.getResponseBodyAsString());
            throw providerFailure(exception, "语音合成");
        } catch (RuntimeException exception) {
            log.warn("语音合成请求异常", exception);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "语音合成失败，请稍后重试");
        }
    }

    /** 429/402 多为厂商资源包未开通或余额不足，给出可操作提示。 */
    private static ApiException providerFailure(RestClientResponseException exception, String action) {
        int status = exception.getStatusCode().value();
        if (status == 429 || status == 402) {
            return new ApiException(HttpStatus.BAD_GATEWAY,
                    action + "服务额度不足（厂商 " + status + "），请在智谱控制台开通语音资源包；当前可继续使用浏览器自带语音能力");
        }
        return new ApiException(HttpStatus.BAD_GATEWAY,
                action + "失败（HTTP " + status + "），请稍后重试");
    }

    private void requireKey() {
        if (apiKey.isBlank() || "missing".equalsIgnoreCase(apiKey)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "尚未配置模型 API Key，请设置 OPENAI_API_KEY 后重启服务。");
        }
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "(空响应)";
        }
        return text.length() <= 400 ? text : text.substring(0, 400) + "...(截断)";
    }

    public record SpeakAudio(byte[] data, MediaType mediaType) {
    }
}
