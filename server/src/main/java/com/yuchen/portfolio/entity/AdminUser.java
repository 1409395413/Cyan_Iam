package com.yuchen.portfolio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** 后台管理员。密码用 BCrypt 存储，永不明文落库。 */
@Getter
@Setter
@Entity
@Table(name = "admin_user")
public class AdminUser {

  public enum Role { owner, editor }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "username", nullable = false, unique = true, length = 32)
  private String username;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  /** 预留：TOTP 二次验证密钥 */
  @Column(name = "totp_secret", length = 64)
  private String totpSecret;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 16)
  private Role role = Role.owner;

  /** 1=启用 0=停用 */
  @Column(name = "status", nullable = false)
  private Integer status = 1;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @Column(name = "last_login_ip", length = 45)
  private String lastLoginIp;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    if (createdAt == null) createdAt = Instant.now();
    if (status == null) status = 1;
    if (role == null) role = Role.owner;
  }

  public boolean isActive() {
    return status != null && status == 1;
  }
}
