package com.zfz.typhoon.service;

import com.zfz.typhoon.model.FeedbackReport;

import java.util.List;
import java.util.Map;

/**
 * 用户反馈业务接口。
 *
 * <p>对应接口文档：{@code POST /api/feedback}（提交）、
 * {@code GET /api/admin/feedback}（管理端列表）、
 * {@code PUT /api/admin/feedback/{id}/handle}（处理）。
 *
 * <h3>反馈是本平台的"需求收集渠道"</h3>
 * 台风数据平台的典型问题是"数据看着对但用着不对"——
 * 例如某个城市的风险等级明显偏高、某个风圈半径与官方公布不一致。
 * 这些只有真正用数据的人才能发现，因此反馈模块不是附属功能，
 * 而是数据质量闭环的入口：{@code DATA_ISSUE} 类型的反馈要能追到具体台风/城市。
 *
 * <h3>为什么处理只允许单向流转</h3>
 * {@code PENDING → PROCESSED} 是正常路径，处理完可以 {@link #reopen} 退回 PENDING
 * （用户补充说明后需要重新处理）。但不允许把 {@code handler_id} 清空，
 * 否则"谁处理的"这一审计信息就丢了。
 *
 * @author 追风者项目组
 */
public interface FeedbackService {

    /**
     * 提交反馈。
     *
     * @param feedback 反馈实体（feedbackType / content 必填）
     * @param userId   提交用户ID，匿名反馈传 null
     * @return 新反馈ID
     */
    long submit(FeedbackReport feedback, Long userId);

    /**
     * 查询反馈列表（管理端）。
     *
     * @param status 状态过滤（PENDING / PROCESSED），可为 null
     * @param type   类型过滤，可为 null
     * @param limit  条数上限
     * @return 反馈列表
     */
    List<FeedbackReport> list(String status, String type, int limit);

    /**
     * 查询某用户自己的反馈。
     *
     * @param userId 用户ID
     * @return 反馈列表
     */
    List<FeedbackReport> listByUser(long userId);

    /**
     * 标记为已处理。
     *
     * @param feedbackId 反馈ID
     * @param handlerId  处理人ID
     * @return 受影响行数
     */
    int handle(long feedbackId, long handlerId);

    /**
     * 退回待处理（用户补充说明后需要重新处理）。
     *
     * @param feedbackId 反馈ID
     * @return 受影响行数
     */
    int reopen(long feedbackId);

    /**
     * 反馈统计（后台卡片）。
     *
     * @return 键为 {@code total / pending / processed}
     */
    Map<String, Integer> stats();
}
