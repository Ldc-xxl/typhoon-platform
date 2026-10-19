package com.zfz.typhoon.dao.impl;

import com.zfz.typhoon.dao.LlmProviderConfigDao;
import com.zfz.typhoon.model.LlmProviderConfig;
import com.zfz.typhoon.util.JdbcTemplate;
import com.zfz.typhoon.util.RowMapper;
import com.zfz.typhoon.util.RsUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 大模型接入配置 DAO 实现。
 *
 * <p>本类<b>不做</b>任何加解密：{@code api_key_cipher} 原样进出，
 * 明文只在 {@code LlmConfigServiceImpl} 发起外部调用前短暂存在。
 *
 * @author 追风者项目组
 */
public class LlmProviderConfigDaoImpl implements LlmProviderConfigDao {

    private static final String COLUMNS =
            "c.config_id, c.model_key, c.model_name, c.provider, c.endpoint_id, c.base_url, "
                    + "c.api_key_cipher, c.verified_at, c.last_error, c.last_latency_ms, "
                    + "c.created_at, c.updated_at";

    private static final RowMapper<LlmProviderConfig> MAPPER = new RowMapper<LlmProviderConfig>() {
        @Override
        public LlmProviderConfig map(ResultSet rs) throws SQLException {
            LlmProviderConfig c = new LlmProviderConfig();
            c.setConfigId(RsUtil.getLong(rs, "config_id"));
            c.setModelKey(RsUtil.getString(rs, "model_key"));
            c.setModelName(RsUtil.getString(rs, "model_name"));
            c.setProvider(RsUtil.getString(rs, "provider"));
            c.setEndpointId(RsUtil.getString(rs, "endpoint_id"));
            c.setBaseUrl(RsUtil.getString(rs, "base_url"));
            c.setApiKeyCipher(RsUtil.getString(rs, "api_key_cipher"));
            c.setVerifiedAt(RsUtil.getDateTime(rs, "verified_at"));
            c.setLastError(RsUtil.getString(rs, "last_error"));
            c.setLastLatencyMs(RsUtil.getInt(rs, "last_latency_ms"));
            c.setCreatedAt(RsUtil.getDateTime(rs, "created_at"));
            c.setUpdatedAt(RsUtil.getDateTime(rs, "updated_at"));
            return c;
        }
    };

    /** 只改凭据三件套，不动 verified_at（"填了 Key" ≠ "Key 可用"） */
    private static final String SAVE_CREDENTIAL_SQL =
            "UPDATE llm_provider_config SET api_key_cipher = ?, endpoint_id = ?, base_url = ? "
                    + "WHERE model_key = ?";

    private static final String CLEAR_SQL =
            "UPDATE llm_provider_config SET api_key_cipher = NULL, endpoint_id = NULL, base_url = NULL, "
                    + "verified_at = NULL, last_error = NULL, last_latency_ms = NULL "
                    + "WHERE model_key = ?";

    private static final String MARK_VERIFIED_SQL =
            "UPDATE llm_provider_config SET verified_at = NOW(), last_error = NULL, last_latency_ms = ? "
                    + "WHERE model_key = ?";

    private static final String MARK_ERROR_SQL =
            "UPDATE llm_provider_config SET last_error = ? WHERE model_key = ?";

    @Override
    public List<LlmProviderConfig> listAll() {
        // 命中 uk_llm_model_key；按 config_id 排序保证表格顺序与 migration 里插入的顺序一致
        return JdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM llm_provider_config c ORDER BY c.config_id ASC", MAPPER);
    }

    @Override
    public LlmProviderConfig findByKey(String modelKey) {
        String sql = "SELECT " + COLUMNS + " FROM llm_provider_config c WHERE c.model_key = ?";
        return JdbcTemplate.queryOne(sql, MAPPER, modelKey);
    }

    @Override
    public int saveCredential(String modelKey, String apiKeyCipher, String endpointId, String baseUrl) {
        return JdbcTemplate.update(SAVE_CREDENTIAL_SQL, apiKeyCipher, endpointId, baseUrl, modelKey);
    }

    @Override
    public int clearCredential(String modelKey) {
        return JdbcTemplate.update(CLEAR_SQL, modelKey);
    }

    @Override
    public int markVerified(String modelKey, long latencyMs) {
        return JdbcTemplate.update(MARK_VERIFIED_SQL, Integer.valueOf((int) Math.min(latencyMs, Integer.MAX_VALUE)),
                modelKey);
    }

    @Override
    public int markError(String modelKey, String error) {
        // last_error 是 varchar(500)：超出会被 MySQL 按严格模式拒绝（或截断），
        // 这里先截断，保证"报错本身不会再把接口打成 500"。
        String safe = error == null ? null : (error.length() > 500 ? error.substring(0, 500) : error);
        return JdbcTemplate.update(MARK_ERROR_SQL, safe, modelKey);
    }
}
