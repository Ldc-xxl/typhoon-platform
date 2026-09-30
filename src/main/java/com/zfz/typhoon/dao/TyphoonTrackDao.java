package com.zfz.typhoon.dao;

import com.zfz.typhoon.model.TyphoonTrack;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 台风实况轨迹表（{@code typhoon_tracks}）数据访问接口。
 *
 * <p>对应接口文档：{@code GET /api/typhoons/{id}/track}（轨迹折线）、
 * {@code GET /api/typhoons/{id}/replay}（回放序列的原始帧来源）。
 *
 * <p>课程要求（阶段三 · ⑤「轨迹需按时间升序返回，并支持获取最新实测点」）。
 * 轨迹表在 {@code (typhoon_id, obs_time)} 上有唯一键，
 * 因此"按台风取全部轨迹"与"取最新点"均为索引覆盖扫描。
 *
 * @author 追风者项目组
 */
public interface TyphoonTrackDao {

    /**
     * 按台风查询全部轨迹点，<b>按观测时间升序</b>。
     *
     * @param typhoonId 台风ID
     * @return 轨迹点列表（可能为空，永不为 null）
     */
    List<TyphoonTrack> listByTyphoon(long typhoonId);

    /**
     * 按台风查询轨迹点，可指定时间区间（用于回放 / 区间统计）。
     *
     * @param typhoonId 台风ID
     * @param from      起始时间（含），可为 null
     * @param to        结束时间（含），可为 null
     * @return 轨迹点列表（升序）
     */
    List<TyphoonTrack> listByTyphoonBetween(long typhoonId, LocalDateTime from, LocalDateTime to);

    /**
     * 取某个台风的最新实测点（即"当前中心位置"）。
     *
     * @param typhoonId 台风ID
     * @return 最新轨迹点；无数据返回 null
     */
    TyphoonTrack findLatest(long typhoonId);

    /**
     * 取某个台风的第一个轨迹点（即"生成位置"）。
     *
     * @param typhoonId 台风ID
     * @return 首个轨迹点；无数据返回 null
     */
    TyphoonTrack findFirst(long typhoonId);

    /**
     * 批量取多个台风各自的最新实测点（<b>单条 SQL 完成</b>，避免 N+1）。
     *
     * <p>总览页需要为 13 个台风同时展示"当前强度与位置"，
     * 若逐个查询会产生 13 次往返；这里用
     * {@code JOIN (SELECT typhoon_id, MAX(obs_time) ... GROUP BY typhoon_id)}
     * 一次取回，Service 再按 typhoonId 归组。
     *
     * @param typhoonIds 台风ID集合
     * @return 各台风最新轨迹点列表（顺序不保证）
     */
    List<TyphoonTrack> listLatestByTyphoons(Collection<Long> typhoonIds);

    /**
     * 统计某台风的轨迹点数量。
     *
     * @param typhoonId 台风ID
     * @return 数量
     */
    int countByTyphoon(long typhoonId);

    /**
     * 取某台风的观测时间跨度（小时），用于回放帧数估算。
     *
     * @param typhoonId 台风ID
     * @return 小时数；不足两个点或无数据时返回 0
     */
    double spanHours(long typhoonId);

    /* ------------------------------------------------------------------ */
    /*  写入                                                               */
    /* ------------------------------------------------------------------ */

    /**
     * 新增单个轨迹点。
     *
     * @param track 轨迹点
     * @return 新生成的主键
     */
    long insert(TyphoonTrack track);

    /**
     * 批量新增轨迹点（数据同步场景）。
     *
     * <p>课程要求（阶段五 ·「批量入库需使用 addBatch / executeBatch」）。
     *
     * @param tracks 轨迹点集合
     * @return 实际写入行数
     */
    int batchInsert(List<TyphoonTrack> tracks);

    /**
     * 幂等写入：同一 {@code (typhoon_id, obs_time)} 已存在时更新其余字段。
     *
     * <p>依赖唯一键 {@code uk_tracks_typhoon_time}，
     * 用于"重复同步同一批数据不产生脏数据"。
     *
     * @param tracks 轨迹点集合
     * @return 受影响行数（新增与更新之和）
     */
    int upsertAll(List<TyphoonTrack> tracks);

    /**
     * 删除某台风在某时刻的轨迹点。
     *
     * @param typhoonId 台风ID
     * @param obsTime   观测时间
     * @return 受影响行数
     */
    int deleteByTyphoonAndTime(long typhoonId, LocalDateTime obsTime);
}
