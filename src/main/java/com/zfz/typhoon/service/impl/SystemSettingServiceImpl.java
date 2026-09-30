package com.zfz.typhoon.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.zfz.typhoon.dao.SystemSettingDao;
import com.zfz.typhoon.dao.impl.SystemSettingDaoImpl;
import com.zfz.typhoon.model.SystemSetting;
import com.zfz.typhoon.service.SystemSettingService;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.JsonUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统参数业务实现。
 *
 * <h3>类型校验策略</h3>
 * 写入前按 {@code setting_type} 做一次"能不能被解析"的试解析：
 * <ul>
 *   <li>NUMBER —— 必须能被 {@link Double#parseDouble} 解析；</li>
 *   <li>BOOLEAN —— 必须是 true/false；</li>
 *   <li>JSON —— 必须能被 Jackson 解析成对象；</li>
 *   <li>STRING —— 不校验。</li>
 * </ul>
 * 校验失败就抛 400 并说明期望格式，而不是静默存下脏值。
 *
 * <p>读取侧的"解析失败退默认值"策略同样重要：
 * 如果数据库里的值是在本校验逻辑上线之前写坏的，读取时不能抛异常让整个接口 500，
 * 而应该退回默认值并在日志里留痕——监控模块的可用性优先于个别参数的正确性。
 *
 * @author 追风者项目组
 * @see SystemSettingService
 */
public class SystemSettingServiceImpl implements SystemSettingService {

    private final SystemSettingDao settingDao;

    /**
     * 生产用构造：按默认实现装配依赖。
     * <p>之所以把它拆成"无参 + 带参"两个构造，是为了给单元测试留出注入替身的缝隙：
     * 依赖全部在字段初始化里 new 出来的话，测试就必须连上真实 MySQL 才能跑，
     * 单元测试也就退化成了集成测试。
     */
    public SystemSettingServiceImpl() {
        this(new SystemSettingDaoImpl());
    }

    /**
     * 测试用构造：注入替身依赖（Mockito 生成的 mock 或手写桩）。
     * <p>参数顺序与上方字段声明一致，均为本服务运行所需的协作者。
     */
    public SystemSettingServiceImpl(SystemSettingDao settingDao) {
        this.settingDao = settingDao;
    }

    @Override
    public List<SystemSetting> listAll() {
        return settingDao.listAll();
    }

    @Override
    public String getString(String key, String defaultValue) {
        String v = settingDao.getValue(key, null);
        return v == null || v.trim().isEmpty() ? defaultValue : v.trim();
    }

    @Override
    public double getDouble(String key, double defaultValue) {
        // 委托给 DAO：它在解析失败时已退回默认值，避免此处再抄一遍 try/catch
        return settingDao.getDouble(key, defaultValue);
    }

    @Override
    public int getInt(String key, int defaultValue) {
        return settingDao.getInt(key, defaultValue);
    }

    @Override
    public boolean getBoolean(String key, String defaultValue) {
        Boolean parsed = parseBoolean(getString(key, defaultValue), null);
        return parsed == null ? Boolean.parseBoolean(defaultValue) : parsed.booleanValue();
    }

    @Override
    public Map<String, String> asMap() {
        List<SystemSetting> rows = settingDao.listAll();
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (SystemSetting s : rows) {
            m.put(s.getSettingKey(), s.getSettingValue());
        }
        return m;
    }

    @Override
    public int update(String key, String value, Long updatedBy) {
        if (key == null || key.trim().isEmpty()) {
            throw BusinessException.badRequest("参数名不能为空");
        }
        String k = key.trim();
        SystemSetting exists = settingDao.findByKey(k);
        if (exists == null) {
            throw BusinessException.notFound("参数不存在：" + k);
        }
        String type = exists.getSettingType();
        validate(k, type, value, exists.getDescription());
        return settingDao.upsertValue(k, value, type, updatedBy);
    }

    @Override
    public int updateBatch(Map<String, String> values, Long updatedBy) {
        if (values == null || values.isEmpty()) {
            throw BusinessException.badRequest("没有需要更新的参数");
        }
        // 先全量校验再统一落库：避免"改了一半报错"留下半套参数，
        // 那会让算法处于一个没人预期过的中间状态
        Map<String, String> pending = new LinkedHashMap<String, String>();
        Map<String, String> types = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> e : values.entrySet()) {
            String k = e.getKey() == null ? "" : e.getKey().trim();
            if (k.isEmpty()) {
                continue;
            }
            SystemSetting exists = settingDao.findByKey(k);
            if (exists == null) {
                throw BusinessException.notFound("参数不存在：" + k);
            }
            validate(k, exists.getSettingType(), e.getValue(), exists.getDescription());
            pending.put(k, e.getValue());
            types.put(k, exists.getSettingType());
        }
        if (pending.isEmpty()) {
            throw BusinessException.badRequest("没有需要更新的参数");
        }
        int done = 0;
        for (Map.Entry<String, String> e : pending.entrySet()) {
            done += settingDao.upsertValue(e.getKey(), e.getValue(), types.get(e.getKey()), updatedBy);
        }
        return done;
    }

    /* ==================================================================== */
    /*  内部                                                                */
    /* ==================================================================== */

    /**
     * 按参数类型做试解析校验。
     *
     * @param key         参数名（用于提示）
     * @param type        参数类型
     * @param value       待写入值
     * @param description 参数说明（用于提示）
     */
    private void validate(String key, String type, String value, String description) {
        if (value == null) {
            throw BusinessException.badRequest("参数「" + key + "」的值不能为空");
        }
        if (type == null) {
            return;
        }
        String t = type.trim().toUpperCase();
        String hint = description == null || description.trim().isEmpty() ? "" : "（" + description.trim() + "）";
        if (TYPE_NUMBER.equals(t) && parseDouble(value) == null) {
            throw BusinessException.badRequest("参数「" + key + "」应为数值" + hint + "，当前值：" + value);
        }
        if (TYPE_BOOLEAN.equals(t) && parseBoolean(value, null) == null) {
            throw BusinessException.badRequest("参数「" + key + "」应为 true / false" + hint + "，当前值：" + value);
        }
        if (TYPE_JSON.equals(t) && !isJson(value)) {
            throw BusinessException.badRequest("参数「" + key + "」应为合法 JSON" + hint + "，当前值：" + value);
        }
    }

    /**
     * 试解析数值。
     *
     * @param value 原值
     * @return 数值；不可解析返回 null
     */
    private Double parseDouble(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(Double.parseDouble(value.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 试解析布尔。
     *
     * @param value        原值
     * @param defaultValue 不可解析时的返回值
     * @return 布尔；不可解析返回 defaultValue
     */
    private Boolean parseBoolean(String value, Boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String v = value.trim();
        if ("true".equalsIgnoreCase(v) || "1".equals(v) || "是".equals(v) || "开启".equals(v)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(v) || "0".equals(v) || "否".equals(v) || "关闭".equals(v)) {
            return Boolean.FALSE;
        }
        return defaultValue;
    }

    /**
     * 判断字符串是否为合法 JSON 对象/数组。
     *
     * @param value 原值
     * @return 合法返回 true
     */
    private boolean isJson(String value) {
        try {
            JsonNode node = JsonUtil.mapper().readTree(value);
            return node != null && !node.isMissingNode();
        } catch (Exception e) {
            return false;
        }
    }
}
