package com.homework.asset.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

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
                .contact(new Contact().name("Java 架构师作业")));
  }
}
