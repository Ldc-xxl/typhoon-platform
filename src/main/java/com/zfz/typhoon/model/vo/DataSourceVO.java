package com.zfz.typhoon.model.vo;

import com.zfz.typhoon.model.DataSource;
import com.zfz.typhoon.util.DateUtil;

import java.io.Serializable;

/**
 * 数据源监控视图对象（面向前端 P8 后台"数据源接入监控"表）。
 *
 * <p>字段名对齐前端 {@code ZFZ.mock.dataSources}：
 * 每行显示「名称　接入方式　最后更新　今日请求　成功率　状态」。
 * {@link #success} 与 {@link #status} 由后端按同步日志统计后直接给结论，
 * 前端不再自己判断"延迟/异常"，避免多处口径不一致。
 *
 * @author 追风者项目组
 */
public class DataSourceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 数据源ID */
    private Long id;

    /** 数据源代码 */
    private String code;

    /** 数据源名称 */
    private String name;

    /** 接入方式：REST API / 集合预报 */
    private String type;

    /** 最后更新时间（yyyy-MM-dd HH:mm） */
    private String updated;

    /** 今日请求次数（带千分位，如 1,248） */
    private String requests;

    /** 同步成功率，如 99.8% */
    private String success;

    /** 状态：正常 / 延迟 / 异常 / 停用 */
    private String status;

    /** 更新频率（分钟） */
    private Integer updateFrequency;

    /** 是否启用 */
    private Boolean enabled;

    /** 接口地址（后台展示用，密钥类参数已由后端脱敏） */
    private String apiUrl;

    /** 数据源说明 */
    private String description;

    /** 平均耗时（秒） */
    private Double avgDuration;

    public DataSourceVO() {
    }

    /**
     * 由实体 + 统计结果组装。
     *
     * @param entity 数据源实体
     * @param todayRequests 今日请求次数
     * @param successRate   成功率（0—1），为 null 表示暂无数据
     * @return 视图对象
     */
    public static DataSourceVO of(DataSource entity, int todayRequests, Double successRate) {
        DataSourceVO vo = new DataSourceVO();
        if (entity == null) {
            return vo;
        }
        vo.id = entity.getSourceId();
        vo.code = entity.getSourceCode();
        vo.name = entity.getSourceName();
        vo.type = entity.getSourceCode() != null && entity.getSourceCode().toUpperCase().contains("ECMWF")
                ? "集合预报" : "REST API";
        vo.updated = DateUtil.format(entity.getLastSyncTime());
        vo.requests = String.format("%,d", todayRequests);
        vo.success = successRate == null ? "—" : String.format("%.1f%%", successRate * 100);
        vo.enabled = entity.getEnabled();
        vo.updateFrequency = entity.getUpdateFrequency();
        vo.apiUrl = entity.getApiUrl();
        vo.description = entity.getDescription();
        vo.status = statusOf(entity, successRate);
        return vo;
    }

    /**
     * 推导状态结论。
     *
     * @param entity      数据源
     * @param successRate 成功率
     * @return 正常 / 延迟 / 异常 / 停用
     */
    private static String statusOf(DataSource entity, Double successRate) {
        if (Boolean.FALSE.equals(entity.getEnabled())) {
            return "停用";
        }
        if (successRate == null) {
            return "未同步";
        }
        if (successRate >= 0.99) {
            return "正常";
        }
        if (successRate >= 0.95) {
            return "延迟";
        }
        return "异常";
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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

    public String getUpdated() {
        return updated;
    }

    public void setUpdated(String updated) {
        this.updated = updated;
    }

    public String getRequests() {
        return requests;
    }

    public void setRequests(String requests) {
        this.requests = requests;
    }

    public String getSuccess() {
        return success;
    }

    public void setSuccess(String success) {
        this.success = success;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getUpdateFrequency() {
        return updateFrequency;
    }

    public void setUpdateFrequency(Integer updateFrequency) {
        this.updateFrequency = updateFrequency;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getAvgDuration() {
        return avgDuration;
    }

    public void setAvgDuration(Double avgDuration) {
        this.avgDuration = avgDuration;
    }
}
