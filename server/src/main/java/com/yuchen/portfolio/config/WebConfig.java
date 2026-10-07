package com.yuchen.portfolio.config;

import com.yuchen.portfolio.service.StorageService;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final AppProperties props;
  private final StorageService storage;

  public WebConfig(AppProperties props, StorageService storage) {
    this.props = props;
    this.storage = storage;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    String raw = props.getCors().getAllowedOrigins();
    String[] origins = raw == null || raw.isBlank()
        ? new String[] {}
        : Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);

    registry.addMapping("/api/**")
        .allowedOrigins(origins)
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("Authorization", "Content-Type", "Accept")
        .allowCredentials(true)
        .maxAge(3600);
  }

  /**
   * 素材目录：Nginx 生产环境会直接 serve 这个路径；
   * 本地开发没有 Nginx 时由这里兜底。文件名含随机值，可放心长缓存。
   */
  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/media/**")
        .addResourceLocations("file:" + storage.root().toString().replace("\\", "/") + "/")
        .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS));
  }
}
