package com.zfz.typhoon.model.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 多机构预报路径视图对象（面向前端）。
 *
 * <p>一条记录 = 一家机构的完整预报折线。前端据此在地图上绘制不同颜色、
 * 不同虚线的预报路径，并在末端标注 {@link #landing}（登陆点描述）。
 *
 * <p><b>业务约束（后端必须保证，接口文档 3.6 节）</b>：
 * 同一台风的多家机构路径，其 {@link #points} 的<b>第一个点必须是当前实况位置</b>，
 * 即所有机构共用同一起点。因为已经走过的路径各机构不会有分歧，
 * 分歧只出现在预报段——若起点各不相同，页面会呈现出错误结论。
 *
 * <p>{@link #points} 中每个元素是 {@code [经度, 纬度]} 数组，
 * 与前端 {@code proj(lon, lat)} 的入参顺序一致。
 *
 * @author 追风者项目组
 */
public class ForecastPathVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 机构代码：CMA / JMA / JTWC / HKO / KMA / ECMWF */
    private String agency;

    /** 机构全称 */
    private String name;

    /** 路径显示颜色 */
    private String color;

    /** 虚线样式（SVG stroke-dasharray） */
    private String dash;

    /** 登陆点描述，如「厦门沿海」 */
    private String landing;

    /** 路径点序列，每个元素为 [经度, 纬度] */
    private List<List<Double>> points = new ArrayList<List<Double>>();

    /** 预报发布时间（yyyy-MM-dd HH:mm） */
    private String publishTime;

    /** 最终强度（末点风力，级） */
    private Integer endWindForce;

    public ForecastPathVO() {
    }

    /**
     * 追加一个路径点。
     *
     * @param lon 经度
     * @param lat 纬度
     */
    public void addPoint(double lon, double lat) {
        List<Double> p = new ArrayList<Double>(2);
        p.add(lon);
        p.add(lat);
        points.add(p);
    }

    /**
     * 取末点。
     *
     * @return 末点坐标 [经度, 纬度]；无点时返回 null
     */
    public List<Double> lastPoint() {
        return points.isEmpty() ? null : points.get(points.size() - 1);
    }

    /**
     * 取首点。
     *
     * @return 首点坐标 [经度, 纬度]；无点时返回 null
     */
    public List<Double> firstPoint() {
        return points.isEmpty() ? null : points.get(0);
    }

    public String getAgency() {
        return agency;
    }

    public void setAgency(String agency) {
        this.agency = agency;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getDash() {
        return dash;
    }

    public void setDash(String dash) {
        this.dash = dash;
    }

    public String getLanding() {
        return landing;
    }

    public void setLanding(String landing) {
        this.landing = landing;
    }

    public List<List<Double>> getPoints() {
        return points;
    }

    public void setPoints(List<List<Double>> points) {
        this.points = points;
    }

    public String getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(String publishTime) {
        this.publishTime = publishTime;
    }

    public Integer getEndWindForce() {
        return endWindForce;
    }

    public void setEndWindForce(Integer endWindForce) {
        this.endWindForce = endWindForce;
    }
}
