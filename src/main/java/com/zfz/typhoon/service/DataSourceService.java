package com.zfz.typhoon.service;

import com.zfz.typhoon.model.DataSource;
import com.zfz.typhoon.model.SyncLog;
import com.zfz.typhoon.model.vo.DataSourceVO;

import java.util.List;
import java.util.Map;

/**
 * 数据源接入与同步监控业务接口。
 *
 * <p>对应接口文档 {@code /api/admin/datasources}、{@code /api/admin/logs/sync}。
 *
 * <h3>本模块要回答的问题</h3>
 * 平台的每一条台风数据都来自外部机构（CMA / JMA / JTWC / ECMWF …）。
 * 一旦某个源挂掉，页面上看到的就不是"没有数据"而是"过期的旧数据"——
 * 后者危险得多，因为它看起来是正常的。因此监控模块的核心不是"展示列表"，
 * 而是把「最后同步时间 + 成功率」翻译成一个一眼能看懂的状态结论
 * （正常 / 延迟 / 异常 / 停用）。这个翻译在 {@link DataSourceVO#of} 里做，
 * 保证前端、后台、报表三处口径完全一致。
 *
 * @author 追风者项目组
 */
public interface DataSourceService {

    /**
     * 查询数据源列表（含实时统计）。
     *
     * @param onlyEnabled true 表示只返回启用的
     * @return 视图列表
     */
    List<DataSourceVO> list(boolean onlyEnabled);

    /**
     * 查询单个数据源。
     *
     * @param sourceId 数据源ID
     * @return 视图；不存在抛 404
     */
    DataSourceVO getById(long sourceId);

    /**
     * 启用 / 停用数据源。
     *
     * @param sourceId 数据源ID
     * @param enabled  目标状态
     * @return 受影响行数
     */
    int setEnabled(long sourceId, boolean enabled);

    /**
     * 登记一次同步结果（写 {@code sync_logs} 并更新数据源的累计统计）。
     *
     * @param log 同步日志（sourceId / syncStatus / recordCount / durationSeconds 必填）
     * @return 新日志ID
     */
    long recordSync(SyncLog log);

    /**
     * 最近同步日志。
     *
     * @param limit 条数上限
     * @return 日志列表
     */
    List<SyncLog> recentLogs(int limit);

    /**
     * 后台"数据源监控"卡片的汇总数据。
     *
     * @return 键为 {@code sources / ok / delayed / error / todayRecords / successRate}
     */
    Map<String, Object> monitorSummary();

    /**
     * 新增数据源（管理员操作）。
     *
     * @param source 数据源实体
     * @return 新数据源ID
     */
    long create(DataSource source);

    /**
     * 修改数据源。
     *
     * @param source 数据源实体（须含 sourceId）
     * @return 受影响行数
     */
    int update(DataSource source);
}
