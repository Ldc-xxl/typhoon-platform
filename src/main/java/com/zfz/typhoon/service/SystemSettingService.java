package com.zfz.typhoon.service;

import com.zfz.typhoon.model.SystemSetting;

import java.util.List;
import java.util.Map;

/**
 * 系统参数业务接口。
 *
 * <p>对应接口文档 {@code GET /api/admin/settings}、{@code PUT /api/admin/settings}。
 *
 * <h3>为什么参数要放数据库而不是配置文件</h3>
 * 平台里已有多处算法阈值（分歧指数高不确定性阈值、沙盘参数范围、
 * AI 置信度归一化尺度…）需要按运行情况微调。
 * 放进 {@code db.properties} 意味着每次调整都要改文件 + 重启 Tomcat；
 * 放进 {@code system_settings} 表则可以在后台改、立即生效。
 *
 * <h3>类型安全</h3>
 * 表里用 {@code setting_type} 标注参数类型（NUMBER / BOOLEAN / STRING / JSON），
 * 写接口会按类型校验后才落库。否则一个手抖把 {@code 0.70} 填成 {@code 0.7O}，
 * 算法不会报错，只会安静地用默认值跑——这类问题排查成本极高。
 *
 * @author 追风者项目组
 */
public interface SystemSettingService {

    /** 类型：数值 */
    String TYPE_NUMBER = "NUMBER";
    /** 类型：布尔 */
    String TYPE_BOOLEAN = "BOOLEAN";
    /** 类型：字符串 */
    String TYPE_STRING = "STRING";
    /** 类型：JSON 对象 */
    String TYPE_JSON = "JSON";

    /**
     * 查询全部参数。
     *
     * @return 参数列表（按 key 升序）
     */
    List<SystemSetting> listAll();

    /**
     * 读取字符串参数。
     *
     * @param key          参数名
     * @param defaultValue 缺省值
     * @return 参数值
     */
    String getString(String key, String defaultValue);

    /**
     * 读取数值参数。
     *
     * @param key          参数名
     * @param defaultValue 缺省值
     * @return 参数值
     */
    double getDouble(String key, double defaultValue);

    /**
     * 读取整数参数。
     *
     * @param key          参数名
     * @param defaultValue 缺省值
     * @return 参数值
     */
    int getInt(String key, int defaultValue);

    /**
     * 读取布尔参数。
     *
     * @param key          参数名
     * @param defaultValue 缺省值
     * @return 参数值
     */
    boolean getBoolean(String key, String defaultValue);

    /**
     * 全量参数转为「键 → 值」映射（供算法一次性读取多个参数，避免多次查库）。
     *
     * @return 不可变映射
     */
    Map<String, String> asMap();

    /**
     * 更新单个参数（校验类型后落库）。
     *
     * @param key       参数名
     * @param value     参数值
     * @param updatedBy 操作者ID，可为 null
     * @return 受影响行数
     */
    int update(String key, String value, Long updatedBy);

    /**
     * 批量更新参数（后台"保存全部"按钮）。
     *
     * @param values    参数名 → 值
     * @param updatedBy 操作者ID
     * @return 成功更新的条数
     */
    int updateBatch(Map<String, String> values, Long updatedBy);
}
