package com.zfz.typhoon.service;

import com.zfz.typhoon.model.SimilarCaseMatch;
import com.zfz.typhoon.model.vo.SimilarCaseVO;

import java.util.List;

/**
 * 历史相似台风检索服务。
 *
 * <p>对应项目描述文档「核心特色功能二：多维相似度评分与历史灾情归因」。
 *
 * <p>与"按年份或名称随便找几个往年的台风"不同，本服务用五个维度做加权打分，
 * 并且每个维度都是<b>可解释</b>的——页面上能直接告诉用户
 * "路径形态相似度 0.90、强度相似度 0.85"，而不是丢出一个黑箱总分。
 *
 * <p>五个维度的含义与打分口径：
 * <table border="1">
 *   <caption>相似度维度</caption>
 *   <tr><th>维度</th><th>含义</th><th>口径</th></tr>
 *   <tr><td>path</td><td>路径形态</td><td>两条轨迹按弧长重采样后，平移+缩放对齐，比较形状差</td></tr>
 *   <tr><td>intensity</td><td>峰值强度</td><td>最大风力之差 / 10 级</td></tr>
 *   <tr><td>season</td><td>季节</td><td>生成月份之差（按 12 个月环形取最短距离）</td></tr>
 *   <tr><td>speed</td><td>移速</td><td>平均移速之差 / 20 km·h⁻¹</td></tr>
 *   <tr><td>windCircle</td><td>风圈尺度</td><td>7 级风圈平均半径之差 / 200 km</td></tr>
 * </table>
 *
 * <p><b>合规提示</b>：结果必须配合"历史相似仅作类比，不代表结果会重演"的提示展示，
 * 见 {@link SimilarCaseVO#NOTE}。
 *
 * @author 追风者项目组
 */
public interface SimilarCaseService {

    /** 系统配置键：返回 Top-N 的 N */
    String KEY_TOP_N = "similar.case.topN";
    /** 系统配置键：相似度低于该值的案例不返回 */
    String KEY_MIN_SCORE = "similar.case.minScore";

    /**
     * 检索与指定台风最相似的历史台风（默认 Top-N）。
     *
     * @param typhoonId 当前台风ID
     * @return 相似案例列表，按综合相似度降序；无候选时返回空列表
     * @throws com.zfz.typhoon.util.BusinessException 台风不存在时抛出（404）
     */
    List<SimilarCaseVO> findSimilar(long typhoonId);

    /**
     * 检索与指定台风最相似的历史台风，并指定返回条数。
     *
     * @param typhoonId 当前台风ID
     * @param topN      返回条数（1—20）
     * @return 相似案例列表，按综合相似度降序
     */
    List<SimilarCaseVO> findSimilar(long typhoonId, int topN);

    /**
     * 计算当前台风与某一历史台风的五维相似度明细（不落库）。
     *
     * @param currentTyphoonId 当前台风ID
     * @param historyTyphoonId 历史台风ID
     * @return 相似度明细；任一台风无轨迹时返回 null
     */
    SimilarCaseMatch score(long currentTyphoonId, long historyTyphoonId);

    /**
     * 把本次检索结果写入 {@code similar_case_matches}，便于复用与审计。
     *
     * @param typhoonId 当前台风ID
     * @return 实际写入的记录数
     */
    int saveMatches(long typhoonId);

    /**
     * 读取已落库的相似案例结果。
     *
     * @param typhoonId 当前台风ID
     * @return 已落库的匹配记录；无记录时返回空列表
     */
    List<SimilarCaseMatch> listSaved(long typhoonId);
}
