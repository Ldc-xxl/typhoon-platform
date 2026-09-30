package com.zfz.typhoon.dao;

import com.zfz.typhoon.model.UserSubscription;

import java.util.List;

/**
 * 用户订阅表（{@code user_subscriptions}）数据访问接口。
 *
 * <p>课程要求（阶段四 ·「用户订阅台风 / 城市，并按偏好接收通知」）。
 * 对应接口文档：{@code GET /api/subscriptions}、{@code POST /api/subscriptions}、
 * {@code PUT /api/subscriptions/{id}}。
 *
 * <h3>本表是"通知分发"的路由表</h3>
 * 当某个台风发生强度变化时，通知发给谁由本表决定：
 * <ol>
 *   <li>订阅了该台风，且 {@code notify_strength_change = 1}，且 {@code push_enabled = 1}；</li>
 *   <li>或订阅了受影响城市，且该城市落在风圈内，且对应 notify 开关为 1。</li>
 * </ol>
 * 因此 {@link #listByTyphoon} 与 {@link #listByCity} 是通知服务的关键查询，
 * 二者都应命中唯一键 {@code uk_sub_user_typhoon_city} 之外的
 * 索引 {@code idx_sub_typhoon} / {@code idx_sub_city}。
 *
 * @author 追风者项目组
 */
public interface UserSubscriptionDao {

    /**
     * 查询某用户的全部订阅（按创建时间倒序）。
     *
     * @param userId 用户ID
     * @return 订阅列表
     */
    List<UserSubscription> listByUser(long userId);

    /**
     * 查询订阅了某台风的所有用户订阅记录（通知分发用）。
     *
     * @param typhoonId 台风ID
     * @return 订阅列表
     */
    List<UserSubscription> listByTyphoon(long typhoonId);

    /**
     * 查询订阅了某城市的所有用户订阅记录（通知分发用）。
     *
     * @param cityId 城市ID
     * @return 订阅列表
     */
    List<UserSubscription> listByCity(long cityId);

    /**
     * 按主键查询。
     *
     * @param subscriptionId 订阅ID
     * @return 订阅；不存在返回 null
     */
    UserSubscription findById(long subscriptionId);

    /**
     * 查询某用户对某台风的订阅（判断是否已订阅）。
     *
     * @param userId    用户ID
     * @param typhoonId 台风ID
     * @return 订阅；不存在返回 null
     */
    UserSubscription findByUserAndTyphoon(long userId, long typhoonId);

    /**
     * 查询某用户对某城市的订阅。
     *
     * @param userId 用户ID
     * @param cityId 城市ID
     * @return 订阅；不存在返回 null
     */
    UserSubscription findByUserAndCity(long userId, long cityId);

    /**
     * 统计某用户的订阅数量。
     *
     * @param userId 用户ID
     * @return 数量
     */
    int countByUser(long userId);

    /**
     * 统计某台风被订阅的次数（管理端"热门台风"）。
     *
     * @param typhoonId 台风ID
     * @return 次数
     */
    int countByTyphoon(long typhoonId);

    /**
     * 新增订阅。
     *
     * @param subscription 订阅
     * @return 新生成的主键
     */
    long insert(UserSubscription subscription);

    /**
     * 更新订阅的通知偏好（四个开关）。
     *
     * @param subscription 订阅（必须含 subscriptionId）
     * @return 受影响行数
     */
    int updatePreferences(UserSubscription subscription);

    /**
     * 批量开启 / 关闭某用户全部订阅的浏览器推送。
     *
     * @param userId  用户ID
     * @param enabled 是否开启
     * @return 受影响行数
     */
    int setPushEnabledForUser(long userId, boolean enabled);

    /**
     * 按主键取消订阅（需同时匹配用户，防越权）。
     *
     * @param subscriptionId 订阅ID
     * @param userId         当前用户ID
     * @return 受影响行数
     */
    int deleteByIdAndUser(long subscriptionId, long userId);

    /**
     * 取消某用户对某台风的订阅。
     *
     * @param userId    用户ID
     * @param typhoonId 台风ID
     * @return 受影响行数
     */
    int deleteByUserAndTyphoon(long userId, long typhoonId);
}
