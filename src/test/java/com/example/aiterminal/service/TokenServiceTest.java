package com.example.aiterminal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.aiterminal.config.AuthSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** 验证自签 token 的签发、验签、篡改与过期行为。 */
class TokenServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void issuedTokenVerifiesAndCarriesUser() {
        TokenService service = new TokenService(new AuthSettings("test-secret", 1, 10, 60), objectMapper);

        String token = service.issue("user-1", "boss@example.com");
        var payload = service.verify(token);

        assertTrue(payload.isPresent());
        assertEquals("user-1", payload.get().userId());
        assertEquals("boss@example.com", payload.get().email());
    }

    @Test
    void tamperedOrForeignTokenIsRejected() {
        TokenService service = new TokenService(new AuthSettings("test-secret", 1, 10, 60), objectMapper);
        TokenService other = new TokenService(new AuthSettings("other-secret", 1, 10, 60), objectMapper);

        String token = service.issue("user-1", "boss@example.com");

        assertFalse(service.verify(token + "x").isPresent());
        assertFalse(other.verify(token).isPresent());
        assertFalse(service.verify("not-a-token").isPresent());
        assertFalse(service.verify(null).isPresent());
    }

    @Test
    void expiredTokenIsRejected() {
        TokenService service = new TokenService(new AuthSettings("test-secret", -1, 10, 60), objectMapper);

        assertFalse(service.verify(service.issue("user-1", "boss@example.com")).isPresent());
    }
}
