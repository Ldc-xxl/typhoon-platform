package com.zfz.typhoon.model.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 时间轴回放视图对象（面向前端 P1 时间轴播放）。
 *
 * <p>为什么单开一个接口：数据库 {@code typhoon_tracks} 存的是<b>实际观测点</b>
 * （飞燕只有 3 条，间隔 24 小时），直接拿来驱动时间轴会非常卡顿。
 * 回放需要的是<b>等时间间隔</b>（本例 6 小时）的连续序列，
 * 由后端在实况点之间线性插值、并衔接机构预报段共同合成，
 * 前端只负责按帧渲染——<b>业务计算留在后端</b>是分层架构的要求。
 *
 * <p>{@link #frames} 中每帧的字段与前端 {@code ZFZ.mock.replay.frames} 完全一致，
 * 前端插值渲染代码零改动。
 *
 * @author 追风者项目组
 */
public class ReplayVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 帧间隔（小时） */
    private Integer step;

    /** 序列起始时刻（yyyy-MM-dd HH:mm） */
    private String startTime;

    /** 序列结束时刻（yyyy-MM-dd HH:mm） */
    private String endTime;

    /** 回放帧序列 */
    private List<Frame> frames = new ArrayList<Frame>();

    public ReplayVO() {
    }

    /**
     * 回放帧。
     */
    public static class Frame implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 经度 */
        private Double lon;

        /** 纬度 */
        private Double lat;

        /** 中心最大风力（级） */
        private Integer level;

        /** 最大风速（m/s） */
        private Integer speed;

        /** 中心气压（hPa） */
        private Integer pressure;

        /** 移动方位角（度） */
        private Double move;

        /** 移动速度（km/h） */
        private Double mph;

        /** 该帧时刻（yyyy-MM-dd HH:mm） */
        private String time;

        /** 数据来源：OBS=实况插值，FCST=预报段 */
        private String source;

        public Frame() {
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

        public Integer getLevel() {
            return level;
        }

        public void setLevel(Integer level) {
            this.level = level;
        }

        public Integer getSpeed() {
            return speed;
        }

        public void setSpeed(Integer speed) {
            this.speed = speed;
        }

        public Integer getPressure() {
            return pressure;
        }

        public void setPressure(Integer pressure) {
            this.pressure = pressure;
        }

        public Double getMove() {
            return move;
        }

        public void setMove(Double move) {
            this.move = move;
        }

        public Double getMph() {
            return mph;
        }

        public void setMph(Double mph) {
            this.mph = mph;
        }

        public String getTime() {
            return time;
        }

        public void setTime(String time) {
            this.time = time;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }
    }

    public Integer getStep() {
        return step;
    }

    public void setStep(Integer step) {
        this.step = step;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public List<Frame> getFrames() {
        return frames;
    }

    public void setFrames(List<Frame> frames) {
        this.frames = frames;
    }
}
