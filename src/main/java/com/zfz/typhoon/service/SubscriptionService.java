package com.zfz.typhoon.service;

import com.zfz.typhoon.model.UserSubscription;

import java.util.List;

/**
 * 订阅业务接口。
 *
 * <p>对应接口文档：{@code GET /api/subscriptions}、
 * {@code POST /api/subscriptions}、{@code PUT /api/subscriptions/{id}}、
 * {@code DELETE /api/subscriptions/{id}}。
 *
 * <h3>订阅与收藏的区别（容易被合并，但不该合并）</h3>
 * <ul>
 *   <li><b>收藏</b>是"我以后想再看一眼"——记录的是<b>兴趣</b>，写入后不再变化；</li>
 *   <li><b>订阅</b>是"有变化时请告诉我"——记录的是<b>通知偏好</b>，
 *       四个开关（新台风/强度/路径/推送）决定什么情况下会打扰我。</li>
 * </ul>
 * 把两者合成一张表会让"收藏了但不想被通知"这种最常见的诉求无法表达。
 *
 * <h3>为什么取消订阅要按 subscriptionId 而不是按台风ID</h3>
 * 同一用户可能对同一个台风有不同层面的订阅（例如同时订阅了台风与该台风登陆的城市），
 * 按 ID 删除才能精确取消其中一个。
 *
 * @author 追风者项目组
 */
public interface SubscriptionService {

    /**
     * 查询用户的全部订阅。
     *
     * @param userId 用户ID
     * @return 订阅列表
     */
    List<UserSubscription> list(long userId);

    /**
     * 查询订阅了某台风的用户订阅记录（供通知分发使用）。
     *
     * @param typhoonId 台风ID
     * @return 订阅列表
     */
    List<UserSubscription> listByTyphoon(long typhoonId);

    /**
     * 新增订阅（幂等：同一用户对同一对象重复订阅时更新偏好而不是报错）。
     *
     * @param subscription 订阅实体（typhoonId 与 cityId 至少有一个）
     * @return 订阅ID
     */
    long subscribe(UserSubscription subscription);

    /**
     * 修改通知偏好。
     *
     * @param subscription 订阅实体（须含 subscriptionId 与 userId）
     * @return 受影响行数
     */
    int updatePreferences(UserSubscription subscription);

    /**
     * 取消订阅。
     *
     * @param subscriptionId 订阅ID
     * @param userId         用户ID（防止取消他人订阅）
     * @return 受影响行数
     */
    int unsubscribe(long subscriptionId, long userId);

    /**
     * 一键开关全部浏览器推送。
     *
     * @param userId  用户ID
     * @param enabled 目标状态
     * @return 受影响行数
     */
    int setPushEnabled(long userId, boolean enabled);

    /**
     * 订阅或取消订阅某台风（详情页订阅按钮）。
     *
     * @param userId    用户ID
     * @param typhoonId 台风ID
     * @param subscribe true=订阅 / false=取消
     * @return 受影响行数
     */
    int toggleTyphoon(long userId, long typhoonId, boolean subscribe);
}
