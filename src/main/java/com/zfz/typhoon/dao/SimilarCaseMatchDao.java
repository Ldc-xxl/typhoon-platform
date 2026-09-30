package com.zfz.typhoon.dao;

import com.zfz.typhoon.model.SimilarCaseMatch;

import java.util.List;

/**
 * 相似案例匹配记录表（{@code similar_case_matches}）数据访问接口。
 *
 * <p>课程特色模块 ②：<b>历史相似台风匹配</b>。
 * 对应接口文档：{@code GET /api/typhoons/{id}/similar-cases}。
 *
 * <p>表结构把综合相似度与五个分维得分（路径形态 / 峰值强度 / 登陆季节 /
 * 移速 / 风圈规模）分别落列，而非塞进一个 JSON。
 * 这样做的收益是<b>可排序、可筛选、可做统计</b>
 * （例如"找出所有路径相似度 &gt; 0.8 的历史案例"），
 * 代价是维度固定在 5 个——对课程场景是合适的取舍。
 *
 * <p>表上有唯一键 {@code uk_scm_pair (current_typhoon_id, history_typhoon_id)}，
 * 因此重复计算同一对台风时会走 {@link #upsert} 更新分数，不会产生重复行。
 *
 * @author 追风者项目组
 * @see com.zfz.typhoon.model.vo.SimilarCaseVO
 */
public interface SimilarCaseMatchDao {

    /**
     * 按当前台风查询相似案例（按综合相似度降序）。
     *
     * @param currentTyphoonId 当前台风ID
     * @return 匹配记录列表
     */
    List<SimilarCaseMatch> listByCurrentTyphoon(long currentTyphoonId);

    /**
     * 取相似度最高的前 N 个案例（详情页只展示 Top3）。
     *
     * @param currentTyphoonId 当前台风ID
     * @param topN             条数
     * @return 匹配记录列表
     */
    List<SimilarCaseMatch> listTopMatches(long currentTyphoonId, int topN);

    /**
     * 查询某一对台风的匹配记录。
     *
     * @param currentTyphoonId 当前台风ID
     * @param historyTyphoonId 历史台风ID
     * @return 匹配记录；不存在返回 null
     */
    SimilarCaseMatch findByPair(long currentTyphoonId, long historyTyphoonId);

    /**
     * 查询某历史台风被哪些当前台风匹配中（反向引用，用于"该台风常被类比"）。
     *
     * @param historyTyphoonId 历史台风ID
     * @return 匹配记录列表（按相似度降序）
     */
    List<SimilarCaseMatch> listByHistoryTyphoon(long historyTyphoonId);

    /**
     * 新增匹配记录。
     *
     * @param match 匹配记录
     * @return 新生成的主键
     */
    long insert(SimilarCaseMatch match);

    /**
     * 幂等写入：命中唯一键 {@code uk_scm_pair} 时更新五个分维得分与综合分。
     *
     * @param match 匹配记录
     * @return 受影响行数
     */
    int upsert(SimilarCaseMatch match);

    /**
     * 批量幂等写入（一次算完当前台风对全部历史台风的匹配度后统一落库）。
     *
     * @param matches 匹配记录集合
     * @return 受影响行数
     */
    int upsertAll(List<SimilarCaseMatch> matches);

    /**
     * 按当前台风删除匹配记录（重算前清理）。
     *
     * @param currentTyphoonId 当前台风ID
     * @return 受影响行数
     */
    int deleteByCurrentTyphoon(long currentTyphoonId);
}
