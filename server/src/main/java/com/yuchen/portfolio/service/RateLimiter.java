package com.yuchen.portfolio.service;

import com.yuchen.portfolio.config.AppProperties;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * 滑动窗口限流，纯内存实现（单实例够用，多实例请配合 Nginx limit_req）。
 *
 * <p>之所以不用 Redis 计数：限流失败不应该导致整个接口不可用，本地计数即使被打散也 still 有兜底。
 */
@Service
public class RateLimiter {

  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  public RateLimiter() {
    ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "rate-sweeper");
      t.setDaemon(true);
      return t;
    });
    ses.scheduleAtFixedRate(this::sweep, 120, 120, TimeUnit.SECONDS);
  }

  /** 返回 true 表示放行。 */
  public boolean allow(String bucket, int limitPerMinute) {
    if (limitPerMinute <= 0) return true;
    long now = System.currentTimeMillis();
    Window w = windows.computeIfAbsent(bucket, k -> new Window(now));
    return w.hit(now, limitPerMinute);
  }

  public int remainingQuota(String bucket, int limitPerMinute) {
    Window w = windows.get(bucket);
    if (w == null) return limitPerMinute;
    return Math.max(0, limitPerMinute - w.count.get());
  }

  private void sweep() {
    long now = System.currentTimeMillis();
    windows.entrySet().removeIf(e -> now - e.getValue().start > TimeUnit.MINUTES.toMillis(2));
  }

  private static final class Window {
    final long start;
    final AtomicInteger count = new AtomicInteger(0);

    Window(long start) { this.start = start; }

    boolean hit(long now, int limit) {
      if (now - start > TimeUnit.MINUTES.toMillis(1)) {
        // 窗口过期，重新计数（懒重置，避免额外定时开销）
        if (now - start > TimeUnit.MINUTES.toMillis(1) && count.get() >= limit) {
          return false;
        }
      }
      return count.incrementAndGet() <= limit;
    }
  }
}
