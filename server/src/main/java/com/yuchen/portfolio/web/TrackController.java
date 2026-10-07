package com.yuchen.portfolio.web;

import com.yuchen.portfolio.service.RateLimiter;
import com.yuchen.portfolio.service.TrackingService;
import com.yuchen.portfolio.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台埋点（匿名）。
 *
 * <p>三个动作：
 * <ol>
 *   <li>session — 打开页面时开一次会话，拿回 sessionId</li>
 *   <li>beat    — 每 20 秒报一次停留增量；关页面时用 sendBeacon 补最后一拍</li>
 *   <li>event   — 点开作品播放 / 提交需求等关键时刻</li>
 * </ol>
 *
 * <p>只存哈希 IP，不采集任何身份信息；按 IP 限流，防止被刷。
 */
@RestController
@RequestMapping("/api/track")
public class TrackController {

  private final TrackingService tracking;
  private final RateLimiter limiter;

  public TrackController(TrackingService tracking, RateLimiter limiter) {
    this.tracking = tracking;
    this.limiter = limiter;
  }

  public record SessionRequest(String path, String referrer) {}
  public record BeatRequest(Long sessionId, Long ms) {}
  public record EventRequest(Long sessionId, String type, String target) {}

  @PostMapping("/session")
  public ResponseEntity<Map<String, Object>> open(@RequestBody(required = false) SessionRequest body,
      HttpServletRequest http) {
    throttle(http);
    String path = body == null ? null : body.path();
    String ref = body == null ? null : body.referrer();
    long id = tracking.startSession(IpUtil.clientIp(http), http.getHeader("User-Agent"), ref, path);
    // 建议心跳间隔告诉前端，方便以后调整而不用改代码
    return ResponseEntity.ok(Map.of("sessionId", id, "beatMs", 20000L));
  }

  @PostMapping("/beat")
  public ResponseEntity<Map<String, String>> beat(@RequestBody(required = false) BeatRequest body,
      HttpServletRequest http) {
    throttle(http);
    if (body != null && body.sessionId() != null) {
      tracking.beat(body.sessionId(), body.ms() == null ? 0L : body.ms());
    }
    return ResponseEntity.ok(Map.of("status", "ok"));
  }

  @PostMapping("/event")
  public ResponseEntity<Map<String, String>> event(@RequestBody(required = false) EventRequest body,
      HttpServletRequest http) {
    throttle(http);
    if (body != null && body.sessionId() != null) {
      tracking.record(body.sessionId(), body.type(), body.target());
    }
    return ResponseEntity.ok(Map.of("status", "ok"));
  }

  private void throttle(HttpServletRequest http) {
    if (!limiter.allow("track:" + IpUtil.clientIp(http), 240)) {
      throw ApiException.tooManyRequests("请求过于频繁");
    }
  }
}
