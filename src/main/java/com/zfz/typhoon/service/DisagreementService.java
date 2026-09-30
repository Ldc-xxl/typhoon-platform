package com.zfz.typhoon.service;

import com.zfz.typhoon.model.ForecastDisagreeRecord;
import com.zfz.typhoon.model.vo.DisagreementVO;

import java.util.List;

/**
 * 多机构预报分歧解析服务。
 *
 * <p>对应项目描述文档「核心特色功能一：多机构集合预报分歧挖掘与不确定性解析引擎」。
 *
 * <p>本服务区别于"把各家路径画在一张图上"的做法：它把分歧<u>量化</u>成
 * 四个可比较的维度（登陆地点 / 登陆时间 / 峰值强度 / 移动速度），
 * 各自算出离散度，再加权归一化成 0—1 的<b>分歧指数</b>，据此给出
 * 风险等级、收敛趋势与面向公众的结论文案。
 *
 * <p>阈值与权重取 {@code system_settings} 表中的可配置项，不硬编码，
 * 便于答辩时演示"参数化配置"。
 *
 * @author 追风者项目组
 * @see DisagreementVO
 */
public interface DisagreementService {

    /**
     * 系统配置键：高不确定性判定阈值。
     *
     * <p>取值与 {@code system_settings} 表中已有的 {@code high_uncertainty_threshold}
     * （默认 0.7）保持一致，避免另起一套键名导致同一个阈值在两处配置。
     */
    String KEY_INDEX_THRESHOLD = "high_uncertainty_threshold";
    /** 系统配置键：分歧"扩大中"的判定变化量（表内未预置，走默认值） */
    String KEY_EXPAND_DELTA = "disagree.expand.delta";

    /**
     * 实时计算指定台风当前的预报分歧解析结果（不落库）。
     *
     * <p>用于详情页与分歧解析页的即时展示。计算完全基于
     * {@code forecast_paths} 中各机构的最新一条预报序列。
     *
     * @param typhoonId 台风ID
     * @return 分歧解析结果；机构数不足 2 家时各维度为空、index 为 0
     * @throws com.zfz.typhoon.util.BusinessException 台风不存在时抛出（404）
     */
    DisagreementVO analyze(long typhoonId);

    /**
     * 取最近一次<b>已落库</b>的分歧分析结果。
     *
     * @param typhoonId 台风ID
     * @return 最近一次分析；从未分析过时退化为实时计算
     * @throws com.zfz.typhoon.util.BusinessException 台风不存在时抛出（404）
     */
    DisagreementVO latest(long typhoonId);

    /**
     * 计算并把本次分析结果写入 {@code forecast_disagree_records}。
     *
     * <p>落库的意义有二：一是历史趋势可回溯（页面上的"分歧是收敛还是扩大"），
     * 二是把耗时的方差计算与在线请求解耦。
     *
     * @param typhoonId 台风ID
     * @return 落库后的记录（含自增主键）
     * @throws com.zfz.typhoon.util.BusinessException 台风不存在时抛出（404）
     */
    ForecastDisagreeRecord analyzeAndSave(long typhoonId);

    /**
     * 查询指定台风的历史分歧记录，按分析时间倒序。
     *
     * @param typhoonId 台风ID
     * @return 历史记录列表；无记录时返回空列表
     */
    List<ForecastDisagreeRecord> history(long typhoonId);

    /**
     * 判定指定台风当前是否处于"高不确定性"状态。
     *
     * @param typhoonId 台风ID
     * @return 分歧指数超过阈值返回 true
     */
    boolean isHighUncertainty(long typhoonId);
}
