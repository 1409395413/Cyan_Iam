package com.yuchen.portfolio.web;

import com.yuchen.portfolio.security.JwtAuthenticationFilter;
import com.yuchen.portfolio.service.AuthService;
import com.yuchen.portfolio.util.IpUtil;
import com.yuchen.portfolio.web.dto.ChangePasswordRequest;
import com.yuchen.portfolio.web.dto.LoginRequest;
import com.yuchen.portfolio.web.dto.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 后台登录态。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req,
      HttpServletRequest http) {
    boolean remember = Boolean.TRUE.equals(req.getRemember());
    LoginResponse res = auth.login(
        req.getUsername(), req.getPassword(), remember,
        IpUtil.clientIp(http), http.getHeader("User-Agent"));
    return ResponseEntity.ok(res);
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest http) {
    String header = http.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      auth.logout(header.substring(7));
    }
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  public ResponseEntity<Map<String, Object>> me() {
    JwtAuthenticationFilter.Principal p = auth.current();
    if (p == null) throw ApiException.unauthorized("未登录");
    return ResponseEntity.ok(Map.of(
        "username", p.username(),
        "role", p.role() == null ? "editor" : p.role(),
        "checkedAt", Instant.now().toString()));
  }

  @PostMapping(value = "/password", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, String>> changePassword(
      @Valid @RequestBody ChangePasswordRequest req, HttpServletRequest http) {
    JwtAuthenticationFilter.Principal p = auth.current();
    if (p == null) throw ApiException.unauthorized("未登录");
    auth.changePassword(p.username(), req.getOldPassword(), req.getNewPassword(),
        IpUtil.clientIp(http));
    return ResponseEntity.ok(Map.of("status", "ok"));
  }
}
