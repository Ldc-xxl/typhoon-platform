package com.zfz.typhoon.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户订阅实体（对应表 {@code user_subscriptions}）。
 *
 * <p>用户关注的台风或城市订阅关系与通知偏好设置
 * （对应基础功能第 18 项：消息通知设置）。
 *
 * <p>四个开关分别控制：台风新增、强度突变、路径调整、浏览器推送。
 * 定时同步任务发现数据变化时，按这些偏好决定是否写入 {@link Notification}。
 *
 * @author 追风者项目组
 */
public class UserSubscription implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订阅ID（主键，自增） */
    private Long subscriptionId;

    /** 用户ID */
    private Long userId;

    /** 订阅台风ID（可为空） */
    private Long typhoonId;

    /** 关注城市ID（可为空） */
    private Long cityId;

    /** 台风新增提醒：true=开 */
    private Boolean notifyNewStorm;

    /** 强度突变提醒：true=开 */
    private Boolean notifyStrengthChange;

    /** 路径调整提醒：true=开 */
    private Boolean notifyPathChange;

    /** 浏览器推送开关：true=开 */
    private Boolean pushEnabled;

    /** 创建时间 */
    private LocalDateTime createdAt;

    public UserSubscription() {
    }

    public Long getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(Long subscriptionId) {
        this.subscriptionId = subscriptionId;
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

    public Boolean getNotifyNewStorm() {
        return notifyNewStorm;
    }

    public void setNotifyNewStorm(Boolean notifyNewStorm) {
        this.notifyNewStorm = notifyNewStorm;
    }

    public Boolean getNotifyStrengthChange() {
        return notifyStrengthChange;
    }

    public void setNotifyStrengthChange(Boolean notifyStrengthChange) {
        this.notifyStrengthChange = notifyStrengthChange;
    }

    public Boolean getNotifyPathChange() {
        return notifyPathChange;
    }

    public void setNotifyPathChange(Boolean notifyPathChange) {
        this.notifyPathChange = notifyPathChange;
    }

    public Boolean getPushEnabled() {
        return pushEnabled;
    }

    public void setPushEnabled(Boolean pushEnabled) {
        this.pushEnabled = pushEnabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
