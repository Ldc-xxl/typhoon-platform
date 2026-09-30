package com.zfz.typhoon.model.vo;

import com.zfz.typhoon.model.Typhoon;
import com.zfz.typhoon.model.TyphoonTrack;
import com.zfz.typhoon.util.DateUtil;
import com.zfz.typhoon.util.GeoUtil;

import java.io.Serializable;

/**
 * 台风列表 / 详情视图对象（面向前端）。
 *
 * <p>为什么需要 VO：数据库 {@code typhoons} 表存的是台风<b>档案</b>
 * （编号、名称、海域、生命周期），而前端页面需要的是"<b>当前状态</b>"
 * （当前强度、中心位置、移向移速）——后者在
 * {@code typhoon_tracks} 的最新一条记录里。本类把两者合成一个对象，
 * 前端一次请求即可渲染完整卡片。
 *
 * <p><b>字段名与前端 {@code js/data.js} 的 mock 结构严格一致</b>，
 * 因此后端就绪后前端只需把 {@code useMock} 关掉，渲染代码零改动。
 *
 * @author 追风者项目组
 */
public class TyphoonVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 生命周期状态：活跃 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 生命周期状态：已归档 */
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    /** 台风ID */
    private Long id;

    /** 国际编号，如 202615 */
    private String code;

    /** 中文名称 */
    private String nameCn;

    /** 英文名称 */
    private String nameEn;

    /** 生成年份 */
    private Integer year;

    /** 所属海域 */
    private String basin;

    /** 生命周期状态：ACTIVE / ARCHIVED（前端语义） */
    private String status;

    /** 状态中文说明，如「活跃中」「已结束」 */
    private String statusText;

    /** 强度等级中文名，如「强台风」（由中国气象局分级换算） */
    private String level;

    /** 当前中心最大风力（级） */
    private Integer windLevel;

    /** 当前最大风速（m/s） */
    private Integer windSpeed;

    /** 当前中心最低气压（hPa） */
    private Integer pressure;

    /** 当前移动速度（km/h） */
    private Double moveSpeed;

    /** 移动方向中文名，如「西北」 */
    private String moveDir;

    /** 移动方向（方位角，度） */
    private Double moveDeg;

    /** 当前中心纬度 */
    private Double lat;

    /** 当前中心经度 */
    private Double lon;

    /** 中心位置文本，如 19.5°N 123.8°E */
    private String centerText;

    /** 生成时间（yyyy-MM-dd HH:mm） */
    private String generateTime;

    /** 消亡时间（yyyy-MM-dd HH:mm，活跃台风为空） */
    private String dissipateTime;

    /** 历史最大风力（级） */
    private Integer maxWindForce;

    /** 历史最大风速（m/s） */
    private Integer maxWindSpeed;

    /** 是否已归档（历史档案标记） */
    private Boolean archived;

    public TyphoonVO() {
    }

    /**
     * 由实体 + 最新实况轨迹组装 VO。
     *
     * @param typhoon 台风实体
     * @param latest  该台风最新一条实况轨迹；可能为 null（尚未同步到轨迹）
     * @return 视图对象
     */
    public static TyphoonVO of(Typhoon typhoon, TyphoonTrack latest) {
        TyphoonVO vo = new TyphoonVO();
        if (typhoon == null) {
            return vo;
        }
        vo.id = typhoon.getTyphoonId();
        vo.code = typhoon.getInternationalId();
        vo.nameCn = typhoon.getChineseName();
        vo.nameEn = typhoon.getEnglishName();
        vo.year = typhoon.getYear();
        vo.basin = typhoon.getBasin();
        vo.status = typhoon.isActive() ? STATUS_ACTIVE : STATUS_ARCHIVED;
        vo.statusText = typhoon.isActive() ? "活跃中" : "已结束";
        vo.archived = Boolean.valueOf(typhoon.getHistoricalArchive() != null
                && typhoon.getHistoricalArchive().booleanValue());
        vo.maxWindForce = typhoon.getMaxWindForce();
        vo.maxWindSpeed = typhoon.getMaxWindSpeed();
        vo.generateTime = DateUtil.format(typhoon.getGenerateTime());
        vo.dissipateTime = DateUtil.format(typhoon.getDissipateTime());

        // 强度：活跃台风取当前实况，历史台风取历史极值
        Integer force = null;
        Integer speed = null;
        Integer pressure = null;
        if (latest != null) {
            force = latest.getWindForce();
            speed = latest.getWindSpeed();
            pressure = latest.getPressure();
        }
        if (force == null) {
            force = typhoon.getMaxWindForce();
        }
        if (speed == null) {
            speed = typhoon.getMaxWindSpeed();
        }
        if (pressure == null) {
            pressure = typhoon.getMinPressure();
        }
        vo.windLevel = force;
        vo.windSpeed = speed;
        vo.pressure = pressure;
        vo.level = levelName(force);

        if (latest != null) {
            vo.lat = latest.getLatitude();
            vo.lon = latest.getLongitude();
            vo.moveDeg = latest.getMoveDirection();
            vo.moveSpeed = latest.getMoveSpeed();
            vo.moveDir = latest.getMoveDirection() == null
                    ? null : GeoUtil.directionName(latest.getMoveDirection());
            vo.centerText = centerText(latest.getLatitude(), latest.getLongitude());
        }
        return vo;
    }

    /**
     * 按风速等级换算中国气象局强度名称。
     *
     * @param windForce 中心最大风力（级）
     * @return 强度名称；入参为空时返回 null
     */
    public static String levelName(Integer windForce) {
        if (windForce == null) {
            return null;
        }
        if (windForce >= 16) {
            return "超强台风";
        }
        if (windForce >= 14) {
            return "强台风";
        }
        if (windForce >= 12) {
            return "台风";
        }
        if (windForce >= 10) {
            return "强热带风暴";
        }
        if (windForce >= 8) {
            return "热带风暴";
        }
        return "热带低压";
    }

    /**
     * 生成"中心位置"文本。
     *
     * @param lat 纬度
     * @param lon 经度
     * @return 形如 {@code 19.5°N 123.8°E}；参数为空时返回 null
     */
    public static String centerText(Double lat, Double lon) {
        if (lat == null || lon == null) {
            return null;
        }
        return String.format("%.1f°N %.1f°E", lat, lon);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getNameCn() {
        return nameCn;
    }

    public void setNameCn(String nameCn) {
        this.nameCn = nameCn;
    }

    public String getNameEn() {
        return nameEn;
    }

    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getBasin() {
        return basin;
    }

    public void setBasin(String basin) {
        this.basin = basin;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusText() {
        return statusText;
    }

    public void setStatusText(String statusText) {
        this.statusText = statusText;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
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

    public Double getMoveSpeed() {
        return moveSpeed;
    }

    public void setMoveSpeed(Double moveSpeed) {
        this.moveSpeed = moveSpeed;
    }

    public String getMoveDir() {
        return moveDir;
    }

    public void setMoveDir(String moveDir) {
        this.moveDir = moveDir;
    }

    public Double getMoveDeg() {
        return moveDeg;
    }

    public void setMoveDeg(Double moveDeg) {
        this.moveDeg = moveDeg;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLon() {
        return lon;
    }

    public void setLon(Double lon) {
        this.lon = lon;
    }

    public String getCenterText() {
        return centerText;
    }

    public void setCenterText(String centerText) {
        this.centerText = centerText;
    }

    public String getGenerateTime() {
        return generateTime;
    }

    public void setGenerateTime(String generateTime) {
        this.generateTime = generateTime;
    }

    public String getDissipateTime() {
        return dissipateTime;
    }

    public void setDissipateTime(String dissipateTime) {
        this.dissipateTime = dissipateTime;
    }

    public Integer getMaxWindForce() {
        return maxWindForce;
    }

    public void setMaxWindForce(Integer maxWindForce) {
        this.maxWindForce = maxWindForce;
    }

    public Integer getMaxWindSpeed() {
        return maxWindSpeed;
    }

    public void setMaxWindSpeed(Integer maxWindSpeed) {
        this.maxWindSpeed = maxWindSpeed;
    }

    public Boolean getArchived() {
        return archived;
    }

    public void setArchived(Boolean archived) {
        this.archived = archived;
    }
}
