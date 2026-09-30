package com.zfz.typhoon.dao;

import com.zfz.typhoon.model.DataSource;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据源表（{@code data_sources}）数据访问接口。
 *
 * <p>对应接口文档：{@code GET /api/data-sources}（P7 管理端"数据源监控"面板）。
 *
 * <h3>字段来源说明（易错点）</h3>
 * {@link DataSource} 实体上的 {@code successCount / failCount / avgDuration}
 * <b>不是本表的列</b>——DDL 中 {@code data_sources} 只有 8 个字段。
 * 这三项是<b>通过 {@code sync_logs} 聚合算出来的</b>，
 * 由 Service 调用 {@code SyncLogDao} 填充后再交给
 * {@code DataSourceVO.of(entity, todayRequests, successRate)} 组装。
 * 这样设计的原因：同步统计是高频变动的运行时数据，
 * 混进主数据表会造成频繁 UPDATE 与写冲突。
 *
 * @author 追风者项目组
 * @see SyncLogDao
 * @see com.zfz.typhoon.model.vo.DataSourceVO
 */
public interface DataSourceDao {

    /**
     * 按主键查询数据源。
     *
     * @param sourceId 数据源ID
     * @return 数据源；不存在返回 null
     */
    DataSource findById(long sourceId);

    /**
     * 按数据源代码查询（如 CMA_TP_API）。
     *
     * @param sourceCode 数据源代码
     * @return 数据源；不存在返回 null
     */
    DataSource findByCode(String sourceCode);

    /**
     * 查询全部数据源（按数据源ID升序）。
     *
     * @return 数据源列表
     */
    List<DataSource> listAll();

    /**
     * 查询启用的数据源（供定时同步任务遍历）。
     *
     * @return 数据源列表
     */
    List<DataSource> listEnabled();

    /**
     * 统计启用的数据源数量（管理端概览卡片）。
     *
     * @return 数量
     */
    int countEnabled();

    /**
     * 新增数据源。
     *
     * @param source 数据源
     * @return 新生成的主键
     */
    long insert(DataSource source);

    /**
     * 更新数据源配置（名称、接口地址、频率、说明）。
     *
     * @param source 数据源（必须含 sourceId）
     * @return 受影响行数
     */
    int update(DataSource source);

    /**
     * 启用 / 停用数据源。
     *
     * @param sourceId 数据源ID
     * @param enabled  是否启用
     * @return 受影响行数
     */
    int setEnabled(long sourceId, boolean enabled);

    /**
     * 回写最后同步时间（同步任务成功后调用）。
     *
     * @param sourceId 数据源ID
     * @param syncTime 同步时间
     * @return 受影响行数
     */
    int updateLastSyncTime(long sourceId, LocalDateTime syncTime);

    /**
     * 按主键删除数据源（存在同步日志时会被外键 RESTRICT 拦截）。
     *
     * @param sourceId 数据源ID
     * @return 受影响行数
     */
    int deleteById(long sourceId);
}
