package com.zfz.typhoon.model;

import com.zfz.typhoon.testsupport.BeanSweeper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code com.zfz.typhoon.model} 根包实体的反射扫描测试。
 *
 * <p>本包共 26 个实体类，绝大多数是与数据库表列一一对应的纯 POJO。
 * 手写逐字段的 getter/setter 测试既冗长又易漏，这里统一交给
 * {@link BeanSweeper#sweep(Class)} 用反射跑完：
 * 构造 → 逐个 setter 写值 → 对应 getter 回读断言 → toString/hashCode/equals。
 *
 * <p>类的清单<b>显式列出</b>（而非反射枚举包），以免主代码新增类时测试静默漏测：
 * 新增实体必须在这里补一行，让覆盖率增长可见、可追溯。
 *
 * @author 追风者项目组
 */
@DisplayName("model 根包 · 实体反射扫描")
class ModelBeanCoverageTest {

    /**
     * 全部实体类 FQCN。
     *
     * <p>顺序按「核心业务表 → 辅助表」排列，便于阅读。
     */
    private static final String[] CLASS_NAMES = {
            "com.zfz.typhoon.model.Typhoon",              // 台风主表
            "com.zfz.typhoon.model.TyphoonTrack",         // 台风实况轨迹
            "com.zfz.typhoon.model.WindCircle",           // 风圈四象限半径
            "com.zfz.typhoon.model.City",                 // 城市档案
            "com.zfz.typhoon.model.CityImpactRecord",     // 城市影响记录
            "com.zfz.typhoon.model.ForecastAgency",       // 预报机构
            "com.zfz.typhoon.model.ForecastPath",         // 机构预报路径点
            "com.zfz.typhoon.model.ForecastDisagreeRecord", // 预报分歧记录
            "com.zfz.typhoon.model.RiskAssessment",       // 城市风险预评估
            "com.zfz.typhoon.model.AiPredictionRecord",   // 大模型测算记录
            "com.zfz.typhoon.model.SimilarCaseMatch",     // 历史相似案例匹配
            "com.zfz.typhoon.model.SandboxScenario",      // 沙盘情景
            "com.zfz.typhoon.model.DisasterReport",       // 灾害报告
            "com.zfz.typhoon.model.TyphoonAlbum",         // 台风影像相册
            "com.zfz.typhoon.model.DataSource",           // 数据源
            "com.zfz.typhoon.model.SyncLog",              // 同步日志
            "com.zfz.typhoon.model.SystemSetting",        // 系统配置
            "com.zfz.typhoon.model.Notification",         // 通知
            "com.zfz.typhoon.model.User",                 // 平台用户
            "com.zfz.typhoon.model.UserFavorite",         // 用户收藏
            "com.zfz.typhoon.model.UserSubscription",     // 用户订阅
            "com.zfz.typhoon.model.UserBehaviorSurvey",   // 防灾行为问卷
            "com.zfz.typhoon.model.SurveyAggResult",      // 问卷聚合结果
            "com.zfz.typhoon.model.HistoricalComparison", // 历史对比
            "com.zfz.typhoon.model.FeedbackReport",       // 反馈报告
            "com.zfz.typhoon.model.ExportRecord"          // 导出记录
    };

    @Test
    @DisplayName("26 个实体：逐个反射扫描，构造/读写/契约方法均不应抛异常")
    void sweepAllModelEntities() {
        int total = 0;
        int count = 0;

        for (String name : CLASS_NAMES) {
            Class<?> clazz = forName(name);
            int covered = BeanSweeper.sweep(clazz);

            assertTrue(covered >= 0,
                    name + " 没有可用的无参构造，BeanSweeper 无法覆盖，需在总结中单列说明");
            total += covered;
            count++;
        }

        assertTrue(count == CLASS_NAMES.length, "存在未能加载的实体类");
        // 26 个实体合计有数百个属性；阈值取 100 只用于防止「路径写错导致空跑」
        assertTrue(total >= 100,
                "成功回读的属性总数过少（" + total + "），扫描很可能未真正执行");
    }

    /** 显式类名加载，失败直接判为测试失败。 */
    private static Class<?> forName(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("实体类不存在：" + name, e);
        }
    }
}
