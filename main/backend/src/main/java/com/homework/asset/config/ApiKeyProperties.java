package com.homework.asset.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * API Key 安全配置属性。
 * 
 * 配置示例（application.yml）：
 * <pre>
 * app:
 *   security:
 *     enabled: true
 *     api-keys:
 *       - key: "dev-api-key-001"
 *         name: "Developer"
 *         roles: "ROLE_USER"
 *       - key: "admin-api-key-001"
 *         name: "Admin"
 *         roles: "ROLE_ADMIN,ROLE_USER"
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "app.security")
public class ApiKeyProperties {

  /** 是否启用认证 */
  private boolean enabled = true;

  /** API Key 列表 */
  private List<ApiKeyEntry> apiKeys = new ArrayList<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public List<ApiKeyEntry> getApiKeys() {
    return apiKeys;
  }

  public void setApiKeys(List<ApiKeyEntry> apiKeys) {
    this.apiKeys = apiKeys;
  }

  /** 单个 API Key 配置。 */
  public static class ApiKeyEntry {
    private String key;
    private String name;
    private String roles;

    public String getKey() {
      return key;
    }

    public void setKey(String key) {
      this.key = key;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getRoles() {
      return roles;
    }

    public void setRoles(String roles) {
      this.roles = roles;
    }
  }
}
