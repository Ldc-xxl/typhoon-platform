package com.zfz.typhoon.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code com.zfz.typhoon.model} 根包中<b>带真实逻辑</b>的实体方法测试。
 *
 * <p>反射扫描（见 {@code ModelBeanCoverageTest}）只能走到 getter/setter 的表面，
 * 这些方法内部的分支必须单独打：
 * <ul>
 *   <li>{@link Typhoon#isActive()} / {@link Typhoon#getYear()} —— 台风生命周期与年份推导；</li>
 *   <li>{@link TyphoonTrack#isAt(LocalDateTime)} —— 轨迹点是否为当前中心；</li>
 *   <li>{@link City#getBaseRiskLevelText()} / {@link RiskAssessment#getRiskLevelText()}
 *       —— 中文等级文案，界面直接展示；</li>
 *   <li>{@link SystemSetting#asDouble(double)} —— 配置解析的容错；</li>
 *   <li>{@link WindCircle} 的半径取值 / 均值 / 标准差 / 十进制转换 —— 相似度计算的地基。</li>
 * </ul>
 *
 * @author 追风者项目组
 */
@DisplayName("model 根包 · 实体逻辑")
class ModelLogicTest {

    @Nested
    @DisplayName("Typhoon · 生命周期与年份")
    class TyphoonLogic {

        @Test
        @DisplayName("isActive 对 ACTIVE 大小写不敏感")
        void isActive() {
            assertTrue(typhoon("ACTIVE").isActive());
            assertTrue(typhoon("active").isActive());
            assertTrue(typhoon("Active").isActive());
        }

        @Test
        @DisplayName("非 ACTIVE 或 status 为空时不是活跃台风")
        void isNotActive() {
            assertFalse(typhoon("FINISHED").isActive());
            assertFalse(typhoon("ARCHIVED").isActive());
            assertFalse(typhoon(null).isActive());
            assertFalse(typhoon("").isActive());
        }

        @Test
        @DisplayName("getYear 取自生成时间")
        void yearFromGenerateTime() {
            Typhoon t = typhoon("ACTIVE");
            t.setGenerateTime(LocalDateTime.of(2026, 9, 13, 8, 0, 0));
            assertEquals(2026, t.getYear());
        }

        @Test
        @DisplayName("生成时间为 null 时 getYear 返回 0")
        void yearNullSafe() {
            assertEquals(0, typhoon("ACTIVE").getYear());
        }

        private Typhoon typhoon(String status) {
            Typhoon t = new Typhoon();
            t.setStatus(status);
            return t;
        }
    }

    @Nested
    @DisplayName("TyphoonTrack · 当前中心判定")
    class TrackLogic {

        @Test
        @DisplayName("观测时刻与最新时刻一致时 isAt 为 true")
        void atLatest() {
            LocalDateTime t = LocalDateTime.of(2026, 9, 13, 8, 0, 0);
            assertTrue(track(t).isAt(t));
        }

        @Test
        @DisplayName("观测时刻与最新时刻不一致时为 false")
        void notAtLatest() {
            assertFalse(track(LocalDateTime.of(2026, 9, 13, 8, 0, 0))
                    .isAt(LocalDateTime.of(2026, 9, 13, 9, 0, 0)));
        }

        @Test
        @DisplayName("obsTime 为 null 时不会 NPE，直接判 false")
        void nullObsTime() {
            assertFalse(track(null).isAt(LocalDateTime.of(2026, 9, 13, 8, 0, 0)));
        }

        @Test
        @DisplayName("latest 为 null 时判 false")
        void nullLatest() {
            assertFalse(track(LocalDateTime.of(2026, 9, 13, 8, 0, 0)).isAt(null));
        }

        private TyphoonTrack track(LocalDateTime obsTime) {
            TyphoonTrack tr = new TyphoonTrack();
            tr.setObsTime(obsTime);
            return tr;
        }
    }

    @Nested
    @DisplayName("City / RiskAssessment · 等级中文文案")
    class LevelText {

        @Test
        @DisplayName("城市基准风险等级：1低 2中 3高 4极高，其余一律「中」")
        void cityBaseRiskLevelText() {
            assertEquals("低", city(1).getBaseRiskLevelText());
            assertEquals("中", city(2).getBaseRiskLevelText());
            assertEquals("高", city(3).getBaseRiskLevelText());
            assertEquals("极高", city(4).getBaseRiskLevelText());
            assertEquals("中", city(5).getBaseRiskLevelText());
            assertEquals("中", city(0).getBaseRiskLevelText());
            assertEquals("中", city(null).getBaseRiskLevelText());
        }

        @Test
        @DisplayName("风险评估等级：LOW/MEDIUM/HIGH/EXTREME 大小写不敏感，未知值回落到「中」")
        void riskLevelText() {
            assertEquals("低", risk("LOW").getRiskLevelText());
            assertEquals("低", risk("low").getRiskLevelText());
            assertEquals("中", risk("Medium").getRiskLevelText());
            assertEquals("高", risk("HIGH").getRiskLevelText());
            assertEquals("极高", risk("EXtreme").getRiskLevelText());
            assertEquals("中", risk("UNKNOWN").getRiskLevelText());
            assertEquals("中", risk("").getRiskLevelText());
            assertEquals("中", risk(null).getRiskLevelText());
        }

        private City city(Integer level) {
            City c = new City();
            c.setBaseRiskLevel(level);
            return c;
        }

        private RiskAssessment risk(String level) {
            RiskAssessment r = new RiskAssessment();
            r.setRiskLevel(level);
            return r;
        }
    }

    @Nested
    @DisplayName("SystemSetting · 配置值解析")
    class SettingParsing {

        @Test
        @DisplayName("合法数字正常解析")
        void parsesNumber() {
            assertEquals(3.5D, setting("3.5").asDouble(-1D), 1e-9D);
            assertEquals(1000D, setting("1e3").asDouble(-1D), 1e-9D);
            assertEquals(-2D, setting("-2").asDouble(0D), 1e-9D);
        }

        @Test
        @DisplayName("非法内容返回传入的默认值")
        void invalidReturnsDefault() {
            assertEquals(9D, setting("abc").asDouble(9D), 1e-9D);
            assertEquals(9D, setting("12abc").asDouble(9D), 1e-9D);
        }

        @Test
        @DisplayName("配置值为 null 或空白时返回默认值，不抛异常")
        void nullSafe() {
            assertEquals(9D, setting(null).asDouble(9D), 1e-9D);
            assertEquals(9D, setting("").asDouble(9D), 1e-9D);
            assertEquals(9D, setting("   ").asDouble(9D), 1e-9D);
        }

        private SystemSetting setting(String value) {
            SystemSetting s = new SystemSetting();
            s.setSettingValue(value);
            return s;
        }
    }

    @Nested
    @DisplayName("WindCircle · 四象限半径")
    class WindCircleLogic {

        /** 四个象限各不相同的 7 级风圈，便于校验取值是否精确落到对应象限。 */
        private WindCircle sample() {
            WindCircle c = new WindCircle();
            c.setLevel7Ne(100);
            c.setLevel7Se(200);
            c.setLevel7Sw(300);
            c.setLevel7Nw(400);
            return c;
        }

        @Test
        @DisplayName("按等级与象限精确取值，象限名大小写不敏感")
        void radiusByQuadrant() {
            WindCircle c = sample();
            assertEquals(100, c.radius(7, "ne"));
            assertEquals(200, c.radius(7, "SE"));
            assertEquals(300, c.radius(7, "sw"));
            assertEquals(400, c.radius(7, "NW"));
        }

        @Test
        @DisplayName("等级越界或象限非法时返回 0，而不是抛异常")
        void invalidArguments() {
            WindCircle c = sample();
            assertEquals(0, c.radius(8, "ne"));
            assertEquals(0, c.radius(7, "xx"));
            assertEquals(0, c.radius(7, null));
            assertEquals(0, c.radius(7, ""));
        }

        @Test
        @DisplayName("字段为 null 时按 0 处理")
        void nullFields() {
            assertEquals(0, new WindCircle().radius(10, "ne"));
        }

        @Test
        @DisplayName("10 级与 12 级各自独立取到自己的字段")
        void otherLevels() {
            WindCircle c = sample();
            c.setLevel10Ne(11);
            c.setLevel10Nw(12);
            c.setLevel12Ne(13);
            c.setLevel12Nw(14);

            assertEquals(11, c.radius(10, "ne"));
            assertEquals(12, c.radius(10, "nw"));
            assertEquals(13, c.radius(12, "ne"));
            assertEquals(14, c.radius(12, "nw"));
        }

        @Test
        @DisplayName("averageRadius 取四象限算术平均")
        void average() {
            assertEquals(250D, sample().averageRadius(7), 1e-9D);
            assertEquals(0D, new WindCircle().averageRadius(7), 1e-9D);
        }

        @Test
        @DisplayName("radiusStdDev 取四象限总体标准差")
        void stdDev() {
            // 均值 250，偏差 -150/-50/50/150，平方和 50000，÷4=12500，开方约 111.8034
            assertEquals(Math.sqrt(12500D), sample().radiusStdDev(7), 1e-6D);
            assertEquals(0D, new WindCircle().radiusStdDev(7), 1e-9D);
        }

        @Test
        @DisplayName("toDecimal 保留 1 位小数并四舍五入")
        void toDecimal() {
            assertEquals(0, WindCircle.toDecimal(1.25D).compareTo(new BigDecimal("1.3")));
            assertEquals(0, WindCircle.toDecimal(1.24D).compareTo(new BigDecimal("1.2")));
            assertEquals(0, WindCircle.toDecimal(2.0D).compareTo(new BigDecimal("2.0")));
            assertEquals(1, WindCircle.toDecimal(1.25D).scale(), "应固定为 1 位小数");
        }
    }
}
