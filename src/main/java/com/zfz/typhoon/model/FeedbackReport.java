package com.zfz.typhoon.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户反馈实体（对应表 {@code feedback_reports}）。
 *
 * <p>用户提交的数据异常、功能建议与问题反馈。管理员在后台认领并处理
 * （{@link #status} 由 PENDING 变为 PROCESSED，同时写入
 * {@link #handlerId} 与 {@link #handleTime}）。
 *
 * @author 追风者项目组
 */
public class FeedbackReport implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 数据异常 */
    public static final String TYPE_DATA_ISSUE = "DATA_ISSUE";
    /** 功能建议 */
    public static final String TYPE_FEATURE = "FEATURE_SUGGESTION";
    /** 问题反馈 */
    public static final String TYPE_PROBLEM = "PROBLEM_REPORT";

    /** 待处理 */
    public static final String STATUS_PENDING = "PENDING";
    /** 已处理 */
    public static final String STATUS_PROCESSED = "PROCESSED";

    /** 反馈ID（主键，自增） */
    private Long feedbackId;

    /** 反馈用户ID（可为空，允许匿名） */
    private Long userId;

    /** 反馈类型：DATA_ISSUE / FEATURE_SUGGESTION / PROBLEM_REPORT */
    private String feedbackType;

    /** 反馈内容 */
    private String content;

    /** 联系方式（选填） */
    private String contactInfo;

    /** 处理状态：PENDING / PROCESSED */
    private String status;

    /** 处理人ID */
    private Long handlerId;

    /** 处理时间 */
    private LocalDateTime handleTime;

    /** 提交时间 */
    private LocalDateTime createdAt;

    /* 以下为联表查询携带，非本表列 */

    /** 提交人账号 */
    private String username;

    public FeedbackReport() {
    }

    public Long getFeedbackId() {
        return feedbackId;
    }

    public void setFeedbackId(Long feedbackId) {
        this.feedbackId = feedbackId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getFeedbackType() {
        return feedbackType;
    }

    public void setFeedbackType(String feedbackType) {
        this.feedbackType = feedbackType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContactInfo() {
        return contactInfo;
    }

    public void setContactInfo(String contactInfo) {
        this.contactInfo = contactInfo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getHandlerId() {
        return handlerId;
    }

    public void setHandlerId(Long handlerId) {
        this.handlerId = handlerId;
    }

    public LocalDateTime getHandleTime() {
        return handleTime;
    }

    public void setHandleTime(LocalDateTime handleTime) {
        this.handleTime = handleTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
