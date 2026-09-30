package com.zfz.typhoon.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通知消息实体（对应表 {@code notifications}）。
 *
 * <p>来源有两类：
 * <ol>
 *   <li><b>系统推送</b>：台风新增（NEW_STORM）、强度突变（STRENGTH_CHANGE）、
 *       路径调整（PATH_CHANGE）—— 由定时同步任务比对数据后生成，
 *       依据 {@code user_subscriptions} 的订阅偏好决定发给谁；</li>
 *   <li><b>系统消息</b>（SYSTEM）：数据源延迟告警、账号审核、模型待接入等
 *       后台待办类提醒。</li>
 * </ol>
 *
 * <p>对应前端顶栏通知中心面板；{@link #read} 为 true 时不再计入红点。
 *
 * @author 追风者项目组
 */
public class Notification implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 台风新增提醒 */
    public static final String TYPE_NEW_STORM = "NEW_STORM";
    /** 强度突变提醒 */
    public static final String TYPE_STRENGTH_CHANGE = "STRENGTH_CHANGE";
    /** 路径调整提醒 */
    public static final String TYPE_PATH_CHANGE = "PATH_CHANGE";
    /** 系统消息 */
    public static final String TYPE_SYSTEM = "SYSTEM";

    /** 通知ID（主键，自增） */
    private Long notificationId;

    /** 接收用户ID */
    private Long userId;

    /** 关联台风ID（可为空） */
    private Long typhoonId;

    /** 关联城市ID（可为空） */
    private Long cityId;

    /** 通知类型：NEW_STORM / STRENGTH_CHANGE / PATH_CHANGE / SYSTEM */
    private String notifyType;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 是否已读：true=已读 */
    private Boolean read;

    /** 发送时间 */
    private LocalDateTime createdAt;

    public Notification() {
    }

    public Long getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(Long notificationId) {
        this.notificationId = notificationId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getTyphoonId() {
        return typhoonId;
    }

    public void setTyphoonId(Long typhoonId) {
        this.typhoonId = typhoonId;
    }

    public Long getCityId() {
        return cityId;
    }

    public void setCityId(Long cityId) {
        this.cityId = cityId;
    }

    public String getNotifyType() {
        return notifyType;
    }

    public void setNotifyType(String notifyType) {
        this.notifyType = notifyType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Boolean getRead() {
        return read;
    }

    public void setRead(Boolean read) {
        this.read = read;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
