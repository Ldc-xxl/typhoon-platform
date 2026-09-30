package com.zfz.typhoon.dao;

import com.zfz.typhoon.model.RiskAssessment;

import java.util.List;

/**
 * 风险评估表（{@code risk_assessments}）数据访问接口。
 *
 * <p>对应接口文档：{@code GET /api/risk-assessment?typhoonId=&amp;cityId=}
 * （P4 风险评估页，返回风险等级、评分、影响时段与处置建议）。
 *
 * <p>课程要求（阶段四 ·「风险评估需可复算、可留痕」）。
 * 本表既是<b>计算结果缓存</b>（避免每次打开页面重算几何），
 * 又是<b>历史留痕</b>（同一台风同一城市在不同时刻的评估会并存，
 * 因此查询一律取"最新一条"，而不是认为每个组合只有一行）。
 *
 * @author 追风者项目组
 * @see com.zfz.typhoon.model.vo.RiskAssessmentVO
 */
public interface RiskAssessmentDao {

    /**
     * 取某台风 + 城市的最新一次评估（按评估时间倒序取第一条）。
     *
     * @param typhoonId 台风ID
     * @param cityId    城市ID
     * @return 评估记录；无数据返回 null
     */
    RiskAssessment findLatest(long typhoonId, long cityId);

    /**
     * 取某台风 + 城市 + 用户的最新一次评估。
     *
     * @param typhoonId 台风ID
     * @param cityId    城市ID
     * @param userId    用户ID
     * @return 评估记录；无数据返回 null
     */
    RiskAssessment findLatestByUser(long typhoonId, long cityId, long userId);

    /**
     * 按台风查询最新一批评估（每个城市只取最新一条，供地图风险着色）。
     *
     * @param typhoonId 台风ID
     * @return 评估记录列表（按风险评分降序）
     */
    List<RiskAssessment> listLatestByTyphoon(long typhoonId);

    /**
     * 按用户查询评估历史（"我的评估记录"）。
     *
     * @param userId 用户ID
     * @param limit  最多返回条数
     * @return 评估记录列表（按评估时间倒序）
     */
    List<RiskAssessment> listByUser(long userId, int limit);

    /**
     * 查询某台风下被评估过的城市ID清单。
     *
     * @param typhoonId 台风ID
     * @return 城市ID列表
     */
    List<Long> listAssessedCityIds(long typhoonId);

    /**
     * 新增评估记录。
     *
     * @param assessment 评估记录
     * @return 新生成的主键
     */
    long insert(RiskAssessment assessment);

    /**
     * 更新评估记录（重新计算同一时刻的结果时使用）。
     *
     * @param assessment 评估记录（必须含 assessmentId）
     * @return 受影响行数
     */
    int update(RiskAssessment assessment);

    /**
     * 按台风删除评估记录。
     *
     * @param typhoonId 台风ID
     * @return 受影响行数
     */
    int deleteByTyphoon(long typhoonId);
}
