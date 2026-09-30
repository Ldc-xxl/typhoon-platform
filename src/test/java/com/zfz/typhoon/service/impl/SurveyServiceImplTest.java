package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.SurveyAggResultDao;
import com.zfz.typhoon.dao.UserBehaviorSurveyDao;
import com.zfz.typhoon.model.City;
import com.zfz.typhoon.model.SurveyAggResult;
import com.zfz.typhoon.model.Typhoon;
import com.zfz.typhoon.model.UserBehaviorSurvey;
import com.zfz.typhoon.model.vo.SurveyAggResultVO;
import com.zfz.typhoon.service.CityService;
import com.zfz.typhoon.service.TyphoonService;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SurveyServiceImpl} 单元测试。
 *
 * <h3>量纲红线（必须遵守）</h3>
 * {@code survey_agg_results} 的各比率列本身就是「%」，聚合 SQL 用
 * {@code 100.0 * ...} 算出 0—100 的值。被测类<b>原样输出、不做除法</b>，
 * 因此本测试断言的所有比率都在 0—100，例如加权后 {@code 80.0} 表示 80%。
 *
 * <h3>重点覆盖</h3>
 * <ol>
 *   <li><b>提交校验</b>——每题缺项、取值非法、信任度越界各一条，
 *       以及联系方式脱敏不落明文；</li>
 *   <li><b>加权汇总</b>——率值必须按样本量加权（用 100/300 两地样本验证
 *       它不是简单平均），并单独覆盖"总样本为 0"这一分母为 0 的退化路径；</li>
 *   <li><b>反向科普提示</b>——4 条候选规则各命中一次 + 未命中时的兜底文案；</li>
 *   <li><b>看板裁剪</b>——城市卡片最多 12 条；</li>
 *   <li><b>聚合落库</b>——无样本时清库返回 0，有样本时按城市补齐后批量写入。</li>
 * </ol>
 *
 * @author 追风者项目组
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SurveyServiceImpl · 防灾行为问卷")
class SurveyServiceImplTest {

    private static final long TYPHOON_ID = 1L;

    @Mock
    private UserBehaviorSurveyDao surveyDao;

    @Mock
    private SurveyAggResultDao aggDao;

    @Mock
    private TyphoonService typhoonService;

    @Mock
    private CityService cityService;

    private SurveyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SurveyServiceImpl(surveyDao, aggDao, typhoonService, cityService);
    }

    /* ------------------------------------------------------------------ */
    /*  数据构造                                                            */
    /* ------------------------------------------------------------------ */

    private Typhoon typhoon() {
        Typhoon t = new Typhoon();
        t.setTyphoonId(Long.valueOf(TYPHOON_ID));
        t.setChineseName("飞燕");
        return t;
    }

    private City city(long id, String name, String province) {
        City c = new City();
        c.setCityId(Long.valueOf(id));
        c.setCityName(name);
        c.setProvince(province);
        return c;
    }

    private UserBehaviorSurvey validSurvey() {
        UserBehaviorSurvey s = new UserBehaviorSurvey();
        s.setTyphoonId(Long.valueOf(TYPHOON_ID));
        s.setCityId(Long.valueOf(2L));
        s.setPreparedSupplies(Boolean.TRUE);
        s.setPayAttentionEvacuation(Boolean.FALSE);
        s.setWorriedDisasterType("rain");
        s.setTrustLevel(Integer.valueOf(4));
        s.setContactInfo("13800138000");
        return s;
    }

    /** 一行城市聚合结果；各率值单位均为 0—100。 */
    private SurveyAggResult row(long cityId, int count, double supply, double evac, double rain,
                               double surge, double trust, double over, double under) {
        SurveyAggResult r = new SurveyAggResult();
        r.setCityId(Long.valueOf(cityId));
        r.setTotalCount(Integer.valueOf(count));
        r.setSupplyPrepRate(Double.valueOf(supply));
        r.setEvacuationAttentionRate(Double.valueOf(evac));
        r.setHeavyRainWorryRate(Double.valueOf(rain));
        r.setStormSurgeWorryRate(Double.valueOf(surge));
        r.setHighTrustRate(Double.valueOf(trust));
        r.setRiskOverestimationRate(Double.valueOf(over));
        r.setRiskUnderestimationRate(Double.valueOf(under));
        return r;
    }

    /** 断言业务异常的 code 与提示片段。 */
    private static BusinessException expectBadRequest(String part, Runnable action) {
        BusinessException e = assertThrows(BusinessException.class, action::run);
        assertEquals(Result.CODE_BAD_REQUEST, e.getCode(), "应为 400：" + e.getMessage());
        assertTrue(e.getMessage() != null && e.getMessage().contains(part),
                "提示应包含「" + part + "」，实际：" + e.getMessage());
        return e;
    }

    /* ==================================================================== */
    /*  submit                                                              */
    /* ==================================================================== */

    @Nested
    @DisplayName("submit · 必填与取值校验")
    class SubmitValidation {

        @Test
        @DisplayName("问卷为 null → 400，且不查台风")
        void nullSurvey() {
            expectBadRequest("问卷内容不能为空", () -> service.submit(null));
            Mockito.verifyNoInteractions(typhoonService);
        }

        @Test
        @DisplayName("typhoonId 缺失 / 非正 → 400")
        void invalidTyphoonId() {
            UserBehaviorSurvey s = validSurvey();
            s.setTyphoonId(null);
            expectBadRequest("typhoonId", () -> service.submit(s));

            s.setTyphoonId(Long.valueOf(0L));
            expectBadRequest("typhoonId", () -> service.submit(s));
        }

        @Test
        @DisplayName("cityId 缺失 / 非正 → 400")
        void invalidCityId() {
            UserBehaviorSurvey s = validSurvey();
            s.setCityId(null);
            expectBadRequest("cityId", () -> service.submit(s));

            s.setCityId(Long.valueOf(-3L));
            expectBadRequest("cityId", () -> service.submit(s));
        }

        @Test
        @DisplayName("台风不存在时把外键异常转成业务异常")
        void typhoonMissing() {
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID))
                    .thenThrow(BusinessException.notFound("台风不存在：id = 1"));

            BusinessException e = assertThrows(BusinessException.class,
                    () -> service.submit(validSurvey()));
            assertEquals(Result.CODE_NOT_FOUND, e.getCode());
        }

        @Test
        @DisplayName("q1 是否储备物资为必答")
        void missingQ1() {
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            UserBehaviorSurvey s = validSurvey();
            s.setPreparedSupplies(null);

            expectBadRequest("q1", () -> service.submit(s));
        }

        @Test
        @DisplayName("q2 是否关注撤离信息为必答")
        void missingQ2() {
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            UserBehaviorSurvey s = validSurvey();
            s.setPayAttentionEvacuation(null);

            expectBadRequest("q2", () -> service.submit(s));
        }

        @Test
        @DisplayName("q3 最担心的灾害类型：空白与非法取值都拒绝")
        void invalidQ3() {
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());

            UserBehaviorSurvey blank = validSurvey();
            blank.setWorriedDisasterType("   ");
            expectBadRequest("q3", () -> service.submit(blank));

            UserBehaviorSurvey illegal = validSurvey();
            illegal.setWorriedDisasterType("EARTHQUAKE");
            expectBadRequest("取值非法", () -> service.submit(illegal));
        }

        @Test
        @DisplayName("q4 信任度为必答，且取值必须在 1—5")
        void invalidQ4() {
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());

            UserBehaviorSurvey missing = validSurvey();
            missing.setTrustLevel(null);
            expectBadRequest("q4", () -> service.submit(missing));

            UserBehaviorSurvey tooLow = validSurvey();
            tooLow.setTrustLevel(Integer.valueOf(0));
            expectBadRequest("trustLevel", () -> service.submit(tooLow));

            UserBehaviorSurvey tooHigh = validSurvey();
            tooHigh.setTrustLevel(Integer.valueOf(6));
            expectBadRequest("trustLevel", () -> service.submit(tooHigh));
        }
    }

    @Nested
    @DisplayName("submit · 正常落库")
    class SubmitHappyPath {

        @Test
        @DisplayName("补齐版本与提交时间，灾害类型转大写，联系方式脱敏后入库")
        void persistsWithDefaults() {
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            Mockito.when(surveyDao.insert(Mockito.any(UserBehaviorSurvey.class))).thenReturn(99L);

            long id = service.submit(validSurvey());

            assertEquals(99L, id);
            ArgumentCaptor<UserBehaviorSurvey> captor =
                    ArgumentCaptor.forClass(UserBehaviorSurvey.class);
            Mockito.verify(surveyDao).insert(captor.capture());
            UserBehaviorSurvey saved = captor.getValue();
            assertEquals("v1.0", saved.getQuestionSetVersion());
            assertNotNull(saved.getSubmitTime());
            assertEquals("RAIN", saved.getWorriedDisasterType());
            assertEquals("138****8000", saved.getContactInfo());
            assertFalse(saved.getContactInfo().contains("13800138000"));
        }

        @Test
        @DisplayName("信任度下边界 1 与上边界 5 均可提交")
        void trustLevelBoundaries() {
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            Mockito.when(surveyDao.insert(Mockito.any(UserBehaviorSurvey.class))).thenReturn(1L);

            UserBehaviorSurvey low = validSurvey();
            low.setTrustLevel(Integer.valueOf(1));
            assertEquals(1L, service.submit(low));

            UserBehaviorSurvey high = validSurvey();
            high.setTrustLevel(Integer.valueOf(5));
            assertEquals(1L, service.submit(high));
        }

        @Test
        @DisplayName("未填联系方式时脱敏结果为 null，不会写入空串")
        void blankContactBecomesNull() {
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            Mockito.when(surveyDao.insert(Mockito.any(UserBehaviorSurvey.class))).thenReturn(1L);
            UserBehaviorSurvey s = validSurvey();
            s.setContactInfo("   ");

            service.submit(s);

            ArgumentCaptor<UserBehaviorSurvey> captor =
                    ArgumentCaptor.forClass(UserBehaviorSurvey.class);
            Mockito.verify(surveyDao).insert(captor.capture());
            assertNull(captor.getValue().getContactInfo());
        }

        @Test
        @DisplayName("同一份问卷连提两次不会去重：各自落一条（当前实现无幂等约束）")
        void duplicateSubmissionsBothInserted() {
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            Mockito.when(surveyDao.insert(Mockito.any(UserBehaviorSurvey.class)))
                    .thenReturn(1L, 2L);

            assertEquals(1L, service.submit(validSurvey()));
            assertEquals(2L, service.submit(validSurvey()));

            Mockito.verify(surveyDao, Mockito.times(2)).insert(Mockito.any(UserBehaviorSurvey.class));
        }
    }

    /* ==================================================================== */
    /*  agg · 全台风视图                                                     */
    /* ==================================================================== */

    @Nested
    @DisplayName("agg · 全台风视图与加权汇总")
    class AggAllCities {

        @Test
        @DisplayName("无样本时给出 0 样本与引导文案，不计算率值")
        void noSamples() {
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            Mockito.when(surveyDao.aggregateByTyphoon(TYPHOON_ID))
                    .thenReturn(new ArrayList<SurveyAggResult>());

            SurveyAggResultVO vo = service.agg(TYPHOON_ID, null);

            assertEquals("飞燕", vo.getTyphoonName());
            assertEquals(Integer.valueOf(0), vo.getSampleSize());
            assertFalse(vo.getEnoughSamples().booleanValue());
            assertTrue(vo.getHint().contains("暂无问卷样本"));
        }

        @Test
        @DisplayName("多城市按样本量加权：100 样本 50% 与 300 样本 90% → 80%（而非简单平均 70%）")
        void weightedBySampleSize() {
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            Mockito.when(surveyDao.aggregateByTyphoon(TYPHOON_ID)).thenReturn(Arrays.asList(
                    row(1L, 100, 50, 40, 20, 20, 50, 10, 10),
                    row(2L, 300, 90, 60, 10, 10, 80, 5, 5)));

            SurveyAggResultVO vo = service.agg(TYPHOON_ID, null);

            assertEquals(Integer.valueOf(400), vo.getSampleSize());
            assertTrue(vo.getEnoughSamples().booleanValue());
            assertEquals(80.0D, vo.getSuppliesRate().doubleValue(), 1e-9);
            assertEquals(55.0D, vo.getEvacuationRate().doubleValue(), 1e-9);
            assertEquals(2, vo.getCityBreakdown().size());
        }

        @Test
        @DisplayName("总样本为 0（各城市样本量缺失）时率值全部归零，不产生除零")
        void zeroTotalCountBranch() {
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            SurveyAggResult empty = new SurveyAggResult();
            empty.setCityId(Long.valueOf(1L));
            empty.setTotalCount(null);
            empty.setSupplyPrepRate(Double.valueOf(90D));
            Mockito.when(surveyDao.aggregateByTyphoon(TYPHOON_ID)).thenReturn(Arrays.asList(empty));

            SurveyAggResultVO vo = service.agg(TYPHOON_ID, null);

            assertEquals(Integer.valueOf(0), vo.getSampleSize());
            assertFalse(vo.getEnoughSamples().booleanValue());
            assertEquals(0.0D, vo.getSuppliesRate().doubleValue(), 1e-9);
            assertEquals(0.0D, vo.getHighTrustRate().doubleValue(), 1e-9);
            assertTrue(vo.getHint().contains("物资储备率仅"));
        }

        @Test
        @DisplayName("样本量不足 30 时仍返回数据，但标记样本不足")
        void notEnoughSamples() {
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            Mockito.when(surveyDao.aggregateByTyphoon(TYPHOON_ID)).thenReturn(Arrays.asList(
                    row(1L, 29, 50, 50, 10, 10, 50, 5, 5)));

            SurveyAggResultVO vo = service.agg(TYPHOON_ID, null);

            assertEquals(Integer.valueOf(29), vo.getSampleSize());
            assertFalse(vo.getEnoughSamples().booleanValue());
        }

        @Test
        @DisplayName("城市卡片最多 12 条（超出部分裁剪）")
        void cityCardsCappedAtTwelve() {
            List<SurveyAggResult> rows = new ArrayList<SurveyAggResult>();
            for (int i = 1; i <= 13; i++) {
                rows.add(row(i, 40, 50, 50, 10, 10, 50, 5, 5));
            }
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            Mockito.when(surveyDao.aggregateByTyphoon(TYPHOON_ID)).thenReturn(rows);

            assertEquals(12, service.agg(TYPHOON_ID, null).getCityBreakdown().size());
        }

        @Test
        @DisplayName("最担心的灾害分布：大风由 100 − 暴雨 − 风暴潮 反推，且不为负")
        void concernDistribution() {
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            Mockito.when(surveyDao.aggregateByTyphoon(TYPHOON_ID)).thenReturn(Arrays.asList(
                    row(1L, 40, 50, 50, 70, 40, 50, 5, 5)));

            SurveyAggResultVO vo = service.agg(TYPHOON_ID, null);

            assertEquals(70.0D, vo.getConcernDist().get("暴雨").doubleValue(), 1e-9);
            assertEquals(40.0D, vo.getConcernDist().get("风暴潮").doubleValue(), 1e-9);
            assertEquals(0.0D, vo.getConcernDist().get("大风").doubleValue(), 1e-9,
                    "反推值为负时应夹到 0");
        }
    }

    /* ==================================================================== */
    /*  agg · 单城市视图                                                     */
    /* ==================================================================== */

    @Nested
    @DisplayName("agg · 单城市视图")
    class AggOneCity {

        @Test
        @DisplayName("该城市无聚合行 → 0 样本占位")
        void noRow() {
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            Mockito.when(surveyDao.aggregateByTyphoonAndCity(TYPHOON_ID, 2L)).thenReturn(null);

            SurveyAggResultVO vo = service.agg(TYPHOON_ID, Long.valueOf(2L));

            assertEquals(Integer.valueOf(0), vo.getSampleSize());
            assertTrue(vo.getHint().contains("该城市暂无问卷样本"));
        }

        @Test
        @DisplayName("聚合行样本量为 0 → 同样按 0 样本处理")
        void zeroCount() {
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            Mockito.when(surveyDao.aggregateByTyphoonAndCity(TYPHOON_ID, 2L))
                    .thenReturn(row(2L, 0, 50, 50, 10, 10, 50, 5, 5));

            assertEquals(Integer.valueOf(0), service.agg(TYPHOON_ID, Long.valueOf(2L)).getSampleSize());
        }

        @Test
        @DisplayName("命中样本时补城市名并生成分城市明细")
        void withCity() {
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            Mockito.when(surveyDao.aggregateByTyphoonAndCity(TYPHOON_ID, 2L))
                    .thenReturn(row(2L, 40, 66.5, 50, 10, 10, 50, 5, 5));
            Mockito.when(cityService.getById(2L)).thenReturn(city(2L, "厦门", "福建"));

            SurveyAggResultVO vo = service.agg(TYPHOON_ID, Long.valueOf(2L));

            assertEquals(Integer.valueOf(40), vo.getSampleSize());
            assertTrue(vo.getEnoughSamples().booleanValue());
            assertEquals(66.5D, vo.getSuppliesRate().doubleValue(), 1e-9);
            assertEquals(1, vo.getCityBreakdown().size());
            assertEquals("厦门", vo.getCityBreakdown().get(0).getCityName());
        }

        @Test
        @DisplayName("城市缓存里找不名称时仍返回明细，只是城市名为空")
        void cityCacheMiss() {
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            Mockito.when(surveyDao.aggregateByTyphoonAndCity(TYPHOON_ID, 2L))
                    .thenReturn(row(2L, 40, 50, 50, 10, 10, 50, 5, 5));
            Mockito.when(cityService.getById(2L)).thenReturn(null);

            SurveyAggResultVO vo = service.agg(TYPHOON_ID, Long.valueOf(2L));

            assertEquals(1, vo.getCityBreakdown().size());
            assertNull(vo.getCityBreakdown().get(0).getCityName());
        }
    }

    /* ==================================================================== */
    /*  buildHint · 反向科普提示                                             */
    /* ==================================================================== */

    @Nested
    @DisplayName("agg · 反向科普提示规则")
    class HintRules {

        private String hintFor(SurveyAggResult only) {
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            Mockito.when(surveyDao.aggregateByTyphoon(TYPHOON_ID)).thenReturn(Arrays.asList(only));
            return service.agg(TYPHOON_ID, null).getHint();
        }

        @Test
        @DisplayName("规则一：多数担心暴雨内涝、少数储备物资 → 推送应急包清单")
        void ruleRainVsSupply() {
            assertTrue(hintFor(row(1L, 40, 30, 50, 70, 10, 50, 5, 5)).contains("暴雨内涝"));
        }

        @Test
        @DisplayName("规则二：多数担心风暴潮、少数关注撤离 → 提示撤离点位图")
        void ruleSurgeVsEvacuation() {
            assertTrue(hintFor(row(1L, 40, 50, 20, 10, 70, 50, 5, 5)).contains("撤离路线"));
        }

        @Test
        @DisplayName("规则三：信任度低且判断偏差大 → 科普预报不确定性")
        void ruleLowTrust() {
            assertTrue(hintFor(row(1L, 40, 50, 50, 10, 10, 30, 35, 35)).contains("不确定性"));
        }

        @Test
        @DisplayName("规则四：物资储备率整体偏低 → 提醒准备物资")
        void ruleLowSupply() {
            assertTrue(hintFor(row(1L, 40, 30, 50, 10, 10, 50, 5, 5)).contains("物资储备率仅"));
        }

        @Test
        @DisplayName("都没命中时如实说「基本匹配」，不硬凑提示")
        void noRuleMatched() {
            assertTrue(hintFor(row(1L, 40, 60, 60, 10, 10, 50, 5, 5)).contains("基本匹配"));
        }
    }

    /* ==================================================================== */
    /*  rebuild / countSamples                                              */
    /* ==================================================================== */

    @Nested
    @DisplayName("rebuild / countSamples")
    class RebuildAndCount {

        @Test
        @DisplayName("无样本时清空旧聚合，返回 0")
        void rebuildEmpty() {
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            Mockito.when(surveyDao.aggregateByTyphoon(TYPHOON_ID))
                    .thenReturn(new ArrayList<SurveyAggResult>());
            Mockito.when(aggDao.deleteByTyphoon(TYPHOON_ID)).thenReturn(3);

            assertEquals(0, service.rebuild(TYPHOON_ID));
            Mockito.verify(aggDao).deleteByTyphoon(TYPHOON_ID);
            Mockito.verify(aggDao, Mockito.never()).upsertAll(Mockito.anyList());
        }

        @Test
        @DisplayName("有样本时补齐台风名与城市信息后批量幂等写入")
        void rebuildPersists() {
            SurveyAggResult withCity = row(2L, 40, 50, 50, 10, 10, 50, 5, 5);
            SurveyAggResult noCity = row(3L, 10, 50, 50, 10, 10, 50, 5, 5);
            noCity.setCityId(null);
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            Mockito.when(surveyDao.aggregateByTyphoon(TYPHOON_ID))
                    .thenReturn(Arrays.asList(withCity, noCity));
            Mockito.when(cityService.getById(2L)).thenReturn(city(2L, "厦门", "福建"));
            Mockito.when(aggDao.upsertAll(Mockito.anyList())).thenReturn(2);

            assertEquals(2, service.rebuild(TYPHOON_ID));

            ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
            Mockito.verify(aggDao).upsertAll(captor.capture());
            List<SurveyAggResult> saved = captor.getValue();
            assertEquals(2, saved.size());
            assertEquals("飞燕", saved.get(0).getTyphoonName());
            assertEquals("厦门", saved.get(0).getCityName());
            assertNotNull(saved.get(0).getStatTime());
            assertNull(saved.get(1).getCityName());
        }

        @Test
        @DisplayName("countSamples 直接转发到明细 DAO")
        void countSamples() {
            Mockito.when(surveyDao.countByTyphoon(TYPHOON_ID)).thenReturn(7);
            assertEquals(7, service.countSamples(TYPHOON_ID));
        }
    }
}
