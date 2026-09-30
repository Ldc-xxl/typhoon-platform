package com.zfz.typhoon.service;

import com.zfz.typhoon.model.dto.SandboxRequest;
import com.zfz.typhoon.model.vo.SandboxScenarioVO;

import java.util.List;
import java.util.Map;

/**
 * 情景推演沙盘服务。
 *
 * <p>对应项目描述文档「核心特色功能三：台风情景模拟推演沙盘」。
 *
 * <p>用户在真实台风的基础上微调四类参数，服务端用<b>规则推演</b>重算整条路径、
 * 重算受影响城市与风险等级，然后把结果存成一套"情景方案"，
 * 支持基准情景与多个假设情景并排对比。
 *
 * <p>四类可调参数（与 {@code system_settings.sandbox_param_range} 一致）：
 * <table border="1">
 *   <caption>沙盘参数</caption>
 *   <tr><th>参数</th><th>含义</th><th>允许范围</th></tr>
 *   <tr><td>landingOffset</td><td>登陆点横向偏移（km）</td><td>±120</td></tr>
 *   <tr><td>speedFactor</td><td>移动速度系数</td><td>0.5—1.5</td></tr>
 *   <tr><td>intensityDelta</td><td>峰值强度增减（级）</td><td>±3</td></tr>
 *   <tr><td>windScale</td><td>风圈缩放系数</td><td>0.5—2.0</td></tr>
 * </table>
 *
 * <p><b>合规提示</b>：本功能是科普假设工具，不是数值模式，
 * 结果必须随 {@link SandboxScenarioVO#NOTE} 一起展示。
 *
 * @author 追风者项目组
 */
public interface SandboxService {

    /**
     * 查询沙盘情景列表。
     *
     * @param userId        用户ID；为 null 时返回公开情景
     * @param baseTyphoonId 基底台风ID；为 null 时不限
     * @param limit         最多返回条数
     * @return 情景列表（含推演结果快照），按创建时间倒序
     */
    List<SandboxScenarioVO> list(Long userId, Long baseTyphoonId, int limit);

    /**
     * 查询单个情景。
     *
     * @param scenarioId 情景ID
     * @return 情景；不存在时抛出 404
     * @throws com.zfz.typhoon.util.BusinessException 情景不存在时抛出（404）
     */
    SandboxScenarioVO getById(long scenarioId);

    /**
     * 按参数做一次推演（<b>只算不落库</b>）。
     *
     * @param request 推演参数
     * @return 推演结果（含受影响城市明细）
     * @throws com.zfz.typhoon.util.BusinessException 台风不存在或无预报路径时抛出
     */
    SandboxScenarioVO simulate(SandboxRequest request);

    /**
     * 推演并保存为一套情景方案。
     *
     * @param userId  创建用户ID（{@code sandbox_scenarios.user_id} 为非空列）
     * @param request 推演参数
     * @return 保存后的情景（含自增主键与结果快照）
     */
    SandboxScenarioVO save(long userId, SandboxRequest request);

    /**
     * 设置情景是否公开分享。
     *
     * @param scenarioId 情景ID
     * @param shared     是否公开
     * @return 受影响行数
     */
    int setShared(long scenarioId, boolean shared);

    /**
     * 删除情景（仅创建者可删）。
     *
     * @param scenarioId 情景ID
     * @param userId     操作用户ID
     * @return 受影响行数
     * @throws com.zfz.typhoon.util.BusinessException 非创建者操作时抛出（403）
     */
    int delete(long scenarioId, long userId);

    /**
     * 读取沙盘参数允许范围（来自 {@code system_settings.sandbox_param_range}）。
     *
     * <p>前端据此设置滑块的最大值与步长，避免把非法参数提交到后端。
     *
     * @return 参数范围，形如 {@code {landing_offset_max_km:120, speed_factor:[0.5,1.5], ...}}
     */
    Map<String, Object> paramRange();
}
