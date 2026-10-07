package com.yuchen.portfolio.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml 里 app.* 这一段的强类型映射。 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

  private String keyPrefix = "yc:";
  private boolean cacheEnabled = true;
  private long cacheTtlSeconds = 300;
  private String uploadDir = "./data/upload";
  private List<String> uploadAllowMime = List.of();
  private long uploadMaxBytes = 209715200L;

  private final Security security = new Security();
  private final Admin admin = new Admin();
  private final Cors cors = new Cors();

  public String getKeyPrefix() { return keyPrefix; }
  public void setKeyPrefix(String v) { this.keyPrefix = v; }
  public boolean isCacheEnabled() { return cacheEnabled; }
  public void setCacheEnabled(boolean v) { this.cacheEnabled = v; }
  public long getCacheTtlSeconds() { return cacheTtlSeconds; }
  public void setCacheTtlSeconds(long v) { this.cacheTtlSeconds = v; }
  public String getUploadDir() { return uploadDir; }
  public void setUploadDir(String v) { this.uploadDir = v; }
  public List<String> getUploadAllowMime() { return uploadAllowMime; }
  public void setUploadAllowMime(List<String> v) { this.uploadAllowMime = v; }
  public long getUploadMaxBytes() { return uploadMaxBytes; }
  public void setUploadMaxBytes(long v) { this.uploadMaxBytes = v; }

  public Security getSecurity() { return security; }
  public Admin getAdmin() { return admin; }
  public Cors getCors() { return cors; }

  public static class Security {
    private String jwtSecret = "please-change-me-please-change-me-please-change-me-32byte-minimum";
    private long accessTokenTtl = 7200L;
    private long rememberTtl = 1209600L;
    private int maxFailedAttempts = 5;
    private long lockSeconds = 900L;
    private int rateLimitLogin = 10;
    private int rateLimitApi = 120;

    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String v) { this.jwtSecret = v; }
    public long getAccessTokenTtl() { return accessTokenTtl; }
    public void setAccessTokenTtl(long v) { this.accessTokenTtl = v; }
    public long getRememberTtl() { return rememberTtl; }
    public void setRememberTtl(long v) { this.rememberTtl = v; }
    public int getMaxFailedAttempts() { return maxFailedAttempts; }
    public void setMaxFailedAttempts(int v) { this.maxFailedAttempts = v; }
    public long getLockSeconds() { return lockSeconds; }
    public void setLockSeconds(long v) { this.lockSeconds = v; }
    public int getRateLimitLogin() { return rateLimitLogin; }
    public void setRateLimitLogin(int v) { this.rateLimitLogin = v; }
    public int getRateLimitApi() { return rateLimitApi; }
    public void setRateLimitApi(int v) { this.rateLimitApi = v; }
  }

  public static class Admin {
    /** 仅首次启动播种管理员时使用，播种后应从环境变量移除。 */
    private String bootstrapUsername = "";
    private String bootstrapPassword = "";

    public String getBootstrapUsername() { return bootstrapUsername; }
    public void setBootstrapUsername(String v) { this.bootstrapUsername = v; }
    public String getBootstrapPassword() { return bootstrapPassword; }
    public void setBootstrapPassword(String v) { this.bootstrapPassword = v; }
  }

  public static class Cors {
    private String allowedOrigins = "";

    public String getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(String v) { this.allowedOrigins = v; }
  }
}
