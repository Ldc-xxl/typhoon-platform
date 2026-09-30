package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.UserSubscriptionDao;
import com.zfz.typhoon.dao.impl.UserSubscriptionDaoImpl;
import com.zfz.typhoon.model.UserSubscription;
import com.zfz.typhoon.service.CityService;
import com.zfz.typhoon.service.SubscriptionService;
import com.zfz.typhoon.service.TyphoonService;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.DateUtil;

import java.util.List;

/**
 * 订阅业务实现。
 *
 * @author 追风者项目组
 * @see SubscriptionService
 */
public class SubscriptionServiceImpl implements SubscriptionService {

    private final UserSubscriptionDao subscriptionDao;
    private final TyphoonService typhoonService;
    private final CityService cityService;

    /**
     * 生产用构造：按默认实现装配依赖。
     * <p>之所以把它拆成"无参 + 带参"两个构造，是为了给单元测试留出注入替身的缝隙：
     * 依赖全部在字段初始化里 new 出来的话，测试就必须连上真实 MySQL 才能跑，
     * 单元测试也就退化成了集成测试。
     */
    public SubscriptionServiceImpl() {
        this(new UserSubscriptionDaoImpl(), new TyphoonServiceImpl(), new CityServiceImpl());
    }

    /**
     * 测试用构造：注入替身依赖（Mockito 生成的 mock 或手写桩）。
     * <p>参数顺序与上方字段声明一致，均为本服务运行所需的协作者。
     */
    public SubscriptionServiceImpl(UserSubscriptionDao subscriptionDao, TyphoonService typhoonService, CityService cityService) {
        this.subscriptionDao = subscriptionDao;
        this.typhoonService = typhoonService;
        this.cityService = cityService;
    }

    @Override
    public List<UserSubscription> list(long userId) {
        return subscriptionDao.listByUser(userId);
    }

    @Override
    public List<UserSubscription> listByTyphoon(long typhoonId) {
        return subscriptionDao.listByTyphoon(typhoonId);
    }

    @Override
    public long subscribe(UserSubscription subscription) {
        if (subscription == null || subscription.getUserId() == null) {
            throw BusinessException.badRequest("缺少 userId");
        }
        long userId = subscription.getUserId().longValue();
        Long typhoonId = subscription.getTyphoonId();
        Long cityId = subscription.getCityId();
        if (typhoonId == null && cityId == null) {
            throw BusinessException.badRequest("typhoonId 与 cityId 至少填一个");
        }
        // 外键前置校验：把数据库异常转成可读的 400，而不是 500
        if (typhoonId != null) {
            typhoonService.requireTyphoon(typhoonId.longValue());
        }
        if (cityId != null) {
            cityService.requireCity(cityId.longValue());
        }

        UserSubscription exists = typhoonId != null
                ? subscriptionDao.findByUserAndTyphoon(userId, typhoonId.longValue())
                : subscriptionDao.findByUserAndCity(userId, cityId.longValue());
        if (exists != null) {
            // 已订阅：把本次提交当成"更新偏好"，保持幂等
            copyPreferences(subscription, exists);
            subscriptionDao.updatePreferences(exists);
            return exists.getSubscriptionId().longValue();
        }

        applyDefaults(subscription);
        subscription.setCreatedAt(DateUtil.now());
        return subscriptionDao.insert(subscription);
    }

    @Override
    public int updatePreferences(UserSubscription subscription) {
        if (subscription == null || subscription.getSubscriptionId() == null
                || subscription.getUserId() == null) {
            throw BusinessException.badRequest("缺少 subscriptionId 或 userId");
        }
        UserSubscription exists = requireOwned(subscription.getSubscriptionId().longValue(),
                subscription.getUserId().longValue());
        // 归属对象不可变：改订阅类型等于新建，不做原地转换
        subscription.setTyphoonId(exists.getTyphoonId());
        subscription.setCityId(exists.getCityId());
        applyDefaults(subscription);
        return subscriptionDao.updatePreferences(subscription);
    }

    @Override
    public int unsubscribe(long subscriptionId, long userId) {
        requireOwned(subscriptionId, userId);
        return subscriptionDao.deleteByIdAndUser(subscriptionId, userId);
    }

    @Override
    public int setPushEnabled(long userId, boolean enabled) {
        return subscriptionDao.setPushEnabledForUser(userId, enabled);
    }

    @Override
    public int toggleTyphoon(long userId, long typhoonId, boolean subscribe) {
        if (subscribe) {
            UserSubscription s = new UserSubscription();
            s.setUserId(Long.valueOf(userId));
            s.setTyphoonId(Long.valueOf(typhoonId));
            subscribe(s);
            return 1;
        }
        return subscriptionDao.deleteByUserAndTyphoon(userId, typhoonId);
    }

    /* ==================================================================== */
    /*  内部                                                                */
    /* ==================================================================== */

    /**
     * 校验订阅归属，防止越权操作他人订阅。
     *
     * @param subscriptionId 订阅ID
     * @param userId         用户ID
     * @return 订阅实体
     */
    private UserSubscription requireOwned(long subscriptionId, long userId) {
        UserSubscription s = subscriptionDao.findById(subscriptionId);
        if (s == null) {
            throw BusinessException.notFound("订阅不存在：" + subscriptionId);
        }
        if (s.getUserId() == null || s.getUserId().longValue() != userId) {
            throw BusinessException.forbidden("无权操作他人的订阅");
        }
        return s;
    }

    /**
     * 补齐默认偏好。
     *
     * <p>数据库列本身有 DEFAULT 1，但显式赋值更稳妥：
     * 一旦将来把默认值改成 0，或走的是 upsert 路径而非 insert，
     * 隐式默认就未必生效，会出现"订阅了但永远收不到通知"的静默故障。
     *
     * @param s 订阅实体
     */
    private void applyDefaults(UserSubscription s) {
        if (s.getNotifyNewStorm() == null) {
            s.setNotifyNewStorm(Boolean.TRUE);
        }
        if (s.getNotifyStrengthChange() == null) {
            s.setNotifyStrengthChange(Boolean.TRUE);
        }
        if (s.getNotifyPathChange() == null) {
            s.setNotifyPathChange(Boolean.TRUE);
        }
        if (s.getPushEnabled() == null) {
            s.setPushEnabled(Boolean.TRUE);
        }
    }

    /**
     * 把偏好字段从源复制到目标（幂等更新时用）。
     *
     * @param from 源
     * @param to   目标
     */
    private void copyPreferences(UserSubscription from, UserSubscription to) {
        if (from.getNotifyNewStorm() != null) {
            to.setNotifyNewStorm(from.getNotifyNewStorm());
        }
        if (from.getNotifyStrengthChange() != null) {
            to.setNotifyStrengthChange(from.getNotifyStrengthChange());
        }
        if (from.getNotifyPathChange() != null) {
            to.setNotifyPathChange(from.getNotifyPathChange());
        }
        if (from.getPushEnabled() != null) {
            to.setPushEnabled(from.getPushEnabled());
        }
    }
}
