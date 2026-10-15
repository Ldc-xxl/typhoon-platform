package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.DataSourceDao;
import com.zfz.typhoon.dao.ForecastAgencyDao;
import com.zfz.typhoon.dao.ForecastPathDao;
import com.zfz.typhoon.dao.TyphoonDao;
import com.zfz.typhoon.dao.TyphoonTrackDao;
import com.zfz.typhoon.dao.WindCircleDao;
import com.zfz.typhoon.dao.impl.DataSourceDaoImpl;
import com.zfz.typhoon.dao.impl.ForecastAgencyDaoImpl;
import com.zfz.typhoon.dao.impl.ForecastPathDaoImpl;
import com.zfz.typhoon.dao.impl.TyphoonDaoImpl;
import com.zfz.typhoon.dao.impl.TyphoonTrackDaoImpl;
import com.zfz.typhoon.dao.impl.WindCircleDaoImpl;
import com.zfz.typhoon.model.DataSource;
import com.zfz.typhoon.model.ForecastAgency;
import com.zfz.typhoon.model.ForecastPath;
import com.zfz.typhoon.model.SyncLog;
import com.zfz.typhoon.model.Typhoon;
import com.zfz.typhoon.model.TyphoonTrack;
import com.zfz.typhoon.model.WindCircle;
import com.zfz.typhoon.service.DataSourceService;
import com.zfz.typhoon.service.TyphoonSyncService;
import com.zfz.typhoon.util.CmaApiClient;
import com.zfz.typhoon.util.DBUtil;
import com.zfz.typhoon.util.DateUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 台风数据同步实现：采集中央气象台台风网 → 落库四张表 → 登记同步日志。
 *
 * <p>同步策略与限制见 {@link TyphoonSyncService} 的类注释，这里只说三处实现要点：
 *
 * <ol>
 *   <li><b>单个台风一个事务</b>。32 个台风里坏掉一个（接口偶发返回残缺结构）
 *       不该把前面 31 个的成功一起回滚，所以事务边界放在"单个台风"这一层，
 *       外层循环用 try/catch 把异常折算成 {@code failed} 计数。</li>
 *   <li><b>先删预报再写</b>。轨迹与风圈都有「台风 + 观测时间」维度的 upsert
 *       （重复同步只会覆盖同一条），但 {@code forecast_paths} 没有对应唯一键，
 *       因此每轮先按台风清空旧预报再批量插入，保证库里只留最新一批。</li>
 *   <li><b>状态收尾是保守的</b>。见 {@link TyphoonSyncService} 的"已知限制"。</li>
 * </ol>
 *
 * @author 追风者项目组
 */
public class TyphoonSyncServiceImpl implements TyphoonSyncService {

    private static final Logger LOG = Logger.getLogger(TyphoonSyncServiceImpl.class.getName());

    /** 与 {@code TyphoonDaoImpl} 的内部常量保持同一字面量（那边是 private，无法直接引用） */
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_FINISHED = "FINISHED";

    /** 接口不提供所属海域，统一按业务范围填西北太平洋 */
    private static final String DEFAULT_BASIN = "西北太平洋";

    /** data_sources 表里中央气象台台风网那一行的编码 */
    private static final String CMA_SOURCE_CODE = "CMA_TP_API";

    /**
     * 预报机构代码映射：接口用的是国际气象电码（WMO 站点标识），
     * 库里 {@code forecast_agencies.agency_code} 用的是机构简称，两者需要翻译。
     * <p>实测接口目前只返回 {@code BABJ}，其余几项是按其命名规律预留的；
     * 遇到表里没有的代码会跳过该条预报，而不是凭空造一个机构出来。
     */
    private static final Map<String, String> AGENCY_ALIAS = new HashMap<String, String>();
    static {
        AGENCY_ALIAS.put("BABJ", "CMA");     // 北京 = 中央气象台
        AGENCY_ALIAS.put("RJTD", "JMA");     // 东京 = 日本气象厅
        AGENCY_ALIAS.put("KNES", "JTWC");    // 关岛 = 联合台风警报中心
        AGENCY_ALIAS.put("PGUM", "JTWC");
        AGENCY_ALIAS.put("RKSL", "KMA");     // 首尔 = 韩国气象厅
        AGENCY_ALIAS.put("VHHH", "HKO");     // 香港天文台
        AGENCY_ALIAS.put("RPMM", "PAGASA");  // 马尼拉
        AGENCY_ALIAS.put("ECMF", "ECMWF");   // 欧洲中心
    }

    private final CmaApiClient client;
    private final TyphoonDao typhoonDao;
    private final TyphoonTrackDao trackDao;
    private final WindCircleDao windCircleDao;
    private final ForecastPathDao forecastPathDao;
    private final ForecastAgencyDao agencyDao;
    private final DataSourceDao dataSourceDao;
    private final DataSourceService dataSourceService;

    /**
     * 生产用构造：按默认实现装配依赖。
     */
    public TyphoonSyncServiceImpl() {
        this(new CmaApiClient(),
                new TyphoonDaoImpl(), new TyphoonTrackDaoImpl(),
                new WindCircleDaoImpl(), new ForecastPathDaoImpl(), new ForecastAgencyDaoImpl(),
                new DataSourceDaoImpl(), new DataSourceServiceImpl());
    }

    /**
     * 测试用构造：注入替身，让同步逻辑可以完全离线做单元测试。
     *
     * @param client            接口客户端
     * @param typhoonDao        台风 DAO
     * @param trackDao          轨迹 DAO
     * @param windCircleDao     风圈 DAO
     * @param forecastPathDao   预报 DAO
     * @param agencyDao         机构 DAO
     * @param dataSourceDao     数据源 DAO
     * @param dataSourceService 数据源服务（登记同步日志）
     */
    public TyphoonSyncServiceImpl(CmaApiClient client, TyphoonDao typhoonDao,
                                  TyphoonTrackDao trackDao, WindCircleDao windCircleDao,
                                  ForecastPathDao forecastPathDao, ForecastAgencyDao agencyDao,
                                  DataSourceDao dataSourceDao, DataSourceService dataSourceService) {
        this.client = client;
        this.typhoonDao = typhoonDao;
        this.trackDao = trackDao;
        this.windCircleDao = windCircleDao;
        this.forecastPathDao = forecastPathDao;
        this.agencyDao = agencyDao;
        this.dataSourceDao = dataSourceDao;
        this.dataSourceService = dataSourceService;
    }

    @Override
    public SyncResult syncAll() {
        SyncResult result = new SyncResult();
        long started = System.currentTimeMillis();

        List<CmaApiClient.TyphoonBrief> briefs;
        try {
            briefs = client.listTyphoons();
        } catch (RuntimeException e) {
            // 列表都拿不到，这一轮整体失败；不能抛出去，否则定时任务会中断
            result.durationMs = System.currentTimeMillis() - started;
            result.firstError = e.getMessage();
            result.failed = 1;
            LOG.log(Level.WARNING, "同步中止：无法获取台风列表 - " + e.getMessage(), e);
            recordLog(result);
            return result;
        }

        result.fetched = briefs.size();

        // 先把"接口认为活跃"的编号挑出来：只有它们才允许被更新
        Set<String> activeIds = new HashSet<String>();
        for (CmaApiClient.TyphoonBrief b : briefs) {
            if (b.isActive()) {
                String id = CmaApiClient.normalizeIntlId(b.number);
                if (id != null && !id.isEmpty()) {
                    activeIds.add(id);
                }
            }
        }

        for (CmaApiClient.TyphoonBrief b : briefs) {
            String intlId = CmaApiClient.normalizeIntlId(b.number);
            if (intlId == null || intlId.isEmpty()) {
                result.skipped++;
                continue;
            }
            Typhoon existing = typhoonDao.findByInternationalId(intlId);
            boolean feedActive = activeIds.contains(intlId);

            if (existing != null && !feedActive) {
                // 库里已有且接口未列为活跃 —— 一律不动（保护既有档案）
                result.skipped++;
                continue;
            }

            try {
                CmaApiClient.TyphoonDetail detail = client.fetchTyphoon(b.apiId);
                if (detail.points.isEmpty()) {
                    result.skipped++;
                    continue;
                }
                int[] counts = saveOne(detail, intlId, existing);
                result.tracks += counts[0];
                result.circles += counts[1];
                result.forecasts += counts[2];
                if (existing == null) {
                    result.inserted++;
                } else {
                    result.updated++;
                }
            } catch (RuntimeException e) {
                result.failed++;
                if (result.firstError == null) {
                    result.firstError = b.chineseName + "(" + intlId + "): " + e.getMessage();
                }
                LOG.log(Level.WARNING, "同步单条台风失败：" + intlId, e);
            }
        }

        // 库里标着活跃、但接口这轮没把它列为活跃 —— 只统计不改动，交运维确认
        result.stale = countStaleActive(activeIds);

        result.durationMs = System.currentTimeMillis() - started;
        LOG.info("台风数据同步完成：" + result.summary());
        recordLog(result);
        return result;
    }

    /* ==================================================================== */
    /*  落库                                                                */
    /* ==================================================================== */

    /**
     * 落库单个台风（主表 + 轨迹 + 风圈 + 预报 + 强度极值）。
     *
     * @param d        接口详情
     * @param intlId   归一化后的国际编号
     * @param existing 库里已有记录；null 表示新增
     * @return {@code [轨迹数, 风圈数, 预报数]}
     */
    private int[] saveOne(final CmaApiClient.TyphoonDetail d, final String intlId, final Typhoon existing) {
        final CmaApiClient.TrackPoint first = d.points.get(0);
        final CmaApiClient.TrackPoint last = d.points.get(d.points.size() - 1);
        final boolean active = d.isActive();

        return DBUtil.runInTransaction(new DBUtil.TxWork<int[]>() {
            @Override
            public int[] run() {
                long typhoonId;
                if (existing == null) {
                    Typhoon ty = new Typhoon();
                    ty.setInternationalId(intlId);
                    ty.setChineseName(d.chineseName);
                    ty.setEnglishName(d.englishName);
                    ty.setBasin(DEFAULT_BASIN);
                    ty.setGenerateTime(first.obsTime);
                    ty.setDissipateTime(active ? null : last.obsTime);
                    ty.setStatus(active ? STATUS_ACTIVE : STATUS_FINISHED);
                    // 同步来的台风不是人工整理的历史档案，便于档案页按"实时数据"区分展示
                    ty.setHistoricalArchive(Boolean.FALSE);
                    typhoonId = typhoonDao.insert(ty);
                } else {
                    Typhoon ty = existing;
                    ty.setChineseName(d.chineseName);
                    ty.setEnglishName(d.englishName);
                    ty.setGenerateTime(first.obsTime);
                    ty.setDissipateTime(active ? null : last.obsTime);
                    ty.setStatus(active ? STATUS_ACTIVE : STATUS_FINISHED);
                    typhoonDao.update(ty);
                    typhoonId = ty.getTyphoonId().longValue();
                }

                int trackCount = writeTracks(typhoonId, d);
                int circleCount = writeCircles(typhoonId, d);
                int forecastCount = writeForecasts(typhoonId, last);

                // 主表上的最大风力/最大风速/最低气压直接由轨迹表回算，避免应用层各算一遍
                typhoonDao.refreshIntensityExtremes(typhoonId);

                return new int[] {trackCount, circleCount, forecastCount};
            }
        });
    }

    /**
     * 写轨迹。按「台风 + 观测时间」upsert，重复同步只会刷新同一行。
     *
     * @param typhoonId 台风主键
     * @param d         接口详情
     * @return 轨迹条数
     */
    private int writeTracks(long typhoonId, CmaApiClient.TyphoonDetail d) {
        List<TyphoonTrack> tracks = new ArrayList<TyphoonTrack>(d.points.size());
        for (CmaApiClient.TrackPoint p : d.points) {
            TyphoonTrack t = new TyphoonTrack();
            t.setTyphoonId(Long.valueOf(typhoonId));
            t.setObsTime(p.obsTime);
            t.setLatitude(Double.valueOf(p.latitude));
            t.setLongitude(Double.valueOf(p.longitude));
            t.setWindForce(p.windForce);
            t.setWindSpeed(Integer.valueOf(p.windSpeed));
            t.setPressure(Integer.valueOf(p.pressure));
            t.setMoveDirection(p.moveDirection);
            t.setMoveSpeed(p.moveSpeed);
            tracks.add(t);
        }
        trackDao.upsertAll(tracks);
        return tracks.size();
    }

    /**
     * 写风圈。接口每个观测点最多给三档（30/50/64 KTS），
     * 这里按观测时间把它们合并成 {@code wind_circles} 的一行。
     *
     * @param typhoonId 台风主键
     * @param d         接口详情
     * @return 写入的风圈行数
     */
    private int writeCircles(long typhoonId, CmaApiClient.TyphoonDetail d) {
        int rows = 0;
        for (CmaApiClient.TrackPoint p : d.points) {
            if (p.circles.isEmpty()) {
                continue;
            }
            WindCircle c = new WindCircle();
            c.setTyphoonId(Long.valueOf(typhoonId));
            c.setObsTime(p.obsTime);
            for (CmaApiClient.WindCircleRaw w : p.circles) {
                if (w.level == 7) {
                    c.setLevel7Ne(Integer.valueOf(w.ne));
                    c.setLevel7Se(Integer.valueOf(w.se));
                    c.setLevel7Sw(Integer.valueOf(w.sw));
                    c.setLevel7Nw(Integer.valueOf(w.nw));
                } else if (w.level == 10) {
                    c.setLevel10Ne(Integer.valueOf(w.ne));
                    c.setLevel10Se(Integer.valueOf(w.se));
                    c.setLevel10Sw(Integer.valueOf(w.sw));
                    c.setLevel10Nw(Integer.valueOf(w.nw));
                } else if (w.level == 12) {
                    c.setLevel12Ne(Integer.valueOf(w.ne));
                    c.setLevel12Se(Integer.valueOf(w.se));
                    c.setLevel12Sw(Integer.valueOf(w.sw));
                    c.setLevel12Nw(Integer.valueOf(w.nw));
                }
            }
            // 未出现的等级保持 null，由 WindCircle.nz() 落库为 0
            windCircleDao.upsert(c);
            rows++;
        }
        return rows;
    }

    /**
     * 写预报。只取<b>最后一个观测点</b>携带的预报 —— 历史点上的预报是当时的预测，
     * 保留它们没有业务意义，反而会让"预报路径"页画出几十条过期曲线。
     *
     * @param typhoonId 台风主键
     * @param last     最后一个观测点
     * @return 写入的预报点数
     */
    private int writeForecasts(long typhoonId, CmaApiClient.TrackPoint last) {
        if (last.forecasts.isEmpty()) {
            return 0;
        }
        List<ForecastPath> paths = new ArrayList<ForecastPath>(last.forecasts.size());
        for (CmaApiClient.ForecastPoint f : last.forecasts) {
            Long agencyId = resolveAgency(f.agencyCode);
            if (agencyId == null) {
                continue;
            }
            ForecastPath fp = new ForecastPath();
            fp.setTyphoonId(Long.valueOf(typhoonId));
            fp.setAgencyId(agencyId);
            fp.setPublishTime(f.publishTime);
            fp.setForecastTime(f.publishTime.plusHours(f.hours));
            fp.setLatitude(Double.valueOf(f.latitude));
            fp.setLongitude(Double.valueOf(f.longitude));
            fp.setForecastWindForce(f.windForce);
            fp.setForecastWindSpeed(Integer.valueOf(f.windSpeed));
            fp.setForecastPressure(Integer.valueOf(f.pressure));
            paths.add(fp);
        }
        if (paths.isEmpty()) {
            return 0;
        }
        // forecast_paths 没有 (台风,机构,发布时间) 唯一键，先清后写保证幂等
        forecastPathDao.deleteByTyphoon(typhoonId);
        forecastPathDao.batchInsert(paths);
        return paths.size();
    }

    /**
     * 把接口机构代码翻译成库里的机构主键。
     *
     * @param code 接口代码，如 BABJ
     * @return 机构主键；无法映射返回 null（调用方跳过该条预报）
     */
    private Long resolveAgency(String code) {
        if (code == null) {
            return null;
        }
        String mapped = AGENCY_ALIAS.get(code.trim().toUpperCase());
        if (mapped == null) {
            LOG.fine("未登记的预报机构代码，已跳过：" + code);
            return null;
        }
        ForecastAgency a = agencyDao.findByCode(mapped);
        if (a == null) {
            LOG.fine("预报机构未在库中登记，已跳过：" + mapped);
            return null;
        }
        return a.getAgencyId();
    }

    /**
     * 统计"库里标着 ACTIVE、但接口本轮未列为活跃"的台风数量。
     *
     * <p>只统计不改动：这类台风既可能是刚刚结束、尚未收尾的真实台风，
     * 也可能是人工录入的演示数据，程序无法分辨，交给运维判断。
     *
     * @param activeIds 接口本轮标记为活跃的编号集合
     * @return 待确认条数
     */
    private int countStaleActive(Set<String> activeIds) {
        try {
            List<Typhoon> actives = typhoonDao.listByStatus(STATUS_ACTIVE);
            int n = 0;
            for (Typhoon t : actives) {
                String id = t.getInternationalId();
                if (id == null || !activeIds.contains(id)) {
                    n++;
                }
            }
            return n;
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "统计待确认台风失败（不影响同步结果）", e);
            return 0;
        }
    }

    /**
     * 把本轮结果登记为一条同步日志，并顺带推进数据源的"最后同步时间"。
     *
     * <p>登记失败不影响同步本身 —— 数据已经进库了，日志只是运维信息，
     * 不能因为写日志失败就让调用方以为同步没成功。
     *
     * @param result 本轮结果
     */
    private void recordLog(SyncResult result) {
        try {
            DataSource source = dataSourceDao.findByCode(CMA_SOURCE_CODE);
            if (source == null) {
                LOG.warning("数据源 " + CMA_SOURCE_CODE + " 未在库中登记，跳过同步日志");
                return;
            }
            SyncLog log = new SyncLog();
            log.setSourceId(source.getSourceId());
            log.setSyncTime(DateUtil.now());
            log.setRecordCount(Integer.valueOf(result.recordCount()));
            log.setSyncStatus(result.ok() ? SyncLog.STATUS_SUCCESS : SyncLog.STATUS_FAILED);
            log.setDurationSeconds(Integer.valueOf((int) Math.max(0L, result.durationMs / 1000L)));
            if (!result.ok()) {
                log.setErrorMessage(truncate(result.firstError));
            }
            dataSourceService.recordSync(log);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "登记同步日志失败（数据本身已落库）", e);
        }
    }

    /**
     * 截断异常信息到库列长度（{@code error_message varchar(500)}）以内。
     *
     * @param s 原始文本
     * @return 不超过 500 字符的文本
     */
    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 500 ? s : s.substring(0, 500);
    }
}
