package com.zfz.typhoon.dao;

import com.zfz.typhoon.model.Notification;

import java.util.List;

/**
 * 通知消息表（{@code notifications}）数据访问接口。
 *
 * <p>对应接口文档：{@code GET /api/notifications}（顶栏通知面板）、
 * {@code POST /api/notifications/read}（标记已读）。
 *
 * <p>课程要求（阶段四 ·「用户订阅后需推送台风生成 / 强度变化 / 路径调整通知」）。
 *
 * <p>性能约定：顶栏需要高频轮询"未读数"（前端每 30 秒一次），
 * 因此单独提供 {@link #countUnread}，它只走
 * {@code idx_notif_user_read (user_id, is_read)} 索引，不回表，
 * <b>不要</b>用 {@link #listByUser} 再在前端数长度代替。
 *
 * @author 追风者项目组
 * @see com.zfz.typhoon.model.vo.NotificationVO
 */
public interface NotificationDao {

    /**
     * 查询某用户的通知列表（按发送时间倒序）。
     *
     * @param userId 用户ID
     * @param limit  最多返回条数（&lt;=0 表示不限）
     * @return 通知列表
     */
    List<Notification> listByUser(long userId, int limit);

    /**
     * 查询某用户的未读通知。
     *
     * @param userId 用户ID
     * @return 通知列表
     */
    List<Notification> listUnreadByUser(long userId);

    /**
     * 按主键查询通知。
     *
     * @param notificationId 通知ID
     * @return 通知；不存在返回 null
     */
    Notification findById(long notificationId);

    /**
     * 统计某用户的未读数（顶栏红点）。
     *
     * @param userId 用户ID
     * @return 未读数
     */
    int countUnread(long userId);

    /**
     * 统计某用户的通知总数。
     *
     * @param userId 用户ID
     * @return 总数
     */
    int countByUser(long userId);

    /**
     * 新增通知。
     *
     * @param notification 通知
     * @return 新生成的主键
     */
    long insert(Notification notification);

    /**
     * 批量新增通知（一次给多个订阅用户推送同一事件）。
     *
     * @param notifications 通知集合
     * @return 实际写入行数
     */
    int batchInsert(List<Notification> notifications);

    /**
     * 标记单条通知为已读。
     *
     * <p>SQL 中额外带 {@code user_id} 条件，防止越权读取他人通知后标记已读。
     *
     * @param notificationId 通知ID
     * @param userId         当前用户ID
     * @return 受影响行数（0 表示通知不存在或不属于该用户）
     */
    int markRead(long notificationId, long userId);

    /**
     * 标记某用户全部通知为已读。
     *
     * @param userId 用户ID
     * @return 受影响行数
     */
    int markAllRead(long userId);

    /**
     * 删除某用户的全部通知（"清空消息"）。
     *
     * @param userId 用户ID
     * @return 受影响行数
     */
    int deleteByUser(long userId);
}
