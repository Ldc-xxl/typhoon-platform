package com.zfz.typhoon.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 风圈半径实体（对应表 {@code wind_circles}）。
 *
 * <p>台风风圈<b>不是正圆</b>：受环境风切变与自身结构影响，四个象限的半径
 * 各不相同，气象部门按 NE / SE / SW / NW 分别发布。本实体完整保存这四个值
 * （7 级、10 级、12 级各四个，共 12 个半径字段），
 * 前端据此绘制成不规则的扇形叠加轮廓。
 *
 * <p>四象限归属由 {@code com.zfz.typhoon.util.GeoUtil#quadrant} 判定。
 *
 * @author 追风者项目组
 */
public class WindCircle implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 风圈记录ID（主键，自增） */
    private Long circleId;

    /** 台风ID */
    private Long typhoonId;

    /** 观测时间 */
    private LocalDateTime obsTime;

    /** 7级风圈东北象限半径（km） */
    private Integer level7Ne;
    /** 7级风圈西北象限半径（km） */
    private Integer level7Nw;
    /** 7级风圈东南象限半径（km） */
    private Integer level7Se;
    /** 7级风圈西南象限半径（km） */
    private Integer level7Sw;

    /** 10级风圈东北象限半径（km） */
    private Integer level10Ne;
    /** 10级风圈西北象限半径（km） */
    private Integer level10Nw;
    /** 10级风圈东南象限半径（km） */
    private Integer level10Se;
    /** 10级风圈西南象限半径（km） */
    private Integer level10Sw;

    /** 12级风圈东北象限半径（km） */
    private Integer level12Ne;
    /** 12级风圈西北象限半径（km） */
    private Integer level12Nw;
    /** 12级风圈东南象限半径（km） */
    private Integer level12Se;
    /** 12级风圈西南象限半径（km） */
    private Integer level12Sw;

    /** 创建时间 */
    private LocalDateTime createdAt;

    public WindCircle() {
    }

    /**
     * 按风圈等级与象限取半径。
     *
     * @param level    风圈等级，取 7 / 10 / 12
     * @param quadrant 象限代号：ne / se / sw / nw
     * @return 半径（km）；参数非法时返回 0
     */
    public int radius(int level, String quadrant) {
        boolean ne = "ne".equalsIgnoreCase(quadrant);
        boolean se = "se".equalsIgnoreCase(quadrant);
        boolean sw = "sw".equalsIgnoreCase(quadrant);
        boolean nw = "nw".equalsIgnoreCase(quadrant);

        if (level == 7) {
            if (ne) {
                return nz(level7Ne);
            }
            if (se) {
                return nz(level7Se);
            }
            if (sw) {
                return nz(level7Sw);
            }
            if (nw) {
                return nz(level7Nw);
            }
        } else if (level == 10) {
            if (ne) {
                return nz(level10Ne);
            }
            if (se) {
                return nz(level10Se);
            }
            if (sw) {
                return nz(level10Sw);
            }
            if (nw) {
                return nz(level10Nw);
            }
        } else if (level == 12) {
            if (ne) {
                return nz(level12Ne);
            }
            if (se) {
                return nz(level12Se);
            }
            if (sw) {
                return nz(level12Sw);
            }
            if (nw) {
                return nz(level12Nw);
            }
        }
        return 0;
    }

    /**
     * 取某等级四象限半径的平均值（用于"风圈规模"相似度计算）。
     *
     * @param level 风圈等级，取 7 / 10 / 12
     * @return 平均半径（km）
     */
    public double averageRadius(int level) {
        return (radius(level, "ne") + radius(level, "se")
                + radius(level, "sw") + radius(level, "nw")) / 4.0D;
    }

    /**
     * 取某等级四象限半径的标准差（用于风圈不对称度评估）。
     *
     * @param level 风圈等级，取 7 / 10 / 12
     * @return 标准差（km）
     */
    public double radiusStdDev(int level) {
        double avg = averageRadius(level);
        double sum = 0D;
        for (String q : new String[]{"ne", "se", "sw", "nw"}) {
            double d = radius(level, q) - avg;
            sum += d * d;
        }
        return Math.sqrt(sum / 4.0D);
    }

    /**
     * null 安全取值。
     *
     * @param v 值
     * @return 非空值或 0
     */
    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    /**
     * 由 double 转 BigDecimal（保留 1 位，供 DAO 写入 decimal 列时使用）。
     *
     * @param v 值
     * @return BigDecimal
     */
    public static BigDecimal toDecimal(double v) {
        return BigDecimal.valueOf(v).setScale(1, BigDecimal.ROUND_HALF_UP);
    }

    /* ------------------------------------------------------------------ */
    /*  getter / setter                                                     */
    /* ------------------------------------------------------------------ */

    public Long getCircleId() {
        return circleId;
    }

    public void setCircleId(Long circleId) {
        this.circleId = circleId;
    }

    public Long getTyphoonId() {
        return typhoonId;
    }

    public void setTyphoonId(Long typhoonId) {
        this.typhoonId = typhoonId;
    }

    public LocalDateTime getObsTime() {
        return obsTime;
    }

    public void setObsTime(LocalDateTime obsTime) {
        this.obsTime = obsTime;
    }

    public Integer getLevel7Ne() {
        return level7Ne;
    }

    public void setLevel7Ne(Integer level7Ne) {
        this.level7Ne = level7Ne;
    }

    public Integer getLevel7Nw() {
        return level7Nw;
    }

    public void setLevel7Nw(Integer level7Nw) {
        this.level7Nw = level7Nw;
    }

    public Integer getLevel7Se() {
        return level7Se;
    }

    public void setLevel7Se(Integer level7Se) {
        this.level7Se = level7Se;
    }

    public Integer getLevel7Sw() {
        return level7Sw;
    }

    public void setLevel7Sw(Integer level7Sw) {
        this.level7Sw = level7Sw;
    }

    public Integer getLevel10Ne() {
        return level10Ne;
    }

    public void setLevel10Ne(Integer level10Ne) {
        this.level10Ne = level10Ne;
    }

    public Integer getLevel10Nw() {
        return level10Nw;
    }

    public void setLevel10Nw(Integer level10Nw) {
        this.level10Nw = level10Nw;
    }

    public Integer getLevel10Se() {
        return level10Se;
    }

    public void setLevel10Se(Integer level10Se) {
        this.level10Se = level10Se;
    }

    public Integer getLevel10Sw() {
        return level10Sw;
    }

    public void setLevel10Sw(Integer level10Sw) {
        this.level10Sw = level10Sw;
    }

    public Integer getLevel12Ne() {
        return level12Ne;
    }

    public void setLevel12Ne(Integer level12Ne) {
        this.level12Ne = level12Ne;
    }

    public Integer getLevel12Nw() {
        return level12Nw;
    }

    public void setLevel12Nw(Integer level12Nw) {
        this.level12Nw = level12Nw;
    }

    public Integer getLevel12Se() {
        return level12Se;
    }

    public void setLevel12Se(Integer level12Se) {
        this.level12Se = level12Se;
    }

    public Integer getLevel12Sw() {
        return level12Sw;
    }

    public void setLevel12Sw(Integer level12Sw) {
        this.level12Sw = level12Sw;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
