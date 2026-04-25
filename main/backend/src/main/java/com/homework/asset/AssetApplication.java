package com.homework.asset;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 视频素材查询服务主程序入口。
 * 
 * 功能：
 * - 启动 Spring Boot 应用
 * - 扫描 MyBatis Mapper
 * - 支持 --ingest 参数进行 ETL 导入（可选，不影响 API 服务）
 */
@SpringBootApplication
@MapperScan("com.homework.asset.mapper")
public class AssetApplication {

  public static void main(String[] args) {
    SpringApplication.run(AssetApplication.class, args);
  }
}
