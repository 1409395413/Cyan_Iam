package com.yuchen.portfolio.security;

import com.yuchen.portfolio.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/** JWT 签发与校验。密钥来自环境变量 JWT_SECRET，长度不足 32 字节直接启动失败。 */
@Component
public class JwtTokenProvider {

  private final SecretKey key;
  private final AppProperties props;

  public JwtTokenProvider(AppProperties props) {
    this.props = props;
    byte[] bytes = props.getSecurity().getJwtSecret().getBytes(StandardCharsets.UTF_8);
    if (bytes.length < 32) {
      throw new IllegalStateException(
          "JWT_SECRET too short: need >= 32 bytes. Generate one with `openssl rand -base64 64`");
    }
    this.key = Keys.hmacShaKeyFor(bytes);
  }

  public String createToken(Long userId, String username, String role, boolean remember, long ttlSeconds) {
    Instant now = Instant.now();
    Instant exp = now.plusSeconds(ttlSeconds > 0 ? ttlSeconds : props.getSecurity().getAccessTokenTtl());
    return Jwts.builder()
        .subject(username)
        .id(UUID.randomUUID().toString())
        .claim("uid", userId)
        .claim("role", role)
        .claim("rmb", remember)
        .issuedAt(Date.from(now))
        .expiration(Date.from(exp))
        .signWith(key)
        .compact();
  }

  public boolean isValid(String token) {
    try {
      parse(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }

  public Claims parse(String token) {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
  }

  /** 取 jti，用于登出后的黑名单判断。 */
  public String jti(String token) {
    return parse(token).getId();
  }

  public long expiresInSeconds(String token) {
    Claims c = parse(token);
    long remain = c.getExpiration().getTime() - System.currentTimeMillis();
    return Math.max(0, remain / 1000);
  }
}
