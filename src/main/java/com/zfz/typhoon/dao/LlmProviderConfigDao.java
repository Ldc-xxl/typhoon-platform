package com.zfz.typhoon.dao;

import com.zfz.typhoon.model.LlmProviderConfig;

import java.util.List;

/**
 * 大模型接入配置 DAO。
 *
 * <p>接口里刻意<b>没有</b>"读明文密钥"的方法：解密只在 Service 层做，
 * 且只在发起外部调用时做。DAO 只搬运密文，减少密钥在代码里流动的路径。
 *
 * @author 追风者项目组
 */
public interface LlmProviderConfigDao {

    /**
     * 全部厂商配置（按 config_id 升序，保证前端表格顺序稳定）。
     *
     * @return 配置列表；表不存在时由实现方降级为空列表
     */
    List<LlmProviderConfig> listAll();

    /**
     * 按模型标识查询。
     *
     * @param modelKey 模型标识，如 doubao-seed-1.6
     * @return 配置；不存在返回 null
     */
    LlmProviderConfig findByKey(String modelKey);

    /**
     * 保存凭据（密文 + 接入点 + 网关）。
     *
     * <p>保存动作<b>不</b>改变 verified_at —— "填了 Key"与"Key 可用"是两件事，
     * 后者只能由一次真实调用证明。
     *
     * @param modelKey    模型标识
     * @param apiKeyCipher 密文
     * @param endpointId  接入点/模型 ID
     * @param baseUrl     自定义网关（可为 null）
     * @return 受影响行数
     */
    int saveCredential(String modelKey, String apiKeyCipher, String endpointId, String baseUrl);

    /**
     * 断开接入：清空密文、验证时间与错误信息（历史测算记录保留）。
     *
     * @param modelKey 模型标识
     * @return 受影响行数
     */
    int clearCredential(String modelKey);

    /**
     * 标记验证成功。
     *
     * @param modelKey  模型标识
     * @param latencyMs 本次往返耗时
     * @return 受影响行数
     */
    int markVerified(String modelKey, long latencyMs);

    /**
     * 标记失败原因（不改变 verified_at，避免"曾经成功过一次"被误清）。
     *
     * @param modelKey 模型标识
     * @param error    已脱敏的错误描述
     * @return 受影响行数
     */
    int markError(String modelKey, String error);
}
