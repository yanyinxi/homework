package com.homework.asset.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI 配置。
 *
 * <p>访问地址：http://localhost:8080/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

  private static final String SECURITY_SCHEME_NAME = "ApiKey";

  @Bean
  public OpenAPI assetServiceOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("视频素材查询服务 API")
                .description(
                    "基于三份异构数据集统一建模的只读素材查询接口。"
                        + "支持多字段过滤（bracket-style DSL）、多字段排序、分页和稀疏字段集。")
                .version("1.0.0")
                .contact(new Contact().name("Java 架构师")))
        .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
        .schemaRequirement(
            SECURITY_SCHEME_NAME,
            new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name("X-API-Key")
                .description("API Key：dev-api-key-001（USER）或 admin-api-key-001（ADMIN）"));
  }
}
