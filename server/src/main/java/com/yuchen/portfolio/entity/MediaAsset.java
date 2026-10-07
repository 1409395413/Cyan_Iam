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
 * 上传的素材元数据。
 *
 * <p>path 存的是相对路径（如 {@code 2026/10/ab12cd34.jpg}），不暴露服务器真实目录，
 * 也不写着绝对地址 —— 这样换域名、换存储路径时旧内容仍然可用。
 */
@Getter
@Setter
@Entity
@Table(name = "media_asset")
public class MediaAsset {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "path", nullable = false, length = 255)
  private String path;

  @Column(name = "mime", nullable = false, length = 64)
  private String mime;

  @Column(name = "size", nullable = false)
  private Long size;

  /** sha256，用于去重与秒传 */
  @Column(name = "checksum", length = 64)
  private String checksum;

  @Column(name = "width")
  private Integer width;

  @Column(name = "height")
  private Integer height;

  @Column(name = "created_by")
  private Long createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    if (createdAt == null) createdAt = Instant.now();
    if (size == null) size = 0L;
  }
}
