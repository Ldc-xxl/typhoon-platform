package com.zfz.typhoon.dao;

import com.zfz.typhoon.model.ForecastDisagreeRecord;

import java.util.List;

/**
 * 预报分歧分析记录表（{@code forecast_disagree_records}）数据访问接口。
 *
 * <p>课程特色模块 ①：<b>多机构预报分歧量化</b>。
 * 对应接口文档：{@code GET /api/typhoons/{id}/disagreement}。
 *
 * <p>本表存的是"分析结论"（四维偏差 + 综合方差 + 是否高不确定性 + 明细 JSON），
 * 而"分析输入"来自 {@code forecast_paths}。因此约定：
 * 每次分析都<b>新增一行</b>（而非覆盖），从而保留"分歧随时间收敛/发散"的趋势；
 * 前端默认只读最新一行，趋势图则读整段历史。这也是
 * {@link #listByTyphoon} 与 {@link #findLatestByTyphoon} 同时存在的原因。
 *
 * @author 追风者项目组
 * @see com.zfz.typhoon.model.vo.DisagreementVO
 */
public interface ForecastDisagreeRecordDao {

    /**
     * 取某台风最新一次分歧分析。
     *
     * @param typhoonId 台风ID
     * @return 分歧记录；无数据返回 null
     */
    ForecastDisagreeRecord findLatestByTyphoon(long typhoonId);

    /**
     * 取某台风全部分歧分析历史（按分析时间升序，供趋势折线）。
     *
     * @param typhoonId 台风ID
     * @return 分歧记录列表
     */
    List<ForecastDisagreeRecord> listByTyphoon(long typhoonId);

    /**
     * 取某台风在指定分析时刻的分歧记录。
     *
     * @param typhoonId    台风ID
     * @param analysisTime 分析时间
     * @return 分歧记录；不存在返回 null
     */
    ForecastDisagreeRecord findByTyphoonAndTime(long typhoonId, java.time.LocalDateTime analysisTime);

    /**
     * 查询全部"高不确定性"台风的最新分歧记录（档案页/大屏预警使用）。
     *
     * @return 分歧记录列表（按综合方差降序）
     */
    List<ForecastDisagreeRecord> listHighUncertainty();

    /**
     * 新增分歧记录。
     *
     * @param record 分歧记录
     * @return 新生成的主键
     */
    long insert(ForecastDisagreeRecord record);

    /**
     * 更新分歧记录（重算同一时刻结论时使用）。
     *
     * @param record 分歧记录（必须含 recordId）
     * @return 受影响行数
     */
    int update(ForecastDisagreeRecord record);

    /**
     * 按台风删除分歧记录。
     *
     * @param typhoonId 台风ID
     * @return 受影响行数
     */
    int deleteByTyphoon(long typhoonId);
}
