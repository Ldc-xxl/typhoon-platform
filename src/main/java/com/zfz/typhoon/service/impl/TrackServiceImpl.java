package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.TyphoonDao;
import com.zfz.typhoon.dao.TyphoonTrackDao;
import com.zfz.typhoon.dao.ForecastPathDao;
import com.zfz.typhoon.dao.impl.ForecastPathDaoImpl;
import com.zfz.typhoon.dao.impl.TyphoonDaoImpl;
import com.zfz.typhoon.dao.impl.TyphoonTrackDaoImpl;
import com.zfz.typhoon.model.ForecastPath;
import com.zfz.typhoon.model.TyphoonTrack;
import com.zfz.typhoon.model.vo.ReplayVO;
import com.zfz.typhoon.model.vo.TrackVO;
import com.zfz.typhoon.service.TrackService;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.DateUtil;
import com.zfz.typhoon.util.GeoUtil;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 台风轨迹与回放业务实现。
 *
 * <h3>回放合成算法</h3>
 * <ol>
 *   <li><b>收集锚点</b>：把"实况轨迹点"与"主基准机构（CMA）的最新预报点"
 *       统一读成一条按时间升序的锚点序列，并给每个锚点标注来源；</li>
 *   <li><b>等距采样</b>：从首个锚点时刻起，每 {@code step} 小时取一个采样时刻，
 *       直到最后一个锚点时刻；</li>
 *   <li><b>线性插值</b>：采样时刻落在两个锚点之间时，按时间比例对
 *       经纬度、风力、风速、气压做线性插值；</li>
 *   <li><b>派生量</b>：移向取"本帧相对上一帧"的方位角（{@link GeoUtil#bearing}），
 *       移速取两点大圆距离除以时间差（km/h）。首帧无前序点，移向沿用锚点自带值。</li>
 * </ol>
 *
 * <h3>为什么用 CMA 作为预报段基准</h3>
 * 多家机构在预报段本身存在分歧，回放若混用会让轨迹在机构之间"跳变"。
 * 因此预报段固定取 <b>CMA（中国中央气象台，本平台主基准）</b>的最新一条路径，
 * 与项目描述文档「以 CMA 为主基准，叠加其它机构」的口径一致。
 * 机构间分歧由 P3 分歧解析模块单独呈现，不混进回放。
 *
 * @author 追风者项目组
 */
public class TrackServiceImpl implements TrackService {

    /** 主基准机构代码 */
    private static final String BASE_AGENCY = "CMA";

    /** 帧来源标记：实测 */
    private static final String SOURCE_OBS = "OBS";

    /** 帧来源标记：预报 */
    private static final String SOURCE_FCST = "FCST";

    private final TyphoonTrackDao trackDao;
    private final ForecastPathDao forecastDao;
    private final TyphoonDao typhoonDao;

    /**
     * 生产用构造：按默认实现装配依赖。
     * <p>之所以把它拆成"无参 + 带参"两个构造，是为了给单元测试留出注入替身的缝隙：
     * 依赖全部在字段初始化里 new 出来的话，测试就必须连上真实 MySQL 才能跑，
     * 单元测试也就退化成了集成测试。
     */
    public TrackServiceImpl() {
        this(new TyphoonTrackDaoImpl(), new ForecastPathDaoImpl(), new TyphoonDaoImpl());
    }

    /**
     * 测试用构造：注入替身依赖（Mockito 生成的 mock 或手写桩）。
     * <p>参数顺序与上方字段声明一致，均为本服务运行所需的协作者。
     *
     * <p><b>为什么这里声明的是接口 {@link ForecastPathDao} 而不是实现类</b>：
     * JDK 25 上 Mockito 无法改写 {@code java.lang.Object}，<b>mock 具体类会直接失败</b>。
     * 字段本来就是接口类型，把参数收紧到实现类只会堵死单测，没有任何收益 —— 已改回接口。
     */
    public TrackServiceImpl(TyphoonTrackDao trackDao, ForecastPathDao forecastDao, TyphoonDao typhoonDao) {
        this.trackDao = trackDao;
        this.forecastDao = forecastDao;
        this.typhoonDao = typhoonDao;
    }

    @Override
    public List<TrackVO> listByTyphoon(long typhoonId) {
        List<TyphoonTrack> tracks = trackDao.listByTyphoon(typhoonId);
        List<TrackVO> result = new ArrayList<TrackVO>(tracks.size());
        int total = tracks.size();
        for (int i = 0; i < total; i++) {
            result.add(TrackVO.of(tracks.get(i), i, total));
        }
        return result;
    }

    @Override
    public List<TyphoonTrack> listEntities(long typhoonId) {
        return trackDao.listByTyphoon(typhoonId);
    }

    @Override
    public ReplayVO replay(long typhoonId) {
        return replay(typhoonId, DEFAULT_STEP_HOURS);
    }

    @Override
    public ReplayVO replay(long typhoonId, int stepHours) {
        int step = clampStep(stepHours);

        ReplayVO vo = new ReplayVO();
        vo.setStep(Integer.valueOf(step));

        List<Anchor> anchors = buildAnchors(typhoonId);
        if (anchors.isEmpty()) {
            // 没有任何实况与预报数据：返回空序列，由前端提示"暂无可回放时段"
            return vo;
        }

        LocalDateTime start = anchors.get(0).time;
        LocalDateTime end = anchors.get(anchors.size() - 1).time;
        vo.setStartTime(DateUtil.format(start));
        vo.setEndTime(DateUtil.format(end));

        List<ReplayVO.Frame> frames = new ArrayList<ReplayVO.Frame>();
        LocalDateTime cursor = start;
        ReplayVO.Frame prev = null;
        while (!cursor.isAfter(end)) {
            ReplayVO.Frame frame = sampleAt(anchors, cursor);
            if (frame != null) {
                // 首帧移向沿用锚点自带值；其后按"上一帧 → 本帧"计算
                if (prev != null) {
                    double deg = GeoUtil.bearing(prev.getLat().doubleValue(), prev.getLon().doubleValue(),
                            frame.getLat().doubleValue(), frame.getLon().doubleValue());
                    frame.setMove(Double.valueOf(GeoUtil.round(deg, 1)));
                    double km = GeoUtil.distanceKm(prev.getLat().doubleValue(), prev.getLon().doubleValue(),
                            frame.getLat().doubleValue(), frame.getLon().doubleValue());
                    frame.setMph(Double.valueOf(GeoUtil.round(km / step, 1)));
                }
                frames.add(frame);
                prev = frame;
            }
            cursor = cursor.plusHours(step);
        }

        // 若最后锚点时刻不是 step 的整数倍，采样循环会漏掉它，此处补上末帧，
        // 保证回放一定能推进到预报终点（登陆点）。
        if (prev != null && prev.getTime() != null
                && !DateUtil.format(end).equals(prev.getTime())) {
            ReplayVO.Frame tail = sampleAt(anchors, end);
            if (tail != null) {
                double deg = GeoUtil.bearing(prev.getLat().doubleValue(), prev.getLon().doubleValue(),
                        tail.getLat().doubleValue(), tail.getLon().doubleValue());
                tail.setMove(Double.valueOf(GeoUtil.round(deg, 1)));
                double hours = Math.max(1d, Duration.between(
                        DateUtil.parse(prev.getTime()), end).toMinutes() / 60d);
                double km = GeoUtil.distanceKm(prev.getLat().doubleValue(), prev.getLon().doubleValue(),
                        tail.getLat().doubleValue(), tail.getLon().doubleValue());
                tail.setMph(Double.valueOf(GeoUtil.round(km / hours, 1)));
                frames.add(tail);
            }
        }

        vo.setFrames(frames);
        return vo;
    }

    @Override
    public int countByTyphoon(long typhoonId) {
        return trackDao.countByTyphoon(typhoonId);
    }

    @Override
    public long addTrack(TyphoonTrack track) {
        if (track.getTyphoonId() == null) {
            throw BusinessException.badRequest("缺少 typhoonId");
        }
        if (track.getObsTime() == null) {
            throw BusinessException.badRequest("缺少观测时间 obsTime");
        }
        long id = trackDao.insert(track);
        // 新轨迹点可能刷新历史极值，同步回写主表
        typhoonDao.refreshIntensityExtremes(track.getTyphoonId().longValue());
        return id;
    }

    @Override
    public int upsertTracks(List<TyphoonTrack> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return 0;
        }
        int n = trackDao.upsertAll(tracks);
        // 一次批量写入后统一刷新一次极值（而不是逐条刷新，减少 UPDATE 次数）
        Long typhoonId = tracks.get(0).getTyphoonId();
        if (typhoonId != null) {
            typhoonDao.refreshIntensityExtremes(typhoonId.longValue());
        }
        return n;
    }

    /* ------------------------------------------------------------------ */
    /*  内部实现：锚点与插值                                                 */
    /* ------------------------------------------------------------------ */

    /**
     * 回放锚点：一个已知的、可信的时刻与位置。
     */
    private static final class Anchor {
        /** 时刻 */
        LocalDateTime time;
        /** 经度 */
        double lon;
        /** 纬度 */
        double lat;
        /** 中心风力（级） */
        int level;
        /** 中心风速（m/s） */
        int speed;
        /** 中心气压（hPa） */
        int pressure;
        /** 移向（度），可能为 0 */
        double move;
        /** 来源：OBS / FCST */
        String source;
    }

    /**
     * 构造锚点序列：实况轨迹 + CMA 最新预报段，按时间升序。
     *
     * @param typhoonId 台风ID
     * @return 锚点列表
     */
    private List<Anchor> buildAnchors(long typhoonId) {
        List<Anchor> anchors = new ArrayList<Anchor>();

        // 1) 实况段
        List<TyphoonTrack> tracks = trackDao.listByTyphoon(typhoonId);
        LocalDateTime lastObsTime = null;
        for (TyphoonTrack t : tracks) {
            if (t.getObsTime() == null || t.getLatitude() == null || t.getLongitude() == null) {
                continue;
            }
            Anchor a = new Anchor();
            a.time = t.getObsTime();
            a.lon = t.getLongitude().doubleValue();
            a.lat = t.getLatitude().doubleValue();
            a.level = t.getWindForce() == null ? 0 : t.getWindForce().intValue();
            a.speed = t.getWindSpeed() == null ? 0 : t.getWindSpeed().intValue();
            a.pressure = t.getPressure() == null ? 0 : t.getPressure().intValue();
            a.move = t.getMoveDirection() == null ? 0d : t.getMoveDirection().doubleValue();
            a.source = SOURCE_OBS;
            anchors.add(a);
            lastObsTime = a.time;
        }

        // 2) 预报段（仅取主基准机构，避免机构分歧导致回放跳变）
        List<ForecastPath> paths = forecastDao.listLatestByTyphoonAndAgency(typhoonId, BASE_AGENCY);
        for (ForecastPath p : paths) {
            if (p.getForecastTime() == null || p.getLatitude() == null || p.getLongitude() == null) {
                continue;
            }
            // 预报时刻早于或等于最后实测时刻的，说明已被实况覆盖，跳过以免时间倒流
            if (lastObsTime != null && !p.getForecastTime().isAfter(lastObsTime)) {
                continue;
            }
            Anchor a = new Anchor();
            a.time = p.getForecastTime();
            a.lon = p.getLongitude().doubleValue();
            a.lat = p.getLatitude().doubleValue();
            a.level = p.getForecastWindForce() == null ? 0 : p.getForecastWindForce().intValue();
            a.speed = p.getForecastWindSpeed() == null ? 0 : p.getForecastWindSpeed().intValue();
            a.pressure = p.getForecastPressure() == null ? 0 : p.getForecastPressure().intValue();
            a.move = 0d;
            a.source = SOURCE_FCST;
            anchors.add(a);
        }
        return anchors;
    }

    /**
     * 在锚点序列上取某个时刻的插值帧。
     *
     * @param anchors 锚点序列（按时间升序）
     * @param at      目标时刻
     * @return 帧；目标时刻早于首锚点或晚于末锚点时返回 null
     */
    private ReplayVO.Frame sampleAt(List<Anchor> anchors, LocalDateTime at) {
        Anchor before = null;
        Anchor after = null;
        for (Anchor a : anchors) {
            if (!a.time.isAfter(at)) {
                before = a;
            } else {
                after = a;
                break;
            }
        }
        if (before == null) {
            return null;
        }
        if (after == null) {
            return toFrame(before, at);
        }
        double totalMinutes = Math.max(1d, Duration.between(before.time, after.time).toMinutes());
        double ratio = Duration.between(before.time, at).toMinutes() / totalMinutes;
        ratio = Math.max(0d, Math.min(1d, ratio));

        Anchor blended = new Anchor();
        blended.time = at;
        blended.lon = lerp(before.lon, after.lon, ratio);
        blended.lat = lerp(before.lat, after.lat, ratio);
        blended.level = (int) Math.round(lerp(before.level, after.level, ratio));
        blended.speed = (int) Math.round(lerp(before.speed, after.speed, ratio));
        blended.pressure = (int) Math.round(lerp(before.pressure, after.pressure, ratio));
        blended.move = before.move;
        // 跨过实况→预报边界时，标记为预报段（更保守，提示该段尚未实测验证）
        blended.source = SOURCE_FCST.equals(after.source) ? SOURCE_FCST : before.source;
        return toFrame(blended, at);
    }

    /**
     * 锚点转帧。
     *
     * @param a  锚点
     * @param at 目标时刻（回填到 time 字段）
     * @return 帧
     */
    private ReplayVO.Frame toFrame(Anchor a, LocalDateTime at) {
        ReplayVO.Frame f = new ReplayVO.Frame();
        f.setTime(DateUtil.format(at));
        f.setLon(Double.valueOf(GeoUtil.round(a.lon, 2)));
        f.setLat(Double.valueOf(GeoUtil.round(a.lat, 2)));
        f.setLevel(Integer.valueOf(a.level));
        f.setSpeed(Integer.valueOf(a.speed));
        f.setPressure(Integer.valueOf(a.pressure));
        f.setMove(Double.valueOf(GeoUtil.round(a.move, 1)));
        f.setMph(Double.valueOf(0d));
        f.setSource(a.source);
        return f;
    }

    /**
     * 线性插值。
     *
     * @param from  起点值
     * @param to    终点值
     * @param ratio 比例（0~1）
     * @return 插值结果
     */
    private static double lerp(double from, double to, double ratio) {
        return from + (to - from) * ratio;
    }

    /**
     * 帧间隔约束到 1~24 小时，防止非法值导致帧数爆炸或除零。
     *
     * @param stepHours 原始间隔
     * @return 合法间隔
     */
    private static int clampStep(int stepHours) {
        if (stepHours <= 0) {
            return DEFAULT_STEP_HOURS;
        }
        return Math.min(stepHours, 24);
    }
}
