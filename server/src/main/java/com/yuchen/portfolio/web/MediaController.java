package com.yuchen.portfolio.web;

import com.yuchen.portfolio.entity.MediaAsset;
import com.yuchen.portfolio.service.AuthService;
import com.yuchen.portfolio.service.MediaService;
import com.yuchen.portfolio.util.IpUtil;
import com.yuchen.portfolio.web.dto.MediaUploadResult;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 素材管理：上传需要登录，删除需要登录，读取由 Nginx 直出不需要登录。 */
@RestController
@RequestMapping("/api/media")
public class MediaController {

  private final MediaService media;
  private final AuthService auth;

  public MediaController(MediaService media, AuthService auth) {
    this.media = media;
    this.auth = auth;
  }

  @PostMapping
  public ResponseEntity<MediaUploadResult> upload(@RequestParam("file") MultipartFile file,
      HttpServletRequest request) {
    return ResponseEntity.ok(media.upload(file, auth.currentUserId(), IpUtil.clientIp(request)));
  }

  @GetMapping
  public ResponseEntity<List<MediaItemView>> list() {
    List<MediaItemView> items = media.list().stream().map(MediaItemView::from).collect(Collectors.toList());
    return ResponseEntity.ok(items);
  }

  @DeleteMapping
  public ResponseEntity<Void> delete(@RequestParam("path") String path, HttpServletRequest request) {
    media.delete(path, auth.currentUserId(), IpUtil.clientIp(request));
    return ResponseEntity.noContent().build();
  }

  public record MediaItemView(String path, String url, String mime, long size,
      Integer width, Integer height, String createdAt) {

    static MediaItemView from(MediaAsset a) {
      return new MediaItemView(
          a.getPath(),
          MediaService.publicUrl(a.getPath()),
          a.getMime(),
          a.getSize() == null ? 0L : a.getSize(),
          a.getWidth(),
          a.getHeight(),
          a.getCreatedAt() == null ? null : a.getCreatedAt().toString());
    }
  }
}
