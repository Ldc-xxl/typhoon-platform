package com.zfz.typhoon.dao;

import com.zfz.typhoon.model.SystemSetting;

import java.util.List;

/**
 * 系统配置表（{@code system_settings}）数据访问接口。
 *
 * <p>课程要求（阶段三 ·「配置需可参数化」；阶段七 ·「管理端可调全局参数」）。
 * 对应接口文档：{@code GET /api/settings}、{@code PUT /api/settings/{key}}。
 *
 * <h3>本表承载的关键参数</h3>
 * <ul>
 *   <li>{@code data_refresh_interval}：前端自动刷新间隔（分钟）；</li>
 *   <li>{@code high_uncertainty_threshold}：分歧"高不确定性"判定阈值（默认 0.7）；</li>
 *   <li>{@code sandbox_param_range}：沙盘四个参数的可调范围（JSON）；</li>
 *   <li>{@code max_export_rows}：单次导出最大行数；</li>
 *   <li>{@code survey_enabled}：问卷采集开关。</li>
 * </ul>
 *
 * <p>约定：Service 层启动时把本表读入内存缓存，业务代码读缓存而非每请求查库；
 * 管理端修改后由 Service 主动刷新缓存。DAO 只负责持久化。
 *
 * @author 追风者项目组
 * @see com.zfz.typhoon.model.SystemSetting
 */
public interface SystemSettingDao {

    // ---- 已知配置键常量（避免业务代码里散落魔法字符串） ----

    /** 数据自动刷新间隔（分钟） */
    String KEY_REFRESH_INTERVAL = "data_refresh_interval";

    /** 默认底图：NORMAL / SATELLITE / TERRAIN */
    String KEY_DEFAULT_BASE_MAP = "default_base_map";

    /** 风圈图层透明度 */
    String KEY_WIND_CIRCLE_OPACITY = "wind_circle_opacity";

    /** 最大展示风圈等级 */
    String KEY_WIND_CIRCLE_MAX_LEVEL = "wind_circle_max_level";

    /** 历史数据保留天数 */
    String KEY_DATA_RETENTION_DAYS = "data_retention_days";

    /** 单次导出最大行数 */
    String KEY_MAX_EXPORT_ROWS = "max_export_rows";

    /** 沙盘参数可调范围（JSON） */
    String KEY_SANDBOX_PARAM_RANGE = "sandbox_param_range";

    /** 问卷采集开关（1=开，0=关） */
    String KEY_SURVEY_ENABLED = "survey_enabled";

    /** 浏览器消息推送总开关 */
    String KEY_PUSH_ENABLED = "push_enabled";

    /** 高不确定性分歧阈值 */
    String KEY_HIGH_UNCERTAINTY_THRESHOLD = "high_uncertainty_threshold";

    /**
     * 查询全部配置（按配置键升序）。
     *
     * @return 配置列表
     */
    List<SystemSetting> listAll();

    /**
     * 按配置键查询。
     *
     * @param settingKey 配置键
     * @return 配置项；不存在返回 null
     */
    SystemSetting findByKey(String settingKey);

    /**
     * 取配置值字符串；不存在时返回默认值。
     *
     * @param settingKey   配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    String getValue(String settingKey, String defaultValue);

    /**
     * 取配置值并转为 double（如阈值、透明度）。
     *
     * @param settingKey   配置键
     * @param defaultValue 默认值
     * @return 数值
     */
    double getDouble(String settingKey, double defaultValue);

    /**
     * 取配置值并转为 int（如刷新间隔、保留天数）。
     *
     * @param settingKey   配置键
     * @param defaultValue 默认值
     * @return 整数值
     */
    int getInt(String settingKey, int defaultValue);

    /**
     * 取配置值并转为布尔（{@code 1 / true} 视为真）。
     *
     * @param settingKey   配置键
     * @param defaultValue 默认值
     * @return 布尔值
     */
    boolean getBoolean(String settingKey, boolean defaultValue);

    /**
     * 统计配置项数量（管理端概览）。
     *
     * @return 数量
     */
    int countAll();

    /**
     * 新增配置项。
     *
     * @param setting 配置项
     * @return 新生成的主键
     */
    long insert(SystemSetting setting);

    /**
     * 更新配置值（幂等写入，不存在时新增），并记录最后修改人。
     *
     * @param settingKey   配置键
     * @param settingValue 新值
     * @param settingType  值类型：INT / DECIMAL / STRING / JSON
     * @param updatedBy    修改人ID，可为 null
     * @return 受影响行数
     */
    int upsertValue(String settingKey, String settingValue, String settingType, Long updatedBy);

    /**
     * 按配置键删除（仅管理端维护使用）。
     *
     * @param settingKey 配置键
     * @return 受影响行数
     */
    int deleteByKey(String settingKey);
}
