package com.zfz.typhoon.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 台风主表实体（对应表 {@code typhoons}）。
 *
 * <p>课程要求（阶段二 · ① Model 层）：根据数据库表创建 Java 实体类（POJO）。
 *
 * <p>字段与表列一一对应，类型映射规则：
 * <ul>
 *   <li>{@code bigint unsigned} → {@link Long}</li>
 *   <li>{@code int / tinyint} → {@link Integer}</li>
 *   <li>{@code varchar} → {@link String}</li>
 *   <li>{@code datetime} → {@link LocalDateTime}（由 Jackson 统一序列化为
 *       {@code yyyy-MM-dd HH:mm:ss}）</li>
 * </ul>
 *
 * <p>注意：本实体对应<b>数据库结构</b>，字段名忠于表列名。面向前端的
 * "中文名 / 当前强度 / 中心位置"等组合字段放在
 * {@code com.zfz.typhoon.model.vo.TyphoonVO} 中组装，两者职责分离。
 *
 * @author 追风者项目组
 * @see com.zfz.typhoon.model.vo.TyphoonVO
 */
public class Typhoon implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 台风ID（主键，自增） */
    private Long typhoonId;

    /** 国际编号，如 202615（唯一） */
    private String internationalId;

    /** 中文名称，如 飞燕 */
    private String chineseName;

    /** 英文名称，如 FEIYAN */
    private String englishName;

    /** 所属海域，如 西北太平洋 */
    private String basin;

    /** 生成时间 */
    private LocalDateTime generateTime;

    /** 消亡时间（活跃台风为 null） */
    private LocalDateTime dissipateTime;

    /** 生命周期状态：ACTIVE=活跃，FINISHED=已结束 */
    private String status;

    /** 历史最大风力（级） */
    private Integer maxWindForce;

    /** 历史最大风速（m/s） */
    private Integer maxWindSpeed;

    /** 中心最低气压（hPa） */
    private Integer minPressure;

    /** 历史档案标记：true=已归档，false=未归档 */
    private Boolean historicalArchive;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    public Typhoon() {
    }

    /* ------------------------------------------------------------------ */
    /*  业务便捷方法（不参与持久化）                                          */
    /* ------------------------------------------------------------------ */

    /**
     * 是否为活跃台风。
     *
     * @return 活跃返回 true
     */
    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    /**
     * 生成年份，取自 {@link #generateTime}。
     *
     * @return 年份；生成时间为空时返回 0
     */
    public int getYear() {
        return generateTime == null ? 0 : generateTime.getYear();
    }

    /* ------------------------------------------------------------------ */
    /*  getter / setter                                                     */
    /* ------------------------------------------------------------------ */

    public Long getTyphoonId() {
        return typhoonId;
    }

    public void setTyphoonId(Long typhoonId) {
        this.typhoonId = typhoonId;
    }

    public String getInternationalId() {
        return internationalId;
    }

    public void setInternationalId(String internationalId) {
        this.internationalId = internationalId;
    }

    public String getChineseName() {
        return chineseName;
    }

    public void setChineseName(String chineseName) {
        this.chineseName = chineseName;
    }

    public String getEnglishName() {
        return englishName;
    }

    public void setEnglishName(String englishName) {
        this.englishName = englishName;
    }

    public String getBasin() {
        return basin;
    }

    public void setBasin(String basin) {
        this.basin = basin;
    }

    public LocalDateTime getGenerateTime() {
        return generateTime;
    }

    public void setGenerateTime(LocalDateTime generateTime) {
        this.generateTime = generateTime;
    }

    public LocalDateTime getDissipateTime() {
        return dissipateTime;
    }

    public void setDissipateTime(LocalDateTime dissipateTime) {
        this.dissipateTime = dissipateTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getMaxWindForce() {
        return maxWindForce;
    }

    public void setMaxWindForce(Integer maxWindForce) {
        this.maxWindForce = maxWindForce;
    }

    public Integer getMaxWindSpeed() {
        return maxWindSpeed;
    }

    public void setMaxWindSpeed(Integer maxWindSpeed) {
        this.maxWindSpeed = maxWindSpeed;
    }

    public Integer getMinPressure() {
        return minPressure;
    }

    public void setMinPressure(Integer minPressure) {
        this.minPressure = minPressure;
    }

    public Boolean getHistoricalArchive() {
        return historicalArchive;
    }

    public void setHistoricalArchive(Boolean historicalArchive) {
        this.historicalArchive = historicalArchive;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "Typhoon{" + internationalId + " " + chineseName + " (" + status + ")}";
    }
}
