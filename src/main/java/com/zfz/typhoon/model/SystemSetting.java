package com.zfz.typhoon.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统配置实体（对应表 {@code system_settings}）。
 *
 * <p>全局参数配置：数据刷新间隔、默认底图、风圈透明度、高不确定性阈值、
 * 沙盘参数上下限、问卷采集开关等。
 *
 * <p>{@link #settingType} 决定前端如何渲染与校验取值：
 * INT / DECIMAL / STRING / JSON。
 *
 * @author 追风者项目组
 */
public class SystemSetting implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 配置ID（主键，自增） */
    private Long settingId;

    /** 配置键（唯一） */
    private String settingKey;

    /** 配置值 */
    private String settingValue;

    /** 配置值类型：INT / DECIMAL / STRING / JSON */
    private String settingType;

    /** 配置说明 */
    private String description;

    /** 最后修改人 */
    private Long updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    public SystemSetting() {
    }

    /**
     * 把配置值按声明类型解析为数値。
     *
     * @param defaultValue 解析失败时的默认值
     * @return 数值型配置值
     */
    public double asDouble(double defaultValue) {
        try {
            return Double.parseDouble(settingValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public Long getSettingId() {
        return settingId;
    }

    public void setSettingId(Long settingId) {
        this.settingId = settingId;
    }

    public String getSettingKey() {
        return settingKey;
    }

    public void setSettingKey(String settingKey) {
        this.settingKey = settingKey;
    }

    public String getSettingValue() {
        return settingValue;
    }

    public void setSettingValue(String settingValue) {
        this.settingValue = settingValue;
    }

    public String getSettingType() {
        return settingType;
    }

    public void setSettingType(String settingType) {
        this.settingType = settingType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
