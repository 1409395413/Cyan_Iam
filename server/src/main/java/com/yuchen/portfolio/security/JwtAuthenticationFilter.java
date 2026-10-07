package com.yuchen.portfolio.security;

import com.yuchen.portfolio.service.AuthService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** 从 Authorization 头或 Cookie 中取出 JWT，校验通过后放入安全上下文。 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtTokenProvider tokenProvider;
  private final AuthService authService;

  public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, AuthService authService) {
    this.tokenProvider = tokenProvider;
    this.authService = authService;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {

    String token = resolveToken(req);
    if (StringUtils.hasText(token) && tokenProvider.isValid(token)) {
      try {
        Claims claims = tokenProvider.parse(token);
        // 登出过的 token 直接作废
        if (!authService.isTokenBlacklisted(claims.getId())) {
          Long uid = claims.get("uid", Number.class) != null
              ? ((Number) claims.get("uid")).longValue()
              : null;
          String role = claims.get("role", String.class);
          Principal principal = new Principal(uid, claims.getSubject(), role);

          SecurityContextHolder.getContext().setAuthentication(
              new UsernamePasswordAuthenticationToken(
                  principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        }
      } catch (Exception ignore) {
        // token 解析异常按匿名处理，交给下游 401
      }
    }
    chain.doFilter(req, res);
  }

  private String resolveToken(HttpServletRequest req) {
    String header = req.getHeader(HttpHeaders.AUTHORIZATION);
    if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
      return header.substring(7);
    }
    return null;
  }

  /** 轻量登录态实体，替代 UserDetails 避免引入多余的 UserDetailsService。 */
  public record Principal(Long id, String username, String role) {}
}
