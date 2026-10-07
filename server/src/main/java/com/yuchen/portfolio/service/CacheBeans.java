package com.yuchen.portfolio.service;

import com.yuchen.portfolio.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class CacheBeans {

  /** Redis 开启：注入真实模板。 */
  @Bean
  @ConditionalOnProperty(name = "app.cache-enabled", havingValue = "true", matchIfMissing = true)
  @Primary
  public CacheService redisBackedCache(AppProperties props, StringRedisTemplate template) {
    return new CacheService(props, template);
  }

  /** Redis 关闭：纯内存版本，接口完全一致。 */
  @Bean
  @ConditionalOnProperty(name = "app.cache-enabled", havingValue = "false")
  @Primary
  public CacheService localOnlyCache(AppProperties props) {
    return new CacheService(props);
  }
}
