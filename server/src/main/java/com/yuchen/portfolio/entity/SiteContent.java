package com.yuchen.portfolio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * 整站内容。
 *
 * <p>payload 是<b>不透明 JSON 字符串</b>：后端不理解里面的模块结构，只负责存取与基本校验。
 * 因此以后新增任意类型的模块，都不需要改 Java 或改表。
 */
@Getter
@Setter
@Entity
@Table(name = "site_content")
public class SiteContent {

  @Id
  @Column(name = "id")
  private Long id = 1L;

  /** 列类型为 LONGTEXT（MySQL JSON 列与 ORM 的映射歧义较多，这里刻意降级为文本）。 */
  @Column(name = "payload", nullable = false, columnDefinition = "LONGTEXT")
  private String payload;

  /** 每次保存自增，前端可据此判断是否需要重新拉取。 */
  @Column(name = "version", nullable = false)
  private Integer version = 1;

  @Column(name = "updated_by")
  private Long updatedBy;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    if (version == null) version = 1;
    if (updatedAt == null) updatedAt = Instant.now();
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }
}
