package com.zfz.typhoon.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 大模型智能测算记录实体（对应表 {@code ai_prediction_records}）。
 *
 * <p>对应项目描述文档「核心特色功能五：大模型辅助的活跃台风路径智能测算引擎」。
 * 每调用一次大模型就落一条记录，台风结束后可与实况路径对比，评估各模型测算精度
 * （{@link #status} 由 PENDING 演进为 COMPARED），支撑提示词与测算策略迭代。
 *
 * <p>{@link #predictedPath} 是 MySQL {@code json} 列，此处用 Jackson 的
 * {@link JsonNode} 承载，可直接参与序列化输出给前端，无需二次转换。
 *
 * @author 追风者项目组
 */
public class AiPredictionRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 记录ID（主键，自增） */
    private Long recordId;

    /** 关联台风ID */
    private Long typhoonId;

    /** 大模型名称（DeepSeek-V3 / 通义千问-max / 豆包Seed-1.6 / 文心一言4.0） */
    private String modelName;

    /** 提示词模板版本 */
    private String promptVersion;

    /** 测算时间 */
    private LocalDateTime predictionTime;

    /** AI 预测路径点 JSON（时间 / 经纬度 / 强度） */
    private JsonNode predictedPath;

    /** AI 预测登陆纬度 */
    private Double predictedLandfallLat;

    /** AI 预测登陆经度 */
    private Double predictedLandfallLon;

    /** 模型置信度（0—100） */
    private Double confidenceScore;

    /** 与机构预报平均偏差（km） */
    private Double deviationVsAgencies;

    /**
     * 单次调用往返耗时（毫秒）。
     *
     * <p>只有<b>真实调用外部大模型</b>的记录才有值；本地多机构融合算法
     * 不产生网络往返，留 null（前端展示为 "—"），不要拿计算耗时凑数。
     */
    private Integer latencyMs;

    /** AI 生成的解读文案 */
    private String summaryText;

    /** 状态：PENDING=待验证 / VALIDATED=已验证 / COMPARED=已与实况对比 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    public AiPredictionRecord() {
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Long getTyphoonId() {
        return typhoonId;
    }

    public void setTyphoonId(Long typhoonId) {
        this.typhoonId = typhoonId;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public LocalDateTime getPredictionTime() {
        return predictionTime;
    }

    public void setPredictionTime(LocalDateTime predictionTime) {
        this.predictionTime = predictionTime;
    }

    public JsonNode getPredictedPath() {
        return predictedPath;
    }

    public void setPredictedPath(JsonNode predictedPath) {
        this.predictedPath = predictedPath;
    }

    public Double getPredictedLandfallLat() {
        return predictedLandfallLat;
    }

    public void setPredictedLandfallLat(Double predictedLandfallLat) {
        this.predictedLandfallLat = predictedLandfallLat;
    }

    public Double getPredictedLandfallLon() {
        return predictedLandfallLon;
    }

    public void setPredictedLandfallLon(Double predictedLandfallLon) {
        this.predictedLandfallLon = predictedLandfallLon;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public Double getDeviationVsAgencies() {
        return deviationVsAgencies;
    }

    public void setDeviationVsAgencies(Double deviationVsAgencies) {
        this.deviationVsAgencies = deviationVsAgencies;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public void setSummaryText(String summaryText) {
        this.summaryText = summaryText;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
