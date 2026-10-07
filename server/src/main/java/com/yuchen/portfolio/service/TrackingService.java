package com.yuchen.portfolio.service;

import com.yuchen.portfolio.entity.VisitEvent;
import com.yuchen.portfolio.entity.VisitSession;
import com.yuchen.portfolio.repository.VisitEventRepository;
import com.yuchen.portfolio.repository.VisitSessionRepository;
import com.yuchen.portfolio.web.ApiException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 访问统计：会话、停留时长、作品播放。
 *
 * <p>设计取舍：只存哈希后的 IP，不存昵称/账号，也不做跨天追踪。
 * 目的是回答"今天几个人来、待多久、有没有播放作品"，而不是建立用户画像。
 */
@Service
public class TrackingService {

  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
  private static final long MAX_BEAT_MS = 5 * 60 * 1000L; // 单次心跳最多累加 5 分钟，防止客户端造假

  private final VisitSessionRepository sessions;
  private final VisitEventRepository events;

  public TrackingService(VisitSessionRepository sessions, VisitEventRepository events) {
    this.sessions = sessions;
    this.events = events;
  }

  public String today() {
    return LocalDate.now(ZONE).toString();
  }

  /** 开一次会话。返回主键，前端存 sessionStorage 后用于心跳与事件上报。 */
  @Transactional
  public long startSession(String ip, String ua, String referrer, String path) {
    String day = today();
    String ipHash = sha256(ip == null ? "" : ip);
    String visitorKey = sha256(ipHash + "|" + (ua == null ? "" : ua) + "|" + day);

    VisitSession s = new VisitSession();
    s.setVisitorKey(visitorKey);
    s.setIpHash(ipHash);
    s.setUa(truncate(ua, 255));
    s.setReferrer(truncate(referrer, 255));
    s.setEntryPath(truncate(path, 255));
    s.setDay(day);
    Instant now = Instant.now();
    s.setStartedAt(now);
    s.setLastSeenAt(now);
    s.setDurationMs(0L);
    s.setPlayCount(0);
    return sessions.save(s).getId();
  }

  /** 心跳：累加停留时长。ms 是"自上次心跳以来"的毫秒数。 */
  @Transactional
  public void beat(long sessionId, long ms) {
    long add = Math.max(0, Math.min(ms, MAX_BEAT_MS));
    Optional<VisitSession> opt = sessions.findById(sessionId);
    if (opt.isEmpty()) return;
    VisitSession s = opt.get();
    s.setDurationMs(Math.max(0, (s.getDurationMs() == null ? 0 : s.getDurationMs())) + add);
    s.setLastSeenAt(Instant.now());
    sessions.save(s);
  }

  /** 记录一个动作；play 会同时累加会话的播放次数。 */
  @Transactional
  public void record(long sessionId, String type, String target) {
    String t = (type == null || type.isBlank()) ? "click" : type.trim();
    if (t.length() > 24) t = t.substring(0, 24);

    VisitEvent e = new VisitEvent();
    e.setSessionId(sessionId);
    e.setType(t);
    e.setTarget(truncate(target, 255));
    e.setDay(today());
    events.save(e);

    if ("play".equals(t)) {
      Optional<VisitSession> opt = sessions.findById(sessionId);
      if (opt.isPresent()) {
        VisitSession s = opt.get();
        s.setPlayCount((s.getPlayCount() == null ? 0 : s.getPlayCount()) + 1);
        s.setLastSeenAt(Instant.now());
        sessions.save(s);
      }
    }
  }

  public long requireSession(Long id) {
    if (id == null) throw ApiException.badRequest("缺少 sessionId");
    return id;
  }

  /* ------------------------------ 统计 ------------------------------ */

  public Map<String, Object> stats(int days) {
    int n = Math.max(1, Math.min(days, 30));
    String today = today();

    long visitors = sessions.countVisitorsByDay(today);
    long sessionCount = sessions.countByDay(today);
    long durationSum = sessions.sumDurationByDay(today);
    long plays = sessions.sumPlayByDay(today);
    long playedSessions = sessions.countPlayedSessionsByDay(today);

    Map<String, Object> t = new LinkedHashMap<>();
    t.put("day", today);
    t.put("visitors", visitors);
    t.put("sessions", sessionCount);
    t.put("plays", plays);
    t.put("playedSessions", playedSessions);
    t.put("avgDurationMs", sessionCount == 0 ? 0L : durationSum / sessionCount);
    t.put("playRate", sessionCount == 0 ? 0.0 : Math.round(playedSessions * 1000.0 / sessionCount) / 10.0);

    // 近 N 天趋势（按天倒序，前端再正序画）
    List<Object[]> rows = sessions.dailyAggregate(PageRequest.of(0, n));
    List<Map<String, Object>> series = new ArrayList<>();
    for (int i = rows.size() - 1; i >= 0; i--) {
      Object[] r = rows.get(i);
      long cnt = toLong(r[1]);
      long sum = toLong(r[2]);
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("day", String.valueOf(r[0]));
      m.put("sessions", cnt);
      m.put("plays", toLong(r[3]));
      m.put("avgDurationMs", cnt == 0 ? 0L : sum / cnt);
      series.add(m);
    }

    // 被点播最多的作品
    List<Map<String, Object>> top = new ArrayList<>();
    for (Object[] r : events.topPlayTargets(PageRequest.of(0, 10))) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("target", r[0] == null ? "未命名作品" : String.valueOf(r[0]));
      m.put("count", toLong(r[1]));
      top.add(m);
    }

    // 最近 20 次访问明细
    List<Map<String, Object>> recent = new ArrayList<>();
    for (VisitSession s : sessions.findTop50ByOrderByStartedAtDesc()) {
      if (recent.size() >= 20) break;
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", s.getId());
      m.put("startedAt", s.getStartedAt() == null ? null : s.getStartedAt().toString());
      m.put("durationMs", s.getDurationMs());
      m.put("playCount", s.getPlayCount());
      m.put("entryPath", s.getEntryPath());
      m.put("referrer", blankToNull(s.getReferrer()));
      m.put("ua", shortUa(s.getUa()));
      recent.add(m);
    }

    Map<String, Object> out = new HashMap<>();
    out.put("today", t);
    out.put("series", series);
    out.put("topPlays", top);
    out.put("recent", recent);
    return out;
  }

  /* ------------------------------ utils ------------------------------ */

  private static long toLong(Object o) {
    if (o == null) return 0L;
    if (o instanceof Number n) return n.longValue();
    return Long.parseLong(String.valueOf(o));
  }

  /** 把冗长的 UA 缩成"浏览器 + 系统"，后台看一眼就够 */
  private static String shortUa(String ua) {
    if (ua == null || ua.isBlank()) return "未知";
    String u = ua.toLowerCase();
    String os = u.contains("iphone") ? "iPhone"
        : u.contains("ipad") ? "iPad"
        : u.contains("android") ? "Android"
        : u.contains("mac os x") || u.contains("macintosh") ? "Mac"
        : u.contains("windows") ? "Windows"
        : u.contains("linux") ? "Linux" : "其他";
    String br = u.contains("edg/") ? "Edge"
        : u.contains("chrome/") && !u.contains("chromium") ? "Chrome"
        : u.contains("safari/") && !u.contains("chrome") ? "Safari"
        : u.contains("firefox/") ? "Firefox" : "浏览器";
    return os + " · " + br;
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }

  private static String truncate(String s, int max) {
    if (s == null) return null;
    String t = s.trim();
    return t.length() > max ? t.substring(0, max) : t;
  }

  private static String sha256(String raw) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] d = md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < d.length; i++) {
        if (i == 8) break; // 16 个十六进制字符足够区分，不必存全
        sb.append(String.format("%02x", d[i]));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      return String.valueOf(Math.abs(raw.hashCode()));
    }
  }
}
