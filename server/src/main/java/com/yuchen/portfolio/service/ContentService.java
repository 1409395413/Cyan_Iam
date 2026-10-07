package com.yuchen.portfolio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuchen.portfolio.entity.ContentRevision;
import com.yuchen.portfolio.entity.SiteContent;
import com.yuchen.portfolio.repository.ContentRevisionRepository;
import com.yuchen.portfolio.repository.SiteContentRepository;
import com.yuchen.portfolio.web.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 站点内容读写。
 *
 * <p>payload 整体作为 JSON 字符串存取；MySQL 负责持久， Redis 只做短期缓存，
 * 缓存失效或未命中时回源 MySQL。前端所见的每个模块都来自这里，
 * 因此在后台改完即时生效，<b>不需要重新构建或重新部署</b>。
 */
@Service
public class ContentService {

  private static final Long CONTENT_ID = 1L;
  private static final String CACHE_KEY = "content:main";

  private final SiteContentRepository repo;
  private final ContentRevisionRepository revisions;
  private final CacheService cache;
  private final AuditService audit;
  private final ContentValidator validator;
  private final ObjectMapper mapper;

  public ContentService(SiteContentRepository repo, ContentRevisionRepository revisions,
      CacheService cache, AuditService audit, ContentValidator validator, ObjectMapper mapper) {
    this.repo = repo;
    this.revisions = revisions;
    this.cache = cache;
    this.audit = audit;
    this.validator = validator;
    this.mapper = mapper;
  }

  /** 前台读取：命中缓存直接返回，避免每次首页都查库。 */
  public String getRaw() {
    Optional<String> cached = cache.get(CACHE_KEY);
    if (cached.isPresent()) return cached.get();

    String json = repo.findById(CONTENT_ID).map(SiteContent::getPayload).orElseGet(this::seedDefault);
    cache.set(CACHE_KEY, json);
    return json;
  }

  public int currentVersion() {
    return repo.findById(CONTENT_ID).map(SiteContent::getVersion).orElse(1);
  }

  /** 后台保存：先存快照，再覆盖，最后清缓存。 */
  @Transactional
  public int save(String rawJson, Long userId, String ip) {
    JsonNode parsed;
    try {
      parsed = mapper.readTree(rawJson);
    } catch (Exception e) {
      throw ApiException.badRequest("JSON 格式错误");
    }

    JsonNode cleaned = validator.validateAndClean(parsed);
    String payload;
    try {
      payload = mapper.writeValueAsString(cleaned);
    } catch (Exception e) {
      throw ApiException.badRequest("内容序列化失败");
    }

    SiteContent entity = repo.findById(CONTENT_ID).orElseGet(SiteContent::new);
    if (entity.getPayload() != null && !entity.getPayload().isBlank()) {
      ContentRevision rev = new ContentRevision();
      rev.setPayload(entity.getPayload());
      rev.setCreatedBy(userId);
      revisions.save(rev);
    }

    entity.setId(CONTENT_ID);
    entity.setPayload(payload);
    entity.setVersion((entity.getVersion() == null ? 0 : entity.getVersion()) + 1);
    entity.setUpdatedBy(userId);
    repo.save(entity);

    cache.del(CACHE_KEY);
    audit.record(userId, "save_content", ip,
        Map.of("version", entity.getVersion(), "bytes", payload.length()));
    return entity.getVersion();
  }

  /** 回滚到某份历史快照。 */
  @Transactional
  public int rollback(Long revisionId, Long userId, String ip) {
    ContentRevision rev = revisions.findById(revisionId)
        .orElseThrow(() -> ApiException.notFound("快照不存在"));
    SiteContent entity = repo.findById(CONTENT_ID).orElseGet(SiteContent::new);

    ContentRevision backup = new ContentRevision();
    backup.setPayload(entity.getPayload());
    backup.setCreatedBy(userId);
    revisions.save(backup);

    entity.setId(CONTENT_ID);
    entity.setPayload(rev.getPayload());
    entity.setVersion((entity.getVersion() == null ? 0 : entity.getVersion()) + 1);
    entity.setUpdatedBy(userId);
    repo.save(entity);

    cache.del(CACHE_KEY);
    audit.record(userId, "rollback_content", ip, Map.of("revision", revisionId));
    return entity.getVersion();
  }

  /** 历史快照摘要，后台用于回滚。 */
  public java.util.List<RevisionItem> listRevisions() {
    return revisions.findTop20ByOrderByCreatedAtDesc().stream()
        .map(r -> new RevisionItem(
            r.getId(),
            r.getCreatedAt(),
            r.getPayload() == null ? 0 : r.getPayload().length(),
            countModules(r.getPayload())))
        .toList();
  }

  private int countModules(String payload) {
    if (payload == null || payload.isBlank()) return 0;
    try {
      com.fasterxml.jackson.databind.JsonNode n = mapper.readTree(payload);
      com.fasterxml.jackson.databind.JsonNode m = n.get("modules");
      return m == null || !m.isArray() ? 0 : m.size();
    } catch (Exception e) {
      return 0;
    }
  }

  /** 首次运行：把随包发布的默认内容写进数据库。 */
  @Transactional
  public String seedDefault() {
    String json = readDefaultFromClasspath();
    SiteContent entity = new SiteContent();
    entity.setId(CONTENT_ID);
    entity.setPayload(json);
    entity.setVersion(1);
    entity.setUpdatedAt(Instant.now());
    repo.save(entity);
    cache.set(CACHE_KEY, json);
    return json;
  }

  private String readDefaultFromClasspath() {
    try (InputStream in = new ClassPathResource("default-content.json").getInputStream()) {
      String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      JsonNode node = mapper.readTree(raw);
      return mapper.writeValueAsString(node);
    } catch (Exception e) {
      return "{\"version\":1,\"site\":{\"brandName\":\"Studio\",\"brandMark\":\"S\",\"footerCols\":[]},\"modules\":[]}";
    }
  }

  /** 暴露给内部用的简化别名 */
  private ApiException bad(String msg) { return ApiException.badRequest(msg); }
}
