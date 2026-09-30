package com.zfz.typhoon.model.vo;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 历史相似案例视图对象（面向前端 P5 相似灾害案例页）。
 *
 * <p>对应项目描述文档「核心特色功能二：历史台风-城市灾害归因匹配系统」。
 * 它回答的不是"过去台风怎么走"，而是「<b>现在这个台风和历史上哪一个相似，
 * 当年那个城市发生了什么</b>」。
 *
 * <p>字段名对齐接口文档 3.10 节示例：
 * {@link #matchDims} 给出五维相似度，{@link #disaster} 带出当年灾情。
 *
 * @author 追风者项目组
 */
public class SimilarCaseVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 固定提示：历史相似仅作类比 */
    public static final String NOTE =
            "历史相似仅做类比参考，不代表本次台风一定会发生同等灾害。";

    /** 历史台风ID */
    private Long typhoonId;

    /** 历史台风中文名 */
    private String nameCn;

    /** 历史台风英文名 */
    private String nameEn;

    /** 年份 */
    private Integer year;

    /** 综合相似度（0—1） */
    private Double score;

    /** 五维相似度明细：path / intensity / season / speed / windCircle */
    private Map<String, Double> matchDims = new LinkedHashMap<String, Double>();

    /** 当年灾情 */
    private DisasterBrief disaster;

    /** 与历史案例的差异提示，如「本次比历史案例更强、移速更慢，致灾风险会更高」 */
    private String diffHint;

    /** 差异点列表，供前端逐条高亮 */
    private java.util.List<String> differences = new java.util.ArrayList<String>();

    /** 类比提示 */
    private String note = NOTE;

    public SimilarCaseVO() {
    }

    /**
     * 历史灾情摘要。
     */
    public static class DisasterBrief implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 影响省份 */
        private String provinces;

        /** 主要影响城市 */
        private String city;

        /** 降雨量级，如「累计 320mm」 */
        private String rainfall;

        /** 积涝情况 */
        private String flood;

        /** 风暴潮情况 */
        private String stormSurge;

        /** 典型受灾场景 */
        private String scene;

        /** 直接经济损失（亿元） */
        private Double loss;

        /** 受灾人口（万人） */
        private Integer affectedPopulation;

        /** 伤亡人数 */
        private Integer casualties;

        /** 灾情综述 */
        private String summary;

        /** 报告来源 */
        private String source;

        public DisasterBrief() {
        }

        public String getProvinces() {
            return provinces;
        }

        public void setProvinces(String provinces) {
            this.provinces = provinces;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getRainfall() {
            return rainfall;
        }

        public void setRainfall(String rainfall) {
            this.rainfall = rainfall;
        }

        public String getFlood() {
            return flood;
        }

        public void setFlood(String flood) {
            this.flood = flood;
        }

        public String getStormSurge() {
            return stormSurge;
        }

        public void setStormSurge(String stormSurge) {
            this.stormSurge = stormSurge;
        }

        public String getScene() {
            return scene;
        }

        public void setScene(String scene) {
            this.scene = scene;
        }

        public Double getLoss() {
            return loss;
        }

        public void setLoss(Double loss) {
            this.loss = loss;
        }

        public Integer getAffectedPopulation() {
            return affectedPopulation;
        }

        public void setAffectedPopulation(Integer affectedPopulation) {
            this.affectedPopulation = affectedPopulation;
        }

        public Integer getCasualties() {
            return casualties;
        }

        public void setCasualties(Integer casualties) {
            this.casualties = casualties;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }
    }

    public Long getTyphoonId() {
        return typhoonId;
    }

    public void setTyphoonId(Long typhoonId) {
        this.typhoonId = typhoonId;
    }

    public String getNameCn() {
        return nameCn;
    }

    public void setNameCn(String nameCn) {
        this.nameCn = nameCn;
    }

    public String getNameEn() {
        return nameEn;
    }

    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public Map<String, Double> getMatchDims() {
        return matchDims;
    }

    public void setMatchDims(Map<String, Double> matchDims) {
        this.matchDims = matchDims;
    }

    public DisasterBrief getDisaster() {
        return disaster;
    }

    public void setDisaster(DisasterBrief disaster) {
        this.disaster = disaster;
    }

    public String getDiffHint() {
        return diffHint;
    }

    public void setDiffHint(String diffHint) {
        this.diffHint = diffHint;
    }

    public java.util.List<String> getDifferences() {
        return differences;
    }

    public void setDifferences(java.util.List<String> differences) {
        this.differences = differences;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
