package com.zfz.typhoon.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 相似案例匹配记录实体（对应表 {@code similar_case_matches}）。
 *
 * <p>对应项目描述文档「核心特色功能二：历史台风-城市灾害归因匹配系统」。
 *
 * <p>从<b>五个特征维度</b>对"当前台风"与"历史台风"做相似度打分（均归一化到 0—1）：
 * <ol>
 *   <li>路径形态 —— {@link #pathSimilarity}</li>
 *   <li>峰值强度 —— {@link #peakIntensitySimilarity}</li>
 *   <li>登陆季节 —— {@link #seasonSimilarity}</li>
 *   <li>移速 —— {@link #moveSpeedSimilarity}</li>
 *   <li>风圈规模 —— {@link #windCircleSimilarity}</li>
 * </ol>
 * 加权后得综合相似度 {@link #similarityScore}，取值 Top-N 作为匹配结果，
 * 再把历史台风当年的城市灾情带出，形成"当前台风 → 历史相似台风 → 当年灾情"的归因链路。
 *
 * <p>合规提示：历史相似仅作类比，不代表结果会重演。
 *
 * @author 追风者项目组
 */
public class SimilarCaseMatch implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 匹配记录ID（主键，自增） */
    private Long matchId;

    /** 当前台风ID */
    private Long currentTyphoonId;

    /** 历史相似台风ID */
    private Long historyTyphoonId;

    /** 综合相似度（0—1） */
    private Double similarityScore;

    /** 路径形态相似度（0—1） */
    private Double pathSimilarity;

    /** 峰值强度相似度（0—1） */
    private Double peakIntensitySimilarity;

    /** 登陆季节相似度（0—1） */
    private Double seasonSimilarity;

    /** 移速相似度（0—1） */
    private Double moveSpeedSimilarity;

    /** 风圈规模相似度（0—1） */
    private Double windCircleSimilarity;

    /** 匹配结果与提示（JSON） */
    private JsonNode matchResult;

    /** 创建时间 */
    private LocalDateTime createdAt;

    public SimilarCaseMatch() {
    }

    public Long getMatchId() {
        return matchId;
    }

    public void setMatchId(Long matchId) {
        this.matchId = matchId;
    }

    public Long getCurrentTyphoonId() {
        return currentTyphoonId;
    }

    public void setCurrentTyphoonId(Long currentTyphoonId) {
        this.currentTyphoonId = currentTyphoonId;
    }

    public Long getHistoryTyphoonId() {
        return historyTyphoonId;
    }

    public void setHistoryTyphoonId(Long historyTyphoonId) {
        this.historyTyphoonId = historyTyphoonId;
    }

    public Double getSimilarityScore() {
        return similarityScore;
    }

    public void setSimilarityScore(Double similarityScore) {
        this.similarityScore = similarityScore;
    }

    public Double getPathSimilarity() {
        return pathSimilarity;
    }

    public void setPathSimilarity(Double pathSimilarity) {
        this.pathSimilarity = pathSimilarity;
    }

    public Double getPeakIntensitySimilarity() {
        return peakIntensitySimilarity;
    }

    public void setPeakIntensitySimilarity(Double peakIntensitySimilarity) {
        this.peakIntensitySimilarity = peakIntensitySimilarity;
    }

    public Double getSeasonSimilarity() {
        return seasonSimilarity;
    }

    public void setSeasonSimilarity(Double seasonSimilarity) {
        this.seasonSimilarity = seasonSimilarity;
    }

    public Double getMoveSpeedSimilarity() {
        return moveSpeedSimilarity;
    }

    public void setMoveSpeedSimilarity(Double moveSpeedSimilarity) {
        this.moveSpeedSimilarity = moveSpeedSimilarity;
    }

    public Double getWindCircleSimilarity() {
        return windCircleSimilarity;
    }

    public void setWindCircleSimilarity(Double windCircleSimilarity) {
        this.windCircleSimilarity = windCircleSimilarity;
    }

    public JsonNode getMatchResult() {
        return matchResult;
    }

    public void setMatchResult(JsonNode matchResult) {
        this.matchResult = matchResult;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
