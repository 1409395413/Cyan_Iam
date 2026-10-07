package com.yuchen.portfolio.util;

import jakarta.servlet.http.HttpServletRequest;

/** 取客户端真实 IP。 */
public final class IpUtil {

  private IpUtil() {}

  /**
   * 仅在 Nginx 正确覆写了 X-Real-IP / X-Forwarded-For 时才可信。
   * 直接暴露端口给外网时请以这三个请求头为准之外的 remoteAddr。
   */
  public static String clientIp(HttpServletRequest request) {
    if (request == null) return "unknown";

    String real = request.getHeader("X-Real-IP");
    if (hasText(real)) return normalize(real);

    String xff = request.getHeader("X-Forwarded-For");
    if (hasText(xff)) {
      int comma = xff.indexOf(',');
      String first = comma > 0 ? xff.substring(0, comma) : xff;
      if (hasText(first)) return normalize(first);
    }

    return normalize(request.getRemoteAddr());
  }

  private static String normalize(String v) {
    String s = v.trim();
    // IPv6 回环统一显示为 IPv4，日志里更易读
    if ("0:0:0:0:0:0:0:1".equals(s) || "::1".equals(s)) return "127.0.0.1";
    return s.length() > 45 ? s.substring(0, 45) : s;
  }

  private static boolean hasText(String s) {
    return s != null && !s.isBlank();
  }
}
