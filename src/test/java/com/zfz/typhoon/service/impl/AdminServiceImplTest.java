package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.AiPredictionRecordDao;
import com.zfz.typhoon.dao.SandboxScenarioDao;
import com.zfz.typhoon.dao.UserBehaviorSurveyDao;
import com.zfz.typhoon.service.DataSourceService;
import com.zfz.typhoon.service.FeedbackService;
import com.zfz.typhoon.service.TyphoonService;
import com.zfz.typhoon.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AdminServiceImpl} 单元测试。
 *
 * <p>后台「统计图表」接口（阶段四④ ECharts 数据来源）的全部计算都落在
 * {@link AdminServiceImpl#stats()} 里：把每日新增累加为累计增长曲线、
 * 按角色拆出分布饼图。本类用 Mockito 替身 UserService，
 * 断言这两段聚合逻辑的数值正确，避免前端拿到错位数据。
 *
 * <p>{@code stats()} 只触碰 {@link UserService}，其余 6 个协作者不参与，
 * 因此全部用 mock 注入即可，不依赖真实数据库。
 *
 * @author 追风者项目组
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminServiceImpl · 后台统计聚合")
class AdminServiceImplTest {

    @Mock
    private UserService userService;

    @Mock
    private TyphoonService typhoonService;

    @Mock
    private DataSourceService dataSourceService;

    @Mock
    private FeedbackService feedbackService;

    @Mock
    private SandboxScenarioDao scenarioDao;

    @Mock
    private UserBehaviorSurveyDao surveyDao;

    @Mock
    private AiPredictionRecordDao aiDao;

    private AdminServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminServiceImpl(userService, typhoonService, dataSourceService,
                feedbackService, scenarioDao, surveyDao, aiDao);
    }

    @Nested
    @DisplayName("stats · 累计增长 + 角色分布")
    class Stats {

        @Test
        @DisplayName("每日新增累加为单调递增累计曲线")
        void cumulativeGrowth() {
            Mockito.when(userService.growthTrend()).thenReturn(Arrays.asList(
                    dailyRow("2026-09-01", 3),
                    dailyRow("2026-09-02", 5),
                    dailyRow("2026-09-03", 2)));
            Mockito.when(userService.stats()).thenReturn(statsMap(10, 2, 7, 1));

            Map<String, Object> out = service.stats();
            List<Map<String, Object>> growth = (List<Map<String, Object>>) out.get("userGrowth");

            assertEquals(3, growth.size());
            assertEquals(Integer.valueOf(3), growth.get(0).get("count"));   // 3
            assertEquals(Integer.valueOf(8), growth.get(1).get("count"));   // 3+5
            assertEquals(Integer.valueOf(10), growth.get(2).get("count")); // 3+5+2
            assertEquals("2026-09-03", growth.get(2).get("date"));
        }

        @Test
        @DisplayName("无用户增长数据时曲线为空，不报错")
        void emptyGrowth() {
            Mockito.when(userService.growthTrend()).thenReturn(Arrays.<Map<String, Object>>asList());
            Mockito.when(userService.stats()).thenReturn(statsMap(0, 0, 0, 0));

            Map<String, Object> out = service.stats();
            assertTrue(((List<?>) out.get("userGrowth")).isEmpty());
        }

        @Test
        @DisplayName("角色分布饼图：管理员取 admin，普通用户取 total-admin")
        void roleDistribution() {
            Mockito.when(userService.growthTrend()).thenReturn(Arrays.<Map<String, Object>>asList());
            // total=10, admin=2 -> 普通用户=8
            Mockito.when(userService.stats()).thenReturn(statsMap(10, 2, 7, 1));

            Map<String, Object> out = service.stats();
            List<Map<String, Object>> dist = (List<Map<String, Object>>) out.get("dataDistribution");

            assertEquals(2, dist.size());
            assertEquals("管理员", dist.get(0).get("name"));
            assertEquals(Integer.valueOf(2), dist.get(0).get("value"));
            assertEquals("普通用户", dist.get(1).get("name"));
            assertEquals(Integer.valueOf(8), dist.get(1).get("value"));   // 10 - 2
        }

        @Test
        @DisplayName("角色分布中 admin 大于 total 时不出现负数")
        void roleDistributionNoNegative() {
            Mockito.when(userService.growthTrend()).thenReturn(Arrays.<Map<String, Object>>asList());
            Mockito.when(userService.stats()).thenReturn(statsMap(3, 5, 0, 0));

            Map<String, Object> out = service.stats();
            List<Map<String, Object>> dist = (List<Map<String, Object>>) out.get("dataDistribution");

            assertEquals(Integer.valueOf(5), dist.get(0).get("value"));   // 管理员
            assertEquals(Integer.valueOf(0), dist.get(1).get("value"));   // max(0, 3-5)=0
        }
    }

    /** 构造一行「每日新增」数据。 */
    private static Map<String, Object> dailyRow(String date, int count) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("date", date);
        row.put("count", Integer.valueOf(count));
        return row;
    }

    /** 构造 userService.stats() 返回的用户统计。 */
    private static Map<String, Integer> statsMap(int total, int admin, int normal, int disabled) {
        Map<String, Integer> m = new LinkedHashMap<String, Integer>();
        m.put("total", Integer.valueOf(total));
        m.put("admin", Integer.valueOf(admin));
        m.put("normal", Integer.valueOf(normal));
        m.put("disabled", Integer.valueOf(disabled));
        return m;
    }
}
