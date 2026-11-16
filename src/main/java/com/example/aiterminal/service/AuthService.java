package com.example.aiterminal.service;

import com.example.aiterminal.entity.AppUser;
import com.example.aiterminal.exception.ApiException;
import com.example.aiterminal.repository.AppUserRepository;
import com.example.aiterminal.repository.ChatSessionRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账号业务：邮箱注册登录、验证码发送、密码重置。
 * 开启 SMTP 时注册需邮箱验证码；未配置邮件服务时保持免验证码注册。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    public static final String PURPOSE_REGISTER = "register";
    public static final String PURPOSE_RESET = "reset";

    private final AppUserRepository users;
    private final ChatSessionRepository sessions;
    private final TokenService tokens;
    private final MailService mailService;
    private final VerificationCodeService codes;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(AppUserRepository users, ChatSessionRepository sessions, TokenService tokens,
            MailService mailService, VerificationCodeService codes) {
        this.users = users;
        this.sessions = sessions;
        this.tokens = tokens;
        this.mailService = mailService;
        this.codes = codes;
    }

    public record UserView(String id, String email, String nickname) {
    }

    public record AuthResult(String token, UserView user) {
    }

    /** 邮件能力：前端据此决定是否展示验证码与"忘记密码"。 */
    public boolean mailEnabled() {
        return mailService.enabled();
    }

    public boolean mailDevMode() {
        return mailService.devMode();
    }

    /** 发送验证码；仅开发模式把验证码回传给前端便于联调。 */
    public String requestCode(String rawEmail, String purpose) {
        if (!mailService.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "服务端未开启邮箱验证码，当前可直接免验证码注册；如需开启请配置 SMTP 发信账号");
        }
        String email = normalize(rawEmail);
        String normalizedPurpose = PURPOSE_RESET.equals(purpose) ? PURPOSE_RESET : PURPOSE_REGISTER;
        if (PURPOSE_REGISTER.equals(normalizedPurpose) && users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "该邮箱已注册，请直接登录");
        }
        if (PURPOSE_RESET.equals(normalizedPurpose) && users.findByEmailIgnoreCase(email).isEmpty()) {
            // 防邮箱枚举：不发送邮件，但照常占用重发限频
            codes.issue(email, PURPOSE_RESET);
            log.info("密码重置请求的邮箱不存在：{}", email);
            return null;
        }
        String code = codes.issue(email, normalizedPurpose);
        mailService.sendCode(email, code, normalizedPurpose);
        return mailService.devMode() ? code : null;
    }

    /** 注册：开启邮件时校验验证码；首个注册用户接管历史（无主）会话。 */
    @Transactional
    public AuthResult register(String rawEmail, String password, String nickname, String code) {
        String email = normalize(rawEmail);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "该邮箱已注册，请直接登录");
        }
        if (mailService.enabled()) {
            if (code == null || code.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "请输入邮箱验证码");
            }
            codes.verify(email, PURPOSE_REGISTER, code);
        }
        String name = nickname == null || nickname.isBlank()
                ? email.substring(0, email.indexOf('@'))
                : nickname.trim();
        AppUser user = users.save(new AppUser(UUID.randomUUID().toString(), email,
                encoder.encode(password), name, Instant.now()));
        if (users.count() == 1) {
            int adopted = sessions.adoptOrphans(user.getId());
            if (adopted > 0) {
                log.info("已将 {} 个历史会话归属首个注册用户 {}", adopted, email);
            }
        }
        return new AuthResult(tokens.issue(user.getId(), user.getEmail()), toView(user));
    }

    @Transactional
    public AuthResult login(String rawEmail, String password) {
        AppUser user = users.findByEmailIgnoreCase(normalize(rawEmail))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "邮箱或密码不正确"));
        if (!encoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "邮箱或密码不正确");
        }
        user.markLogin(Instant.now());
        return new AuthResult(tokens.issue(user.getId(), user.getEmail()), toView(user));
    }

    /** 忘记密码：验证码校验通过后重设密码（不返回登录态，需重新登录）。 */
    @Transactional
    public void resetPassword(String rawEmail, String code, String newPassword) {
        if (!mailService.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "服务端未开启邮箱验证码，暂无法自助重置密码");
        }
        String email = normalize(rawEmail);
        codes.verify(email, PURPOSE_RESET, code);
        AppUser user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "账号不存在或验证码不正确"));
        user.changePassword(encoder.encode(newPassword), Instant.now());
        log.info("密码已重置：{}", email);
    }

    @Transactional(readOnly = true)
    public UserView me(String userId) {
        return users.findById(userId).map(AuthService::toView)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "账号不存在，请重新登录"));
    }

    private static UserView toView(AppUser user) {
        return new UserView(user.getId(), user.getEmail(), user.getNickname());
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
