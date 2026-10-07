package com.yuchen.portfolio.service;

import com.yuchen.portfolio.config.AppProperties;
import com.yuchen.portfolio.entity.AdminUser;
import com.yuchen.portfolio.repository.AdminUserRepository;
import com.yuchen.portfolio.service.ContentService;
import com.yuchen.portfolio.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 启后自检：把仓库 + 数据库对齐到可用状态。
 *
 * <p>做三件事，全部幂等：
 * <ol>
 *   <li>顺手把历史上写成 MySQL JSON 的列改成 LONGTEXT（ORM 对 JSON 列的映射在不同版本行为不一，
 *       统一成文本最省心，且 MySQL 8 对 JSON 列的常规读写没有性能优势）</li>
 *   <li>没有任何管理员时，用环境变量播种初始账号</li>
 *   <li>没有内容行时，用随包的默认内容播种</li>
 * </ol>
 */
@Component
public class DataInitializer implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

  private final JdbcTemplate jdbc;
  private final AdminUserRepository users;
  private final ContentService content;
  private final StorageService storage;
  private final PasswordEncoder encoder;
  private final AppProperties props;

  public DataInitializer(JdbcTemplate jdbc, AdminUserRepository users, ContentService content,
      StorageService storage, PasswordEncoder encoder, AppProperties props) {
    this.jdbc = jdbc;
    this.users = users;
    this.content = content;
    this.storage = storage;
    this.encoder = encoder;
    this.props = props;
  }

  @Override
  public void run(String... args) {
    migrateJsonColumns();
    bootstrapAdmin();
    bootstrapContent();
  }

  /** 兼容旧 schema：JSON 列统一转 LONGTEXT。 */
  private void migrateJsonColumns() {
    migrate("site_content", "payload");
    migrate("content_revision", "payload");
    migrate("audit_log", "detail");
  }

  private void migrate(String table, String column) {
    try {
      String type = jdbc.queryForObject(
          "SELECT DATA_TYPE FROM information_schema.COLUMNS "
              + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
          String.class, table, column);
      if (type != null && "json".equalsIgnoreCase(type)) {
        jdbc.execute("ALTER TABLE " + table + " MODIFY " + column + " LONGTEXT NULL");
        log.info("[init] migrated {}.{} json -> LONGTEXT", table, column);
      }
    } catch (Exception e) {
      // 列不存在（ddl-auto 尚未建表）或权限不足时忽略，交给 Hibernate
      log.debug("[init] column check skipped: {}.{} ({})", table, column, e.getMessage());
    }
  }

  private void bootstrapAdmin() {
    long exists = users.count();
    if (exists > 0) {
      log.info("[init] admin users: {}", exists);
      return;
    }
    String username = props.getAdmin().getBootstrapUsername();
    String password = props.getAdmin().getBootstrapPassword();
    if (username == null || username.isBlank() || password == null || password.length() < 8) {
      log.warn("[init] no admin exists and no ADMIN_USERNAME/ADMIN_PASSWORD provided. "
          + "Set them and restart to create the first account.");
      return;
    }
    AdminUser u = new AdminUser();
    u.setUsername(username.trim());
    u.setPasswordHash(encoder.encode(password));
    u.setRole(AdminUser.Role.owner);
    u.setStatus(1);
    users.save(u);
    log.info("[init] created bootstrap admin: {}", username);
  }

  private void bootstrapContent() {
    content.getRaw();   // 内部若为空会自动播种默认内容
    log.info("[init] content ready, version={}, uploadDir={}", content.currentVersion(),
        storage.root());
  }
}
