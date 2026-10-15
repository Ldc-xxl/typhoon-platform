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

    /**
     * 联系方式（选填）。
     *
     * <p>入库时是 {@code enc1:...} 的 AES-GCM 密文；Service 层读取后会
     * 解密成原文再放入本字段，因此<b>对 API 调用方而言这里是可直接回访的原文</b>。
     * 界面展示请改用 {@link #getContactMasked()}。
     */
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

    /**
     * 联系方式的<b>脱敏展示串</b>（派生字段，不落库）。
     *
     * <p>由 {@link #contactInfo} 实时计算（{@code 138****8888} 之类），
     * 专供界面渲染。之所以做成派生 getter 而不是单独存一列：
     * 脱敏规则变了不必刷数据，也不会出现"原文与脱敏串不一致"的脏状态。
     *
     * <p>先 {@code decrypt} 再 {@code mask}：本字段在两条路径上分别可能拿到
     * 「Service 解密后的原文」（读库回来）或「刚加密、还没落库的密文」
     * （{@code submit} 之后调用方手里的对象）。两步都幂等——
     * 对明文 decrypt 原样返回，因此两种输入都能得到正确的脱敏串。
     *
     * <p>Jackson 会把它一并序列化，因此前端同时拿到
     * {@code contactInfo}（原文，回访用）与 {@code contactMasked}（展示用）。
     *
     * @return 脱敏串；无联系方式时返回 null
     */
    public String getContactMasked() {
        return com.zfz.typhoon.util.ContactUtil.mask(
                com.zfz.typhoon.util.ContactUtil.decrypt(contactInfo));
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
