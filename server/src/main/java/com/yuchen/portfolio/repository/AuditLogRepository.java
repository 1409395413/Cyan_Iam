package com.yuchen.portfolio.repository;

import com.yuchen.portfolio.entity.AuditLog;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

  java.util.List<AuditLog> findTopByActionOrderByCreatedAtDesc(String action, Pageable page);
}
