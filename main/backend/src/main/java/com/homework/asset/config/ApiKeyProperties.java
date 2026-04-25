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

  /**
   * 获取认证启用状态。
   *
   * @return true 表示启用认证，false 表示禁用
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * 设置认证启用状态。
   *
   * @param enabled 是否启用认证
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * 获取 API Key 列表。
   *
   * @return API Key 配置列表
   */
  public List<ApiKeyEntry> getApiKeys() {
    return apiKeys;
  }

  /**
   * 设置 API Key 列表。
   *
   * @param apiKeys API Key 配置列表
   */
  public void setApiKeys(List<ApiKeyEntry> apiKeys) {
    this.apiKeys = apiKeys;
  }

  /** 单个 API Key 配置。 */
  public static class ApiKeyEntry {
    private String key;
    private String name;
    private String roles;

    /**
     * 获取 API Key 值。
     *
     * @return API Key 字符串
     */
    public String getKey() {
      return key;
    }

    /**
     * 设置 API Key 值。
     *
     * @param key API Key 字符串
     */
    public void setKey(String key) {
      this.key = key;
    }

    /**
     * 获取用户名称。
     *
     * @return 用户名称
     */
    public String getName() {
      return name;
    }

    /**
     * 设置用户名称。
     *
     * @param name 用户名称
     */
    public void setName(String name) {
      this.name = name;
    }

    /**
     * 获取角色列表字符串。
     *
     * @return 角色列表，逗号分隔（如 "ROLE_USER,ROLE_ADMIN"）
     */
    public String getRoles() {
      return roles;
    }

    /**
     * 设置角色列表字符串。
     *
     * @param roles 角色列表，逗号分隔
     */
    public void setRoles(String roles) {
      this.roles = roles;
    }
  }
}
