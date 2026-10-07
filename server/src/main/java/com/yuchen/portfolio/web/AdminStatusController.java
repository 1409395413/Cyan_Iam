package com.yuchen.portfolio.web;

import com.yuchen.portfolio.service.CacheService;
import com.yuchen.portfolio.service.ContentService;
import com.yuchen.portfolio.service.MediaService;
import com.yuchen.portfolio.service.StorageService;
import com.yuchen.portfolio.config.AppProperties;
import com.yuchen.portfolio.util.SizeUtil;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 后台首页用的运行状态。 */
@RestController
@RequestMapping("/api/admin")
public class AdminStatusController {

  private final ContentService content;
  private final MediaService media;
  private final CacheService cache;
  private final StorageService storage;
  private final AppProperties props;

  public AdminStatusController(ContentService content, MediaService media, CacheService cache,
      StorageService storage, AppProperties props) {
    this.content = content;
    this.media = media;
    this.cache = cache;
    this.storage = storage;
    this.props = props;
  }

  @GetMapping("/status")
  public ResponseEntity<Map<String, Object>> status() {
    String redis = props.isCacheEnabled() ? (cache.isRedisDown() ? "down" : "up") : "disabled";
    return ResponseEntity.ok(Map.of(
        "contentVersion", content.currentVersion(),
        "mediaCount", media.list().size(),
        "redis", redis,
        "uploadDir", storage.root().toString(),
        "maxUploadMB", props.getUploadMaxBytes() / (1024 * 1024),
        "maxUploadText", SizeUtil.human(props.getUploadMaxBytes())));
  }
}
