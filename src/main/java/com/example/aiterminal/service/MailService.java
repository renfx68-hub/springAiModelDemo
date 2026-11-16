package com.example.aiterminal.service;

import com.example.aiterminal.config.MailSettings;
import com.example.aiterminal.exception.ApiException;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/** 验证码邮件发送：默认 QQ 邮箱 SMTP，dev-mode 下只打日志不发信，便于本地联调。 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final String BRAND = "辣条log";

    private final MailSettings settings;
    private final JavaMailSenderImpl sender;

    public MailService(MailSettings settings) {
        this.settings = settings;
        this.sender = createSender(settings);
    }

    /** 是否启用邮箱验证码流程（false 时注册免验证码）。 */
    public boolean enabled() {
        return Boolean.TRUE.equals(settings.enabled());
    }

    /** 开发模式：验证码只写日志并由接口返回，不发真实邮件。 */
    public boolean devMode() {
        return Boolean.TRUE.equals(settings.devMode());
    }

    public void sendCode(String email, String code, String purpose) {
        boolean reset = "reset".equals(purpose);
        String subject = reset ? BRAND + " 密码重置验证码" : BRAND + " 注册验证码";
        String action = reset ? "重置密码" : "完成注册";
        String html = """
                <div style="font-family:-apple-system,'Segoe UI',sans-serif;max-width:520px;margin:0 auto;padding:24px">
                  <h2 style="margin:0 0 8px;color:#e4392e">🌶️ %s</h2>
                  <p style="color:#444;line-height:1.7">你正在%s，验证码：</p>
                  <p style="font-size:30px;letter-spacing:8px;font-weight:700;color:#e4392e;margin:14px 0">%s</p>
                  <p style="color:#888;font-size:13px;line-height:1.7">验证码 10 分钟内有效。若非本人操作请忽略本邮件。</p>
                </div>
                """.formatted(BRAND, action, code);

        if (devMode()) {
            log.info("【开发模式】{}（{}）→ {}，验证码 {}", subject, email, action, code);
            return;
        }
        if (sender == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "服务端未配置发信账号（APP_MAIL_USERNAME / APP_MAIL_PASSWORD），暂无法发送验证码");
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(settings.from());
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(message);
            log.info("验证码邮件已发送：{} → {}", purpose, email);
        } catch (Exception exception) {
            log.warn("验证码邮件发送失败：{} → {}", email, exception.toString());
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "验证码邮件发送失败，请检查发信配置或稍后重试");
        }
    }

    private static JavaMailSenderImpl createSender(MailSettings settings) {
        if (!Boolean.TRUE.equals(settings.enabled()) || Boolean.TRUE.equals(settings.devMode())) {
            return null;
        }
        if (settings.username().isBlank() || settings.password().isBlank()) {
            log.warn("已开启邮箱验证码但未配置 APP_MAIL_USERNAME / APP_MAIL_PASSWORD，验证码邮件将无法发送");
            return null;
        }
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(settings.host());
        mailSender.setPort(settings.port());
        mailSender.setUsername(settings.username());
        mailSender.setPassword(settings.password());
        mailSender.setDefaultEncoding("UTF-8");
        Properties properties = mailSender.getJavaMailProperties();
        properties.put("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.ssl.enable", String.valueOf(Boolean.TRUE.equals(settings.ssl())));
        properties.put("mail.smtp.starttls.enable", String.valueOf(!Boolean.TRUE.equals(settings.ssl())));
        properties.put("mail.smtp.connectiontimeout", "5000");
        properties.put("mail.smtp.timeout", "8000");
        properties.put("mail.smtp.writetimeout", "8000");
        return mailSender;
    }
}
