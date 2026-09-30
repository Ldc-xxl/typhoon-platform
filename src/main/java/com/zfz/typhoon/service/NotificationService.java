package com.zfz.typhoon.service;

import com.zfz.typhoon.model.Notification;
import com.zfz.typhoon.model.vo.NotificationVO;

import java.util.List;

/**
 * 通知中心业务接口。
 *
 * <p>对应接口文档：{@code GET /api/notifications}、
 * {@code PUT /api/notifications/{id}/read}、{@code PUT /api/notifications/read-all}。
 *
 * <h3>通知从哪来</h3>
 * 课程场景下没有真实的推送通道，通知由<b>数据变化</b>触发：
 * 台风强度跨档、机构预报分歧扩大、数据源同步失败。
 * 因此本服务提供 {@link #notifyOnChange} 一族方法，
 * 让"数据写入"与"通知生成"在同一处发生——
 * 而不是让各个 Servlet 各写一遍通知文案。
 *
 * @author 追风者项目组
 */
public interface NotificationService {

    /**
     * 查询某用户的通知（按时间倒序）。
     *
     * @param userId 用户ID
     * @param limit  条数上限
     * @return 通知视图列表
     */
    List<NotificationVO> list(long userId, int limit);

    /**
     * 未读条数（顶栏红点）。
     *
     * @param userId 用户ID
     * @return 未读数
     */
    int countUnread(long userId);

    /**
     * 标记单条已读。
     *
     * @param notificationId 通知ID
     * @param userId         用户ID（防止越权标记他人通知）
     * @return 受影响行数
     */
    int markRead(long notificationId, long userId);

    /**
     * 全部标记已读。
     *
     * @param userId 用户ID
     * @return 受影响行数
     */
    int markAllRead(long userId);

    /**
     * 台风强度变化时给订阅者发通知。
     *
     * @param typhoonId   台风ID
     * @param typhoonName 台风名（写进文案）
     * @param fromForce   变化前风力等级
     * @param toForce     变化后风力等级
     * @return 实际发出的通知条数
     */
    int notifyStrengthChange(long typhoonId, String typhoonName, int fromForce, int toForce);

    /**
     * 多机构预报分歧扩大时给订阅者发通知。
     *
     * @param typhoonId   台风ID
     * @param typhoonName 台风名
     * @param index       当前分歧指数（0—1）
     * @param levelText   分歧等级中文（较高 / 高）
     * @return 实际发出的通知条数
     */
    int notifyPathChange(long typhoonId, String typhoonName, double index, String levelText);

    /**
     * 新台风生成时向开启"新台风提醒"的订阅者广播。
     *
     * @param typhoonId   台风ID
     * @param typhoonName 台风名
     * @return 实际发出的通知条数
     */
    int notifyNewStorm(long typhoonId, String typhoonName);

    /**
     * 数据源同步失败时给管理员发系统通知。
     *
     * @param sourceName 数据源名称
     * @param message    失败原因
     * @return 实际发出的通知条数
     */
    int notifySyncFailure(String sourceName, String message);

    /**
     * 写入一条通知（内部通用出口）。
     *
     * @param notification 通知实体
     * @return 新通知ID
     */
    long push(Notification notification);
}
