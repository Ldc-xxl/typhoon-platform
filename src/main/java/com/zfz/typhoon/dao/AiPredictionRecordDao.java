package com.zfz.typhoon.dao;

import com.zfz.typhoon.model.AiPredictionRecord;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 大模型智能测算记录表（{@code ai_prediction_records}）数据访问接口。
 *
 * <p>课程特色模块 ④：<b>AI 多模型台风路径智能测算</b>。
 * 对应接口文档：{@code GET /api/typhoons/{id}/ai-prediction}。
 *
 * <p><b>重要合规约定</b>：本表数据<b>仅用于教学演示</b>，
 * 返回给前端的 VO 必须携带免责声明（见 {@code AiPredictionVO.DISCLAIMER}），
 * 说明测算结果不构成任何防灾决策依据。该约定由 Service 层落实，DAO 只负责存取。
 *
 * <p>同一台风会有多个模型（DeepSeek-V3 / 通义千问-max / 豆包Seed-1.6）
 * 的多次测算（提示词版本 v1.0 / v1.1），
 * 因此"当前测算"的取数口径是：<b>每个模型各自最新的一条</b>，
 * 而不是全表最新一条——否则多模型融合展示会缺模型。
 *
 * @author 追风者项目组
 * @see com.zfz.typhoon.model.vo.AiPredictionVO
 */
public interface AiPredictionRecordDao {

    /**
     * 取某台风<b>各模型各自最新</b>的一次测算（多模型融合展示用）。
     *
     * @param typhoonId 台风ID
     * @return 测算记录列表（按模型名升序）
     */
    List<AiPredictionRecord> listLatestPerModel(long typhoonId);

    /**
     * 取某台风全表最新的一条测算（单模型简要展示用）。
     *
     * @param typhoonId 台风ID
     * @return 测算记录；无数据返回 null
     */
    AiPredictionRecord findLatest(long typhoonId);

    /**
     * 按台风查询全部测算记录（含历史版本，供"测算演变"对比）。
     *
     * @param typhoonId 台风ID
     * @return 测算记录列表（按测算时间倒序）
     */
    List<AiPredictionRecord> listByTyphoon(long typhoonId);

    /**
     * 按台风 + 模型查询历史测算。
     *
     * @param typhoonId 台风ID
     * @param modelName 模型名称
     * @return 测算记录列表（按测算时间倒序）
     */
    List<AiPredictionRecord> listByTyphoonAndModel(long typhoonId, String modelName);

    /**
     * 统计某台风的测算次数（管理端"模型调用统计"）。
     *
     * @param typhoonId 台风ID
     * @return 次数
     */
    int countByTyphoon(long typhoonId);

    /**
     * 统计每个模型的调用次数。
     *
     * @return 每项为 {@code [模型名, 调用次数]} 的列表
     */
    List<Object[]> countGroupByModel();

    /**
     * 按模型汇总管理端"大模型接入配置"表所需的全部指标。
     *
     * <p>比 {@link #countGroupByModel} 多带回平均置信度与最近调用时间，
     * 让后台表格一次查询即可渲染，不必"先查模型列表再逐个查置信度"。
     * 用一条 {@code GROUP BY} 代替 N 次查询，是这类管理页面的常规优化。
     *
     * @return 每项为 {@code [模型名, 调用次数, 平均置信度, 最近测算时间]} 的列表；
     *         平均置信度为 {@link Double}（0—100，无数据为 null），
     *         最近测算时间为 {@link LocalDateTime}
     */
    List<Object[]> statsGroupByModel();

    /**
     * 取某台风最近一次测算时间（前端显示"测算于 xx:xx"）。
     *
     * @param typhoonId 台风ID
     * @return 时间；无数据返回 null
     */
    LocalDateTime findLatestPredictionTime(long typhoonId);

    /**
     * 新增测算记录。
     *
     * @param record 测算记录
     * @return 新生成的主键
     */
    long insert(AiPredictionRecord record);

    /**
     * 更新测算状态（如 PENDING → VALIDATED，与实况比对后回写偏差）。
     *
     * @param recordId        记录ID
     * @param status          新状态
     * @param deviationVsAgencies 与机构预报平均偏差（km），可为 null
     * @return 受影响行数
     */
    int updateStatus(long recordId, String status, Double deviationVsAgencies);

    /**
     * 按台风删除测算记录。
     *
     * @param typhoonId 台风ID
     * @return 受影响行数
     */
    int deleteByTyphoon(long typhoonId);
}
