package com.zfz.typhoon.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 沙盘模拟情景实体（对应表 {@code sandbox_scenarios}）。
 *
 * <p>对应项目描述文档「核心特色功能三：台风情景模拟推演沙盘」。
 * 用户以某个真实台风为基底，微调四类关键参数后保存为情景方案：
 * 登陆点偏移、移动速度系数、峰值强度增减、风圈缩放。
 * 参数落在 {@link #paramsJson}，规则推演出的受影响城市与风险快照落在 {@link #resultJson}。
 *
 * <p>{@link #scenarioType} 取 BASELINE（基准情景，即官方预报）、
 * SCENARIO_A（如偏西登陆）、SCENARIO_B（如快速转向出海），支持多情景并排对比。
 *
 * <p><b>合规提示</b>：本功能属科普假设工具，并非专业气象数值模式，
 * 所有推演结果不能用于真实防灾决策。
 *
 * @author 追风者项目组
 */
public class SandboxScenario implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 基准情景标识 */
    public static final String TYPE_BASELINE = "BASELINE";
    /** 情景 A 标识 */
    public static final String TYPE_A = "SCENARIO_A";
    /** 情景 B 标识 */
    public static final String TYPE_B = "SCENARIO_B";

    /** 情景ID（主键，自增） */
    private Long scenarioId;

    /** 创建用户ID */
    private Long userId;

    /** 基底台风ID */
    private Long baseTyphoonId;

    /** 情景名称 */
    private String scenarioName;

    /** 情景类型：BASELINE / SCENARIO_A / SCENARIO_B */
    private String scenarioType;

    /** 修改参数（登陆点偏移、移速、强度、风圈） */
    private JsonNode paramsJson;

    /** 推演结果快照（受影响城市、风险等级） */
    private JsonNode resultJson;

    /** 是否公开分享：true=是 */
    private Boolean publicShared;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    public SandboxScenario() {
    }

    public Long getScenarioId() {
        return scenarioId;
    }

    public void setScenarioId(Long scenarioId) {
        this.scenarioId = scenarioId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getBaseTyphoonId() {
        return baseTyphoonId;
    }

    public void setBaseTyphoonId(Long baseTyphoonId) {
        this.baseTyphoonId = baseTyphoonId;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public void setScenarioName(String scenarioName) {
        this.scenarioName = scenarioName;
    }

    public String getScenarioType() {
        return scenarioType;
    }

    public void setScenarioType(String scenarioType) {
        this.scenarioType = scenarioType;
    }

    public JsonNode getParamsJson() {
        return paramsJson;
    }

    public void setParamsJson(JsonNode paramsJson) {
        this.paramsJson = paramsJson;
    }

    public JsonNode getResultJson() {
        return resultJson;
    }

    public void setResultJson(JsonNode resultJson) {
        this.resultJson = resultJson;
    }

    public Boolean getPublicShared() {
        return publicShared;
    }

    public void setPublicShared(Boolean publicShared) {
        this.publicShared = publicShared;
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
