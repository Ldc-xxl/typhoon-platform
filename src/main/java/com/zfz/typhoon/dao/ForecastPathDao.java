package com.zfz.typhoon.dao;

import com.zfz.typhoon.model.ForecastPath;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 预报路径表（{@code forecast_paths}）数据访问接口。
 *
 * <p>对应接口文档：{@code GET /api/typhoons/{id}/forecast-paths}（多机构预报路径对比）、
 * {@code GET /api/typhoons/{id}/disagreement}（分歧量化，需要各机构"最新一条"预报）。
 *
 * <p><b>业务约束（写在接口层以统一认识）</b>：
 * 前端展示多家机构路径时，要求<b>各机构起点必须一致</b>（都从台风当前实测位置出发）。
 * 若某机构最新发布时间戳早于其它机构，其起点会落在旧位置上，导致视觉错乱。
 * 因此 {@link #listLatestByTyphoon} 取的是"每个机构各自最新的一批点"，
 * 由 Service 层在组装 {@code ForecastPathVO} 时用当前实测点统一校正起点——
 * DAO 层只保证数据正确，不承担展示语义。
 *
 * @author 追风者项目组
 * @see com.zfz.typhoon.model.vo.ForecastPathVO
 */
public interface ForecastPathDao {

    /**
     * 查询某台风全部预报点（含机构代码 / 名称 / 配色），按机构、预报时刻升序。
     *
     * @param typhoonId 台风ID
     * @return 预报点列表
     */
    List<ForecastPath> listByTyphoon(long typhoonId);

    /**
     * 查询某台风<b>各机构最新一次发布</b>的预报点（单条 SQL 完成）。
     *
     * <p>这是分歧分析与路径对比的主查询：若直接取全部历史发布，
     * 会把同一机构不同时次的路径叠在一起，得到错误的"分歧"结论。
     *
     * @param typhoonId 台风ID
     * @return 预报点列表（含机构信息），按机构ID、预报时刻升序
     */
    List<ForecastPath> listLatestByTyphoon(long typhoonId);

    /**
     * 查询某台风某机构最新一次发布的预报点。
     *
     * @param typhoonId  台风ID
     * @param agencyCode 机构代码
     * @return 预报点列表（按预报时刻升序）
     */
    List<ForecastPath> listLatestByTyphoonAndAgency(long typhoonId, String agencyCode);

    /**
     * 取某台风某机构的最新发布时间（分歧面板展示"数据更新于"）。
     *
     * @param typhoonId  台风ID
     * @param agencyCode 机构代码
     * @return 发布时间；无数据返回 null
     */
    LocalDateTime findLatestPublishTime(long typhoonId, String agencyCode);

    /**
     * 取某台风全部机构的最新发布时间中最早的一个。
     *
     * <p>返回"最旧的那次更新"，用于前端提示数据新鲜度（保守取值）。
     *
     * @param typhoonId 台风ID
     * @return 发布时间；无数据返回 null
     */
    LocalDateTime findOldestLatestPublishTime(long typhoonId);

    /**
     * 统计某台风的预报点数量。
     *
     * @param typhoonId 台风ID
     * @return 数量
     */
    int countByTyphoon(long typhoonId);

    /* ------------------------------------------------------------------ */
    /*  写入                                                               */
    /* ------------------------------------------------------------------ */

    /**
     * 新增预报点。
     *
     * @param path 预报点
     * @return 新生成的主键
     */
    long insert(ForecastPath path);

    /**
     * 批量新增预报点（一次同步多家机构的一条路径）。
     *
     * @param paths 预报点集合
     * @return 实际写入行数
     */
    int batchInsert(List<ForecastPath> paths);

    /**
     * 用"先删后插"策略替换某台风某机构的预报路径。
     *
     * <p>预报路径是"整体重发"语义（机构每次发布都是完整路径，而非增量点），
     * 因此不需要按点去重。调用方应用事务包住本方法，
     * 避免删除成功后插入失败造成数据空洞。
     *
     * @param typhoonId  台风ID
     * @param agencyId   机构ID
     * @param publishTime 本次发布时间
     * @return 受影响行数
     */
    int deleteByTyphoonAgencyAndPublish(long typhoonId, long agencyId, LocalDateTime publishTime);

    /**
     * 删除某台风的全部预报路径（台风删除时由外键级联承担，此方法供数据清理使用）。
     *
     * @param typhoonId 台风ID
     * @return 受影响行数
     */
    int deleteByTyphoon(long typhoonId);
}
