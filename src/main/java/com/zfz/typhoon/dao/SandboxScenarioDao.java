package com.zfz.typhoon.dao;

import com.zfz.typhoon.model.SandboxScenario;

import java.util.List;

/**
 * 沙盘模拟情景表（{@code sandbox_scenarios}）数据访问接口。
 *
 * <p>课程特色模块 ③：<b>台风路径沙盘推演</b>。
 * 对应接口文档：{@code GET /api/sandbox/scenarios}（情景列表）、
 * {@code POST /api/sandbox/scenarios}（保存推演结果）。
 *
 * <p>设计要点：情景采用<b>基底台风 + 参数偏移</b>的表示法
 * （{@code base_typhoon_id} + {@code params_json}），
 * 而不是把推演后的整条路径存死。好处是参数可复现、可对比、
 * 可在不改动基底真实数据的前提下反复推演；
 * 坏处是需要 Service 层重放算法——这是刻意的取舍，
 * 保证"推演"与"实况"数据物理隔离，不会污染真实台风数据。
 *
 * <p><b>合规约定</b>：本表结果属于<b>假设推演</b>，
 * 前端展示必须带 {@code SandboxScenarioVO.NOTE} 提示，不得作为防灾依据。
 *
 * @author 追风者项目组
 * @see com.zfz.typhoon.model.vo.SandboxScenarioVO
 */
public interface SandboxScenarioDao {

    /**
     * 按主键查询情景。
     *
     * @param scenarioId 情景ID
     * @return 情景；不存在返回 null
     */
    SandboxScenario findById(long scenarioId);

    /**
     * 查询某用户创建的情景（"我的推演"）。
     *
     * @param userId 用户ID
     * @return 情景列表（按更新时间倒序）
     */
    List<SandboxScenario> listByUser(long userId);

    /**
     * 查询某用户针对某个基底台风的情景。
     *
     * @param userId         用户ID
     * @param baseTyphoonId  基底台风ID
     * @return 情景列表
     */
    List<SandboxScenario> listByUserAndTyphoon(long userId, long baseTyphoonId);

    /**
     * 查询公开分享的情景（沙盘页"案例广场"）。
     *
     * @param limit 最多返回条数
     * @return 情景列表（按更新时间倒序）
     */
    List<SandboxScenario> listPublic(int limit);

    /**
     * 查询某基底台风下的全部情景（对比推演前后，含他人公开的）。
     *
     * @param baseTyphoonId 基底台风ID
     * @return 情景列表
     */
    List<SandboxScenario> listByBaseTyphoon(long baseTyphoonId);

    /**
     * 统计某用户的推演次数。
     *
     * @param userId 用户ID
     * @return 次数
     */
    int countByUser(long userId);

    /**
     * 统计全部情景方案数（后台运行概览的"本月情景推演"）。
     *
     * @return 总数
     */
    int countAll();

    /**
     * 新增情景。
     *
     * @param scenario 情景
     * @return 新生成的主键
     */
    long insert(SandboxScenario scenario);

    /**
     * 更新情景（名称、参数、结果快照、公开标记）。
     *
     * @param scenario 情景（必须含 scenarioId）
     * @return 受影响行数
     */
    int update(SandboxScenario scenario);

    /**
     * 设置公开分享开关。
     *
     * @param scenarioId 情景ID
     * @param shared     是否公开
     * @return 受影响行数
     */
    int setPublic(long scenarioId, boolean shared);

    /**
     * 按主键删除情景（仅创建者可删，权限校验在 Service 层）。
     *
     * @param scenarioId 情景ID
     * @return 受影响行数
     */
    int deleteById(long scenarioId);
}
