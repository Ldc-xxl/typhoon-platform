package com.zfz.typhoon.service;

import com.zfz.typhoon.dao.query.TyphoonQuery;
import com.zfz.typhoon.model.DisasterReport;
import com.zfz.typhoon.model.Typhoon;
import com.zfz.typhoon.model.TyphoonTrack;
import com.zfz.typhoon.model.vo.TyphoonVO;

import java.util.List;

/**
 * 台风档案业务接口。
 *
 * <p>对应接口文档：{@code GET /api/typhoons}（列表 / 多条件筛选）、
 * {@code GET /api/typhoons/{id}}（详情）。
 *
 * <h3>核心职责：把「主表 + 最新实测点」组装成前端要的一条记录</h3>
 * 前端列表一行要显示"当前强度、中心位置、移向移速"，这些字段分散在
 * {@code typhoons}（主表极值）与 {@code typhoon_tracks}（最新实测点）两张表。
 * 若让前端拿两个数组自己拼，会带来两个问题：
 * <ol>
 *   <li>列表接口语义变得不完整，前端要理解数据模型；</li>
 *   <li>N 个台风要发 N+1 次请求。</li>
 * </ol>
 * 因此组装放在服务端的 {@link #list} 里，且用<b>一次批量查询</b>取全部最新点
 * （见 {@code TyphoonTrackDao#listLatestByTyphoons}）。
 *
 * <p>另一个容易出错的点：数据库的 {@code status} 只有 {@code ACTIVE / FINISHED}，
 * 而前端契约用的是 {@code ACTIVE / ARCHIVED}。转换在
 * {@link TyphoonVO#of} 内完成，本服务不重复处理，避免两处口径不一致。
 *
 * @author 追风者项目组
 */
public interface TyphoonService {

    /* ------------------------------------------------------------------ */
    /*  查询                                                               */
    /* ------------------------------------------------------------------ */

    /**
     * 多条件查询台风列表（组装为前端视图对象）。
     *
     * @param query 查询条件，可为 null（等价于查全部）
     * @return 台风视图列表，按生成时间倒序
     */
    List<TyphoonVO> list(TyphoonQuery query);

    /**
     * 查询活跃台风（总览页左侧"活跃台风"分组）。
     *
     * @return 台风视图列表
     */
    List<TyphoonVO> listActive();

    /**
     * 查询历史台风（总览页左侧"历史台风"分组 / 档案页）。
     *
     * @return 台风视图列表
     */
    List<TyphoonVO> listArchived();

    /**
     * 关键词搜索（同时匹配中文名 / 英文名 / 国际编号）。
     *
     * @param keyword 关键词
     * @param status  状态过滤，可为 null
     * @return 台风视图列表
     */
    List<TyphoonVO> search(String keyword, String status);

    /**
     * 统计满足条件的台风数量（分页用）。
     *
     * @param query 查询条件
     * @return 数量
     */
    int count(TyphoonQuery query);

    /**
     * 按主键查询台风详情。
     *
     * @param typhoonId 台风ID
     * @return 台风视图
     * @throws com.zfz.typhoon.util.BusinessException 台风不存在（404）
     */
    TyphoonVO findById(long typhoonId);

    /**
     * 按国际编号查询台风详情。
     *
     * @param internationalId 国际编号，如 202615
     * @return 台风视图
     * @throws com.zfz.typhoon.util.BusinessException 台风不存在（404）
     */
    TyphoonVO findByCode(String internationalId);

    /**
     * 取台风实体（供其它服务内部使用，不做 VO 转换）。
     *
     * @param typhoonId 台风ID
     * @return 台风实体
     * @throws com.zfz.typhoon.util.BusinessException 台风不存在（404）
     */
    Typhoon requireTyphoon(long typhoonId);

    /**
     * 取台风最新实测点（当前中心位置）。
     *
     * @param typhoonId 台风ID
     * @return 最新实测点；无轨迹返回 null
     */
    TyphoonTrack latestTrack(long typhoonId);

    /**
     * 取台风在指定观测时刻的实测点（「以某一历史时刻为基准重演评估」用）。
     *
     * <p>与 {@link #latestTrack(long)} 只差「取哪一点」：本方法取 {@code obsTime}
     * 那一刻的中心位置。风圈表 {@code wind_circles} 本来就是<b>按观测时刻</b>存的，
     * 两者配合即可用"当时的中心 + 当时的风圈"重算一遍历史结论，
     * 而不必凭空外推一个真实观测中不存在的中心位置。
     *
     * <p><b>容错策略</b>：{@code obsTime} 为 null 时等价于 {@link #latestTrack(long)}；
     * 找不到完全相等的时刻时，取<b>不晚于</b>该时刻的最后一个实测点。
     * 下拉里的时次直接来自同一张表，正常情况下必然精确命中——这条容错是给
     * "手工拼出来的 URL 参数"兜底的，避免一个错位的时刻直接变成 404。
     *
     * @param typhoonId 台风ID
     * @param obsTime   观测时刻，可为 null（表示取最新）
     * @return 实测点；该时刻之前没有任何轨迹时返回 null
     */
    TyphoonTrack trackAt(long typhoonId, java.time.LocalDateTime obsTime);

    /**
     * 出现过台风的年份清单（档案页筛选下拉）。
     *
     * @return 年份列表（降序）
     */
    List<Integer> listYears();

    /**
     * 出现过的海域清单（档案页筛选下拉）。
     *
     * @return 海域列表
     */
    List<String> listBasins();

    /**
     * 取台风的灾情报告（详情页"灾情复盘"）。
     *
     * @param typhoonId 台风ID
     * @return 灾情报告；无记录返回 null
     */
    DisasterReport disaster(long typhoonId);

    /* ------------------------------------------------------------------ */
    /*  统计（管理端概览卡片）                                               */
    /* ------------------------------------------------------------------ */

    /**
     * 台风总数。
     *
     * @return 数量
     */
    int countAll();

    /**
     * 活跃台风数。
     *
     * @return 数量
     */
    int countActive();

    /**
     * 已归档台风数。
     *
     * @return 数量
     */
    int countArchived();

    /* ------------------------------------------------------------------ */
    /*  写入（管理端）                                                       */
    /* ------------------------------------------------------------------ */

    /**
     * 新增台风。
     *
     * @param typhoon 台风
     * @return 新台风ID
     */
    long create(Typhoon typhoon);

    /**
     * 更新台风基础信息。
     *
     * @param typhoon 台风
     * @return 受影响行数
     */
    int update(Typhoon typhoon);

    /**
     * 归档台风：置为 FINISHED、记录消亡时间并标记历史档案。
     *
     * @param typhoonId     台风ID
     * @param dissipateTime 消亡时间，可为 null（取当前时间）
     * @return 受影响行数
     */
    int archive(long typhoonId, java.time.LocalDateTime dissipateTime);

    /**
     * 删除台风（轨迹、风圈、预报路径等按外键级联删除）。
     *
     * @param typhoonId 台风ID
     * @return 受影响行数
     */
    int delete(long typhoonId);
}
