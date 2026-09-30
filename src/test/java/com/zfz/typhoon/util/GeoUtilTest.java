package com.zfz.typhoon.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GeoUtil} 单元测试。
 *
 * <p>被测对象是台风距离/方位计算的地基，一旦算错，风向象限、城市影响半径、
 * 沙盘登陆点偏移会一起错，因此这里对每个方法都给出<b>可独立验算</b>的期望值：
 * 能用球面几何手推出的就写精确值，推不出的就用区间断言，不写"跑一遍看结果"式的空断言。
 *
 * <p>所有涉及距离的断言容差取 0.05 km —— 小于台风尺度该有的精度，
 * 又能容忍 double 运算的末位差异。
 */
@DisplayName("GeoUtil · 地理计算")
class GeoUtilTest {

    /** 距离断言容差（km） */
    private static final double KM_TOLERANCE = 0.05D;

    /** 角度断言容差（度） */
    private static final double DEG_TOLERANCE = 0.001D;

    @Nested
    @DisplayName("distanceKm · 大圆距离（Haversine）")
    class Distance {

        @Test
        @DisplayName("同一点距离为 0")
        void samePointIsZero() {
            assertEquals(0D, GeoUtil.distanceKm(20D, 110D, 20D, 110D), 1e-9);
        }

        @Test
        @DisplayName("赤道上 1 经度 = 地球半径 × 1° 的弧长")
        void oneDegreeOfLongitudeAtEquator() {
            // 赤道上经度差 1° 恰好是 1° 的弧长：R × π/180
            double expected = GeoUtil.EARTH_RADIUS_KM * Math.PI / 180D;
            assertEquals(expected, GeoUtil.distanceKm(0D, 0D, 0D, 1D), KM_TOLERANCE);
        }

        @Test
        @DisplayName("1 纬度差 = 1° 的弧长（与经度无关）")
        void oneDegreeOfLatitude() {
            double expected = GeoUtil.EARTH_RADIUS_KM * Math.PI / 180D;
            assertEquals(expected, GeoUtil.distanceKm(0D, 0D, 1D, 0D), KM_TOLERANCE);
            // 高纬度处同样成立：球面距离只取决于纬度差
            assertEquals(expected, GeoUtil.distanceKm(50D, 100D, 51D, 100D), KM_TOLERANCE);
        }

        @Test
        @DisplayName("对称：A→B 与 B→A 完全相等")
        void isSymmetric() {
            double ab = GeoUtil.distanceKm(39.9042D, 116.4074D, 31.2304D, 121.4737D);
            double ba = GeoUtil.distanceKm(31.2304D, 121.4737D, 39.9042D, 116.4074D);
            assertEquals(ab, ba, 1e-9);
        }

        @Test
        @DisplayName("北京—上海约 1067 km（区间断言）")
        void beijingToShanghai() {
            double d = GeoUtil.distanceKm(39.9042D, 116.4074D, 31.2304D, 121.4737D);
            assertTrue(d > 1000D && d < 1120D, "北京到上海应在 1000—1120 km，实际 " + d);
        }

        @Test
        @DisplayName("对跖点距离约等于半个地球周长")
        void antipodalPoints() {
            double d = GeoUtil.distanceKm(0D, 0D, 0D, 180D);
            assertEquals(Math.PI * GeoUtil.EARTH_RADIUS_KM, d, 1.0D);
        }
    }

    @Nested
    @DisplayName("bearing · 方位角")
    class Bearing {

        @Test
        @DisplayName("正北 0°、正东 90°、正南 180°、正西 270°")
        void fourCardinalDirections() {
            assertEquals(0D, GeoUtil.bearing(0D, 0D, 1D, 0D), DEG_TOLERANCE);
            assertEquals(90D, GeoUtil.bearing(0D, 0D, 0D, 1D), DEG_TOLERANCE);
            assertEquals(180D, GeoUtil.bearing(1D, 0D, 0D, 0D), DEG_TOLERANCE);
            assertEquals(270D, GeoUtil.bearing(0D, 1D, 0D, 0D), DEG_TOLERANCE);
        }

        @Test
        @DisplayName("结果恒定落在 [0, 360)")
        void resultAlwaysNormalized() {
            // 西北方向为负角，实现里用 +360 归一化，必须仍是正数
            double west = GeoUtil.bearing(0D, 1D, 0D, 0D);
            assertTrue(west >= 0D && west < 360D, "方位角应落在 [0,360)，实际 " + west);
            assertTrue(GeoUtil.bearing(10D, 10D, -10D, 5D) >= 0D);
        }
    }

    @Nested
    @DisplayName("directionName · 方位角转中文")
    class DirectionName {

        @Test
        @DisplayName("八个主方向逐一对齐")
        void eightDirections() {
            assertEquals("北", GeoUtil.directionName(0D));
            assertEquals("东北", GeoUtil.directionName(45D));
            assertEquals("东", GeoUtil.directionName(90D));
            assertEquals("东南", GeoUtil.directionName(135D));
            assertEquals("南", GeoUtil.directionName(180D));
            assertEquals("西南", GeoUtil.directionName(225D));
            assertEquals("西", GeoUtil.directionName(270D));
            assertEquals("西北", GeoUtil.directionName(315D));
        }

        @Test
        @DisplayName("360° 回到「北」，不越界")
        void fullCircleWrapsToNorth() {
            assertEquals("北", GeoUtil.directionName(360D));
            assertEquals("北", GeoUtil.directionName(720D));
        }

        @Test
        @DisplayName("负角度归一化，不得抛越界异常")
        void negativeDegreesAreNormalized() {
            assertEquals("西北", GeoUtil.directionName(-45D));
            assertEquals("北", GeoUtil.directionName(-0.1D));
            assertEquals("西", GeoUtil.directionName(-90D));
        }
    }

    @Nested
    @DisplayName("quadrant · 四象限判定")
    class Quadrant {

        @Test
        @DisplayName("东北/东南/西南/西北四个象限")
        void fourQuadrants() {
            assertEquals("ne", GeoUtil.quadrant(20D, 110D, 21D, 111D));
            assertEquals("se", GeoUtil.quadrant(20D, 110D, 19D, 111D));
            assertEquals("sw", GeoUtil.quadrant(20D, 110D, 19D, 109D));
            assertEquals("nw", GeoUtil.quadrant(20D, 110D, 21D, 109D));
        }

        @Test
        @DisplayName("与中心点重合时归入「东北」（实现用 >= 判定）")
        void coincidentFallsIntoNorthEast() {
            assertEquals("ne", GeoUtil.quadrant(20D, 110D, 20D, 110D));
        }

        @Test
        @DisplayName("仅纬度差 / 仅经度差时象限正确")
        void axisAlignedTargets() {
            assertEquals("ne", GeoUtil.quadrant(20D, 110D, 21D, 110D));
            assertEquals("se", GeoUtil.quadrant(20D, 110D, 19D, 110D));
            assertEquals("ne", GeoUtil.quadrant(20D, 110D, 20D, 111D));
            assertEquals("nw", GeoUtil.quadrant(20D, 110D, 20D, 109D));
        }
    }

    @Nested
    @DisplayName("kmPerLon · 每经度公里数")
    class KmPerLon {

        @Test
        @DisplayName("赤道取最大值 111.32")
        void equatorIsMaximum() {
            assertEquals(GeoUtil.KM_PER_LAT, GeoUtil.kmPerLon(0D), 1e-6);
        }

        @Test
        @DisplayName("60° 纬度处减半")
        void sixtyDegreesIsHalf() {
            assertEquals(GeoUtil.KM_PER_LAT * 0.5D, GeoUtil.kmPerLon(60D), 1e-6);
        }

        @Test
        @DisplayName("极点趋近于 0")
        void poleIsZero() {
            assertEquals(0D, GeoUtil.kmPerLon(90D), 1e-9);
        }
    }

    @Nested
    @DisplayName("offset · 按距离与方位推算坐标")
    class Offset {

        @Test
        @DisplayName("沿 1 经度弧长向东偏移，正好得到经度 +1")
        void eastOffset() {
            double oneDegreeKm = GeoUtil.EARTH_RADIUS_KM * Math.PI / 180D;
            double[] p = GeoUtil.offset(0D, 0D, oneDegreeKm, 90D);
            assertEquals(0D, p[0], DEG_TOLERANCE);
            assertEquals(1D, p[1], DEG_TOLERANCE);
        }

        @Test
        @DisplayName("沿 1 经度弧长向北偏移，正好得到纬度 +1")
        void northOffset() {
            double oneDegreeKm = GeoUtil.EARTH_RADIUS_KM * Math.PI / 180D;
            double[] p = GeoUtil.offset(0D, 0D, oneDegreeKm, 0D);
            assertEquals(1D, p[0], DEG_TOLERANCE);
            assertEquals(0D, p[1], DEG_TOLERANCE);
        }

        @Test
        @DisplayName("偏移 0 km 时坐标不变")
        void zeroDistanceKeepsPoint() {
            double[] p = GeoUtil.offset(20D, 110D, 0D, 123D);
            assertEquals(20D, p[0], DEG_TOLERANCE);
            assertEquals(110D, p[1], DEG_TOLERANCE);
        }

        @Test
        @DisplayName("返回数组长度为 2（[lat, lon]）")
        void returnsLatLonPair() {
            assertEquals(2, GeoUtil.offset(20D, 110D, 100D, 90D).length);
        }

        @Test
        @DisplayName("与 distanceKm 互为逆运算")
        void roundTripWithDistance() {
            double[] p = GeoUtil.offset(21.5D, 111.0D, 150D, 75D);
            double d = GeoUtil.distanceKm(21.5D, 111.0D, p[0], p[1]);
            assertEquals(150D, d, KM_TOLERANCE);
        }
    }

    @Nested
    @DisplayName("round · 四舍五入")
    class Round {

        @Test
        @DisplayName("保留 2 位")
        void twoDigits() {
            assertEquals(1.23D, GeoUtil.round(1.2345D, 2), 1e-9);
            assertEquals(1.24D, GeoUtil.round(1.236D, 2), 1e-9);
        }

        @Test
        @DisplayName("保留 0 位即取整")
        void zeroDigits() {
            assertEquals(3D, GeoUtil.round(2.5D, 0), 1e-9);
            assertEquals(2D, GeoUtil.round(2.4D, 0), 1e-9);
        }

        @Test
        @DisplayName("负值同样按绝对值四舍五入（Math.round 的语义）")
        void negativeValues() {
            assertEquals(-1.24D, GeoUtil.round(-1.236D, 2), 1e-9);
        }
    }

    @Test
    @DisplayName("工具类不可实例化：不存在公开构造，仅有的构造器为 private")
    void utilityClassCannotBeInstantiated() {
        assertThrows(NoSuchMethodException.class,
                () -> GeoUtil.class.getConstructor(), "不应存在公开的无参构造");
        java.lang.reflect.Constructor<?>[] declared = GeoUtil.class.getDeclaredConstructors();
        assertEquals(1, declared.length, "工具类应只有一个构造器");
        assertTrue(java.lang.reflect.Modifier.isPrivate(declared[0].getModifiers()),
                "该构造器必须是 private");
    }
}
