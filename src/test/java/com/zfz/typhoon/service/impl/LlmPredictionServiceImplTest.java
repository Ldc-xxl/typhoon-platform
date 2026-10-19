package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.AiPredictionRecordDao;
import com.zfz.typhoon.model.AiPredictionRecord;
import com.zfz.typhoon.model.ForecastPath;
import com.zfz.typhoon.model.LlmProviderConfig;
import com.zfz.typhoon.model.Typhoon;
import com.zfz.typhoon.model.vo.AiPredictionVO;
import com.zfz.typhoon.service.AiPredictionService;
import com.zfz.typhoon.service.ForecastService;
import com.zfz.typhoon.service.LlmConfigService;
import com.zfz.typhoon.service.TyphoonService;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LlmPredictionServiceImpl} 单元测试。
 *
 * <p>这一层最容易出的两类事故，测试盯的就是它们：
 * <ol>
 *   <li><b>模型胡说八道也照单全收</b>：越界坐标、点数不足、根本不是 JSON
 *       —— 必须拒收，不能画到地图上；</li>
 *   <li><b>调用失败却悄悄回退</b>：失败必须抛异常，绝不能拿融合结果冒充模型输出。</li>
 * </ol>
 *
 * @author 追风者项目组
 */
@ExtendWith(MockitoExtension.class)
class LlmPredictionServiceImplTest {

    private static final String KEY = "doubao-seed-1.6";
    private static final long TYPHOON_ID = 3L;

    @Mock
    private LlmConfigService llmConfigService;

    @Mock
    private TyphoonService typhoonService;

    @Mock
    private ForecastService forecastService;

    @Mock
    private AiPredictionRecordDao recordDao;

    @Mock
    private AiPredictionService aiPredictionService;

    private LlmPredictionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LlmPredictionServiceImpl(llmConfigService, typhoonService, forecastService,
                recordDao, aiPredictionService);
    }

    private Typhoon typhoon() {
        Typhoon t = new Typhoon();
        t.setTyphoonId(Long.valueOf(TYPHOON_ID));
        t.setChineseName("飞燕");
        t.setInternationalId("202613");
        t.setMaxWindForce(Integer.valueOf(12));
        return t;
    }

    private Map<String, List<ForecastPath>> twoAgencies() {
        Map<String, List<ForecastPath>> map = new LinkedHashMap<String, List<ForecastPath>>();
        map.put("中央气象台", series(121.0, 22.0));
        map.put("日本气象厅", series(122.5, 23.5));
        return map;
    }

    private List<ForecastPath> series(double lon, double lat) {
        List<ForecastPath> list = new ArrayList<ForecastPath>();
        LocalDateTime base = LocalDateTime.of(2026, 10, 18, 20, 0);
        for (int i = 0; i < 3; i++) {
            ForecastPath p = new ForecastPath();
            p.setForecastTime(base.plusHours(6L * i));
            p.setLongitude(Double.valueOf(lon + i * 0.5D));
            p.setLatitude(Double.valueOf(lat + i * 0.3D));
            p.setForecastWindForce(Integer.valueOf(12 - i));
            list.add(p);
        }
        return list;
    }

    private String goodJson() {
        return "{\"points\":[{\"time\":\"10-18 20:00\",\"lon\":121.2,\"lat\":22.1,\"force\":12},"
                + "{\"time\":\"10-19 02:00\",\"lon\":122.0,\"lat\":22.6,\"force\":11},"
                + "{\"time\":\"10-19 08:00\",\"lon\":122.8,\"lat\":23.0,\"force\":10}],"
                + "\"confidence\":78,\"summary\":\"路径稳定西北行，需关注浙闽沿海。\"}";
    }

    private void stubModelConfig() {
        LlmProviderConfig cfg = new LlmProviderConfig();
        cfg.setModelKey(KEY);
        cfg.setModelName("豆包 Seed-1.6");
        when(llmConfigService.find(KEY)).thenReturn(cfg);
    }

    @Nested
    @DisplayName("成功路径")
    class Success {

        @Test
        @DisplayName("解析模型 JSON 并落库：模型名、耗时、置信度都来自真实结果")
        void persistsRealResult() {
            when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            when(forecastService.groupByAgency(TYPHOON_ID)).thenReturn(twoAgencies());
            when(llmConfigService.call(eq(KEY), anyString(), anyInt()))
                    .thenReturn(LlmClient.Result.success(goodJson(), 912L));
            when(recordDao.insert(any(AiPredictionRecord.class))).thenReturn(1L);
            stubModelConfig();

            AiPredictionVO vo = service.predict(TYPHOON_ID, KEY);

            assertEquals(3, vo.getPoints().size());
            assertEquals(Double.valueOf(0.78D), vo.getConfidence());
            assertTrue(vo.getSummary().contains("浙闽"));
            assertEquals("北纬 23.0° / 东经 122.8° 附近", vo.getLandingRange());

            ArgumentCaptor<AiPredictionRecord> captor = ArgumentCaptor.forClass(AiPredictionRecord.class);
            verify(recordDao).insert(captor.capture());
            AiPredictionRecord saved = captor.getValue();
            assertEquals("豆包 Seed-1.6", saved.getModelName());
            assertEquals(Integer.valueOf(912), saved.getLatencyMs());
            assertEquals(Double.valueOf(78D), saved.getConfidenceScore());
            assertEquals("llm-v1", saved.getPromptVersion());
        }

        @Test
        @DisplayName("提示词里必须带上机构名与「只输出 JSON」的约束")
        void promptContainsConstraints() {
            String prompt = service.buildPrompt(typhoon(), twoAgencies());

            assertTrue(prompt.contains("中央气象台"));
            assertTrue(prompt.contains("日本气象厅"));
            assertTrue(prompt.contains("只输出一个 JSON 对象"));
            assertTrue(prompt.contains("100~180"));
        }

        @Test
        @DisplayName("容忍 markdown 代码块包裹")
        void parsesFencedJson() {
            String fenced = "```json\n" + goodJson() + "\n```";
            LlmPredictionServiceImpl.Parsed p = service.parse(fenced);
            assertEquals(3, p.points.size());
            assertEquals(78, p.confidence);
        }

        @Test
        @DisplayName("容忍 JSON 前后有说明文字")
        void parsesWithProse() {
            LlmPredictionServiceImpl.Parsed p = service.parse("好的，结果如下：\n" + goodJson() + "\n以上。");
            assertEquals(3, p.points.size());
        }
    }

    @Nested
    @DisplayName("拒收与失败")
    class Reject {

        @Test
        @DisplayName("模型调用失败 → 抛异常，一条记录都不写")
        void throwsOnCallFailure() {
            when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            when(forecastService.groupByAgency(TYPHOON_ID)).thenReturn(twoAgencies());
            when(llmConfigService.call(eq(KEY), anyString(), anyInt()))
                    .thenReturn(LlmClient.Result.failure("鉴权失败（HTTP 401）", 200L, 401));

            BusinessException e = assertThrows(BusinessException.class,
                    () -> service.predict(TYPHOON_ID, KEY));
            assertTrue(e.getMessage().contains("鉴权失败"));
            verify(recordDao, never()).insert(any(AiPredictionRecord.class));
        }

        @Test
        @DisplayName("返回的不是 JSON → 拒收")
        void rejectsNonJson() {
            when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            when(forecastService.groupByAgency(TYPHOON_ID)).thenReturn(twoAgencies());
            when(llmConfigService.call(eq(KEY), anyString(), anyInt()))
                    .thenReturn(LlmClient.Result.success("抱歉，我无法完成该请求。", 300L));

            assertThrows(BusinessException.class, () -> service.predict(TYPHOON_ID, KEY));
            verify(recordDao, never()).insert(any(AiPredictionRecord.class));
        }

        @Test
        @DisplayName("坐标越界（经度 200°E）→ 拒收而不是画到地图外")
        void rejectsOutOfRange() {
            String bad = "{\"points\":[{\"lon\":200,\"lat\":22},{\"lon\":121,\"lat\":23}],\"confidence\":50}";
            when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            when(forecastService.groupByAgency(TYPHOON_ID)).thenReturn(twoAgencies());
            when(llmConfigService.call(eq(KEY), anyString(), anyInt()))
                    .thenReturn(LlmClient.Result.success(bad, 300L));

            BusinessException e = assertThrows(BusinessException.class,
                    () -> service.predict(TYPHOON_ID, KEY));
            assertTrue(e.getMessage().contains("越界"));
        }

        @Test
        @DisplayName("只有 1 个点 → 拒收（画不成线）")
        void rejectsTooFewPoints() {
            String one = "{\"points\":[{\"lon\":121,\"lat\":22}],\"confidence\":50,\"summary\":\"x\"}";
            when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            when(forecastService.groupByAgency(TYPHOON_ID)).thenReturn(twoAgencies());
            when(llmConfigService.call(eq(KEY), anyString(), anyInt()))
                    .thenReturn(LlmClient.Result.success(one, 300L));

            assertThrows(BusinessException.class, () -> service.predict(TYPHOON_ID, KEY));
        }

        @Test
        @DisplayName("没有任何机构预报 → 直接拒绝，不发请求")
        void rejectsWithoutForecast() {
            when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            when(forecastService.groupByAgency(TYPHOON_ID))
                    .thenReturn(new LinkedHashMap<String, List<ForecastPath>>());

            assertThrows(BusinessException.class, () -> service.predict(TYPHOON_ID, KEY));
            verify(llmConfigService, never()).call(anyString(), anyString(), anyInt());
        }

        @Test
        @DisplayName("置信度超过 95 会被压到 95，不出现「100% 把握」")
        void capsConfidence() {
            LlmPredictionServiceImpl.Parsed p = service.parse(
                    "{\"points\":[{\"lon\":121,\"lat\":22},{\"lon\":122,\"lat\":23}],\"confidence\":100}");
            assertEquals(95, p.confidence);
        }

        @Test
        @DisplayName("缺 summary 时补一句说明，不留空串")
        void fillsMissingSummary() {
            LlmPredictionServiceImpl.Parsed p = service.parse(
                    "{\"points\":[{\"lon\":121,\"lat\":22},{\"lon\":122,\"lat\":23}],\"confidence\":60}");
            assertNotNull(p.summary);
            assertTrue(p.summary.length() > 0);
        }
    }

    @Nested
    @DisplayName("与融合路径的偏差")
    class Deviation {

        @Test
        @DisplayName("融合结果不可用时留 null，不编数字")
        void nullWhenFusionMissing() {
            when(aiPredictionService.latest(TYPHOON_ID)).thenReturn(null);
            assertEquals(null, service.deviationVsFusion(TYPHOON_ID, new double[]{121D, 22D}));
        }

        @Test
        @DisplayName("有融合路径时给出量级参考（km）")
        void computesDistance() {
            AiPredictionVO fusion = new AiPredictionVO();
            List<List<Double>> pts = new ArrayList<List<Double>>();
            pts.add(Arrays.asList(Double.valueOf(121D), Double.valueOf(22D)));
            pts.add(Arrays.asList(Double.valueOf(122D), Double.valueOf(23D)));
            fusion.setPoints(pts);
            when(aiPredictionService.latest(TYPHOON_ID)).thenReturn(fusion);

            Double km = service.deviationVsFusion(TYPHOON_ID, new double[]{122D, 23D});
            assertEquals(Double.valueOf(0D), km);
        }
    }
}
