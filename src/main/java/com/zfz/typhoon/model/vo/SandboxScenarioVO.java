package com.zfz.typhoon.model.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 沙盘情景视图对象（面向前端 P6 情景推演沙盘）。
 *
 * <p>对应项目描述文档「核心特色功能三：台风情景模拟推演沙盘」。
 * 一次响应同时给出<b>输入参数</b>与<b>推演输出</b>，便于前端做多情景并排对比：
 * {@link #params} 是用户调的四项（登陆点偏移 / 移速系数 / 强度增减 / 风圈缩放），
 * {@link #result} 是规则引擎算出的受影响城市与风险等级。
 *
 * <p><b>合规</b>：{@link #note} 必须随结果返回——沙盘是科普假设工具，
 * 不是数值气象模式，推演结果不能用于真实防灾决策。
 *
 * @author 追风者项目组
 */
public class SandboxScenarioVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 固定提示 */
    public static final String NOTE = "假设推演结果，不可作为防灾依据。";

    /** 情景ID */
    private Long id;

    /** 情景名称 */
    private String name;

    /** 情景类型：BASELINE / SCENARIO_A / SCENARIO_B */
    private String type;

    /** 基底台风ID */
    private Long baseTyphoonId;

    /** 基底台风名称 */
    private String baseTyphoonName;

    /** 修改参数 */
    private Map<String, Object> params = new LinkedHashMap<String, Object>();

    /** 推演结果 */
    private Map<String, Object> result = new LinkedHashMap<String, Object>();

    /** 受影响城市明细 */
    private List<CityImpact> cities = new ArrayList<CityImpact>();

    /** 创建时间（yyyy-MM-dd HH:mm） */
    private String createdAt;

    /** 科普提示，如「台风移动越慢，暴雨累积总量越大，内涝风险上升」 */
    private String tip;

    /** 免责提示 */
    private String note = NOTE;

    public SandboxScenarioVO() {
    }

    /**
     * 推演结果中的单个城市条目。
     */
    public static class CityImpact implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 城市ID */
        private Long cityId;

        /** 城市名称 */
        private String city;

        /** 所属省份 */
        private String province;

        /** 距台风中心距离（km） */
        private Double distance;

        /** 预估风力等级（级） */
        private Integer expectLevel;

        /** 风险等级中文 */
        private String riskLevel;

        /** 是否处于风圈内 */
        private Boolean inCircle;

        public CityImpact() {
        }

        public Long getCityId() {
            return cityId;
        }

        public void setCityId(Long cityId) {
            this.cityId = cityId;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getProvince() {
            return province;
        }

        public void setProvince(String province) {
            this.province = province;
        }

        public Double getDistance() {
            return distance;
        }

        public void setDistance(Double distance) {
            this.distance = distance;
        }

        public Integer getExpectLevel() {
            return expectLevel;
        }

        public void setExpectLevel(Integer expectLevel) {
            this.expectLevel = expectLevel;
        }

        public String getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(String riskLevel) {
            this.riskLevel = riskLevel;
        }

        public Boolean getInCircle() {
            return inCircle;
        }

        public void setInCircle(Boolean inCircle) {
            this.inCircle = inCircle;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getBaseTyphoonId() {
        return baseTyphoonId;
    }

    public void setBaseTyphoonId(Long baseTyphoonId) {
        this.baseTyphoonId = baseTyphoonId;
    }

    public String getBaseTyphoonName() {
        return baseTyphoonName;
    }

    public void setBaseTyphoonName(String baseTyphoonName) {
        this.baseTyphoonName = baseTyphoonName;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result;
    }

    public List<CityImpact> getCities() {
        return cities;
    }

    public void setCities(List<CityImpact> cities) {
        this.cities = cities;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getTip() {
        return tip;
    }

    public void setTip(String tip) {
        this.tip = tip;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
