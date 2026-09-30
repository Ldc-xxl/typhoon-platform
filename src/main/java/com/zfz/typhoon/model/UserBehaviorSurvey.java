package com.zfz.typhoon.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户防灾行为调研实体（对应表 {@code user_behavior_surveys}）。
 *
 * <p>对应项目描述文档「核心特色功能四：面向公众的台风防灾认知行为反馈采集模块」。
 * 一条记录 = 一份问卷原始填写。
 *
 * <p><b>隐私要求（必须在代码与页面上同时体现）</b>：
 * <ul>
 *   <li>{@link #contactInfo} 为<b>选填</b>，仅用于问卷回访与科普推送，不对外公开；</li>
 *   <li>本表数据<b>绝不直接返回前端</b>，对外只暴露按城市聚合后的
 *       {@link SurveyAggResult}（群体统计，不含任何个人信息）；</li>
 *   <li>入库前联系方式经 {@code PasswordUtil#maskContact} 脱敏。</li>
 * </ul>
 *
 * @author 追风者项目组
 * @see SurveyAggResult
 */
public class UserBehaviorSurvey implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 灾害类型：大风 */
    public static final String CONCERN_WIND = "WIND";
    /** 灾害类型：暴雨 */
    public static final String CONCERN_RAIN = "RAIN";
    /** 灾害类型：风暴潮 */
    public static final String CONCERN_STORM_SURGE = "STORM_SURGE";
    /** 灾害类型：其他 */
    public static final String CONCERN_OTHER = "OTHER";

    /** 问卷记录ID（主键，自增） */
    private Long surveyId;

    /** 关联台风ID */
    private Long typhoonId;

    /** 所在城市ID */
    private Long cityId;

    /** 问卷版本 */
    private String questionSetVersion;

    /** 是否储备物资：true=是 */
    private Boolean preparedSupplies;

    /** 是否关注撤离信息：true=是 */
    private Boolean payAttentionEvacuation;

    /** 最担心灾害类型：WIND / RAIN / STORM_SURGE / OTHER */
    private String worriedDisasterType;

    /** 对预报可信度主观判断（1—5） */
    private Integer trustLevel;

    /** 提交时间 */
    private LocalDateTime submitTime;

    /** 联系方式（选填，脱敏后存储） */
    private String contactInfo;

    public UserBehaviorSurvey() {
    }

    public Long getSurveyId() {
        return surveyId;
    }

    public void setSurveyId(Long surveyId) {
        this.surveyId = surveyId;
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

    public String getQuestionSetVersion() {
        return questionSetVersion;
    }

    public void setQuestionSetVersion(String questionSetVersion) {
        this.questionSetVersion = questionSetVersion;
    }

    public Boolean getPreparedSupplies() {
        return preparedSupplies;
    }

    public void setPreparedSupplies(Boolean preparedSupplies) {
        this.preparedSupplies = preparedSupplies;
    }

    public Boolean getPayAttentionEvacuation() {
        return payAttentionEvacuation;
    }

    public void setPayAttentionEvacuation(Boolean payAttentionEvacuation) {
        this.payAttentionEvacuation = payAttentionEvacuation;
    }

    public String getWorriedDisasterType() {
        return worriedDisasterType;
    }

    public void setWorriedDisasterType(String worriedDisasterType) {
        this.worriedDisasterType = worriedDisasterType;
    }

    public Integer getTrustLevel() {
        return trustLevel;
    }

    public void setTrustLevel(Integer trustLevel) {
        this.trustLevel = trustLevel;
    }

    public LocalDateTime getSubmitTime() {
        return submitTime;
    }

    public void setSubmitTime(LocalDateTime submitTime) {
        this.submitTime = submitTime;
    }

    public String getContactInfo() {
        return contactInfo;
    }

    public void setContactInfo(String contactInfo) {
        this.contactInfo = contactInfo;
    }
}
