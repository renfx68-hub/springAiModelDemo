package com.example.aiterminal.config;

import com.example.aiterminal.exception.ApiException;
import com.example.aiterminal.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 校验 Bearer token 并把 userId 放进请求属性。
 * 放行：/api/auth/**（登录注册，其中 /api/auth/me 仍需登录）、GET /api/images/{文件}（&lt;img&gt; 标签无法携带请求头，仅限图片文件）、OPTIONS 预检。
 * 注意 /api/images/generate（文生图）仍需登录，否则统一 401。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER = "bearer ";

    /** 账号体系关闭时的虚拟用户；会话落库为无主（owner_id null），首个注册用户可接管。 */
    public static final String ANONYMOUS = "anonymous";

    private final TokenService tokens;
    private final AuthSettings settings;

    public AuthInterceptor(TokenService tokens, AuthSettings settings) {
        this.tokens = tokens;
        this.settings = settings;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        boolean anonymousAuthPath = path.startsWith("/api/auth/") && !path.equals("/api/auth/me");
        boolean anonymousImageFile = "GET".equalsIgnoreCase(request.getMethod())
                && path.matches("/api/images/[^/]+");
        if (!path.startsWith("/api/") || anonymousImageFile || anonymousAuthPath) {
            return true;
        }
        // 开发阶段总开关关闭：全部匿名放行，接口层无需感知
        if (!settings.enabled()) {
            request.setAttribute("userId", ANONYMOUS);
            request.setAttribute("userEmail", "");
            return true;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.toLowerCase().startsWith(BEARER)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        TokenService.TokenPayload payload = tokens.verify(header.substring(BEARER.length()).trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "登录已过期，请重新登录"));
        request.setAttribute("userId", payload.userId());
        request.setAttribute("userEmail", payload.email());
        return true;
    }
}
