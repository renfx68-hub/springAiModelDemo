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

/** 邮箱注册登录：BCrypt 存储密码，签发无状态 token。 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AppUserRepository users;
    private final ChatSessionRepository sessions;
    private final TokenService tokens;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(AppUserRepository users, ChatSessionRepository sessions, TokenService tokens) {
        this.users = users;
        this.sessions = sessions;
        this.tokens = tokens;
    }

    public record UserView(String id, String email, String nickname) {
    }

    public record AuthResult(String token, UserView user) {
    }

    /** 注册：邮箱唯一；首个注册用户接管历史（无主）会话。 */
    @Transactional
    public AuthResult register(String rawEmail, String password, String nickname) {
        String email = normalize(rawEmail);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "该邮箱已注册，请直接登录");
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
