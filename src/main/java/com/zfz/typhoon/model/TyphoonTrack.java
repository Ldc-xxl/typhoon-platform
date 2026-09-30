package com.zfz.typhoon.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 台风实况轨迹实体（对应表 {@code typhoon_tracks}）。
 *
 * <p>每个时刻一条记录，是整个平台最基础的数据：地图上的路径线、时间轴回放、
 * 数据导出 CSV 全部由它派生。
 *
 * <p>唯一键 {@code uk_tracks_typhoon_time(typhoon_id, obs_time)} 保证同一台风
 * 同一观测时刻只有一条，因此重复同步时用
 * {@code INSERT ... ON DUPLICATE KEY UPDATE} 即可幂等写入。
 *
 * @author 追风者项目组
 */
public class TyphoonTrack implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 轨迹点ID（主键，自增） */
    private Long trackId;

    /** 台风ID */
    private Long typhoonId;

    /** 观测时间 */
    private LocalDateTime obsTime;

    /** 纬度 */
    private Double latitude;

    /** 经度 */
    private Double longitude;

    /** 中心最大风力（级） */
    private Integer windForce;

    /** 中心最大风速（m/s） */
    private Integer windSpeed;

    /** 中心气压（hPa） */
    private Integer pressure;

    /** 移动方向（方位角，度） */
    private Double moveDirection;

    /** 移动速度（km/h） */
    private Double moveSpeed;

    public TyphoonTrack() {
    }

    /**
     * 便捷判断：是否为该台风的最后一个实况点（当前中心位置）。
     *
     * @param latest 待比较的"最新时刻"
     * @return 是最后一点返回 true
     */
    public boolean isAt(LocalDateTime latest) {
        return obsTime != null && obsTime.equals(latest);
    }

    public Long getTrackId() {
        return trackId;
    }

    public void setTrackId(Long trackId) {
        this.trackId = trackId;
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

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Integer getWindForce() {
        return windForce;
    }

    public void setWindForce(Integer windForce) {
        this.windForce = windForce;
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

    public Double getMoveDirection() {
        return moveDirection;
    }

    public void setMoveDirection(Double moveDirection) {
        this.moveDirection = moveDirection;
    }

    public Double getMoveSpeed() {
        return moveSpeed;
    }

    public void setMoveSpeed(Double moveSpeed) {
        this.moveSpeed = moveSpeed;
    }

    @Override
    public String toString() {
        return "TyphoonTrack{typhoonId=" + typhoonId + ", obsTime=" + obsTime
                + ", lon=" + longitude + ", lat=" + latitude + '}';
    }
}
