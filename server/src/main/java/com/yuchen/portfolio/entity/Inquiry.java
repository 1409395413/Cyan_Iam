package com.yuchen.portfolio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * 工作对接：访客在网站底部提交的项目需求。
 *
 * <p>以前这些留言塞在 audit_log 里，只能看不能处理。现在独立成表，
 * 后台可以按「未读 / 已联系 / 已归档」流转，还能写内部备注。
 */
@Getter
@Setter
@Entity
@Table(name = "inquiry")
public class Inquiry {

  public enum Status { new_, contacted, archived }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "name", length = 64)
  private String name;

  @Column(name = "contact", length = 128)
  private String contact;

  /** 项目类型，取自后台配置的 projectTypes */
  @Column(name = "type", length = 64)
  private String type;

  @Column(name = "budget", length = 64)
  private String budget;

  @Column(name = "message", columnDefinition = "LONGTEXT")
  private String message;

  @Column(name = "status", nullable = false, length = 16)
  private String status = "new";

  /** 内部备注，只有后台可见，不会回到前台 */
  @Column(name = "note", columnDefinition = "LONGTEXT")
  private String note;

  @Column(name = "ip", length = 45)
  private String ip;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    if (createdAt == null) createdAt = Instant.now();
    if (status == null || status.isBlank()) status = "new";
  }
}
