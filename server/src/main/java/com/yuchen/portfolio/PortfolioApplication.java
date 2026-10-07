package com.yuchen.portfolio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.yuchen.portfolio.config.AppProperties;

/**
 * 个人作品集后端。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>内容为不透明 JSON</b>：site_content.payload 整体存整份页面结构。
 *       新增模块类型不需要改 Java 代码，也不需要改表。</li>
 *   <li><b>素材在仓库之外</b>：上传文件落在 APP_UPLOAD_DIR 指向的持久卷，
 *       GitHub 仓库里只有代码，重新发布版本不会丢内容。</li>
 * </ul>
 */
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class PortfolioApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortfolioApplication.class, args);
    }
}
