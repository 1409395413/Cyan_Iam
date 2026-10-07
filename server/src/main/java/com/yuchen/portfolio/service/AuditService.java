package com.yuchen.portfolio.service;

import com.yuchen.portfolio.entity.AuditLog;
import com.yuchen.portfolio.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 审计日志：登录、改内容、传素材等关键动作都留痕。 */
@Service
public class AuditService {

  private final AuditLogRepository repo;
  private final ObjectMapper mapper = new ObjectMapper();

  public AuditService(AuditLogRepository repo) {
    this.repo = repo;
  }

  public void record(Long userId, String action, String ip, Map<String, Object> detail) {
    try {
      AuditLog log = new AuditLog();
      log.setUserId(userId);
      log.setAction(action);
      log.setIp(ip);
      log.setDetail(detail == null ? null : mapper.writeValueAsString(detail));
      repo.save(log);
    } catch (Exception e) {
      // 审计失败绝不能影响主流程
    }
  }

  /** 查最近的同类事件（留言列表、登录记录都走这里）。 */
  public java.util.List<AuditLog> findRecent(String action, int limit) {
    return repo.findTopByActionOrderByCreatedAtDesc(action,
        org.springframework.data.domain.PageRequest.of(0, Math.max(1, limit)));
  }
}
