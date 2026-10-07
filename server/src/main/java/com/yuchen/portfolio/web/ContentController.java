package com.yuchen.portfolio.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuchen.portfolio.service.ContentService;
import com.yuchen.portfolio.service.RevisionItem;
import com.yuchen.portfolio.util.IpUtil;
import com.yuchen.portfolio.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 站点内容：前台公开读，后台登录写。 */
@RestController
@RequestMapping("/api")
public class ContentController {

  private final ContentService content;
  private final AuthService auth;
  private final ObjectMapper mapper;

  public ContentController(ContentService content, AuthService auth, ObjectMapper mapper) {
    this.content = content;
    this.auth = auth;
    this.mapper = mapper;
  }

  @GetMapping(value = "/content", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> get() {
    String json = content.getRaw();
    return ResponseEntity.ok()
        .contentType(new MediaType(MediaType.APPLICATION_JSON,
            java.nio.charset.StandardCharsets.UTF_8))
        // 内容随时可能被后台改动，交给浏览器每次校验
        .cacheControl(CacheControl.noCache())
        .header("X-Content-Version", String.valueOf(content.currentVersion()))
        .body(json);
  }

  @PutMapping(value = "/content", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, Object>> save(@RequestBody JsonNode body,
      HttpServletRequest request) {
    String raw;
    try {
      raw = mapper.writeValueAsString(body);
    } catch (Exception e) {
      throw ApiException.badRequest("内容无法解析");
    }
    int version = content.save(raw, auth.currentUserId(), IpUtil.clientIp(request));
    return ResponseEntity.ok(Map.of("version", version));
  }

  /** 历史快照列表，后台用于回滚。 */
  @GetMapping("/admin/revisions")
  public ResponseEntity<java.util.List<RevisionItem>> revisions() {
    return ResponseEntity.ok(content.listRevisions());
  }

  @PostMapping("/admin/revisions/{id}/rollback")
  public ResponseEntity<Map<String, Object>> rollback(@PathVariable Long id,
      HttpServletRequest request) {
    int v = content.rollback(id, auth.currentUserId(), IpUtil.clientIp(request));
    return ResponseEntity.ok(Map.of("version", v));
  }
}
