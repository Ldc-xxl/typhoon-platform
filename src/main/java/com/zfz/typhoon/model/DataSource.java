package com.zfz.typhoon.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据源实体（对应表 {@code data_sources}）。
 *
 * <p>维护各气象数据接口的地址、更新频率与接入状态。
 * 平台定时同步任务按 {@link #updateFrequency}（分钟）拉取，
 * 结果写入 {@link SyncLog}。对应后台"数据源接入监控"面板。
 *
 * @author 追风者项目组
 */
public class DataSource implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 数据源ID（主键，自增） */
    private Long sourceId;

    /** 数据源代码（唯一），如 CMA_API */
    private String sourceCode;

    /** 数据源名称 */
    private String sourceName;

    /** 接口地址 */
    private String apiUrl;

    /** 更新频率（分钟） */
    private Integer updateFrequency;

    /** 接入状态：true=启用 */
    private Boolean enabled;

    /** 最后同步时间 */
    private LocalDateTime lastSyncTime;

    /** 数据源说明 */
    private String description;

    /* 以下为统计查询携带，非本表列 */

    /** 近 24 小时同步成功次数 */
    private Integer successCount;

    /** 近 24 小时同步失败次数 */
    private Integer failCount;

    /** 平均耗时（秒） */
    private Double avgDuration;

    public DataSource() {
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public Integer getUpdateFrequency() {
        return updateFrequency;
    }

    public void setUpdateFrequency(Integer updateFrequency) {
        this.updateFrequency = updateFrequency;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getLastSyncTime() {
        return lastSyncTime;
    }

    public void setLastSyncTime(LocalDateTime lastSyncTime) {
        this.lastSyncTime = lastSyncTime;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(Integer successCount) {
        this.successCount = successCount;
    }

    public Integer getFailCount() {
        return failCount;
    }

    public void setFailCount(Integer failCount) {
        this.failCount = failCount;
    }

    public Double getAvgDuration() {
        return avgDuration;
    }

    public void setAvgDuration(Double avgDuration) {
        this.avgDuration = avgDuration;
    }
}
