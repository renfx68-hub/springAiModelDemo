package com.example.aiterminal.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.aiterminal.config.AuthSettings;
import com.example.aiterminal.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** 验证码：位数、校验、过期、重发限频与错误次数上限。 */
class VerificationCodeServiceTest {

    private static VerificationCodeService service(long ttlMinutes, long resendSeconds) {
        return new VerificationCodeService(new AuthSettings(true, "test-secret", 1, ttlMinutes, resendSeconds));
    }

    @Test
    void issuedCodeIsSixDigitsAndVerifies() {
        VerificationCodeService codes = service(10, 60);
        String code = codes.issue("boss@example.com", AuthService.PURPOSE_REGISTER);

        assertTrue(code.matches("\\d{6}"));
        assertDoesNotThrow(() -> codes.verify("boss@example.com", AuthService.PURPOSE_REGISTER, code));
        // 消费后不可重复使用
        ApiException reused = assertThrows(ApiException.class,
                () -> codes.verify("boss@example.com", AuthService.PURPOSE_REGISTER, code));
        assertEquals(HttpStatus.BAD_REQUEST, reused.getStatus());
    }

    @Test
    void emailIsCaseInsensitive() {
        VerificationCodeService codes = service(10, 60);
        String code = codes.issue("Boss@Example.com", AuthService.PURPOSE_REGISTER);

        assertDoesNotThrow(() -> codes.verify("boss@example.com", AuthService.PURPOSE_REGISTER, code));
    }

    @Test
    void wrongCodeIsRejectedAndLocksAfterFiveAttempts() {
        VerificationCodeService codes = service(10, 60);
        codes.issue("boss@example.com", AuthService.PURPOSE_RESET);

        for (int attempt = 0; attempt < 5; attempt++) {
            ApiException error = assertThrows(ApiException.class,
                    () -> codes.verify("boss@example.com", AuthService.PURPOSE_RESET, "000000"));
            assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        }
        ApiException locked = assertThrows(ApiException.class,
                () -> codes.verify("boss@example.com", AuthService.PURPOSE_RESET, "000000"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, locked.getStatus());
    }

    @Test
    void expiredCodeIsRejected() {
        VerificationCodeService codes = service(-1, 60);
        String code = codes.issue("boss@example.com", AuthService.PURPOSE_REGISTER);

        ApiException error = assertThrows(ApiException.class,
                () -> codes.verify("boss@example.com", AuthService.PURPOSE_REGISTER, code));
        assertTrue(error.getMessage().contains("过期"));
    }

    @Test
    void resendWithinIntervalIsLimited() {
        VerificationCodeService codes = service(10, 60);
        codes.issue("boss@example.com", AuthService.PURPOSE_REGISTER);

        ApiException error = assertThrows(ApiException.class,
                () -> codes.issue("boss@example.com", AuthService.PURPOSE_REGISTER));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatus());
    }
}
