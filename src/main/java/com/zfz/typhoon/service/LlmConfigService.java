package com.zfz.typhoon.service;

import com.zfz.typhoon.model.LlmProviderConfig;
import com.zfz.typhoon.util.LlmClient;

import java.util.List;
import java.util.Map;

/**
 * 大模型接入配置服务。
 *
 * <p>职责边界：
 * <ul>
 *   <li>本接口负责「凭据的存取 + 连通性验证 + 按配置发起一次调用」；</li>
 *   <li>「把模型输出变成一条测算记录」属于
 *       {@link LlmPredictionService}，不在这一层做业务解释。</li>
 * </ul>
 *
 * <p>密钥策略：落库前 AES-256-GCM 加密；解密只在 {@link #call} 内部发生，
 * 且明文不写日志、不进响应体。
 *
 * @author 追风者项目组
 */
public interface LlmConfigService {

    /**
     * 全部厂商配置。数据库缺表时返回空列表（功能降级为"全部待接入"），
     * 而不是让整个后台 500。
     *
     * @return 配置列表
     */
    List<LlmProviderConfig> listAll();

    /**
     * 按模型标识查询。
     *
     * @param modelKey 模型标识，如 doubao-seed-1.6
     * @return 配置；不存在返回 null
     */
    LlmProviderConfig find(String modelKey);

    /**
     * 保存凭据并立即验证一次。
     *
     * <p>为什么保存时就要调一次：只落库不验证的话，管理员会以为"接好了"，
     * 直到真正测算时才看到 401。宁可现在就多花一次几百 token 的调用。
     *
     * @param modelKey   模型标识
     * @param apiKey     API Key 明文（为空则报错，不支持"只改接入点"）
     * @param endpointId 推理接入点/模型 ID
     * @param baseUrl    自定义网关（可空）
     * @return 结构：{@code ok, message, latencyMs, verifiedAt}
     */
    Map<String, Object> connect(String modelKey, String apiKey, String endpointId, String baseUrl);

    /**
     * 用已保存的凭据重新验证。
     *
     * @param modelKey 模型标识
     * @return 结构同 {@link #connect}
     */
    Map<String, Object> test(String modelKey);

    /**
     * 断开接入：清空密钥与验证状态。历史测算记录不删（那是已经发生的事实）。
     *
     * @param modelKey 模型标识
     * @return 是否确实清掉了一条配置
     */
    boolean disconnect(String modelKey);

    /**
     * 按已保存配置发起一次调用（供测算服务使用）。
     *
     * @param modelKey  模型标识
     * @param prompt    提示词
     * @param maxTokens 最大输出 token
     * @return 调用结果；配置缺失/未配密钥也以失败结果返回，不抛异常
     */
    LlmClient.Result call(String modelKey, String prompt, int maxTokens);

    /**
     * 解析实际请求地址。
     *
     * @param cfg 配置
     * @return 完整 URL
     */
    String resolveUrl(LlmProviderConfig cfg);
}
