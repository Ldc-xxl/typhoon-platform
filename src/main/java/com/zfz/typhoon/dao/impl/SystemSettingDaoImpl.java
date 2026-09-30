package com.zfz.typhoon.dao.impl;

import com.zfz.typhoon.dao.SystemSettingDao;
import com.zfz.typhoon.model.SystemSetting;
import com.zfz.typhoon.util.JdbcTemplate;
import com.zfz.typhoon.util.RowMapper;
import com.zfz.typhoon.util.RsUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 系统配置表 DAO 实现。
 *
 * <p>注意 {@link #getValue} 等方法<b>每次都会查库</b>，
 * 它们是为"低频读取 + 立即可见"的场景准备的（如管理端表单回填）。
 * 高频业务路径请读 Service 层的内存缓存，不要每请求调这里。
 *
 * @author 追风者项目组
 */
public class SystemSettingDaoImpl implements SystemSettingDao {

    private static final String COLUMNS =
            "s.setting_id, s.setting_key, s.setting_value, s.setting_type, "
                    + "s.description, s.updated_by, s.updated_at";

    private static final RowMapper<SystemSetting> MAPPER = new RowMapper<SystemSetting>() {
        @Override
        public SystemSetting map(ResultSet rs) throws SQLException {
            SystemSetting s = new SystemSetting();
            s.setSettingId(RsUtil.getLong(rs, "setting_id"));
            s.setSettingKey(RsUtil.getString(rs, "setting_key"));
            s.setSettingValue(RsUtil.getString(rs, "setting_value"));
            s.setSettingType(RsUtil.getString(rs, "setting_type"));
            s.setDescription(RsUtil.getString(rs, "description"));
            s.setUpdatedBy(RsUtil.getLong(rs, "updated_by"));
            s.setUpdatedAt(RsUtil.getDateTime(rs, "updated_at"));
            return s;
        }
    };

    private static final String INSERT_SQL =
            "INSERT INTO system_settings (setting_key, setting_value, setting_type, "
                    + "description, updated_by) VALUES (?, ?, ?, ?, ?)";

    /** 幂等写入：命中唯一键 uk_settings_key 时更新值、类型与修改人 */
    private static final String UPSERT_SQL =
            "INSERT INTO system_settings (setting_key, setting_value, setting_type, updated_by) "
                    + "VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value), "
                    + "setting_type = VALUES(setting_type), updated_by = VALUES(updated_by)";

    @Override
    public List<SystemSetting> listAll() {
        return JdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM system_settings s ORDER BY s.setting_key ASC", MAPPER);
    }

    @Override
    public SystemSetting findByKey(String settingKey) {
        // 命中唯一键 uk_settings_key
        String sql = "SELECT " + COLUMNS + " FROM system_settings s WHERE s.setting_key = ?";
        return JdbcTemplate.queryOne(sql, MAPPER, settingKey);
    }

    @Override
    public String getValue(String settingKey, String defaultValue) {
        SystemSetting s = findByKey(settingKey);
        if (s == null || s.getSettingValue() == null) {
            return defaultValue;
        }
        return s.getSettingValue();
    }

    @Override
    public double getDouble(String settingKey, double defaultValue) {
        String v = getValue(settingKey, null);
        if (v == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            // 配置被人工改坏时不报错，回退默认值保证服务可用
            return defaultValue;
        }
    }

    @Override
    public int getInt(String settingKey, int defaultValue) {
        String v = getValue(settingKey, null);
        if (v == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public boolean getBoolean(String settingKey, boolean defaultValue) {
        String v = getValue(settingKey, null);
        if (v == null) {
            return defaultValue;
        }
        String t = v.trim();
        return "1".equals(t) || "true".equalsIgnoreCase(t) || "on".equalsIgnoreCase(t);
    }

    @Override
    public int countAll() {
        Integer n = JdbcTemplate.queryInt("SELECT COUNT(*) FROM system_settings");
        return n == null ? 0 : n.intValue();
    }

    @Override
    public long insert(SystemSetting s) {
        Long id = JdbcTemplate.insert(INSERT_SQL,
                s.getSettingKey(), s.getSettingValue(),
                s.getSettingType() == null ? "STRING" : s.getSettingType(),
                s.getDescription(), s.getUpdatedBy());
        return id == null ? 0L : id.longValue();
    }

    @Override
    public int upsertValue(String settingKey, String settingValue, String settingType, Long updatedBy) {
        return JdbcTemplate.update(UPSERT_SQL, settingKey, settingValue,
                settingType == null ? "STRING" : settingType, updatedBy);
    }

    @Override
    public int deleteByKey(String settingKey) {
        return JdbcTemplate.update(
                "DELETE FROM system_settings WHERE setting_key = ?", settingKey);
    }
}
