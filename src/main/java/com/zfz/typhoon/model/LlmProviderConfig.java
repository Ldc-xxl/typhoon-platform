package com.zfz.typhoon.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 大模型厂商接入配置（对应表 {@code llm_provider_config}）。
 *
 * <p><b>密钥处理约定</b>：
 * <ul>
 *   <li>{@link #apiKeyCipher} 存的是 AES-256-GCM 密文（复用 {@code ContactUtil}），
 *       <b>任何情况下都不允许明文落库</b>；</li>
 *   <li>本对象只用于服务端内部，<b>不得直接序列化返回给前端</b>——
 *       对外一律走 {@code ModelConfigVO}，那里只有"是否已配置密钥"的布尔值；</li>
 *   <li>{@link #lastError} 在写入前已剥离密钥，可以直接展示给管理员。</li>
 * </ul>
 *
 * @author 追风者项目组
 */
public class LlmProviderConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 配置主键 */
    private Long configId;

    /** 模型标识，与 AdminService.SUPPORTED_MODELS 的第二列一致，如 doubao-seed-1.6 */
    private String modelKey;

    /** 展示名，如「豆包 Seed-1.6」；真实调用成功后写进 ai_prediction_records.model_name */
    private String modelName;

    /** 厂商协议：doubao / qianfan / openai-compatible；决定默认网关地址 */
    private String provider;

    /** 推理接入点或模型 ID（豆包形如 ep-xxxx 或 doubao-seed-1-6-250615） */
    private String endpointId;

    /** 自定义网关地址；为空时按 provider 取默认域名 */
    private String baseUrl;

    /** API Key 的密文（enc1:...）；为空表示尚未配置 */
    private String apiKeyCipher;

    /** 最近一次连通性测试成功的时间；null 表示从未验证通过 */
    private LocalDateTime verifiedAt;

    /** 最近一次失败原因（已脱敏） */
    private String lastError;

    /** 最近一次成功调用的往返耗时（毫秒） */
    private Integer lastLatencyMs;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * 是否已配置密钥。
     *
     * <p>注意这只是"填过 Key"，不代表验证通过或调用成功 ——
     * 是否需要区分由调用方（{@code ModelConfigVO} 的三态）决定，
     * 不要在这一层把两者混成一个布尔。
     *
     * @return 密文非空返回 true
     */
    public boolean hasKey() {
        return apiKeyCipher != null && !apiKeyCipher.trim().isEmpty();
    }

    public Long getConfigId() {
        return configId;
    }

    public void setConfigId(Long configId) {
        this.configId = configId;
    }

    public String getModelKey() {
        return modelKey;
    }

    public void setModelKey(String modelKey) {
        this.modelKey = modelKey;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getEndpointId() {
        return endpointId;
    }

    public void setEndpointId(String endpointId) {
        this.endpointId = endpointId;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKeyCipher() {
        return apiKeyCipher;
    }

    public void setApiKeyCipher(String apiKeyCipher) {
        this.apiKeyCipher = apiKeyCipher;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Integer getLastLatencyMs() {
        return lastLatencyMs;
    }

    public void setLastLatencyMs(Integer lastLatencyMs) {
        this.lastLatencyMs = lastLatencyMs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
