package com.zfz.typhoon.service;

import com.zfz.typhoon.model.vo.AiPredictionVO;

import java.util.List;

/**
 * AI 路径智能测算服务。
 *
 * <p>对应项目描述文档「核心特色功能 · AI 路径智能测算」与接口文档 3.8 节。
 *
 * <p><b>关于"AI"的实现口径（重要，须在答辩时如实说明）</b>
 *
 * <p>本课程环境不配备真实大模型 API 密钥，因此 {@link #trigger} 的测算内核是
 * <b>可复现的规则算法</b>，而不是真的去调大模型：
 *
 * <ol>
 *   <li><b>时间对齐重采样</b>：把 CMA / JMA / JTWC 等机构的预报序列各自按时间插值
 *       到"所有机构预报时刻的并集"上，解决各家发布时次不一致的问题；</li>
 *   <li><b>逐点加权融合</b>：在每个时刻对各家坐标取均值（可按机构可信度加权），
 *       得到融合路径；</li>
 *   <li><b>置信度反推</b>：用各机构在该时刻的坐标离散度衡量一致性，
 *       一致性越高置信度越高；</li>
 *   <li><b>解读文案生成</b>：由融合结果与离散度拼装成自然语言说明。</li>
 * </ol>
 *
 * <p>数据库 {@code ai_prediction_records} 表的结构是为"真实大模型接入"设计的
 * （有 {@code model_name}、{@code prompt_version}、{@code status} 等列），
 * 因此将来只需把上面的内核换成一次 HTTP 调用即可，表结构与前端都不用动。
 * 这一点也是本模块的设计价值所在。
 *
 * <p><b>合规要求</b>：界面必须显著标注 {@link AiPredictionVO#DISCLAIMER}。
 *
 * @author 追风者项目组
 */
public interface AiPredictionService {

    /** 系统配置键：参与融合的机构代码（逗号分隔）；不配则用全部已启用机构 */
    String KEY_FUSION_AGENCIES = "ai.fusion.agencies";
    /** 系统配置键：置信度计算所用的归一化参考离散度（km） */
    String KEY_CONFIDENCE_SCALE_KM = "ai.confidence.scaleKm";

    /** 默认的置信度归一化参考离散度：机构登陆点相差 400km 时置信度趋近 0 */
    double DEFAULT_CONFIDENCE_SCALE_KM = 400D;

    /** 默认提示词模板版本 */
    String DEFAULT_PROMPT_VERSION = "v1.1";

    /**
     * 取指定台风的 AI 测算结果（融合结果 + 各模型明细）。
     *
     * @param typhoonId 台风ID
     * @return 测算结果；无任何记录时返回基于当前预报现算的结果
     * @throws com.zfz.typhoon.util.BusinessException 台风不存在时抛出（404）
     */
    AiPredictionVO latest(long typhoonId);

    /**
     * 列出指定台风下各模型的最新一次测算明细。
     *
     * @param typhoonId 台风ID
     * @return 模型明细列表；无记录时返回空列表
     */
    List<AiPredictionVO.ModelItem> listModels(long typhoonId);

    /**
     * 触发一次新的 AI 测算并落库。
     *
     * @param typhoonId 台风ID
     * @param modelName 模型名称；为空时使用默认模型
     * @return 本次测算结果
     * @throws com.zfz.typhoon.util.BusinessException 台风不存在时抛出（404）
     */
    AiPredictionVO trigger(long typhoonId, String modelName);

    /**
     * 清除指定台风的测算记录。
     *
     * @param typhoonId 台风ID
     * @return 受影响行数
     */
    int clear(long typhoonId);
}
