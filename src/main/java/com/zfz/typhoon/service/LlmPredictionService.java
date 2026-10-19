package com.zfz.typhoon.service;

import com.zfz.typhoon.model.vo.AiPredictionVO;

/**
 * 真实大模型路径测算服务。
 *
 * <p>与 {@link AiPredictionService} 的分工：
 * <ul>
 *   <li>{@code AiPredictionService}：<b>本地多机构融合算法</b>，不依赖外部网络，
 *       {@code model_name} 只是写进记录的标签；</li>
 *   <li>本接口：把机构预报拼成提示词，<b>真的调用厂商 API</b>，
 *       把模型返回的路径解析后落库。失败时抛异常，绝不回退到融合结果冒充模型输出。</li>
 * </ul>
 *
 * @author 追风者项目组
 */
public interface LlmPredictionService {

    /**
     * 用指定厂商模型测算一次并落库。
     *
     * @param typhoonId 台风 ID
     * @param modelKey  模型标识（如 doubao-seed-1.6）
     * @return 测算结果
     * @throws com.zfz.typhoon.util.BusinessException 未接入、调用失败、输出无法解析时
     */
    AiPredictionVO predict(long typhoonId, String modelKey);
}
