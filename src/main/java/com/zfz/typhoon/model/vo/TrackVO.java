package com.zfz.typhoon.model.vo;

import com.zfz.typhoon.model.TyphoonTrack;
import com.zfz.typhoon.util.DateUtil;

import java.io.Serializable;

/**
 * 实况轨迹点视图对象（面向前端）。
 *
 * <p>前端用等距经纬投影把 {@code lon / lat} 换算成画布像素后连成
 * {@code <polyline>}，因此 <b>轨迹形状完全由本对象决定</b>。
 *
 * <p>{@link #marker} 供前端绘制不同样式的节点：
 * {@code start} 起点空心圆、{@code current} 当前中心、{@code mid} 中间实心点。
 *
 * @author 追风者项目组
 */
public class TrackVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 观测时刻（yyyy-MM-dd HH:mm） */
    private String time;

    /** 经度 */
    private Double lon;

    /** 纬度 */
    private Double lat;

    /** 中心最大风力（级） */
    private Integer windLevel;

    /** 中心最大风速（m/s） */
    private Integer windSpeed;

    /** 中心气压（hPa） */
    private Integer pressure;

    /** 节点标记：start / mid / current */
    private String marker;

    public TrackVO() {
    }

    /**
     * 由实体组装。
     *
     * @param track 轨迹实体
     * @param index 序号（从 0 开始）
     * @param total 总条数
     * @return 视图对象
     */
    public static TrackVO of(TyphoonTrack track, int index, int total) {
        TrackVO vo = new TrackVO();
        if (track == null) {
            return vo;
        }
        vo.time = DateUtil.format(track.getObsTime());
        vo.lon = track.getLongitude();
        vo.lat = track.getLatitude();
        vo.windLevel = track.getWindForce();
        vo.windSpeed = track.getWindSpeed();
        vo.pressure = track.getPressure();
        if (index == 0) {
            vo.marker = "start";
        } else if (index == total - 1) {
            vo.marker = "current";
        } else {
            vo.marker = "mid";
        }
        return vo;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public Double getLon() {
        return lon;
    }

    public void setLon(Double lon) {
        this.lon = lon;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Integer getWindLevel() {
        return windLevel;
    }

    public void setWindLevel(Integer windLevel) {
        this.windLevel = windLevel;
    }

    public Integer getWindSpeed() {
        return windSpeed;
    }

    public void setWindSpeed(Integer windSpeed) {
        this.windSpeed = windSpeed;
    }

    public Integer getPressure() {
        return pressure;
    }

    public void setPressure(Integer pressure) {
        this.pressure = pressure;
    }

    public String getMarker() {
        return marker;
    }

    public void setMarker(String marker) {
        this.marker = marker;
    }
}
