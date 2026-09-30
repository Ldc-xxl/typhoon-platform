package com.zfz.typhoon.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据同步日志实体（对应表 {@code sync_logs}）。
 *
 * <p>记录每次台风数据拉取的时间、条数、状态与异常信息。
 * 项目描述文档"设置完整的数据同步监控与异常反馈机制"的落地：
 * 对数据缺失、接口异常、时间戳错误等问题进行日志记录。
 *
 * @author 追风者项目组
 */
public class SyncLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 同步成功 */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** 同步失败 */
    public static final String STATUS_FAILED = "FAILED";

    /** 日志ID（主键，自增） */
    private Long logId;

    /** 数据源ID */
    private Long sourceId;

    /** 同步时间 */
    private LocalDateTime syncTime;

    /** 同步记录条数 */
    private Integer recordCount;

    /** 同步状态：SUCCESS / FAILED */
    private String syncStatus;

    /** 异常信息 */
    private String errorMessage;

    /** 同步耗时（秒） */
    private Integer durationSeconds;

    /* 以下为联表查询携带，非本表列 */

    /** 数据源名称 */
    private String sourceName;

    /** 数据源代码 */
    private String sourceCode;

    public SyncLog() {
    }

    public Long getLogId() {
        return logId;
    }

    public void setLogId(Long logId) {
        this.logId = logId;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public LocalDateTime getSyncTime() {
        return syncTime;
    }

    public void setSyncTime(LocalDateTime syncTime) {
        this.syncTime = syncTime;
    }

    public Integer getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(Integer recordCount) {
        this.recordCount = recordCount;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }
}
