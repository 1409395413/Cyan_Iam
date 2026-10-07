package com.yuchen.portfolio.service;

import com.yuchen.portfolio.config.AppProperties;
import com.yuchen.portfolio.web.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 素材落盘。
 *
 * <p>目录由 {@code APP_UPLOAD_DIR} 指定，Docker 部署时应挂载为持久卷。
 * 仓库里不会有任何素材文件 —— 重新部署只是换二进制，内容不会丢。
 */
@Service
public class StorageService {

  /** 允许的魔数签名；ContentType 可伪造，这里按文件头二次确认。 */
  private static final Map<String, byte[][]> SIGNATURES = Map.of(
      "image/jpeg", new byte[][] {{ (byte) 0xFF, (byte) 0xD8, (byte) 0xFF }},
      "image/png", new byte[][] {{ (byte) 0x89, 'P', 'N', 'G' }},
      "image/gif", new byte[][] {{ 'G', 'I', 'F' }},
      "image/webp", new byte[][] {{ 'R', 'I', 'F', 'F' }},
      "video/webm", new byte[][] {{ (byte) 0x1A, (byte) 0x45, (byte) 0xDF, (byte) 0xA3 }},
      "video/mp4", new byte[][] {
          { 0, 0, 0, 0x18, 'f', 't', 'y', 'p' },
          { 0, 0, 0, 0x1C, 'f', 't', 'y', 'p' },
          { 0, 0, 0, 0x20, 'f', 't', 'y', 'p' }
      }
  );

  private static final Set<String> EXT_BY_MIME = Set.of("jpg", "png", "gif", "webp", "mp4", "webm");

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(StorageService.class);

  private final Path root;

  public StorageService(AppProperties props) {
    Path configured = Paths.get(props.getUploadDir());
    this.root = configured.toAbsolutePath().normalize();
    // 相对路径会跟着 java 进程的工作目录解析 —— 换台服务器、换个启动脚本，
    // 素材就"消失"了（实际是写到了别处，/media/ 全变 404）。启动时直接叫出来。
    if (!configured.isAbsolute()) {
      log.warn(
          "上传目录配置的是相对路径 '{}'，已按当前工作目录解析为 '{}'。"
              + "服务器部署请改成绝对路径，否则换目录启动会找不到已有素材。",
          props.getUploadDir(), this.root);
    }
    try {
      Files.createDirectories(this.root);
    } catch (IOException e) {
      throw new IllegalStateException("无法创建上传目录: " + this.root, e);
    }
    log.info("上传目录: {}", this.root);
  }

  public Path root() { return root; }

  /** 按文件头嗅探真实 MIME，返回 null 表示不认识。 */
  public String sniff(MultipartFile file) throws IOException {
    byte[] head = new byte[32];
    int n;
    try (InputStream in = file.getInputStream()) {
      n = in.read(head);
    }
    if (n < 8) return null;

    for (Map.Entry<String, byte[][]> e : SIGNATURES.entrySet()) {
      for (byte[] sig : e.getValue()) {
        if (head.length >= sig.length && prefixMatch(head, sig)) return e.getKey();
      }
    }
    // MP4 的 box size 不固定，单独再兜一层
    if (head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p') return "video/mp4";
    if (new String(head, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
        && new String(head, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP")) {
      return "image/webp";
    }
    return null;
  }

  private boolean prefixMatch(byte[] data, byte[] sig) {
    for (int i = 0; i < sig.length; i++) {
      if (sig[i] != 0 && data[i] != sig[i]) return false;
    }
    return true;
  }

  /** 落盘并返回相对路径（yyyy/MM/uuid.ext），浏览器侧拼成 /media/<相对路径>。 */
  public String store(MultipartFile file, String mime) throws IOException {
    String ext = switch (mime) {
      case "image/jpeg" -> "jpg";
      case "image/png" -> "png";
      case "image/gif" -> "gif";
      case "image/webp" -> "webp";
      case "video/webm" -> "webm";
      default -> "mp4";
    };
    String folder = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
    Path dir = root.resolve(folder);
    Files.createDirectories(dir);
    String rel = folder + "/" + UUID.randomUUID().toString().replace("-", "").substring(0, 16) + "." + ext;
    Path target = root.resolve(rel).normalize();
    try (InputStream in = file.getInputStream()) {
      Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    }
    return rel;
  }

  /** 删除：先把相对路径限制在 root 之内，杜绝路径穿越。 */
  public boolean delete(String relativePath) {
    if (relativePath == null || relativePath.isBlank()) return false;
    try {
      Path target = root.resolve(relativePath).normalize();
      if (!target.startsWith(root)) return false;
      return Files.deleteIfExists(target);
    } catch (IOException e) {
      return false;
    }
  }

  public byte[] head(String relativePath, int len) throws IOException {
    Path p = resolve(relativePath);
    byte[] buf = new byte[len];
    try (InputStream in = Files.newInputStream(p)) {
      int n = in.read(buf);
      return n < len ? Arrays.copyOf(buf, Math.max(0, n)) : buf;
    }
  }

  public Path resolve(String relativePath) {
    Path p = root.resolve(relativePath).normalize();
    if (!p.startsWith(root)) throw ApiException.badRequest("非法路径");
    return p;
  }
}
