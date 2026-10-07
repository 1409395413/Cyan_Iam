package com.yuchen.portfolio.web;

import com.yuchen.portfolio.repository.InquiryRepository;
import com.yuchen.portfolio.service.TrackingService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 后台「监控」板块：今日访客、停留时长、作品播放。 */
@RestController
@RequestMapping("/api/admin")
public class AdminStatsController {

  private final TrackingService tracking;
  private final InquiryRepository inquiries;

  public AdminStatsController(TrackingService tracking, InquiryRepository inquiries) {
    this.tracking = tracking;
    this.inquiries = inquiries;
  }

  @GetMapping("/stats")
  public ResponseEntity<Map<String, Object>> stats(
      @RequestParam(value = "days", defaultValue = "7") int days) {
    Map<String, Object> out = new java.util.LinkedHashMap<>(tracking.stats(days));
    // 顺带把需求件数带上，后台一张卡片就能看完
    out.put("inquiry", Map.of(
        "total", inquiries.count(),
        "new", inquiries.countByStatus("new"),
        "contacted", inquiries.countByStatus("contacted"),
        "archived", inquiries.countByStatus("archived")));
    return ResponseEntity.ok(out);
  }
}
