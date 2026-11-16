package com.example.aiterminal.service;

import com.example.aiterminal.config.AuthSettings;
import com.example.aiterminal.exception.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 邮箱验证码：6 位数字，内存存储（重启即失效），带有效期、重发间隔与错误次数上限。
 * 单实例够用；多实例部署时再换 Redis。
 */
@Service
public class VerificationCodeService {

    private static final int MAX_ATTEMPTS = 5;

    private final Map<String, Entry> codes = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Duration resendInterval;

    public VerificationCodeService(AuthSettings settings) {
        this.ttl = Duration.ofMinutes(settings.codeTtlMinutes());
        this.resendInterval = Duration.ofSeconds(settings.codeResendSeconds());
    }

    /** 生成并暂存验证码；距上次发送不足间隔时抛出 429。 */
    public String issue(String email, String purpose) {
        String key = key(email, purpose);
        Instant now = Instant.now();
        Entry existing = codes.get(key);
        if (existing != null && now.isBefore(existing.resendAt())) {
            long seconds = Math.max(1, Duration.between(now, existing.resendAt()).toSeconds());
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "请求太频繁，请 " + seconds + " 秒后再试");
        }
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        codes.put(key, new Entry(code, now.plus(ttl), now.plus(resendInterval), new AtomicInteger()));
        return code;
    }

    /** 校验并消费验证码；错误、过期、次数超限分别给出不同提示。 */
    public void verify(String email, String purpose, String code) {
        String key = key(email, purpose);
        Entry entry = codes.get(key);
        Instant now = Instant.now();
        if (entry == null || now.isAfter(entry.expiresAt())) {
            codes.remove(key);
            throw new ApiException(HttpStatus.BAD_REQUEST, "验证码已过期，请重新获取");
        }
        if (entry.attempts().incrementAndGet() > MAX_ATTEMPTS) {
            codes.remove(key);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "验证码错误次数过多，请重新获取");
        }
        if (code == null || !entry.code().equals(code.trim())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "验证码不正确");
        }
        codes.remove(key);
    }

    /** 清理某个用途的验证码（如注册成功后）。 */
    public void clear(String email, String purpose) {
        codes.remove(key(email, purpose));
    }

    private static String key(String email, String purpose) {
        return (email == null ? "" : email.trim().toLowerCase(Locale.ROOT)) + '|' + purpose;
    }

    private record Entry(String code, Instant expiresAt, Instant resendAt, AtomicInteger attempts) {
    }
}
