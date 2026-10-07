package com.yuchen.portfolio.service;

import com.yuchen.portfolio.config.AppProperties;
import com.yuchen.portfolio.entity.AdminUser;
import com.yuchen.portfolio.repository.AdminUserRepository;
import com.yuchen.portfolio.security.JwtAuthenticationFilter;
import com.yuchen.portfolio.security.JwtTokenProvider;
import com.yuchen.portfolio.web.ApiException;
import com.yuchen.portfolio.web.dto.LoginResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 后台登录 / 登出 / 改密。 */
@Service
public class AuthService {

  private final PasswordEncoder encoder;
  /** 用户名不存在时也跑一次同样的哈希，避免通过响应时间差枚举账号。 */
  private final String dummyHash;

  private final AdminUserRepository users;
  private final JwtTokenProvider jwt;
  private final CacheService cache;
  private final AuditService audit;
  private final AppProperties props;

  public AuthService(AdminUserRepository users, JwtTokenProvider jwt, CacheService cache,
      AuditService audit, AppProperties props, PasswordEncoder encoder) {
    this.users = users;
    this.jwt = jwt;
    this.cache = cache;
    this.audit = audit;
    this.props = props;
    this.encoder = encoder;
    this.dummyHash = encoder.encode("dummy-password-for-timing-safety");
  }

  @Transactional
  public LoginResponse login(String username, String password, boolean remember, String ip, String ua) {
    String lockKey = "login:fail:" + username + ":" + ip;
    long failed = readCounter(lockKey);
    long remain = props.getSecurity().getLockSeconds();

    if (failed >= props.getSecurity().getMaxFailedAttempts()) {
      long left = cache.ttlSeconds(lockKey);
      throw ApiException.tooManyRequests(
          "登录失败次数过多，请 " + Math.max(1, left / 60) + " 分钟后再试");
    }

    AdminUser user = users.findByUsername(username).orElse(null);

    boolean ok = user != null && user.isActive() && encoder.matches(password, user.getPasswordHash());
    if (!ok) {
      // 恒定时间：无论用户名是否存在都执行同样的工作量
      encoder.matches(password, dummyHash);
      cache.incr(lockKey, Duration.ofSeconds(Math.max(remain, 60)));
      long attempts = failed + 1;
      audit.record(user == null ? null : user.getId(), "login_failed", ip,
          Map.of("username", username, "attempt", attempts));
      long left = props.getSecurity().getMaxFailedAttempts() - attempts;
      throw ApiException.unauthorized(left > 0
          ? "用户名或密码不正确，还可尝试 " + left + " 次"
          : "登录失败次数过多，账号已被临时锁定");
    }

    cache.del(lockKey);
    user.setLastLoginAt(java.time.Instant.now());
    user.setLastLoginIp(ip);
    users.save(user);

    long ttl = remember ? props.getSecurity().getRememberTtl() : props.getSecurity().getAccessTokenTtl();
    String token = jwt.createToken(user.getId(), user.getUsername(), user.getRole().name(), remember, ttl);

    audit.record(user.getId(), "login", ip, Map.of("remember", remember, "ua", truncate(ua)));
    return new LoginResponse(token, ttl, user.getUsername(), user.getRole().name());
  }

  @Transactional
  public void changePassword(String username, String oldPassword, String newPassword, String ip) {
    AdminUser user = users.findByUsername(username)
        .orElseThrow(() -> ApiException.unauthorized("未登录"));
    if (!encoder.matches(oldPassword, user.getPasswordHash())) {
      throw ApiException.badRequest("原密码不正确");
    }
    if (encoder.matches(newPassword, user.getPasswordHash())) {
      throw ApiException.badRequest("新密码不能与原密码相同");
    }
    user.setPasswordHash(encoder.encode(newPassword));
    users.save(user);
    audit.record(user.getId(), "change_password", ip, Map.of());
  }

  /** 登出：把 jti 放进黑名单，剩余有效期内不再承认。 */
  public void logout(String token) {
    try {
      String jti = jwt.jti(token);
      long left = jwt.expiresInSeconds(token);
      if (left > 0) cache.set("blacklist:" + jti, "1", Duration.ofSeconds(left));
      SecurityContextHolder.clearContext();
    } catch (Exception ignore) {
      // token 本身无效时无需处理
    }
  }

  public boolean isTokenBlacklisted(String jti) {
    if (jti == null) return false;
    return cache.get("blacklist:" + jti).isPresent();
  }

  public JwtAuthenticationFilter.Principal current() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof JwtAuthenticationFilter.Principal p)) {
      return null;
    }
    return p;
  }

  public Long currentUserId() {
    var p = current();
    return p == null ? null : p.id();
  }

  PasswordEncoder encoder() { return encoder; }

  private long readCounter(String key) {
    try {
      // get 返回空则计数为 0
      return cache.get(key).map(Long::parseLong).orElse(0L);
    } catch (Exception e) {
      return 0L;
    }
  }

  private String truncate(String s) {
    if (s == null) return "";
    return s.length() > 200 ? s.substring(0, 200) : s;
  }
}
