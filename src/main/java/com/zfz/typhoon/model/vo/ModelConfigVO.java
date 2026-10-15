package com.zfz.typhoon.model.vo;

import java.io.Serializable;

/**
 * 大模型接入配置视图对象（面向前端 P8 后台"大模型接入配置"表）。
 *
 * <p>字段名对齐前端 {@code ZFZ.mock.modelConfig}：
 * 「模型名　版本　状态　调用次数　平均耗时　平均置信度　操作」。
 *
 * <p><b>安全（课程要求：密钥脱敏）</b>：本对象<b>不包含 API Key</b>。
 * 后台只展示"是否已配置密钥"，绝不回传密钥原文；
 * 密钥由服务端配置管理，前端改配置时只提交新密钥而不读回旧密钥。
 *
 * @author 追风者项目组
 */
public class ModelConfigVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 配置ID */
    private Long id;

    /** 模型名称，如 DeepSeek-V3 */
    private String name;

    /** 模型标识（前端提交触发测算时使用），如 deepseek-v3 */
    private String modelKey;

    /** 提示词版本 */
    private String version;

    /** 状态：已接入 / 待接入 */
    private String status;

    /** 累计调用次数（带千分位） */
    private String calls;

    /** 平均响应耗时，如 1.2 s */
    private String cost;

    /** 平均置信度（0—100，带 % 号的字符串），如 "78%" */
    private String confidence;

    /** 建议操作文案：配置 / 接入 */
    private String action;

    /** 是否已配置密钥（不返回值，只回是否） */
    private Boolean keyConfigured;

    /** 是否启用 */
    private Boolean enabled;

    /** 最近一次测算时间 */
    private String lastUsed;

    public ModelConfigVO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModelKey() {
        return modelKey;
    }

    public void setModelKey(String modelKey) {
        this.modelKey = modelKey;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCalls() {
        return calls;
    }

    public void setCalls(String calls) {
        this.calls = calls;
    }

    public String getCost() {
        return cost;
    }

    public void setCost(String cost) {
        this.cost = cost;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Boolean getKeyConfigured() {
        return keyConfigured;
    }

    public void setKeyConfigured(Boolean keyConfigured) {
        this.keyConfigured = keyConfigured;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getLastUsed() {
        return lastUsed;
    }

    public void setLastUsed(String lastUsed) {
        this.lastUsed = lastUsed;
    }
}
