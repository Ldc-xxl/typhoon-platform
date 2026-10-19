package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.LlmProviderConfigDao;
import com.zfz.typhoon.dao.impl.LlmProviderConfigDaoImpl;
import com.zfz.typhoon.model.LlmProviderConfig;
import com.zfz.typhoon.service.LlmConfigService;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.ContactUtil;
import com.zfz.typhoon.util.DateUtil;
import com.zfz.typhoon.util.LlmClient;
import com.zfz.typhoon.util.OpenAiCompatLlmClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 大模型接入配置服务实现。
 *
 * <p>三个容易踩的点，都在这里处理掉了：
 * <ol>
 *   <li><b>密钥绝不明文落库</b>：复用 {@link ContactUtil} 的 AES-256-GCM
 *       （同一条 {@code contact.cipher.key}）。换密钥会导致已存密文解不开，
 *       此时如实报"解密失败"，而不是当成"没配"悄悄放过。</li>
 *   <li><b>缺表不能拖垮后台</b>：{@code llm_provider_config} 是增量迁移建的表，
 *       旧库可能没有。{@link #listAll} 捕获后返回空列表，
 *       后台退化成"全部待接入"，其它功能不受影响。</li>
 *   <li><b>保存即验证</b>：只存不验会让管理员误以为接好了。
 *       保存后立刻发一次最小请求（几十 token），把真实结果摆出来。</li>
 * </ol>
 *
 * @author 追风者项目组
 */
public class LlmConfigServiceImpl implements LlmConfigService {

    private static final Logger LOG = Logger.getLogger(LlmConfigServiceImpl.class.getName());

    /** 火山方舟（豆包）OpenAI 兼容端点 */
    private static final String DEFAULT_DOUBAO_URL = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";

    /** 百度千帆 v2（文心）OpenAI 兼容端点 */
    private static final String DEFAULT_QIANFAN_URL = "https://qianfan.baidubce.com/v2/chat/completions";

    /**
     * 连通性测试用的提示词。
     *
     * <p>刻意短：验证"能不能调通、鉴权对不对"，不需要模型真的思考。
     * 这几十个 token 的成本远低于"配错了等到真测算才发现"。
     */
    private static final String PING_PROMPT = "连通性测试：请只回复两个字符 OK，不要输出其它内容。";

    private static final int PING_MAX_TOKENS = 16;

    private final LlmProviderConfigDao configDao;
    private final LlmClient client;

    /**
     * 生产构造：装配默认 DAO 与 OpenAI 兼容客户端。
     */
    public LlmConfigServiceImpl() {
        this(new LlmProviderConfigDaoImpl(), new OpenAiCompatLlmClient());
    }

    /**
     * 测试构造：注入替身，避免单元测试依赖外网与真实密钥。
     *
     * @param configDao 配置 DAO
     * @param client    大模型客户端
     */
    public LlmConfigServiceImpl(LlmProviderConfigDao configDao, LlmClient client) {
        this.configDao = configDao;
        this.client = client;
    }

    @Override
    public List<LlmProviderConfig> listAll() {
        try {
            return configDao.listAll();
        } catch (RuntimeException e) {
            // 旧库缺 llm_provider_config 表：整块降级为"待接入"，不影响后台其它卡片
            LOG.log(Level.WARNING, "读取 llm_provider_config 失败（可能未执行增量迁移），已降级为空列表："
                    + e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    @Override
    public LlmProviderConfig find(String modelKey) {
        if (modelKey == null || modelKey.trim().isEmpty()) {
            return null;
        }
        try {
            return configDao.findByKey(modelKey.trim());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "查询模型配置失败：" + e.getMessage());
            return null;
        }
    }

    @Override
    public Map<String, Object> connect(String modelKey, String apiKey, String endpointId, String baseUrl) {
        LlmProviderConfig cfg = require(modelKey);
        if (endpointId == null || endpointId.trim().isEmpty()) {
            throw BusinessException.badRequest("缺少推理接入点 / 模型 ID（如 ep-2026xxxx 或 doubao-seed-1-6-250615）");
        }

        /* 密钥留空 = 保留原密钥。
           为什么这样设计：密钥不回传前端，管理员改一个字符的接入点时
           没法把原 Key 填回来；若强制必填，等于逼他重新去控制台复制一次。
           代价是要在这里显式拦住"从没配过 Key 却留空"的情况。 */
        boolean keepKey = apiKey == null || apiKey.trim().isEmpty();
        if (keepKey && !cfg.hasKey()) {
            throw BusinessException.badRequest("缺少 API Key");
        }

        String cipher = keepKey ? cfg.getApiKeyCipher() : ContactUtil.encrypt(apiKey.trim());
        int rows = configDao.saveCredential(cfg.getModelKey(), cipher,
                endpointId.trim(), trimToNull(baseUrl));
        if (rows == 0) {
            throw BusinessException.notFound("模型配置不存在：" + modelKey);
        }
        // 保存成功不等于可用，立刻验证一次
        return test(cfg.getModelKey());
    }

    @Override
    public Map<String, Object> test(String modelKey) {
        LlmProviderConfig cfg = require(modelKey);
        if (!cfg.hasKey()) {
            return result(false, "尚未配置 API Key，请先填写密钥", 0L, cfg);
        }

        String apiKey = ContactUtil.decrypt(cfg.getApiKeyCipher());
        if (apiKey == null || apiKey.trim().isEmpty()) {
            String msg = "密钥解密失败（服务端加密密钥可能已更换），请重新填写 API Key";
            configDao.markError(cfg.getModelKey(), msg);
            return result(false, msg, 0L, configDao.findByKey(cfg.getModelKey()));
        }

        String url;
        try {
            url = resolveUrl(cfg);
        } catch (BusinessException e) {
            configDao.markError(cfg.getModelKey(), e.getMessage());
            return result(false, e.getMessage(), 0L, configDao.findByKey(cfg.getModelKey()));
        }

        LlmClient.Result r = client.chat(new LlmClient.Request(
                apiKey, cfg.getEndpointId(), url, PING_PROMPT, PING_MAX_TOKENS));

        if (r.isOk()) {
            configDao.markVerified(cfg.getModelKey(), r.getLatencyMs());
            return result(true, "连通性验证通过，模型可用", r.getLatencyMs(),
                    configDao.findByKey(cfg.getModelKey()));
        }
        configDao.markError(cfg.getModelKey(), r.getError());
        return result(false, r.getError(), r.getLatencyMs(), configDao.findByKey(cfg.getModelKey()));
    }

    @Override
    public boolean disconnect(String modelKey) {
        LlmProviderConfig cfg = require(modelKey);
        return configDao.clearCredential(cfg.getModelKey()) > 0;
    }

    @Override
    public LlmClient.Result call(String modelKey, String prompt, int maxTokens) {
        LlmProviderConfig cfg = find(modelKey);
        if (cfg == null) {
            return LlmClient.Result.failure("模型配置不存在：" + modelKey, 0L, 0);
        }
        if (!cfg.hasKey()) {
            return LlmClient.Result.failure("尚未配置 API Key，请先在管理后台完成接入", 0L, 0);
        }
        String apiKey = ContactUtil.decrypt(cfg.getApiKeyCipher());
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return LlmClient.Result.failure("密钥解密失败，请重新填写 API Key", 0L, 0);
        }
        String url;
        try {
            url = resolveUrl(cfg);
        } catch (BusinessException e) {
            return LlmClient.Result.failure(e.getMessage(), 0L, 0);
        }

        LlmClient.Result r = client.chat(new LlmClient.Request(
                apiKey, cfg.getEndpointId(), url, prompt, maxTokens));
        if (r.isOk()) {
            configDao.markVerified(cfg.getModelKey(), r.getLatencyMs());
        } else {
            configDao.markError(cfg.getModelKey(), r.getError());
        }
        return r;
    }

    @Override
    public String resolveUrl(LlmProviderConfig cfg) {
        String base = trimToNull(cfg.getBaseUrl());
        if (base != null) {
            String lower = base.toLowerCase();
            if (lower.endsWith("/chat/completions")) {
                return base;
            }
            return base.endsWith("/") ? base + "chat/completions" : base + "/chat/completions";
        }
        String provider = cfg.getProvider() == null ? "" : cfg.getProvider().trim().toLowerCase();
        if ("doubao".equals(provider)) {
            return DEFAULT_DOUBAO_URL;
        }
        if ("qianfan".equals(provider)) {
            return DEFAULT_QIANFAN_URL;
        }
        // openai-compatible 没有统一域名，必须由管理员填写
        throw BusinessException.badRequest("「" + cfg.getModelName()
                + "」需要填写网关地址（base_url），例如 https://api.deepseek.com/v1");
    }

    /**
     * 取配置，不存在直接抛 404。
     *
     * @param modelKey 模型标识
     * @return 配置
     */
    private LlmProviderConfig require(String modelKey) {
        LlmProviderConfig cfg = find(modelKey);
        if (cfg == null) {
            throw BusinessException.notFound("模型配置不存在：" + (modelKey == null ? "" : modelKey));
        }
        return cfg;
    }

    /**
     * 组装统一的返回值。
     *
     * @param ok       是否成功
     * @param message  提示文案
     * @param latency  耗时
     * @param cfg      最新配置（可能为 null）
     * @return 响应体
     */
    private Map<String, Object> result(boolean ok, String message, long latency, LlmProviderConfig cfg) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("ok", Boolean.valueOf(ok));
        m.put("message", message);
        m.put("latencyMs", Long.valueOf(latency));
        m.put("keyConfigured", Boolean.valueOf(cfg != null && cfg.hasKey()));
        m.put("endpointId", cfg == null ? null : cfg.getEndpointId());
        m.put("baseUrl", cfg == null ? null : cfg.getBaseUrl());
        m.put("verifiedAt", cfg == null || cfg.getVerifiedAt() == null
                ? null : DateUtil.format(cfg.getVerifiedAt()));
        m.put("lastError", cfg == null ? null : cfg.getLastError());
        return m;
    }

    private static String trimToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }
}
