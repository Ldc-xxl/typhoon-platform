package com.zfz.typhoon.service;

import com.zfz.typhoon.model.HistoricalComparison;

import java.util.List;

/**
 * 历史台风对比业务接口。
 *
 * <p>对应接口文档：{@code GET /api/comparisons}、
 * {@code POST /api/comparisons}、{@code GET /api/comparisons/{id}}、
 * {@code DELETE /api/comparisons/{id}}。
 *
 * <h3>对比任务为什么要落库</h3>
 * 一次对比涉及 N 个台风的轨迹、强度、风圈与影响范围，计算量远大于单台风的详情查询。
 * 而且对比结论是<b>讨论材料</b>——教研、复盘、汇报都会反复引用同一组对比。
 * 因此创建时算一次、把结论写进 {@code result_summary} JSON 列，
 * 之后读取直接返回，既省算力也保证"同一份报告每次打开看到的是同一个结论"。
 *
 * <h3>结论的计算口径</h3>
 * 复用 {@link SimilarCaseService} 的五维相似度打分，
 * 但对外只暴露两个最直观的指标：路径偏差（km）与强度差（级）。
 * 指标越少越容易被理解，细分的五个维度反而会淹没结论。
 *
 * @author 追风者项目组
 */
public interface ComparisonService {

    /** 单次对比最多包含的台风数（含主台风），防止一次请求拉满 CPU */
    int MAX_TYPHOONS = 6;

    /**
     * 创建对比任务并立即计算结论。
     *
     * @param userId           创建者ID
     * @param comparisonName   对比名称；为空时自动生成
     * @param primaryTyphoonId 主台风ID
     * @param compareTyphoonIds 对比台风ID列表
     * @return 任务实体（含 {@code resultSummary}）
     */
    HistoricalComparison create(long userId, String comparisonName,
                               long primaryTyphoonId, List<Long> compareTyphoonIds);

    /**
     * 查询用户的对比任务。
     *
     * @param userId 用户ID
     * @return 任务列表
     */
    List<HistoricalComparison> list(long userId);

    /**
     * 查询某台风作为主台风参与过的对比任务。
     *
     * @param typhoonId 台风ID
     * @return 任务列表
     */
    List<HistoricalComparison> listByPrimaryTyphoon(long typhoonId);

    /**
     * 取单个任务（校验归属）。
     *
     * @param comparisonId 任务ID
     * @param userId       用户ID
     * @return 任务实体
     */
    HistoricalComparison get(long comparisonId, long userId);

    /**
     * 重命名任务。
     *
     * @param comparisonId 任务ID
     * @param name         新名称
     * @param userId       用户ID
     * @return 受影响行数
     */
    int rename(long comparisonId, String name, long userId);

    /**
     * 删除任务。
     *
     * @param comparisonId 任务ID
     * @param userId       用户ID
     * @return 受影响行数
     */
    int delete(long comparisonId, long userId);
}
