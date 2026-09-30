package com.zfz.typhoon.dao;

import com.zfz.typhoon.model.WindCircle;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 风圈半径表（{@code wind_circles}）数据访问接口。
 *
 * <p>对应接口文档：{@code GET /api/typhoons/{id}/wind-circles}（风圈图层，
 * 前端 P1 总览页的 {@code scaleWind} 与 P5 详情页的风圈渲染都消费本数据）。
 *
 * <p>课程要求（阶段四 ·「风圈为四象限非对称结构，需按 7/10/12 三级存取」）。
 * 表中每个观测时刻一行、12 个半径列，属于典型的"宽表"设计——
 * 好处是一次查询即可绘出完整风圈，代价是级别固定为 7/10/12。
 *
 * @author 追风者项目组
 */
public interface WindCircleDao {

    /** 风圈级别：7 级 */
    int LEVEL_7 = 7;

    /** 风圈级别：10 级 */
    int LEVEL_10 = 10;

    /** 风圈级别：12 级 */
    int LEVEL_12 = 12;

    /**
     * 取某台风的最新一组风圈（当前时刻风圈）。
     *
     * @param typhoonId 台风ID
     * @return 风圈记录；无数据返回 null
     */
    WindCircle findLatest(long typhoonId);

    /**
     * 取某台风在指定观测时刻的风圈。
     *
     * @param typhoonId 台风ID
     * @param obsTime   观测时间
     * @return 风圈记录；不存在返回 null
     */
    WindCircle findByTyphoonAndTime(long typhoonId, LocalDateTime obsTime);

    /**
     * 取某台风全部风圈记录（按时间升序），供回放时逐帧切换风圈。
     *
     * @param typhoonId 台风ID
     * @return 风圈列表
     */
    List<WindCircle> listByTyphoon(long typhoonId);

    /**
     * 批量取多个台风各自的最新风圈（单条 SQL，避免 N+1）。
     *
     * @param typhoonIds 台风ID集合
     * @return 各台风最新风圈列表
     */
    List<WindCircle> listLatestByTyphoons(Collection<Long> typhoonIds);

    /**
     * 新增一组风圈。
     *
     * @param circle 风圈记录
     * @return 新生成的主键
     */
    long insert(WindCircle circle);

    /**
     * 幂等写入：同一 {@code (typhoon_id, obs_time)} 已存在时更新 12 个半径值。
     *
     * @param circle 风圈记录
     * @return 受影响行数
     */
    int upsert(WindCircle circle);

    /**
     * 删除某台风在某时刻的风圈记录。
     *
     * @param typhoonId 台风ID
     * @param obsTime   观测时间
     * @return 受影响行数
     */
    int deleteByTyphoonAndTime(long typhoonId, LocalDateTime obsTime);
}
