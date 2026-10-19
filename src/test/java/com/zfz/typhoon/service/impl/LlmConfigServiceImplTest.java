package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.LlmProviderConfigDao;
import com.zfz.typhoon.model.LlmProviderConfig;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.ContactUtil;
import com.zfz.typhoon.util.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link LlmConfigServiceImpl} 单元测试。
 *
 * <p>重点不在"能存能读"，而在几条<b>不做就会出事</b>的规则：
 * <ul>
 *   <li>密钥留空时不能把已存的密文冲掉；</li>
 *   <li>从没配过 Key 又留空，必须拒绝而不是存一个空 Key；</li>
 *   <li>调用失败要落 last_error 并把原因回给管理员；</li>
 *   <li>缺表要降级成空列表，而不是让后台 500。</li>
 * </ul>
 *
 * @author 追风者项目组
 */
@ExtendWith(MockitoExtension.class)
class LlmConfigServiceImplTest {

    private static final String KEY = "doubao-seed-1.6";

    @Mock
    private LlmProviderConfigDao configDao;

    @Mock
    private LlmClient client;

    private LlmConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LlmConfigServiceImpl(configDao, client);
    }

    private LlmProviderConfig config(String cipher) {
        LlmProviderConfig c = new LlmProviderConfig();
        c.setModelKey(KEY);
        c.setModelName("豆包 Seed-1.6");
        c.setProvider("doubao");
        c.setEndpointId("doubao-seed-1-6-250615");
        c.setApiKeyCipher(cipher);
        return c;
    }

    @Nested
    @DisplayName("保存凭据")
    class Connect {

        @Test
        @DisplayName("没配过 Key 又留空 → 拒绝（不能存一个空密钥）")
        void rejectsBlankKeyWhenNoneSaved() {
            when(configDao.findByKey(KEY)).thenReturn(config(null));

            BusinessException e = assertThrows(BusinessException.class,
                    () -> service.connect(KEY, "  ", "ep-1", null));
            assertTrue(e.getMessage().contains("API Key"));
        }

        @Test
        @DisplayName("已存 Key 时留空 → 沿用原密文，不覆盖")
        void keepsExistingCipherWhenBlank() {
            String cipher = ContactUtil.encrypt("sk-old");
            when(configDao.findByKey(KEY)).thenReturn(config(cipher));
            when(configDao.saveCredential(eq(KEY), anyString(), anyString(), eq(null))).thenReturn(1);
            when(client.chat(org.mockito.ArgumentMatchers.any(LlmClient.Request.class)))
                    .thenReturn(LlmClient.Result.success("OK", 120L));

            service.connect(KEY, "", "ep-2", null);

            ArgumentCaptor<String> cipherArg = ArgumentCaptor.forClass(String.class);
            verify(configDao).saveCredential(eq(KEY), cipherArg.capture(), eq("ep-2"), eq(null));
            assertEquals(cipher, cipherArg.getValue(), "留空时必须沿用原密文");
        }

        @Test
        @DisplayName("验证通过 → 标记 verified 并返回 ok")
        void marksVerifiedOnSuccess() {
            when(configDao.findByKey(KEY)).thenReturn(config(ContactUtil.encrypt("sk-ok")));
            when(configDao.saveCredential(eq(KEY), anyString(), anyString(), eq(null))).thenReturn(1);
            when(client.chat(org.mockito.ArgumentMatchers.any(LlmClient.Request.class)))
                    .thenReturn(LlmClient.Result.success("OK", 830L));

            Map<String, Object> r = service.connect(KEY, "sk-new", "ep-1", null);

            assertEquals(Boolean.TRUE, r.get("ok"));
            assertEquals(Long.valueOf(830L), r.get("latencyMs"));
            verify(configDao).markVerified(KEY, 830L);
        }

        @Test
        @DisplayName("验证失败 → 落 last_error，ok=false，但凭据确实已保存")
        void recordsErrorOnFailure() {
            /* 注意：connect() 是先 saveCredential 再调 test()，而 test() 会**重新查一次库**。
               所以这里的 stub 必须返回"密钥已落库"的状态 —— 否则 test() 看到的是"没有 Key"，
               断言会拿到"尚未配置 API Key"而不是客户端的 401 文案（假失败）。 */
            when(configDao.findByKey(KEY)).thenReturn(config(ContactUtil.encrypt("sk-bad")));
            when(configDao.saveCredential(eq(KEY), anyString(), anyString(), eq(null))).thenReturn(1);
            when(client.chat(org.mockito.ArgumentMatchers.any(LlmClient.Request.class)))
                    .thenReturn(LlmClient.Result.failure("鉴权失败（HTTP 401）：API Key 无效", 246L, 401));

            Map<String, Object> r = service.connect(KEY, "sk-bad", "ep-1", null);

            assertEquals(Boolean.FALSE, r.get("ok"));
            assertEquals("鉴权失败（HTTP 401）：API Key 无效", r.get("message"));
            verify(configDao).saveCredential(eq(KEY), anyString(), eq("ep-1"), eq(null));
            verify(configDao).markError(eq(KEY), eq("鉴权失败（HTTP 401）：API Key 无效"));
        }
    }

    @Nested
    @DisplayName("调用")
    class Call {

        @Test
        @DisplayName("未配密钥 → 直接失败，不发请求")
        void skipsRequestWithoutKey() {
            when(configDao.findByKey(KEY)).thenReturn(config(null));

            LlmClient.Result r = service.call(KEY, "hi", 16);

            assertFalse(r.isOk());
            assertTrue(r.getError().contains("尚未配置"));
            verifyNoInteractions(client);
        }

        @Test
        @DisplayName("配置不存在 → 失败而非抛异常")
        void failsWhenConfigMissing() {
            when(configDao.findByKey("nope")).thenReturn(null);

            LlmClient.Result r = service.call("nope", "hi", 16);
            assertFalse(r.isOk());
            assertTrue(r.getError().contains("不存在"));
        }

        @Test
        @DisplayName("成功时把真实耗时原样返回")
        void returnsLatency() {
            when(configDao.findByKey(KEY)).thenReturn(config(ContactUtil.encrypt("sk-ok")));
            when(client.chat(org.mockito.ArgumentMatchers.any(LlmClient.Request.class)))
                    .thenReturn(LlmClient.Result.success("OK", 1024L));

            LlmClient.Result r = service.call(KEY, "hi", 16);

            assertTrue(r.isOk());
            assertEquals(1024L, r.getLatencyMs());
            verify(configDao).markVerified(KEY, 1024L);
        }
    }

    @Nested
    @DisplayName("网关地址解析")
    class ResolveUrl {

        @Test
        @DisplayName("豆包走火山方舟默认地址")
        void doubaoDefault() {
            assertEquals("https://ark.cn-beijing.volces.com/api/v3/chat/completions",
                    service.resolveUrl(config(null)));
        }

        @Test
        @DisplayName("文心走千帆 v2 默认地址")
        void qianfanDefault() {
            LlmProviderConfig c = config(null);
            c.setProvider("qianfan");
            assertEquals("https://qianfan.baidubce.com/v2/chat/completions", service.resolveUrl(c));
        }

        @Test
        @DisplayName("自定义网关自动补 /chat/completions")
        void appendsPath() {
            LlmProviderConfig c = config(null);
            c.setProvider("openai-compatible");
            c.setBaseUrl("https://api.deepseek.com/v1");
            assertEquals("https://api.deepseek.com/v1/chat/completions", service.resolveUrl(c));

            c.setBaseUrl("https://api.deepseek.com/v1/");
            assertEquals("https://api.deepseek.com/v1/chat/completions", service.resolveUrl(c));

            c.setBaseUrl("https://x.cn/chat/completions");
            assertEquals("https://x.cn/chat/completions", service.resolveUrl(c));
        }

        @Test
        @DisplayName("OpenAI 兼容协议缺网关 → 明确报错（而不是拼一个猜的地址）")
        void requiresBaseUrl() {
            LlmProviderConfig c = config(null);
            c.setProvider("openai-compatible");
            BusinessException e = assertThrows(BusinessException.class, () -> service.resolveUrl(c));
            assertTrue(e.getMessage().contains("base_url"));
        }
    }

    @Nested
    @DisplayName("降级")
    class Degrade {

        @Test
        @DisplayName("缺表 → 返回空列表，不抛")
        void emptyWhenTableMissing() {
            when(configDao.listAll()).thenThrow(new RuntimeException("Table 'llm_provider_config' doesn't exist"));

            List<LlmProviderConfig> list = service.listAll();
            assertNotNull(list);
            assertEquals(0, list.size());
        }

        @Test
        @DisplayName("有表 → 原样返回")
        void returnsRows() {
            when(configDao.listAll()).thenReturn(Collections.singletonList(config(null)));
            assertEquals(1, service.listAll().size());
        }
    }
}
