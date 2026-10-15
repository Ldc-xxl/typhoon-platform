package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.DisasterReportDao;
import com.zfz.typhoon.dao.TyphoonDao;
import com.zfz.typhoon.dao.TyphoonTrackDao;
import com.zfz.typhoon.dao.impl.DisasterReportDaoImpl;
import com.zfz.typhoon.dao.impl.TyphoonDaoImpl;
import com.zfz.typhoon.dao.impl.TyphoonTrackDaoImpl;
import com.zfz.typhoon.dao.query.TyphoonQuery;
import com.zfz.typhoon.model.DisasterReport;
import com.zfz.typhoon.model.Typhoon;
import com.zfz.typhoon.model.TyphoonTrack;
import com.zfz.typhoon.model.vo.TyphoonVO;
import com.zfz.typhoon.service.TyphoonService;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.DateUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 台风档案业务实现。
 *
 * <h3>批量组装最新实测点（避免 N+1）</h3>
 * {@link #assemble} 的业务是"给每个台风补上当前强度与位置"。若逐个调用
 * {@code findLatest(typhoonId)}，13 个台风就要 13 次查询；
 * 这里改为一次性 {@code listLatestByTyphoons(ids)} 取回，再用 Map 归组，
 * 把往返次数压到 <b>2 次</b>（1 次主表 + 1 次最新点）。
 *
 * <h3>失效降级</h3>
 * 若某个台风<b>没有轨迹数据</b>（历史数据缺失），{@code latest} 为 null，
 * 此时 {@link TyphoonVO#of} 会自动回退到主表的历史极值字段
 * （{@code max_wind_force / max_wind_speed / min_pressure}），
 * 保证列表不会出现空白强度，也不会抛异常。
 *
 * @author 追风者项目组
 */
public class TyphoonServiceImpl implements TyphoonService {

    private final TyphoonDao typhoonDao;
    private final TyphoonTrackDao trackDao;
    private final DisasterReportDao disasterDao;

    /**
     * 生产用构造：按默认实现装配依赖。
     * <p>之所以把它拆成"无参 + 带参"两个构造，是为了给单元测试留出注入替身的缝隙：
     * 依赖全部在字段初始化里 new 出来的话，测试就必须连上真实 MySQL 才能跑，
     * 单元测试也就退化成了集成测试。
     */
    public TyphoonServiceImpl() {
        this(new TyphoonDaoImpl(), new TyphoonTrackDaoImpl(), new DisasterReportDaoImpl());
    }

    /**
     * 测试用构造：注入替身依赖（Mockito 生成的 mock 或手写桩）。
     * <p>参数顺序与上方字段声明一致，均为本服务运行所需的协作者。
     */
    public TyphoonServiceImpl(TyphoonDao typhoonDao, TyphoonTrackDao trackDao, DisasterReportDao disasterDao) {
        this.typhoonDao = typhoonDao;
        this.trackDao = trackDao;
        this.disasterDao = disasterDao;
    }

    @Override
    public List<TyphoonVO> list(TyphoonQuery query) {
        List<Typhoon> rows = typhoonDao.search(query);
        return assemble(rows);
    }

    @Override
    public List<TyphoonVO> listActive() {
        return assemble(typhoonDao.listByStatus(TyphoonDao.STATUS_ACTIVE));
    }

    @Override
    public List<TyphoonVO> listArchived() {
        // 历史台风 = 非 ACTIVE 的全部（含 FINISHED 与潜在的历史遗留状态），
        // 与前端"历史台风"分组的语义一致，避免漏掉状态异常的数据。
        List<Typhoon> all = typhoonDao.listAll();
        List<Typhoon> archived = new ArrayList<Typhoon>();
        for (Typhoon t : all) {
            if (!TyphoonDao.STATUS_ACTIVE.equalsIgnoreCase(t.getStatus())) {
                archived.add(t);
            }
        }
        return assemble(archived);
    }

    @Override
    public List<TyphoonVO> search(String keyword, String status) {
        return assemble(typhoonDao.search(TyphoonQuery.of(keyword, status)));
    }

    @Override
    public int count(TyphoonQuery query) {
        return typhoonDao.countByCondition(query);
    }

    @Override
    public TyphoonVO findById(long typhoonId) {
        Typhoon t = typhoonDao.findById(typhoonId);
        if (t == null) {
            throw BusinessException.notFound("台风不存在：id=" + typhoonId);
        }
        return TyphoonVO.of(t, trackDao.findLatest(typhoonId));
    }

    @Override
    public TyphoonVO findByCode(String internationalId) {
        if (internationalId == null || internationalId.trim().isEmpty()) {
            throw BusinessException.badRequest("国际编号不能为空");
        }
        Typhoon t = typhoonDao.findByInternationalId(internationalId.trim());
        if (t == null) {
            throw BusinessException.notFound("台风不存在：编号=" + internationalId);
        }
        return TyphoonVO.of(t, trackDao.findLatest(t.getTyphoonId().longValue()));
    }

    @Override
    public Typhoon requireTyphoon(long typhoonId) {
        Typhoon t = typhoonDao.findById(typhoonId);
        if (t == null) {
            throw BusinessException.notFound("台风不存在：id=" + typhoonId);
        }
        return t;
    }

    @Override
    public TyphoonTrack latestTrack(long typhoonId) {
        return trackDao.findLatest(typhoonId);
    }

    @Override
    public TyphoonTrack trackAt(long typhoonId, LocalDateTime obsTime) {
        if (obsTime == null) {
            return trackDao.findLatest(typhoonId);
        }
        // listByTyphoon 已保证按 obs_time 升序，故一次线性扫描即可，
        // 无需把整表读进来再排序；命中相等时刻立即返回，
        // 遇到第一个晚于 obsTime 的点就停止（后面的只会更晚）。
        TyphoonTrack floor = null;
        for (TyphoonTrack t : trackDao.listByTyphoon(typhoonId)) {
            if (t == null || t.getObsTime() == null) {
                continue;
            }
            if (t.getObsTime().isEqual(obsTime)) {
                return t;
            }
            if (t.getObsTime().isBefore(obsTime)) {
                floor = t;
            } else {
                break;
            }
        }
        return floor;
    }

    @Override
    public List<Integer> listYears() {
        return typhoonDao.listYears();
    }

    @Override
    public List<String> listBasins() {
        return typhoonDao.listBasins();
    }

    @Override
    public DisasterReport disaster(long typhoonId) {
        return disasterDao.findLatestByTyphoon(typhoonId);
    }

    @Override
    public int countAll() {
        return typhoonDao.countAll();
    }

    @Override
    public int countActive() {
        return typhoonDao.countByStatus(TyphoonDao.STATUS_ACTIVE);
    }

    @Override
    public int countArchived() {
        return typhoonDao.countArchived();
    }

    @Override
    public long create(Typhoon typhoon) {
        if (typhoon.getInternationalId() == null || typhoon.getInternationalId().trim().isEmpty()) {
            throw BusinessException.badRequest("国际编号不能为空");
        }
        if (typhoon.getChineseName() == null || typhoon.getChineseName().trim().isEmpty()) {
            throw BusinessException.badRequest("台风中文名不能为空");
        }
        // 唯一键前置校验：把数据库的 1062 重复键异常转换成可读的 400 提示
        if (typhoonDao.findByInternationalId(typhoon.getInternationalId()) != null) {
            throw BusinessException.badRequest("国际编号已存在：" + typhoon.getInternationalId());
        }
        if (typhoon.getGenerateTime() == null) {
            typhoon.setGenerateTime(DateUtil.now());
        }
        return typhoonDao.insert(typhoon);
    }

    /**
     * 局部更新台风档案。
     *
     * <h3>为什么必须先读再写</h3>
     * {@code TyphoonDaoImpl.update} 生成的是一条<b>全列</b>
     * {@code UPDATE typhoons SET international_id=?, chinese_name=?, ... WHERE typhoon_id=?}，
     * 每个 {@code ?} 都取自传入实体。若直接把请求体反序列化出的实体丢给它，
     * 那么调用方<b>没有传</b>的字段会以 {@code null} 写进库里：
     * 轻则把台风的中文名、国际编号清空（页面立刻变成空白卡片），
     * 重则撞上 {@code international_id NOT NULL} 约束，
     * 抛 {@code DataAccessException} —— 前端只看到一句
     * 「数据访问失败，请稍后重试」，完全不知道是自己少传了字段。
     *
     * <p>而 {@code PUT} 的语义（以及 {@code js/api.js} 里
     * {@code updateTyphoon(id, payload)} 的文档「{@code payload} 待更新字段」）
     * 都是<b>局部更新</b>：只改传进来的那几项。
     * 因此这里先按主键读出库中现有记录，再用请求体里非 {@code null}
     * 的字段覆盖它，最后整体回写。
     *
     * <h3>已知取舍</h3>
     * 这种"非空即覆盖"的合并方式<b>无法把某个字段显式改成 null</b>
     * （例如清空 {@code dissipateTime}）。台风档案里没有"需要清空"的字段——
     * 结束时间是通过 {@link #archive} 单独写入的——所以此处不构成问题。
     * 若将来真需要清空语义，应改用 {@code PATCH} + 显式 {@code null} 列表。
     *
     * @param typhoon 只包含待更新字段的实体（必须带 typhoonId）
     * @return 受影响行数
     */
    @Override
    public int update(Typhoon typhoon) {
        if (typhoon.getTyphoonId() == null) {
            throw BusinessException.badRequest("缺少 typhoonId");
        }
        Typhoon merged = mergeIntoExisting(typhoon);
        int n = typhoonDao.update(merged);
        if (n == 0) {
            throw BusinessException.notFound("台风不存在：id=" + typhoon.getTyphoonId());
        }
        return n;
    }

    /**
     * 把请求体里的非 null 字段合并到库中现有记录上。
     *
     * @param patch 只包含待更新字段的实体
     * @return 合并后的完整实体
     */
    private Typhoon mergeIntoExisting(Typhoon patch) {
        long id = patch.getTyphoonId().longValue();
        Typhoon current = typhoonDao.findById(id);
        if (current == null) {
            throw BusinessException.notFound("台风不存在：id=" + id);
        }
        if (patch.getInternationalId() != null) {
            current.setInternationalId(patch.getInternationalId());
        }
        if (patch.getChineseName() != null) {
            current.setChineseName(patch.getChineseName());
        }
        if (patch.getEnglishName() != null) {
            current.setEnglishName(patch.getEnglishName());
        }
        if (patch.getBasin() != null) {
            current.setBasin(patch.getBasin());
        }
        if (patch.getGenerateTime() != null) {
            current.setGenerateTime(patch.getGenerateTime());
        }
        if (patch.getDissipateTime() != null) {
            current.setDissipateTime(patch.getDissipateTime());
        }
        if (patch.getStatus() != null) {
            current.setStatus(patch.getStatus());
        }
        if (patch.getMaxWindForce() != null) {
            current.setMaxWindForce(patch.getMaxWindForce());
        }
        if (patch.getMaxWindSpeed() != null) {
            current.setMaxWindSpeed(patch.getMaxWindSpeed());
        }
        if (patch.getMinPressure() != null) {
            current.setMinPressure(patch.getMinPressure());
        }
        if (patch.getHistoricalArchive() != null) {
            current.setHistoricalArchive(patch.getHistoricalArchive());
        }
        return current;
    }

    @Override
    public int archive(long typhoonId, LocalDateTime dissipateTime) {
        requireTyphoon(typhoonId);
        LocalDateTime time = dissipateTime == null ? DateUtil.now() : dissipateTime;
        int n = typhoonDao.archive(typhoonId, time);
        // 归档前重算一次极值，保证档案里的"历史最大风力"是最终值
        typhoonDao.refreshIntensityExtremes(typhoonId);
        return n;
    }

    @Override
    public int delete(long typhoonId) {
        requireTyphoon(typhoonId);
        return typhoonDao.deleteById(typhoonId);
    }

    /* ------------------------------------------------------------------ */
    /*  内部实现                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * 把台风实体列表组装成前端视图列表。
     *
     * @param rows 台风实体列表
     * @return 视图列表
     */
    private List<TyphoonVO> assemble(List<Typhoon> rows) {
        List<TyphoonVO> result = new ArrayList<TyphoonVO>();
        if (rows == null || rows.isEmpty()) {
            return result;
        }

        // 收集需要补"最新实测点"的台风ID
        Map<Long, TyphoonTrack> latestMap = new HashMap<Long, TyphoonTrack>();
        Set<Long> ids = new HashSet<Long>();
        for (Typhoon t : rows) {
            if (t.getTyphoonId() != null) {
                ids.add(t.getTyphoonId());
            }
        }
        if (!ids.isEmpty()) {
            List<TyphoonTrack> latestList = trackDao.listLatestByTyphoons(ids);
            for (TyphoonTrack tr : latestList) {
                latestMap.put(tr.getTyphoonId(), tr);
            }
        }

        for (Typhoon t : rows) {
            TyphoonTrack latest = t.getTyphoonId() == null ? null : latestMap.get(t.getTyphoonId());
            result.add(TyphoonVO.of(t, latest));
        }
        return result;
    }
}
