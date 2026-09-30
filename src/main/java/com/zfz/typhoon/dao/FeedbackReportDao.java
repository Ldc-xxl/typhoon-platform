package com.zfz.typhoon.dao;

import com.zfz.typhoon.model.FeedbackReport;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户反馈表（{@code feedback_reports}）数据访问接口。
 *
 * <p>课程要求（阶段七 ·「管理端需处理用户反馈，形成闭环」）。
 * 对应接口文档：{@code POST /api/feedback}（提交）、
 * {@code GET /api/feedback}（管理端列表）、
 * {@code PUT /api/feedback/{id}/handle}（标记处理）。
 *
 * <p>闭环体现在两处字段：{@code handler_id}（谁处理的）与
 * {@code handle_time}（何时处理的）。因此本 DAO 的
 * {@link #handle} 方法一次性写这两个字段，避免只改状态不记处理人。
 *
 * @author 追风者项目组
 */
public interface FeedbackReportDao {

    /** 反馈类型：数据异常 */
    String TYPE_DATA_ISSUE = FeedbackReport.TYPE_DATA_ISSUE;

    /** 反馈类型：功能建议 */
    String TYPE_FEATURE = FeedbackReport.TYPE_FEATURE;

    /** 反馈类型：问题反馈 */
    String TYPE_PROBLEM = FeedbackReport.TYPE_PROBLEM;

    /** 处理状态：待处理 */
    String STATUS_PENDING = FeedbackReport.STATUS_PENDING;

    /** 处理状态：已处理 */
    String STATUS_PROCESSED = FeedbackReport.STATUS_PROCESSED;

    /**
     * 查询反馈列表（联表带出提交人用户名），按提交时间倒序。
     *
     * @param status 处理状态，为 null 时查全部
     * @param type   反馈类型，为 null 时查全部
     * @param limit  最多返回条数
     * @return 反馈列表
     */
    List<FeedbackReport> list(String status, String type, int limit);

    /**
     * 查询某用户提交的反馈。
     *
     * @param userId 用户ID
     * @return 反馈列表
     */
    List<FeedbackReport> listByUser(long userId);

    /**
     * 按主键查询。
     *
     * @param feedbackId 反馈ID
     * @return 反馈；不存在返回 null
     */
    FeedbackReport findById(long feedbackId);

    /**
     * 按状态统计数量（管理端待办角标）。
     *
     * @param status 处理状态
     * @return 数量
     */
    int countByStatus(String status);

    /**
     * 按类型统计数量（管理端分布图）。
     *
     * @param type 反馈类型
     * @return 数量
     */
    int countByType(String type);

    /**
     * 统计总数。
     *
     * @return 数量
     */
    int countAll();

    /**
     * 新增反馈。
     *
     * @param feedback 反馈
     * @return 新生成的主键
     */
    long insert(FeedbackReport feedback);

    /**
     * 标记为已处理：同时写入处理人与处理时间（闭环）。
     *
     * @param feedbackId 反馈ID
     * @param handlerId  处理人ID
     * @param handleTime 处理时间
     * @return 受影响行数
     */
    int handle(long feedbackId, long handlerId, LocalDateTime handleTime);

    /**
     * 将已处理的反馈打回为待处理（误操作纠正）。
     *
     * @param feedbackId 反馈ID
     * @return 受影响行数
     */
    int reopen(long feedbackId);

    /**
     * 按主键删除（仅管理端）。
     *
     * @param feedbackId 反馈ID
     * @return 受影响行数
     */
    int deleteById(long feedbackId);
}
