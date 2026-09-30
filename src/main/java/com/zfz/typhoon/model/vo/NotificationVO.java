package com.zfz.typhoon.model.vo;

import com.zfz.typhoon.model.Notification;
import com.zfz.typhoon.util.DateUtil;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通知视图对象（面向前端顶栏通知中心）。
 *
 * <p>字段名对齐前端 {@code ZFZ.mock.notifications}。
 * {@link #level} 决定面板条目左侧的色条：{@code warn} 告警 / {@code info} 提示 / {@code ok} 完成，
 * 由通知类型映射而来，前端不需要写映射逻辑。
 *
 * @author 追风者项目组
 */
public class NotificationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 通知ID */
    private Long id;

    /** 级别：warn / info / ok */
    private String level;

    /** 通知类型：NEW_STORM / STRENGTH_CHANGE / PATH_CHANGE / SYSTEM */
    private String type;

    /** 标题 */
    private String title;

    /** 内容摘要 */
    private String desc;

    /** 相对时间显示，如「08:12」「昨天」 */
    private String time;

    /** 完整时间 */
    private String createdAt;

    /** 点击跳转地址 */
    private String href;

    /** 是否已读 */
    private Boolean read;

    /** 关联台风ID */
    private Long typhoonId;

    /** 关联城市ID */
    private Long cityId;

    public NotificationVO() {
    }

    /**
     * 由实体组装。跳转地址按通知类型推导，前端拿到即可用。
     *
     * @param entity 通知实体
     * @return 视图对象
     */
    public static NotificationVO of(Notification entity) {
        NotificationVO vo = new NotificationVO();
        vo.id = entity.getNotificationId();
        vo.type = entity.getNotifyType();
        vo.title = entity.getTitle();
        vo.desc = entity.getContent();
        vo.read = entity.getRead();
        vo.typhoonId = entity.getTyphoonId();
        vo.cityId = entity.getCityId();
        vo.createdAt = DateUtil.format(entity.getCreatedAt());
        vo.time = shortTime(entity.getCreatedAt());
        vo.level = levelOf(entity.getNotifyType());
        vo.href = hrefOf(entity);
        return vo;
    }

    /**
     * 通知类型 → 面板色条级别。
     *
     * @param type 通知类型
     * @return warn / info / ok
     */
    private static String levelOf(String type) {
        if (Notification.TYPE_STRENGTH_CHANGE.equalsIgnoreCase(type)
                || Notification.TYPE_PATH_CHANGE.equalsIgnoreCase(type)) {
            return "warn";
        }
        if (Notification.TYPE_SYSTEM.equalsIgnoreCase(type)) {
            return "info";
        }
        return "ok";
    }

    /**
     * 通知类型 → 点击跳转地址。
     *
     * @param entity 通知实体
     * @return 页面相对地址
     */
    private static String hrefOf(Notification entity) {
        String type = entity.getNotifyType() == null ? "" : entity.getNotifyType().toUpperCase();
        if ("NEW_STORM".equals(type) || "STRENGTH_CHANGE".equals(type)) {
            return entity.getTyphoonId() == null
                    ? "index.html" : "typhoon-detail.html?id=" + entity.getTyphoonId();
        }
        if ("PATH_CHANGE".equals(type)) {
            return "forecast-disagreement.html";
        }
        return "admin.html";
    }

    /**
     * 相对时间显示：当天显示 HH:mm，昨天显示「昨天」，更早显示 MM-dd。
     *
     * @param time 完整时间
     * @return 简短时间文本
     */
    private static String shortTime(LocalDateTime time) {
        if (time == null) {
            return "";
        }
        LocalDateTime now = LocalDateTime.now();
        if (time.toLocalDate().equals(now.toLocalDate())) {
            return String.format("%02d:%02d", time.getHour(), time.getMinute());
        }
        if (time.toLocalDate().equals(now.toLocalDate().minusDays(1))) {
            return "昨天";
        }
        return String.format("%02d-%02d", time.getMonthValue(), time.getDayOfMonth());
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }

    public Boolean getRead() {
        return read;
    }

    public void setRead(Boolean read) {
        this.read = read;
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
}
