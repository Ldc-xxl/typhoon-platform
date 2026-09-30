package com.zfz.typhoon.model.vo;

import com.zfz.typhoon.testsupport.BeanSweeper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code com.zfz.typhoon.model.vo} 视图对象的反射扫描测试。
 *
 * <p>VO 是后端与前端之间的字段契约，字段名写错会直接导致页面渲染空白，
 * 因此这里对每个 VO（含 6 个嵌套明细类）逐字段做「写入 → 回读」验证，
 * 顺带覆盖每个属性的 getter/setter 行。
 *
 * <p>带真实逻辑的静态工厂方法（{@code of} / {@code colorOf} / {@code levelName} 等）
 * 不在本类覆盖，另见 {@link VoLogicTest}。
 *
 * @author 追风者项目组
 */
@DisplayName("model.vo 包 · 视图对象反射扫描")
class VoBeanCoverageTest {

    /** 顶层 VO。 */
    private static final String[] VO_CLASS_NAMES = {
            "com.zfz.typhoon.model.vo.TyphoonVO",          // 台风列表/详情卡片
            "com.zfz.typhoon.model.vo.TrackVO",            // 实况轨迹点
            "com.zfz.typhoon.model.vo.WindCircleVO",       // 风圈
            "com.zfz.typhoon.model.vo.CityVO",             // 城市点位
            "com.zfz.typhoon.model.vo.CityImpactVO",       // 风圈影响城市卡片
            "com.zfz.typhoon.model.vo.ForecastPathVO",     // 机构预报路径
            "com.zfz.typhoon.model.vo.AgencyVO",           // 预报机构说明
            "com.zfz.typhoon.model.vo.DisagreementVO",     // 预报分歧解析
            "com.zfz.typhoon.model.vo.RiskAssessmentVO",   // 城市风险预评估
            "com.zfz.typhoon.model.vo.AiPredictionVO",     // 大模型路径测算
            "com.zfz.typhoon.model.vo.SimilarCaseVO",      // 历史相似案例
            "com.zfz.typhoon.model.vo.ReplayVO",           // 时间轴回放
            "com.zfz.typhoon.model.vo.SandboxScenarioVO",  // 沙盘情景
            "com.zfz.typhoon.model.vo.SurveyAggResultVO",  // 问卷聚合看板
            "com.zfz.typhoon.model.vo.DataSourceVO",       // 数据源监控
            "com.zfz.typhoon.model.vo.ModelConfigVO",      // 大模型接入配置
            "com.zfz.typhoon.model.vo.NotificationVO",     // 顶栏通知中心
            "com.zfz.typhoon.model.vo.UserVO",             // 用户管理表
            "com.zfz.typhoon.model.vo.LoginVO",            // 登录结果
            "com.zfz.typhoon.model.vo.ExportVO"            // 导出结果
    };

    /** VO 内部的嵌套明细类（$ 分隔）。 */
    private static final String[] NESTED_CLASS_NAMES = {
            "com.zfz.typhoon.model.vo.AiPredictionVO$ModelItem",          // 单模型明细
            "com.zfz.typhoon.model.vo.DisagreementVO$DimensionItem",      // 单个分歧维度
            "com.zfz.typhoon.model.vo.ReplayVO$Frame",                    // 回放帧
            "com.zfz.typhoon.model.vo.SandboxScenarioVO$CityImpact",      // 沙盘受影响城市
            "com.zfz.typhoon.model.vo.SimilarCaseVO$DisasterBrief",       // 历史灾情摘要
            "com.zfz.typhoon.model.vo.SurveyAggResultVO$CityAgg"          // 分城市聚合
    };

    @Test
    @DisplayName("20 个顶层 VO：逐个反射扫描，构造/读写/契约方法均不应抛异常")
    void sweepAllTopLevelVos() {
        int total = sweep(VO_CLASS_NAMES, "VO");
        assertTrue(total >= 150,
                "成功回读的属性总数过少（" + total + "），扫描很可能未真正执行");
    }

    @Test
    @DisplayName("6 个嵌套明细类：同样逐个反射扫描")
    void sweepAllNestedVos() {
        int total = sweep(NESTED_CLASS_NAMES, "嵌套 VO");
        assertTrue(total >= 30,
                "嵌套 VO 成功回读的属性总数过少（" + total + "）");
    }

    private static int sweep(String[] names, String label) {
        int total = 0;
        for (String name : names) {
            Class<?> clazz = forName(name);
            int covered = BeanSweeper.sweep(clazz);
            assertTrue(covered >= 0,
                    label + " " + name + " 没有可用的无参构造，无法扫描");
            total += covered;
        }
        return total;
    }

    private static Class<?> forName(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("VO 类不存在：" + name, e);
        }
    }
}
