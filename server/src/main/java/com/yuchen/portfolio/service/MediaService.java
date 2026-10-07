package com.yuchen.portfolio.service;

import com.yuchen.portfolio.config.AppProperties;
import com.yuchen.portfolio.entity.MediaAsset;
import com.yuchen.portfolio.repository.MediaAssetRepository;
import com.yuchen.portfolio.util.SizeUtil;
import com.yuchen.portfolio.web.ApiException;
import com.yuchen.portfolio.web.dto.MediaUploadResult;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 素材上传 / 删除 / 列表。
 *
 * <p>只在数据库里存相对路径（例如 {@code 2026/10/ab12.jpg}），
 * 对外暴露 {@code /media/2026/10/ab12.jpg}。换域名或换存储位置都不影响已发布的内容。
 */
@Service
public class MediaService {

  private final StorageService storage;
  private final MediaAssetRepository repo;
  private final AuditService audit;
  private final AppProperties props;

  public MediaService(StorageService storage, MediaAssetRepository repo, AuditService audit,
      AppProperties props) {
    this.storage = storage;
    this.repo = repo;
    this.audit = audit;
    this.props = props;
  }

  @Transactional
  public MediaUploadResult upload(MultipartFile file, Long userId, String ip) {
    if (file == null || file.isEmpty()) {
      throw ApiException.badRequest("没有收到文件");
    }
    if (file.getSize() > props.getUploadMaxBytes()) {
      throw ApiException.badRequest(
          "文件超过上限 " + SizeUtil.human(props.getUploadMaxBytes()) + "，当前 " + SizeUtil.human(file.getSize()));
    }

    String mime;
    try {
      mime = storage.sniff(file);
    } catch (IOException e) {
      throw ApiException.badRequest("读取文件失败");
    }
    if (mime == null) {
      throw ApiException.badRequest("无法识别的文件类型，只允许图片(jpg/png/gif/webp)与视频(mp4/webm)");
    }
    if (props.getUploadAllowMime() != null && !props.getUploadAllowMime().isEmpty()
        && !props.getUploadAllowMime().contains(mime)) {
      throw ApiException.badRequest("该文件类型不在允许列表中：" + mime);
    }

    String checksum;
    try {
      checksum = sha256(file.getBytes());
    } catch (Exception e) {
      throw ApiException.badRequest("文件校验失败");
    }

    // 同内容直接复用，避免重复占空间
    Optional<MediaAsset> existing = repo.findTopByChecksum(checksum);
    if (existing.isPresent()) {
      MediaAsset e = existing.get();
      return new MediaUploadResult(publicUrl(e.getPath()), e.getMime(), e.getSize(), e.getWidth(), e.getHeight());
    }

    String rel;
    try {
      rel = storage.store(file, mime);
    } catch (IOException e) {
      throw ApiException.badRequest("保存文件失败");
    }

    int[] wh = readImageSize(mime, rel);

    MediaAsset asset = new MediaAsset();
    asset.setPath(rel);
    asset.setMime(mime);
    asset.setSize(file.getSize());
    asset.setChecksum(checksum);
    asset.setWidth(wh == null ? null : wh[0]);
    asset.setHeight(wh == null ? null : wh[1]);
    asset.setCreatedBy(userId);
    repo.save(asset);

    audit.record(userId, "upload_media", ip, Map.of("path", rel, "mime", mime, "size", file.getSize()));
    return new MediaUploadResult(publicUrl(rel), mime, file.getSize(), asset.getWidth(), asset.getHeight());
  }

  @Transactional
  public void delete(String relativePath, Long userId, String ip) {
    MediaAsset asset = repo.findByPath(relativePath)
        .orElseThrow(() -> ApiException.notFound("素材不存在"));
    storage.delete(asset.getPath());
    repo.delete(asset);
    audit.record(userId, "delete_media", ip, Map.of("path", relativePath));
  }

  public List<MediaAsset> list() {
    return repo.findTop100ByOrderByCreatedAtDesc();
  }

  public static String publicUrl(String relativePath) {
    return "/media/" + relativePath;
  }

  private int[] readImageSize(String mime, String rel) {
    if (!mime.startsWith("image/")) return null;
    try {
      java.nio.file.Path p = storage.resolve(rel);
      BufferedImage img = ImageIO.read(p.toFile());
      if (img == null) return null;
      return new int[] { img.getWidth(), img.getHeight() };
    } catch (Exception e) {
      return null;
    }
  }

  private String sha256(byte[] data) throws NoSuchAlgorithmException {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    return HexFormat.of().formatHex(md.digest(data));
  }
}
