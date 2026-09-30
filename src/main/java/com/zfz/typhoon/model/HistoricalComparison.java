package com.zfz.typhoon.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 历史对比任务实体（对应表 {@code historical_comparisons}）。
 *
 * <p>保存用户创建的多台风路径对比配置与结果（对应基础功能：
 * 生命周期回放与同海域案例对比）。
 *
 * @author 追风者项目组
 */
public class HistoricalComparison implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 对比任务ID（主键，自增） */
    private Long comparisonId;

    /** 创建用户ID */
    private Long userId;

    /** 对比名称 */
    private String comparisonName;

    /** 主对比台风ID */
    private Long primaryTyphoonId;

    /** 对比台风ID数组 JSON，如 [8,9] */
    private JsonNode compareTyphoonIds;

    /** 对比结果摘要 JSON */
    private JsonNode resultSummary;

    /** 创建时间 */
    private LocalDateTime createdAt;

    public HistoricalComparison() {
    }

    public Long getComparisonId() {
        return comparisonId;
    }

    public void setComparisonId(Long comparisonId) {
        this.comparisonId = comparisonId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getComparisonName() {
        return comparisonName;
    }

    public void setComparisonName(String comparisonName) {
        this.comparisonName = comparisonName;
    }

    public Long getPrimaryTyphoonId() {
        return primaryTyphoonId;
    }

    public void setPrimaryTyphoonId(Long primaryTyphoonId) {
        this.primaryTyphoonId = primaryTyphoonId;
    }

    public JsonNode getCompareTyphoonIds() {
        return compareTyphoonIds;
    }

    public void setCompareTyphoonIds(JsonNode compareTyphoonIds) {
        this.compareTyphoonIds = compareTyphoonIds;
    }

    public JsonNode getResultSummary() {
        return resultSummary;
    }

    public void setResultSummary(JsonNode resultSummary) {
        this.resultSummary = resultSummary;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
