package com.zfz.typhoon.model.vo;

import com.zfz.typhoon.model.WindCircle;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 风圈视图对象（面向前端）。
 *
 * <p>前端按 {@link #level} 分三层绘制不规则风圈轮廓：
 * 7 级（黄）、10 级（橙）、12 级（红），每层用
 * {@link #quad} 里的四个象限半径分别计算边界点。
 *
 * <p>{@link #r} 是四象限平均半径，用于快速绘制示意圆与图例文字；
 * {@link #quad} 是精确的四象限值，用于绘制真实的不规则风圈。
 *
 * @author 追风者项目组
 */
public class WindCircleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 风圈等级：7 / 10 / 12 */
    private Integer level;

    /** 显示颜色 */
    private String color;

    /** 四象限平均半径（km） */
    private Integer r;

    /** 四象限半径（km）：键为 ne / se / sw / nw */
    private Map<String, Integer> quad;

    public WindCircleVO() {
    }

    /**
     * 由风圈实体组装。
     *
     * @param circle 风圈实体
     * @param level  风圈等级（7 / 10 / 12）
     * @param color  显示颜色
     * @return 视图对象
     */
    public static WindCircleVO of(WindCircle circle, int level, String color) {
        WindCircleVO vo = new WindCircleVO();
        vo.level = level;
        vo.color = color;
        vo.r = (int) Math.round(circle.averageRadius(level));

        Map<String, Integer> quad = new LinkedHashMap<String, Integer>();
        quad.put("ne", circle.radius(level, "ne"));
        quad.put("se", circle.radius(level, "se"));
        quad.put("sw", circle.radius(level, "sw"));
        quad.put("nw", circle.radius(level, "nw"));
        vo.quad = quad;
        return vo;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Integer getR() {
        return r;
    }

    public void setR(Integer r) {
        this.r = r;
    }

    public Map<String, Integer> getQuad() {
        return quad;
    }

    public void setQuad(Map<String, Integer> quad) {
        this.quad = quad;
    }
}
