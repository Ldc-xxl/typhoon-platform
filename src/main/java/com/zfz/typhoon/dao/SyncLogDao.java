package com.zfz.typhoon.dao;

import com.zfz.typhoon.model.SyncLog;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据同步日志表（{@code sync_logs}）数据访问接口。
 *
 * <p>对应接口文档：{@code GET /api/sync-logs}（P7 管理端"同步日志"列表 + 失败告警）。
 *
 * <p>本表同时承担<b>运行时统计</b>职责：数据源面板上的
 * "今日请求数 / 成功率 / 平均耗时"都从这里聚合而来
 * （因为 {@code data_sources} 表刻意不存这些高频变动值，见 {@link DataSourceDao}）。
 *
 * @author 追风者项目组
 */
public interface SyncLogDao {

    /** 同步状态：成功 */
    String STATUS_SUCCESS = "SUCCESS";

    /** 同步状态：失败 */
    String STATUS_FAILED = "FAILED";

    /* ------------------------------------------------------------------ */
    /*  列表                                                               */
    /* ------------------------------------------------------------------ */

    /**
     * 查询最近的同步日志（联表带出数据源名称与代码），按同步时间倒序。
     *
     * @param limit 最多返回条数（&lt;=0 表示不限）
     * @return 日志列表
     */
    List<SyncLog> listRecent(int limit);

    /**
     * 查询某数据源的同步日志。
     *
     * @param sourceId 数据源ID
     * @param limit    最多返回条数
     * @return 日志列表（按同步时间倒序）
     */
    List<SyncLog> listBySource(long sourceId, int limit);

    /**
     * 查询失败的同步日志（管理端告警区）。
     *
     * @param limit 最多返回条数
     * @return 日志列表（按同步时间倒序）
     */
    List<SyncLog> listFailed(int limit);

    /**
     * 按状态查询日志。
     *
     * @param status SUCCESS / FAILED
     * @param limit  最多返回条数
     * @return 日志列表
     */
    List<SyncLog> listByStatus(String status, int limit);

    /* ------------------------------------------------------------------ */
    /*  统计（供数据源监控面板）                                             */
    /* ------------------------------------------------------------------ */

    /**
     * 统计某数据源的同步次数（总数）。
     *
     * @param sourceId 数据源ID
     * @return 次数
     */
    int countBySource(long sourceId);

    /**
     * 统计某数据源在指定状态下的次数。
     *
     * @param sourceId 数据源ID
     * @param status   SUCCESS / FAILED
     * @return 次数
     */
    int countBySourceAndStatus(long sourceId, String status);

    /**
     * 计算某数据源的成功率（%）。
     *
     * @param sourceId 数据源ID
     * @return 成功率 0~100；无记录时返回 100（视为尚未失败）
     */
    double successRate(long sourceId);

    /**
     * 统计某数据源今日同步的<b>记录条数</b>（即面板上的"今日请求数"）。
     *
     * <p>用 {@code DATE(sync_time) = CURDATE()} 会在列上套函数导致
     * {@code idx_sync_source_time} 只用上前缀 {@code source_id}；
     * 因此改写为范围条件 {@code sync_time >= 今日零点}，让索引完整生效。
     *
     * @param sourceId 数据源ID
     * @return 记录条数
     */
    int sumRecordCountToday(long sourceId);

    /**
     * 计算某数据源的平均同步耗时（秒）。
     *
     * @param sourceId 数据源ID
     * @return 平均耗时；无有效记录返回 0
     */
    double avgDurationSeconds(long sourceId);

    /**
     * 取某数据源最近一次同步时间。
     *
     * @param sourceId 数据源ID
     * @return 时间；无记录返回 null
     */
    LocalDateTime findLastSyncTime(long sourceId);

    /**
     * 统计全表指定状态的日志数（管理端概览卡片）。
     *
     * @param status SUCCESS / FAILED
     * @return 数量
     */
    int countByStatus(String status);

    /**
     * 统计全表日志总数。
     *
     * @return 数量
     */
    int countAll();

    /* ------------------------------------------------------------------ */
    /*  写入                                                               */
    /* ------------------------------------------------------------------ */

    /**
     * 新增同步日志。
     *
     * @param log 日志
     * @return 新生成的主键
     */
    long insert(SyncLog log);

    /**
     * 清理指定天数之前的日志（避免日志表无限增长）。
     *
     * @param before 该时间之前的日志将被删除
     * @return 受影响行数
     */
    int deleteBefore(LocalDateTime before);
}
