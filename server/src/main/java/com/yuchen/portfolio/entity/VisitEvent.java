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

/** 会话内的关键动作，目前主要记录作品播放（play）。 */
@Getter
@Setter
@Entity
@Table(name = "visit_event")
public class VisitEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "session_id", nullable = false)
  private Long sessionId;

  /** play / inquiry / contact_click */
  @Column(name = "type", nullable = false, length = 24)
  private String type;

  /** 被点的对象，例如模块 id 或作品标题 */
  @Column(name = "target", length = 255)
  private String target;

  @Column(name = "day", length = 10)
  private String day;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    if (createdAt == null) createdAt = Instant.now();
  }
}
