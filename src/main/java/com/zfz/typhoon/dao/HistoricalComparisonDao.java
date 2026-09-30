package com.zfz.typhoon.dao;

import com.zfz.typhoon.model.HistoricalComparison;

import java.util.List;

/**
 * 历史对比任务表（{@code historical_comparisons}）数据访问接口。
 *
 * <p>课程要求（阶段四 ·「支持多台风路径对比」）。
 * 对应接口文档：{@code GET /api/comparisons}（我的对比任务）、
 * {@code POST /api/comparisons}（创建对比）。
 *
 * <h3>JSON 列设计</h3>
 * {@code compare_typhoon_ids} 存"要对比哪些台风"的ID数组（如 {@code [8,9]}），
 * {@code result_summary} 存对比结论（路径偏差、强度差、文字说明）。
 * 之所以用 JSON 而非关联表：
 * <ul>
 *   <li>对比是<b>用户私有的一次性任务</b>，不需要被反向查询（"哪些任务对比了台风8"极少用到）；</li>
 *   <li>结论结构会随算法演进变化，JSON 可以不加迁移地扩展字段；</li>
 *   <li>主台风 {@code primary_typhoon_id} 单独建列并加索引 + 外键，
 *       保证"删除主台风时对比任务一并级联删除"，不会留下悬空引用。</li>
 * </ul>
 *
 * @author 追风者项目组
 */
public interface HistoricalComparisonDao {

    /**
     * 按主键查询对比任务。
     *
     * @param comparisonId 对比任务ID
     * @return 对比任务；不存在返回 null
     */
    HistoricalComparison findById(long comparisonId);

    /**
     * 查询某用户的对比任务（按创建时间倒序）。
     *
     * @param userId 用户ID
     * @return 对比任务列表
     */
    List<HistoricalComparison> listByUser(long userId);

    /**
     * 查询以某台风为主对比对象的任务（用于"该台风被对比过"提示）。
     *
     * @param primaryTyphoonId 主台风ID
     * @return 对比任务列表
     */
    List<HistoricalComparison> listByPrimaryTyphoon(long primaryTyphoonId);

    /**
     * 统计某用户的对比任务数量。
     *
     * @param userId 用户ID
     * @return 数量
     */
    int countByUser(long userId);

    /**
     * 新增对比任务。
     *
     * @param comparison 对比任务
     * @return 新生成的主键
     */
    long insert(HistoricalComparison comparison);

    /**
     * 更新对比任务结果摘要（对比算法执行完成后回写）。
     *
     * @param comparisonId 对比任务ID
     * @param resultSummary 结果摘要 JSON 文本
     * @return 受影响行数
     */
    int updateResult(long comparisonId, String resultSummary);

    /**
     * 重命名对比任务。
     *
     * @param comparisonId   对比任务ID
     * @param comparisonName 新名称
     * @param userId         当前用户ID（防越权）
     * @return 受影响行数
     */
    int rename(long comparisonId, String comparisonName, long userId);

    /**
     * 按主键删除（需匹配用户，防越权）。
     *
     * @param comparisonId 对比任务ID
     * @param userId       当前用户ID
     * @return 受影响行数
     */
    int deleteByIdAndUser(long comparisonId, long userId);
}
