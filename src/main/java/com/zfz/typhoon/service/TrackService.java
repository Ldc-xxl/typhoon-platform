package com.zfz.typhoon.service;

import com.zfz.typhoon.model.TyphoonTrack;
import com.zfz.typhoon.model.vo.ReplayVO;
import com.zfz.typhoon.model.vo.TrackVO;

import java.util.List;

/**
 * 台风轨迹与回放业务接口。
 *
 * <p>对应接口文档：{@code GET /api/typhoons/{id}/tracks}（实况轨迹）、
 * {@code GET /api/typhoons/{id}/replay}（时间轴回放序列，前端 P1 时间轴消费）。
 *
 * <h3>为什么回放序列要在服务端合成</h3>
 * 数据库的 {@code typhoon_tracks} 是<b>实测点</b>，间隔不均匀（6 / 12 / 24 小时都有可能），
 * 而前端时间轴回放需要<b>等间隔帧</b>才能做平滑推进。
 * 若把插值放在前端，会出现两个问题：
 * <ol>
 *   <li>插值算法属于业务规则，散落在页面里难以复用与测试；</li>
 *   <li>回放要"跨过实测段、进入预报段"，需要同时拿到实况与预报两套数据，
 *       前端分别请求会产生时序竞争。</li>
 * </ol>
 * 因此本服务统一读取实况 + 预报，合成等间隔帧后一次性下发，
 * 并用每帧的 {@code source} 字段标注该帧来自实测（{@code OBS}）还是预报（{@code FCST}）。
 *
 * <p><b>数据诚实性约定</b>：本服务<b>不凭空外推</b>。回放的时间上界
 * 由"实测最后时刻"与"预报最后时刻"的较大者决定；若两者都没有，
 * 则返回空序列而不是编造数据。前端据此判断是否可播放。
 *
 * @author 追风者项目组
 */
public interface TrackService {

    /** 默认帧间隔（小时）：与台风业务报文的小时步长一致 */
    int DEFAULT_STEP_HOURS = 6;

    /**
     * 查询台风实况轨迹（按时间升序）。
     *
     * @param typhoonId 台风ID
     * @return 轨迹点视图列表
     */
    List<TrackVO> listByTyphoon(long typhoonId);

    /**
     * 查询台风实况轨迹（实体，供内部计算使用）。
     *
     * @param typhoonId 台风ID
     * @return 轨迹点实体列表
     */
    List<TyphoonTrack> listEntities(long typhoonId);

    /**
     * 合成回放序列（默认 6 小时一帧）。
     *
     * @param typhoonId 台风ID
     * @return 回放序列；无数据时 {@code frames} 为空列表
     */
    ReplayVO replay(long typhoonId);

    /**
     * 合成回放序列（指定帧间隔）。
     *
     * @param typhoonId  台风ID
     * @param stepHours  帧间隔（小时），取值被限制在 1~24
     * @return 回放序列
     */
    ReplayVO replay(long typhoonId, int stepHours);

    /**
     * 取轨迹点数量（前端进度条刻度）。
     *
     * @param typhoonId 台风ID
     * @return 数量
     */
    int countByTyphoon(long typhoonId);

    /* ------------------------------------------------------------------ */
    /*  写入（数据同步 / 管理端）                                            */
    /* ------------------------------------------------------------------ */

    /**
     * 新增单个轨迹点，并同步刷新台风主表的强度极值。
     *
     * @param track 轨迹点
     * @return 新轨迹点ID
     */
    long addTrack(TyphoonTrack track);

    /**
     * 批量幂等写入轨迹点（重复同步同一时刻不会产生重复行），
     * 完成后刷新主表强度极值。
     *
     * @param tracks 轨迹点集合
     * @return 受影响行数
     */
    int upsertTracks(List<TyphoonTrack> tracks);
}
