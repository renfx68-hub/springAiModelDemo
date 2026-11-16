package com.example.aiterminal.service;

import com.example.aiterminal.config.AuthSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 轻量 JWT（HS256）：自签自验，无状态会话。
 * 不引入第三方 JWT 库，避免为一个 token 增加依赖面。
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final String HEADER = ENCODER.encodeToString(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private final byte[] secret;
    private final long ttlMillis;
    private final ObjectMapper objectMapper;

    public TokenService(AuthSettings settings, ObjectMapper objectMapper) {
        this.secret = settings.secret().getBytes(StandardCharsets.UTF_8);
        this.ttlMillis = settings.tokenTtlHours() * 3600_000L;
        this.objectMapper = objectMapper;
    }

    /** 签发 token，payload 含 sub（用户 ID）、email 与 exp（秒）。 */
    public String issue(String userId, String email) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", userId);
        payload.put("email", email);
        payload.put("exp", Instant.now().plusMillis(ttlMillis).getEpochSecond());
        String body;
        try {
            body = ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
        } catch (Exception exception) {
            throw new IllegalStateException("无法签发登录凭证", exception);
        }
        String content = HEADER + "." + body;
        return content + "." + sign(content);
    }

    /** 验签并校验过期时间；失败返回 empty。 */
    public Optional<TokenPayload> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        String content = parts[0] + "." + parts[1];
        byte[] expected = sign(content).getBytes(StandardCharsets.UTF_8);
        byte[] actual = parts[2].getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(DECODER.decode(parts[1]));
            long exp = node.path("exp").asLong(0);
            if (exp * 1000 <= System.currentTimeMillis()) {
                return Optional.empty();
            }
            String userId = node.path("sub").asText("");
            if (userId.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new TokenPayload(userId, node.path("email").asText("")));
        } catch (Exception exception) {
            log.debug("token 解析失败", exception);
            return Optional.empty();
        }
    }

    private String sign(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return ENCODER.encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法签发登录凭证", exception);
        }
    }

    public record TokenPayload(String userId, String email) {
    }
}
