package com.zfz.typhoon.dao;

import com.zfz.typhoon.model.SurveyAggResult;
import com.zfz.typhoon.model.UserBehaviorSurvey;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户防灾行为调研表（{@code user_behavior_surveys}）数据访问接口。
 *
 * <p>课程特色模块 ⑤：<b>公众防灾认知调研与反向科普</b>。
 * 对应接口文档：{@code POST /api/surveys}（提交问卷）、
 * {@code GET /api/surveys/aggregate}（聚合统计）。
 *
 * <p><b>隐私边界（重要）</b>：本表含 {@code contact_info}（联系方式），
 * 属于<b>个人可识别信息</b>。因此约定：
 * <ul>
 *   <li>{@link #insert} 入库前由 Service 调用
 *       {@code PasswordUtil.maskContact} 脱敏，<b>不落原始手机号</b>；</li>
 *   <li>{@link #aggregateByTyphoon} 只返回<b>城市级聚合百分比</b>，
 *       不返回任何单条记录，从接口层面杜绝通过聚合接口反查个体；</li>
 *   <li>任何对外 VO 不得包含 {@code contactInfo}（见 {@code SurveyAggResultVO.PRIVACY_NOTE}）。</li>
 * </ul>
 *
 * @author 追风者项目组
 * @see com.zfz.typhoon.model.vo.SurveyAggResultVO
 */
public interface UserBehaviorSurveyDao {

    /**
     * 新增一份问卷。
     *
     * @param survey 问卷（{@code contactInfo} 应已脱敏）
     * @return 新生成的主键
     */
    long insert(UserBehaviorSurvey survey);

    /**
     * 查询某台风 + 城市的原始问卷（仅管理端与调试使用，不对外暴露）。
     *
     * @param typhoonId 台风ID
     * @param cityId    城市ID
     * @return 问卷列表
     */
    List<UserBehaviorSurvey> listByTyphoonAndCity(long typhoonId, long cityId);

    /**
     * 统计某台风 + 城市的有效样本量（{@code total_count} 的来源）。
     *
     * @param typhoonId 台风ID
     * @param cityId    城市ID
     * @return 样本量
     */
    int countByTyphoonAndCity(long typhoonId, long cityId);

    /**
     * 统计某台风的累计样本量（用于判断样本是否足够出结论）。
     *
     * @param typhoonId 台风ID
     * @return 样本量
     */
    int countByTyphoon(long typhoonId);

    /**
     * 统计全平台问卷样本总量（后台运行概览）。
     *
     * @return 样本量
     */
    int countAll();

    /**
     * 按城市聚合某台风的问卷，直接算出各维度百分比。
     *
     * <p>计算全部下推到 MySQL 完成（一条 SQL、一次往返），原因有二：
     * <ol>
     *   <li>样本量增长后不必把原始明细搬到应用内存；</li>
     *   <li>聚合口径（分母、四舍五入）集中在一处，避免前后端各算一遍导致数字对不上。</li>
     * </ol>
     *
     * <p>返回对象的字段口径：
     * <ul>
     *   <li>{@code totalCount}：该城市样本量；</li>
     *   <li>{@code supplyPrepRate}：{@code prepared_supplies = 1} 占比（%）；</li>
     *   <li>{@code evacuationAttentionRate}：{@code pay_attention_evacuation = 1} 占比（%）；</li>
     *   <li>{@code heavyRainWorryRate}：最担心 {@code RAIN} 占比（%）；</li>
     *   <li>{@code stormSurgeWorryRate}：最担心 {@code STORM_SURGE} 占比（%）；</li>
     *   <li>{@code highTrustRate}：{@code trust_level &gt;= 4} 占比（%）；</li>
     *   <li>{@code riskOverestimationRate}：{@code trust_level &lt;= 2} 占比（%），
     *       即"不信预报 → 高估风险"；</li>
     *   <li>{@code riskUnderestimationRate}：{@code trust_level = 5} 占比（%），
     *       即"完全信任 → 低估风险"。</li>
     * </ul>
     *
     * @param typhoonId 台风ID
     * @return 按城市聚合的结果列表
     */
    List<SurveyAggResult> aggregateByTyphoon(long typhoonId);

    /**
     * 查询某台风 + 城市的聚合结果（不落库版本，实时计算）。
     *
     * @param typhoonId 台风ID
     * @param cityId    城市ID
     * @return 聚合结果；无样本返回 null
     */
    SurveyAggResult aggregateByTyphoonAndCity(long typhoonId, long cityId);

    /**
     * 查询某台风最早 / 最晚的问卷提交时间，供趋势图横轴。
     *
     * @param typhoonId 台风ID
     * @return 长度 2 的数组 {@code [最早, 最晚]}；无样本时为 {@code [null, null]}
     */
    LocalDateTime[] submitTimeRange(long typhoonId);

    /**
     * 删除某台风的问卷明细（台风删除时由外键级联承担，此方法供隐私清理使用）。
     *
     * @param typhoonId 台风ID
     * @return 受影响行数
     */
    int deleteByTyphoon(long typhoonId);
}
