package com.zfz.typhoon.util;

/**
 * 地理计算工具（球面距离、方位角、风圈象限判定）。
 *
 * <p>课程要求（项目描述文档 · 技术价值）：覆盖"<b>空间查询与距离计算</b>"知识点。
 * 本平台需要它做三件事：
 *
 * <ol>
 *   <li><b>城市距台风中心直线距离</b> —— 前端地图上"邻近 xxx km"、
 *       风险预评估里的距离与预计到达时间；</li>
 *   <li><b>移动方位角</b> —— 由相邻两个实况点反算台风移向（度），
 *       并映射成"西北 / 东南"等中文方位；</li>
 *   <li><b>风圈象限判定</b> —— 台风 7/10/12 级风圈是四象限不对称的
 *       （数据库 {@code wind_circles} 表存 ne/se/sw/nw 四个半径），
 *       要判断某城市落在哪个象限才能取到正确的半径。</li>
 * </ol>
 *
 * <p>距离公式采用 <b>Haversine</b>（大圆距离），地球平均半径取 6371.0088 km（IUGG 推荐值）。
 * 在台风尺度（数百公里）下误差小于 0.5%，完全满足科普展示与规则推演的精度要求。
 *
 * @author 追风者项目组
 */
public final class GeoUtil {

    /** 地球平均半径（km，IUGG 推荐值） */
    public static final double EARTH_RADIUS_KM = 6371.0088D;

    /** 1 纬度对应的公里数（球面近似，全球基本恒定） */
    public static final double KM_PER_LAT = 111.32D;

    /** 方位角名称，索引 = round(deg / 45) % 8 */
    private static final String[] DIRECTION_NAMES = {
            "北", "东北", "东", "东南", "南", "西南", "西", "西北"
    };

    private GeoUtil() {
        // 工具类禁止实例化
    }

    /**
     * 计算两点间的大圆距离（Haversine）。
     *
     * @param lat1 起点纬度
     * @param lon1 起点经度
     * @param lat2 终点纬度
     * @param lon2 终点经度
     * @return 距离（km）
     */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    /**
     * 计算从起点指向终点的方位角。
     *
     * @param lat1 起点纬度
     * @param lon1 起点经度
     * @param lat2 终点纬度
     * @param lon2 终点经度
     * @return 方位角（0—360 度，正北为 0，顺时针增大）
     */
    public static double bearing(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dLambda = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLambda);
        double deg = Math.toDegrees(Math.atan2(y, x));
        return (deg + 360D) % 360D;
    }

    /**
     * 方位角转中文方位名。
     *
     * @param degree 方位角（度）
     * @return 中文方位，如「西北」
     */
    public static String directionName(double degree) {
        int idx = (int) Math.round(degree / 45D) % 8;
        if (idx < 0) {
            idx += 8;
        }
        return DIRECTION_NAMES[idx];
    }

    /**
     * 判断目标点相对中心点落在哪个象限。
     *
     * <p>用于风圈四象限半径取值：返回 {@code "ne"} / {@code "se"} / {@code "sw"} / {@code "nw"}。
     * 判定方式：目标点相对中心点的经度差决定东/西，纬度差决定南/北。
     *
     * @param centerLat 中心点纬度（台风中心）
     * @param centerLon 中心点经度（台风中心）
     * @param targetLat 目标点纬度（城市）
     * @param targetLon 目标点经度（城市）
     * @return 象限代号
     */
    public static String quadrant(double centerLat, double centerLon, double targetLat, double targetLon) {
        boolean north = targetLat >= centerLat;
        boolean east = targetLon >= centerLon;
        if (north && east) {
            return "ne";
        }
        if (!north && east) {
            return "se";
        }
        if (!north && !east) {
            return "sw";
        }
        return "nw";
    }

    /**
     * 近似换算：给定纬度处，1 经度对应的公里数。
     *
     * <p>用于把"登陆点向东偏移 100 km"这类沙盘参数换算成经纬度增量。
     *
     * @param lat 纬度
     * @return 1 经度对应的公里数
     */
    public static double kmPerLon(double lat) {
        return KM_PER_LAT * Math.cos(Math.toRadians(lat));
    }

    /**
     * 按距离与方位角推算目标点经纬度。
     *
     * <p>沙盘推演用：把"登陆点偏移 km"转到实际的经纬度坐标。
     *
     * @param lat       起点纬度
     * @param lon       起点经度
     * @param distanceKm 距离（km）
     * @param bearingDeg 方位角（度）
     * @return 目标点坐标数组 {@code [lat, lon]}
     */
    public static double[] offset(double lat, double lon, double distanceKm, double bearingDeg) {
        double angular = distanceKm / EARTH_RADIUS_KM;
        double theta = Math.toRadians(bearingDeg);
        double phi1 = Math.toRadians(lat);
        double lambda1 = Math.toRadians(lon);

        double phi2 = Math.asin(Math.sin(phi1) * Math.cos(angular)
                + Math.cos(phi1) * Math.sin(angular) * Math.cos(theta));
        double lambda2 = lambda1 + Math.atan2(
                Math.sin(theta) * Math.sin(angular) * Math.cos(phi1),
                Math.cos(angular) - Math.sin(phi1) * Math.sin(phi2));

        return new double[]{Math.toDegrees(phi2), Math.toDegrees(lambda2)};
    }

    /**
     * 按四舍五入保留指定小数位。
     *
     * @param value  原始值
     * @param digits 小数位数
     * @return 舍入后的值
     */
    public static double round(double value, int digits) {
        double factor = Math.pow(10, digits);
        return Math.round(value * factor) / factor;
    }
}
