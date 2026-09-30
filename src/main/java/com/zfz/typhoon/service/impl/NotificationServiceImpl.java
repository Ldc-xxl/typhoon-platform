package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.NotificationDao;
import com.zfz.typhoon.dao.UserDao;
import com.zfz.typhoon.dao.UserSubscriptionDao;
import com.zfz.typhoon.dao.impl.NotificationDaoImpl;
import com.zfz.typhoon.dao.impl.UserDaoImpl;
import com.zfz.typhoon.dao.impl.UserSubscriptionDaoImpl;
import com.zfz.typhoon.model.Notification;
import com.zfz.typhoon.model.User;
import com.zfz.typhoon.model.UserSubscription;
import com.zfz.typhoon.model.vo.NotificationVO;
import com.zfz.typhoon.service.NotificationService;
import com.zfz.typhoon.util.DateUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 通知中心业务实现。
 *
 * <h3>为什么按"订阅关系"而不是"全体广播"发通知</h3>
 * 用户关注的台风不同，把所有台风的变化推给所有人，通知面板会在一个活跃台风季
 * 变成刷不完的噪音，最终没人看。因此强度/路径类通知只发给
 * <b>订阅了该台风</b>的用户；但系统级故障（数据源同步失败）必须发给管理员——
 * 系统故障是"运维必须知道"，不属于用户偏好范畴。
 *
 * <h3>为什么一次变化只发一条</h3>
 * {@link #notifyStrengthChange} 等内容里已经包含了"从 X 级到 Y 级"，
 * 不需要为每一级变化各发一条。同一台风同类型通知在
 * {@value #DEDUP_HOURS} 小时内不重复发，避免短时间内反复更新把面板刷爆。
 *
 * @author 追风者项目组
 * @see NotificationService
 */
public class NotificationServiceImpl implements NotificationService {

    /** 默认返回条数 */
    private static final int DEFAULT_LIMIT = 20;

    /** 单次查询最多返回条数，防止前端传个 100000 把内存打满 */
    private static final int MAX_LIMIT = 100;

    /** 同台风同类型通知的去重窗口（小时） */
    private static final int DEDUP_HOURS = 2;

    private final NotificationDao notificationDao;
    private final UserSubscriptionDao subscriptionDao;
    private final UserDao userDao;

    /**
     * 生产用构造：按默认实现装配依赖。
     * <p>之所以把它拆成"无参 + 带参"两个构造，是为了给单元测试留出注入替身的缝隙：
     * 依赖全部在字段初始化里 new 出来的话，测试就必须连上真实 MySQL 才能跑，
     * 单元测试也就退化成了集成测试。
     */
    public NotificationServiceImpl() {
        this(new NotificationDaoImpl(), new UserSubscriptionDaoImpl(), new UserDaoImpl());
    }

    /**
     * 测试用构造：注入替身依赖（Mockito 生成的 mock 或手写桩）。
     * <p>参数顺序与上方字段声明一致，均为本服务运行所需的协作者。
     */
    public NotificationServiceImpl(NotificationDao notificationDao, UserSubscriptionDao subscriptionDao, UserDao userDao) {
        this.notificationDao = notificationDao;
        this.subscriptionDao = subscriptionDao;
        this.userDao = userDao;
    }

    @Override
    public List<NotificationVO> list(long userId, int limit) {
        int n = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        List<Notification> rows = notificationDao.listByUser(userId, n);
        List<NotificationVO> result = new ArrayList<NotificationVO>(rows.size());
        for (Notification row : rows) {
            result.add(NotificationVO.of(row));
        }
        return result;
    }

    @Override
    public int countUnread(long userId) {
        return notificationDao.countUnread(userId);
    }

    @Override
    public int markRead(long notificationId, long userId) {
        // DAO 的 WHERE 里带 user_id，天然防止越权把别人的通知标成已读
        return notificationDao.markRead(notificationId, userId);
    }

    @Override
    public int markAllRead(long userId) {
        return notificationDao.markAllRead(userId);
    }

    @Override
    public int notifyStrengthChange(long typhoonId, String typhoonName, int fromForce, int toForce) {
        String name = displayName(typhoonName);
        String title = "强度变化：" + name + " 由 " + fromForce + " 级变为 " + toForce + " 级";
        String content = toForce > fromForce
                ? "监测到" + name + " 强度增强，请沿海地区关注最新预警与撤离安排。"
                : "监测到" + name + " 强度减弱，仍需警惕登陆前后的风雨影响。";
        return fanOut(typhoonId, Notification.TYPE_STRENGTH_CHANGE, title, content,
                Filter.NOTIFY_STRENGTH_CHANGE);
    }

    @Override
    public int notifyPathChange(long typhoonId, String typhoonName, double index, String levelText) {
        String name = displayName(typhoonName);
        String title = "预报分歧扩大：" + name + " 各机构路径差异" + levelText;
        String content = "当前多机构预报分歧指数 " + Math.round(index * 100D) + "%，"
                + "登陆点与影响范围存在较大不确定性，请以官方滚动预报为准。";
        return fanOut(typhoonId, Notification.TYPE_PATH_CHANGE, title, content,
                Filter.NOTIFY_PATH_CHANGE);
    }

    @Override
    public int notifyNewStorm(long typhoonId, String typhoonName) {
        String name = displayName(typhoonName);
        String title = "新台风生成：" + name;
        String content = name + " 已生成，平台已开始接入多机构预报与实况轨迹，可前往档案页查看。";
        return fanOut(typhoonId, Notification.TYPE_NEW_STORM, title, content, Filter.NOTIFY_NEW_STORM);
    }

    @Override
    public int notifySyncFailure(String sourceName, String message) {
        List<User> admins = userDao.listByRole(User.ROLE_ADMIN);
        if (admins.isEmpty()) {
            return 0;
        }
        String title = "数据源同步失败：" + sourceName;
        String content = "数据源「" + sourceName + "」本次同步失败：" + safe(message)
                + "。请前往后台数据源监控查看详情。";
        List<Notification> batch = new ArrayList<Notification>(admins.size());
        for (User admin : admins) {
            batch.add(newNotification(admin.getUserId(), null, null,
                    Notification.TYPE_SYSTEM, title, content));
        }
        return notificationDao.batchInsert(batch);
    }

    @Override
    public long push(Notification notification) {
        if (notification.getCreatedAt() == null) {
            notification.setCreatedAt(DateUtil.now());
        }
        if (notification.getRead() == null) {
            notification.setRead(Boolean.FALSE);
        }
        return notificationDao.insert(notification);
    }

    /* ==================================================================== */
    /*  内部                                                                */
    /* ==================================================================== */

    /** 订阅偏好过滤器：决定某类变化该发给哪些订阅者 */
    private enum Filter {
        /** 打开了「新台风提醒」 */
        NOTIFY_NEW_STORM,
        /** 打开了「强度变化提醒」 */
        NOTIFY_STRENGTH_CHANGE,
        /** 打开了「路径变化提醒」 */
        NOTIFY_PATH_CHANGE
    }

    /**
     * 按订阅关系扇形分发一条通知。
     *
     * @param typhoonId 台风ID
     * @param type      通知类型
     * @param title     标题
     * @param content   内容
     * @param filter    订阅偏好过滤
     * @return 实际写入条数
     */
    private int fanOut(long typhoonId, String type, String title, String content, Filter filter) {
        List<UserSubscription> subs = subscriptionDao.listByTyphoon(typhoonId);
        if (subs.isEmpty()) {
            return 0;
        }
        // 同一用户可能同时订阅了该台风与多个城市，去重避免重复收
        Set<Long> recipients = new LinkedHashSet<Long>();
        for (UserSubscription sub : subs) {
            if (sub.getUserId() == null) {
                continue;
            }
            if (Boolean.FALSE.equals(sub.getPushEnabled())) {
                continue;
            }
            if (!accept(sub, filter)) {
                continue;
            }
            recipients.add(sub.getUserId());
        }
        if (recipients.isEmpty()) {
            return 0;
        }
        if (recentlyNotified(recipients, typhoonId, type)) {
            // 刚刚发过同类通知，本轮静默
            return 0;
        }
        List<Notification> batch = new ArrayList<Notification>(recipients.size());
        for (Long uid : recipients) {
            batch.add(newNotification(uid, Long.valueOf(typhoonId), null, type, title, content));
        }
        return notificationDao.batchInsert(batch);
    }

    /**
     * 订阅偏好是否允许接收该类通知。
     *
     * @param sub    订阅记录
     * @param filter 类型
     * @return 允许返回 true
     */
    private boolean accept(UserSubscription sub, Filter filter) {
        switch (filter) {
            case NOTIFY_NEW_STORM:
                return !Boolean.FALSE.equals(sub.getNotifyNewStorm());
            case NOTIFY_STRENGTH_CHANGE:
                return !Boolean.FALSE.equals(sub.getNotifyStrengthChange());
            default:
                return !Boolean.FALSE.equals(sub.getNotifyPathChange());
        }
    }

    /**
     * 去重窗口判断：只要任一接收者在窗口内已收到同台风同类型通知，本轮就整体静默。
     *
     * <p>之所以用"整体静默"而不是"逐个过滤"：一次台风变化对所有人是同一个事件，
     * 部分人收到、部分人没收到会让通知面板的口径变得难以解释。
     *
     * @param recipients 接收者
     * @param typhoonId  台风ID
     * @param type       通知类型
     * @return 需要静默返回 true
     */
    private boolean recentlyNotified(Set<Long> recipients, long typhoonId, String type) {
        for (Long uid : recipients) {
            for (Notification n : notificationDao.listByUser(uid.longValue(), MAX_LIMIT)) {
                if (!type.equals(n.getNotifyType()) || n.getTyphoonId() == null
                        || n.getTyphoonId().longValue() != typhoonId) {
                    continue;
                }
                if (DateUtil.hoursBetween(n.getCreatedAt(), DateUtil.now()) < DEDUP_HOURS) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 构造通知实体。
     *
     * @param userId    接收者
     * @param typhoonId 台风ID，可为 null
     * @param cityId    城市ID，可为 null
     * @param type      类型
     * @param title     标题
     * @param content   内容
     * @return 通知实体
     */
    private Notification newNotification(Long userId, Long typhoonId, Long cityId,
                                         String type, String title, String content) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setTyphoonId(typhoonId);
        n.setCityId(cityId);
        n.setNotifyType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setRead(Boolean.FALSE);
        n.setCreatedAt(DateUtil.now());
        return n;
    }

    /**
     * 空台风名兜底。
     *
     * @param name 原名
     * @return 展示名
     */
    private String displayName(String name) {
        return name == null || name.trim().isEmpty() ? "该台风" : name.trim();
    }

    /**
     * 空文本兜底。
     *
     * @param text 原文本
     * @return 非空文本
     */
    private String safe(String text) {
        return text == null || text.trim().isEmpty() ? "原因未知" : text.trim();
    }
}
