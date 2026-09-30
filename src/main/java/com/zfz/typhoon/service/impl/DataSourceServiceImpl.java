package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.DataSourceDao;
import com.zfz.typhoon.dao.SyncLogDao;
import com.zfz.typhoon.dao.impl.DataSourceDaoImpl;
import com.zfz.typhoon.dao.impl.SyncLogDaoImpl;
import com.zfz.typhoon.model.DataSource;
import com.zfz.typhoon.model.SyncLog;
import com.zfz.typhoon.model.vo.DataSourceVO;
import com.zfz.typhoon.service.DataSourceService;
import com.zfz.typhoon.service.NotificationService;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.DateUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据源接入与同步监控业务实现。
 *
 * <h3>统计口径</h3>
 * 「今日请求次数」用 {@code sync_logs} 当天的 {@code record_count} 求和，
 * 而不是统计日志条数——因为一次同步可能拉回几百条轨迹记录，
 * 运维关心的是数据量而不是调用次数。
 *
 * <p>「成功率」直接取 {@code sync_logs} 按状态统计的比值。
 * 数据源实体上虽然有 {@code success_count / fail_count} 两个累计列，
 * 但它们会随运行时间无限增长，越到后期越掩盖近期故障；
 * 因此列表里展示"近期成功率"更有诊断价值，累计值只作辅助。
 *
 * @author 追风者项目组
 * @see DataSourceService
 */
public class DataSourceServiceImpl implements DataSourceService {

    /** 默认日志条数 */
    private static final int DEFAULT_LOG_LIMIT = 20;

    /** 日志条数上限 */
    private static final int MAX_LOG_LIMIT = 200;

    /** 成功率低于此值判定为「异常」，与 {@link DataSourceVO} 的口径一致 */
    private static final double RATE_OK = 0.99D;

    /** 成功率低于此值判定为「延迟」 */
    private static final double RATE_DELAYED = 0.95D;

    private final DataSourceDao sourceDao;
    private final SyncLogDao logDao;
    private final NotificationService notificationService;

    /**
     * 生产用构造：按默认实现装配依赖。
     * <p>之所以把它拆成"无参 + 带参"两个构造，是为了给单元测试留出注入替身的缝隙：
     * 依赖全部在字段初始化里 new 出来的话，测试就必须连上真实 MySQL 才能跑，
     * 单元测试也就退化成了集成测试。
     */
    public DataSourceServiceImpl() {
        this(new DataSourceDaoImpl(), new SyncLogDaoImpl(), new NotificationServiceImpl());
    }

    /**
     * 测试用构造：注入替身依赖（Mockito 生成的 mock 或手写桩）。
     * <p>参数顺序与上方字段声明一致，均为本服务运行所需的协作者。
     */
    public DataSourceServiceImpl(DataSourceDao sourceDao, SyncLogDao logDao, NotificationService notificationService) {
        this.sourceDao = sourceDao;
        this.logDao = logDao;
        this.notificationService = notificationService;
    }

    @Override
    public List<DataSourceVO> list(boolean onlyEnabled) {
        List<DataSource> rows = onlyEnabled ? sourceDao.listEnabled() : sourceDao.listAll();
        List<DataSourceVO> result = new ArrayList<DataSourceVO>(rows.size());
        for (DataSource s : rows) {
            result.add(toVo(s));
        }
        return result;
    }

    @Override
    public DataSourceVO getById(long sourceId) {
        DataSource s = sourceDao.findById(sourceId);
        if (s == null) {
            throw BusinessException.notFound("数据源不存在：" + sourceId);
        }
        return toVo(s);
    }

    @Override
    public int setEnabled(long sourceId, boolean enabled) {
        requireSource(sourceId);
        int rows = sourceDao.setEnabled(sourceId, enabled);
        if (rows == 0) {
            throw BusinessException.notFound("数据源不存在：" + sourceId);
        }
        return rows;
    }

    @Override
    public long recordSync(SyncLog log) {
        if (log == null || log.getSourceId() == null) {
            throw BusinessException.badRequest("同步日志缺少 sourceId");
        }
        long sourceId = log.getSourceId().longValue();
        DataSource source = requireSource(sourceId);

        String status = log.getSyncStatus();
        if (status == null || status.trim().isEmpty()) {
            status = SyncLog.STATUS_SUCCESS;
        }
        status = status.trim().toUpperCase();
        if (!SyncLog.STATUS_SUCCESS.equals(status) && !SyncLog.STATUS_FAILED.equals(status)) {
            throw BusinessException.badRequest("syncStatus 取值非法，应为 SUCCESS 或 FAILED");
        }
        log.setSyncStatus(status);
        if (log.getSyncTime() == null) {
            log.setSyncTime(DateUtil.now());
        }
        if (log.getRecordCount() == null) {
            log.setRecordCount(Integer.valueOf(0));
        }
        if (log.getDurationSeconds() == null) {
            log.setDurationSeconds(Integer.valueOf(0));
        }

        long id = logDao.insert(log);

        // 同步成功才推进 last_sync_time：失败时推进会让监控页显示"刚刚更新过"，
        // 掩盖故障——这是监控语义上一个很容易踩的坑
        if (SyncLog.STATUS_SUCCESS.equals(status)) {
            sourceDao.updateLastSyncTime(sourceId, log.getSyncTime());
        } else {
            notificationService.notifySyncFailure(source.getSourceName(), log.getErrorMessage());
        }
        return id;
    }

    @Override
    public List<SyncLog> recentLogs(int limit) {
        int n = limit <= 0 ? DEFAULT_LOG_LIMIT : Math.min(limit, MAX_LOG_LIMIT);
        return logDao.listRecent(n);
    }

    @Override
    public Map<String, Object> monitorSummary() {
        List<DataSource> all = sourceDao.listAll();
        int ok = 0;
        int delayed = 0;
        int error = 0;
        int disabled = 0;
        long todayRecords = 0L;

        for (DataSource s : all) {
            if (Boolean.FALSE.equals(s.getEnabled())) {
                disabled++;
                continue;
            }
            long id = s.getSourceId().longValue();
            Double rate = currentRate(id);
            if (rate == null) {
                // 从未同步过：不归入任何健康档，但也不当作正常，计入 error 更保守
                error++;
            } else if (rate.doubleValue() >= RATE_OK) {
                ok++;
            } else if (rate.doubleValue() >= RATE_DELAYED) {
                delayed++;
            } else {
                error++;
            }
            todayRecords += logDao.sumRecordCountToday(id);
        }

        int active = all.size() - disabled;
        Double overallRate = averageRate(all);

        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("sources", Integer.valueOf(all.size()));
        m.put("active", Integer.valueOf(active));
        m.put("ok", Integer.valueOf(ok));
        m.put("delayed", Integer.valueOf(delayed));
        m.put("error", Integer.valueOf(error));
        m.put("disabled", Integer.valueOf(disabled));
        m.put("todayRecords", Long.valueOf(todayRecords));
        m.put("successRate", overallRate == null ? "—"
                : String.format("%.1f%%", Double.valueOf(overallRate.doubleValue() * 100D)));
        m.put("updatedAt", DateUtil.format(DateUtil.now()));
        return m;
    }

    @Override
    public long create(DataSource source) {
        if (source == null) {
            throw BusinessException.badRequest("数据源信息不能为空");
        }
        if (source.getSourceCode() == null || source.getSourceCode().trim().isEmpty()) {
            throw BusinessException.badRequest("sourceCode 不能为空");
        }
        String code = source.getSourceCode().trim().toUpperCase();
        source.setSourceCode(code);
        if (sourceDao.findByCode(code) != null) {
            throw BusinessException.badRequest("数据源代码已存在：" + code);
        }
        if (source.getSourceName() == null || source.getSourceName().trim().isEmpty()) {
            source.setSourceName(code);
        }
        if (source.getEnabled() == null) {
            source.setEnabled(Boolean.TRUE);
        }
        if (source.getUpdateFrequency() == null) {
            source.setUpdateFrequency(Integer.valueOf(30));
        }
        source.setSuccessCount(Integer.valueOf(0));
        source.setFailCount(Integer.valueOf(0));
        return sourceDao.insert(source);
    }

    @Override
    public int update(DataSource source) {
        if (source == null || source.getSourceId() == null) {
            throw BusinessException.badRequest("缺少 sourceId");
        }
        requireSource(source.getSourceId().longValue());
        // 编码是数据源的身份，不允许改
        source.setSourceCode(null);
        // 统计字段由同步流程维护，不接受外部覆写
        source.setSuccessCount(null);
        source.setFailCount(null);
        return sourceDao.update(source);
    }

    /* ==================================================================== */
    /*  内部                                                                */
    /* ==================================================================== */

    /**
     * 组装数据源视图（含实时统计）。
     *
     * @param source 数据源实体
     * @return 视图
     */
    private DataSourceVO toVo(DataSource source) {
        long id = source.getSourceId().longValue();
        int today = (int) Math.min(Integer.MAX_VALUE, logDao.sumRecordCountToday(id));
        DataSourceVO vo = DataSourceVO.of(source, today, currentRate(id));
        vo.setAvgDuration(Double.valueOf(logDao.avgDurationSeconds(id)));
        return vo;
    }

    /**
     * 近期成功率。
     *
     * <p><b>量纲说明（曾在此处踩坑）</b>：{@code SyncLogDao.successRate()} 返回的是
     * <b>百分数</b>（{@code 100d * ok / total}，取值 0—100），而本类下游的
     * {@link #RATE_OK}（0.99）、{@link #RATE_DELAYED}（0.95）以及
     * {@link #averageRate} 之后统一乘 100 展示的写法，全部是按 <b>0—1</b> 设计的。
     * 两侧量纲不一致时，{@code rate >= 0.99} 对百分数恒成立，
     * 结果是<b>所有数据源都被判成"正常"</b>，健康档位形同虚设；
     * 同时概览里的成功率会被再乘一次 100，显示成 {@code 10000.0%}。
     * 因此这里统一归一为 0—1，DAO 仍保持百分数契约不变。
     *
     * @param sourceId 数据源ID
     * @return 0—1；无日志返回 null
     */
    private Double currentRate(long sourceId) {
        int total = logDao.countBySource(sourceId);
        if (total <= 0) {
            return null;
        }
        double rate = logDao.successRate(sourceId);
        return Double.valueOf(rate / 100D);
    }

    /**
     * 全部启用数据源的平均成功率。
     *
     * @param all 全部数据源
     * @return 0—1；无数据返回 null
     */
    private Double averageRate(List<DataSource> all) {
        double sum = 0D;
        int counted = 0;
        for (DataSource s : all) {
            if (Boolean.FALSE.equals(s.getEnabled())) {
                continue;
            }
            Double rate = currentRate(s.getSourceId().longValue());
            if (rate == null) {
                continue;
            }
            sum += rate.doubleValue();
            counted++;
        }
        return counted == 0 ? null : Double.valueOf(sum / counted);
    }

    /**
     * 取数据源，不存在抛 404。
     *
     * @param sourceId 数据源ID
     * @return 实体
     */
    private DataSource requireSource(long sourceId) {
        DataSource s = sourceDao.findById(sourceId);
        if (s == null) {
            throw BusinessException.notFound("数据源不存在：" + sourceId);
        }
        return s;
    }
}
