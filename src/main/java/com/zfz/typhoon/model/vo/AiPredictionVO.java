package com.zfz.typhoon.model.vo;

import com.zfz.typhoon.model.AiPredictionRecord;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 大模型路径智能测算视图对象（面向前端 P1 测算面板 / P2 详情 / P3 分歧对照）。
 *
 * <p>对应项目描述文档「核心特色功能五」。一次响应包含三个层次的信息：
 * <ol>
 *   <li><b>多模型明细</b> {@link #models} —— 各模型名称、置信度与状态，
 *       供 P1 的 AI 面板按模型铺成小卡片；</li>
 *   <li><b>融合结果</b> {@link #points} / {@link #landingRange} —— 多模型结果融合后的
 *       平台自有路径，与机构预报并排画在地图上；</li>
 *   <li><b>解读文案</b> {@link #summary} / {@link #intensityTrend} —— 大模型生成的
 *       通俗解读，辅助普通用户理解复杂气象信息。</li>
 * </ol>
 *
 * <p><b>合规要求（项目描述文档 · 七）</b>：{@link #disclaimer} 必须随结果一起返回，
 * 页面须显著标注「AI 测算仅供科普参考，不替代官方预报」。
 *
 * @author 追风者项目组
 */
public class AiPredictionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 固定免责声明，前端必须随测算结果一起展示 */
    public static final String DISCLAIMER =
            "AI 测算结果由算法基于公开数据推理生成，仅供科普与学习参考，不代表任何官方预报结论。";

    /** 台风ID */
    private Long typhoonId;

    /** 提示词模板版本 */
    private String version;

    /** 多模型明细 */
    private List<ModelItem> models = new ArrayList<ModelItem>();

    /** 模型一致性（0—100） */
    private Integer consistency;

    /** 一致性文字评价，如「较好」 */
    private String consistencyText;

    /** 综合解读文案 */
    private String summary;

    /** 融合后的路径点，每个元素为 [经度, 纬度] */
    private List<List<Double>> points = new ArrayList<List<Double>>();

    /** 登陆点区间描述，如「福建泉州 ~ 厦门沿海」 */
    private String landingRange;

    /** 强度演变描述 */
    private String intensityTrend;

    /** 与机构预报平均偏差（km） */
    private Double deviation;

    /**
     * 综合置信度（0—1）。
     *
     * <p>注意与 {@link ModelItem#getConfidence()} 的量纲区别：后者是<b>单个模型</b>
     * 的置信度，取数据库 {@code ai_prediction_records.confidence_score} 列的原值
     * （该列注释即「模型置信度（0-100）」，前端 mock 也用 80/78/70）；
     * 本字段是<b>融合结果</b>的置信度，取 0—1 小数，与接口文档 3.8 节示例一致。
     * 两者语义不同，量纲也不同，前端取用时必须区分。
     */
    private Double confidence;

    /** 记录状态：PENDING=样本不足待验证 / VALIDATED=已验证 / COMPARED=已与实况对比 */
    private String status;

    /** 测算时间（yyyy-MM-dd HH:mm） */
    private String predictionTime;

    /**
     * 融合路径在地图上的线条颜色。
     *
     * <p>前端 P1 把 AI 融合路径与三家机构路径并排绘制，需要一条
     * <b>与机构配色都不冲突</b>的青蓝色（#2BB8C4）来区分"平台自有测算"。
     * 该色值属于前端视觉契约，由后端随数据下发，前端不硬编码。
     */
    private String color = "#2BB8C4";

    /**
     * 融合路径的 SVG 虚线样式。
     *
     * <p>与机构预报路径的 {@code "7 5"} 区分：AI 融合路径用更密的 {@code "6 5"}，
     * 让用户从线型上就能看出"这条不是机构预报"。
     */
    private String dash = "6 5";

    /** 免责声明 */
    private String disclaimer = DISCLAIMER;

    public AiPredictionVO() {
    }

    /**
     * 单个模型的测算明细。
     */
    public static class ModelItem implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 模型名称，如 DeepSeek-V3 */
        private String name;

        /** 置信度（0—100）；尚未测算时为 null */
        private Integer confidence;

        /** 状态，如「已接入」「待测算」 */
        private String status;

        /** 预测登陆点纬度 */
        private Double landfallLat;

        /** 预测登陆点经度 */
        private Double landfallLon;

        /** 与机构预报平均偏差（km） */
        private Double deviation;

        public ModelItem() {
        }

        /**
         * 由记录组装明细条目。
         *
         * @param record 测算记录
         * @return 明细条目
         */
        public static ModelItem of(AiPredictionRecord record) {
            ModelItem item = new ModelItem();
            item.name = record.getModelName();
            item.confidence = record.getConfidenceScore() == null
                    ? null : (int) Math.round(record.getConfidenceScore());
            item.status = "已接入";
            item.landfallLat = record.getPredictedLandfallLat();
            item.landfallLon = record.getPredictedLandfallLon();
            item.deviation = record.getDeviationVsAgencies();
            return item;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getConfidence() {
            return confidence;
        }

        public void setConfidence(Integer confidence) {
            this.confidence = confidence;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Double getLandfallLat() {
            return landfallLat;
        }

        public void setLandfallLat(Double landfallLat) {
            this.landfallLat = landfallLat;
        }

        public Double getLandfallLon() {
            return landfallLon;
        }

        public void setLandfallLon(Double landfallLon) {
            this.landfallLon = landfallLon;
        }

        public Double getDeviation() {
            return deviation;
        }

        public void setDeviation(Double deviation) {
            this.deviation = deviation;
        }
    }

    public Long getTyphoonId() {
        return typhoonId;
    }

    public void setTyphoonId(Long typhoonId) {
        this.typhoonId = typhoonId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<ModelItem> getModels() {
        return models;
    }

    public void setModels(List<ModelItem> models) {
        this.models = models;
    }

    public Integer getConsistency() {
        return consistency;
    }

    public void setConsistency(Integer consistency) {
        this.consistency = consistency;
    }

    public String getConsistencyText() {
        return consistencyText;
    }

    public void setConsistencyText(String consistencyText) {
        this.consistencyText = consistencyText;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<List<Double>> getPoints() {
        return points;
    }

    public void setPoints(List<List<Double>> points) {
        this.points = points;
    }

    public String getLandingRange() {
        return landingRange;
    }

    public void setLandingRange(String landingRange) {
        this.landingRange = landingRange;
    }

    public String getIntensityTrend() {
        return intensityTrend;
    }

    public void setIntensityTrend(String intensityTrend) {
        this.intensityTrend = intensityTrend;
    }

    public Double getDeviation() {
        return deviation;
    }

    public void setDeviation(Double deviation) {
        this.deviation = deviation;
    }

    public String getPredictionTime() {
        return predictionTime;
    }

    public void setPredictionTime(String predictionTime) {
        this.predictionTime = predictionTime;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getDash() {
        return dash;
    }

    public void setDash(String dash) {
        this.dash = dash;
    }

    public String getDisclaimer() {
        return disclaimer;
    }

    public void setDisclaimer(String disclaimer) {
        this.disclaimer = disclaimer;
    }
}
