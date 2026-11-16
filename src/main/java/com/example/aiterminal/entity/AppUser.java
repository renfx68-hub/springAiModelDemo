package com.example.aiterminal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 账号：邮箱为主身份，密码仅存 BCrypt 哈希。 */
@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 200)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 60)
    private String nickname;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant lastLoginAt;

    protected AppUser() {
    }

    public AppUser(String id, String email, String passwordHash, String nickname, Instant now) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.createdAt = now;
        this.lastLoginAt = now;
    }

    public void markLogin(Instant now) {
        this.lastLoginAt = now;
    }

    /** 重置密码后同时刷新最近登录时间，便于审计。 */
    public void changePassword(String passwordHash, Instant now) {
        this.passwordHash = passwordHash;
        this.lastLoginAt = now;
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getNickname() { return nickname; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
}
