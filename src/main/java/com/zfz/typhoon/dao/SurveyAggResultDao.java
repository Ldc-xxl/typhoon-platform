package com.zfz.typhoon.dao;

import com.zfz.typhoon.model.SurveyAggResult;

import java.util.List;

/**
 * 调研聚合统计表（{@code survey_agg_results}）数据访问接口。
 *
 * <p>课程特色模块 ⑤ 的<b>落库侧</b>。
 * 对应接口文档：{@code GET /api/surveys/aggregate?typhoonId=}。
 *
 * <h3>为什么聚合结果要落库</h3>
 * {@code user_behavior_surveys} 是明细表（每次提交 +1 行），
 * 而聚合是"读多写少"的重计算。若每次打开调研页都实时 GROUP BY，
 * 样本上万后单次查询会明显变慢。因此：
 * <ul>
 *   <li>明细表负责<b>写入</b>（高频、轻量）；</li>
 *   <li>本表负责<b>读取</b>（低频刷新、写入时算好）；</li>
 *   <li>两者由 Service 在"提交问卷后 / 定时任务"时同步，
 *       接口直接读本表，实现读写分离式的性能优化。</li>
 * </ul>
 *
 * <p>唯一键 {@code uk_sar_typhoon_city (typhoon_id, city_id)} 保证
 * 同一台风同一城市只有一行汇总，重算走 {@link #upsert}。
 *
 * @author 追风者项目组
 * @see UserBehaviorSurveyDao
 */
public interface SurveyAggResultDao {

    /**
     * 查询某台风的全部城市聚合结果（按样本量降序）。
     *
     * @param typhoonId 台风ID
     * @return 聚合结果列表
     */
    List<SurveyAggResult> listByTyphoon(long typhoonId);

    /**
     * 查询某台风 + 城市的聚合结果。
     *
     * @param typhoonId 台风ID
     * @param cityId    城市ID
     * @return 聚合结果；不存在返回 null
     */
    SurveyAggResult findByTyphoonAndCity(long typhoonId, long cityId);

    /**
     * 查询样本量最大的前 N 个城市（调研页"重点关注城市"）。
     *
     * @param typhoonId 台风ID
     * @param topN      条数
     * @return 聚合结果列表
     */
    List<SurveyAggResult> listTopBySampleSize(long typhoonId, int topN);

    /**
     * 查询某台风的累计样本量（各城市求和）。
     *
     * @param typhoonId 台风ID
     * @return 样本总数
     */
    int sumTotalCount(long typhoonId);

    /**
     * 新增聚合结果。
     *
     * @param result 聚合结果
     * @return 新生成的主键
     */
    long insert(SurveyAggResult result);

    /**
     * 幂等写入：命中唯一键 {@code uk_sar_typhoon_city} 时更新全部比率与统计时间。
     *
     * @param result 聚合结果
     * @return 受影响行数
     */
    int upsert(SurveyAggResult result);

    /**
     * 批量幂等写入（一次重算整台风的全部城市）。
     *
     * @param results 聚合结果集合
     * @return 受影响行数
     */
    int upsertAll(List<SurveyAggResult> results);

    /**
     * 按台风删除聚合结果（重算前清理）。
     *
     * @param typhoonId 台风ID
     * @return 受影响行数
     */
    int deleteByTyphoon(long typhoonId);
}
