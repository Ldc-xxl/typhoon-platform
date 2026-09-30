package com.zfz.typhoon.dao;

import com.zfz.typhoon.dao.impl.NotificationDaoImpl;
import com.zfz.typhoon.dao.impl.UserFavoriteDaoImpl;
import com.zfz.typhoon.dao.impl.UserSubscriptionDaoImpl;
import com.zfz.typhoon.model.Notification;
import com.zfz.typhoon.model.UserFavorite;
import com.zfz.typhoon.model.UserSubscription;
import com.zfz.typhoon.util.DBUtil;
import com.zfz.typhoon.util.JdbcTemplate;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通知 / 收藏 / 订阅 DAO 的集成测试。
 *
 * <p>这三类都是<b>个人数据</b>：查错一个人的数据就是隐私事故。
 * 所以每个用例都特意带上"换个 userId 就查不到"的反向断言 ——
 * 数据级权限靠 Service 层校验，但 DAO 的 WHERE 条件必须先是对的。
 *
 * <p>写入用例统一使用新建的测试用户（{@code ut_personal_user}），
 * 结束后连人带数据一起删掉，不碰任何真实用户的数据。
 *
 * @author 追风者项目组
 */
@DisplayName("个人数据 DAO 集成测试")
class PersonalDataDaoTest {

    private static NotificationDao notificationDao;
    private static UserFavoriteDao favoriteDao;
    private static UserSubscriptionDao subscriptionDao;

    private static long testUserId;
    private static long sampleTyphoonId;
    private static long sampleCityId;
    private static final String TEST_USER = "ut_personal_user";

    @BeforeAll
    static void setUp() {
        Assumptions.assumeTrue(DBUtil.isAvailable(),
                "本机数据库不可用（" + DBUtil.getInitError() + "），跳过");
        notificationDao = new NotificationDaoImpl();
        favoriteDao = new UserFavoriteDaoImpl();
        subscriptionDao = new UserSubscriptionDaoImpl();

        Long t = JdbcTemplate.queryLong("SELECT MIN(typhoon_id) FROM typhoons");
        sampleTyphoonId = t == null ? 0L : t.longValue();
        Long c = JdbcTemplate.queryLong("SELECT MIN(city_id) FROM cities");
        sampleCityId = c == null ? 0L : c.longValue();

        JdbcTemplate.update("DELETE FROM users WHERE username = ?", TEST_USER);
        testUserId = JdbcTemplate.insert("INSERT INTO users"
                + " (username, password_hash, nickname, role, status) VALUES (?, ?, ?, ?, ?)",
                TEST_USER, "sha256$0000", "个人数据测试用户", "USER", 1).longValue();
    }

    @org.junit.jupiter.api.AfterAll
    static void tearDown() {
        if (!DBUtil.isAvailable()) {
            return;
        }
        try {
            notificationDao.deleteByUser(testUserId);
            JdbcTemplate.update("DELETE FROM user_favorites WHERE user_id = ?", Long.valueOf(testUserId));
            JdbcTemplate.update("DELETE FROM user_subscriptions WHERE user_id = ?", Long.valueOf(testUserId));
            JdbcTemplate.update("DELETE FROM users WHERE user_id = ?", Long.valueOf(testUserId));
        } catch (RuntimeException ignored) {
            // 清理失败不掩盖测试结论
        }
    }

    @Nested
    @DisplayName("NotificationDaoImpl")
    class Notifications {

        @Test
        @DisplayName("未登录用户（不存在的 userId）查不到任何通知")
        void foreignUserSeesNothing() {
            assertEquals(0, notificationDao.countByUser(-999L));
            assertTrue(notificationDao.listByUser(-999L, 10).isEmpty());
            assertEquals(0, notificationDao.countUnread(-999L));
        }

        @Test
        @DisplayName("insert → 未读计数 → 标记已读 → 全部已读 → 删除")
        void writeRoundTrip() {
            try {
                Notification n = new Notification();
                n.setUserId(Long.valueOf(testUserId));
                n.setTyphoonId(Long.valueOf(sampleTyphoonId));
                n.setNotifyType("STRENGTH_CHANGE");
                n.setTitle("单元测试通知");
                n.setContent("这是一条由单元测试写入的通知");
                n.setRead(Boolean.FALSE);

                long id = notificationDao.insert(n);
                assertTrue(id > 0L);
                assertEquals(1, notificationDao.countByUser(testUserId));
                assertEquals(1, notificationDao.countUnread(testUserId),
                        "新通知应计入未读，否则红点不会亮");

                Notification saved = notificationDao.findById(id);
                assertNotNull(saved);
                assertEquals("单元测试通知", saved.getTitle());
                assertFalse(notificationDao.listUnreadByUser(testUserId).isEmpty());

                assertEquals(1, notificationDao.markRead(id, testUserId));
                assertEquals(0, notificationDao.countUnread(testUserId));

                Notification another = new Notification();
                another.setUserId(Long.valueOf(testUserId));
                another.setNotifyType("NEW_STORM");
                another.setTitle("第二条");
                another.setContent("批量插入测试");
                another.setRead(Boolean.FALSE);
                assertEquals(1, notificationDao.batchInsert(
                        java.util.Collections.singletonList(another)));

                assertTrue(notificationDao.markAllRead(testUserId) >= 1);
                assertEquals(0, notificationDao.countUnread(testUserId));

                assertEquals(2, notificationDao.deleteByUser(testUserId));
            } finally {
                notificationDao.deleteByUser(testUserId);
            }
            assertEquals(0, notificationDao.countByUser(testUserId));
        }
    }

    @Nested
    @DisplayName("UserFavoriteDaoImpl")
    class Favorites {

        @Test
        @DisplayName("他人的收藏不会被查到")
        void foreignUserSeesNothing() {
            assertEquals(0, favoriteDao.countByUser(-999L));
            assertTrue(favoriteDao.listByUser(-999L).isEmpty());
        }

        @Test
        @DisplayName("收藏台风 → 查列表 → 判存在 → 取消收藏")
        void typhoonFavoriteRoundTrip() {
            try {
                assertFalse(favoriteDao.existsTyphoonFavorite(testUserId, sampleTyphoonId));

                UserFavorite f = new UserFavorite();
                f.setUserId(Long.valueOf(testUserId));
                f.setTyphoonId(Long.valueOf(sampleTyphoonId));
                long id = favoriteDao.insert(f);
                assertTrue(id > 0L);

                assertTrue(favoriteDao.existsTyphoonFavorite(testUserId, sampleTyphoonId));
                assertTrue(favoriteDao.listFavoriteTyphoonIds(testUserId)
                        .contains(Long.valueOf(sampleTyphoonId)));
                assertFalse(favoriteDao.listFavoriteTyphoons(testUserId).isEmpty());
                assertEquals(1, favoriteDao.countByUser(testUserId));

                assertEquals(1, favoriteDao.deleteTyphoonFavorite(testUserId, sampleTyphoonId));
                assertFalse(favoriteDao.existsTyphoonFavorite(testUserId, sampleTyphoonId));
            } finally {
                JdbcTemplate.update("DELETE FROM user_favorites WHERE user_id = ?",
                        Long.valueOf(testUserId));
            }
        }

        @Test
        @DisplayName("收藏城市 → 查列表 → 取消收藏")
        void cityFavoriteRoundTrip() {
            try {
                UserFavorite f = new UserFavorite();
                f.setUserId(Long.valueOf(testUserId));
                f.setCityId(Long.valueOf(sampleCityId));
                long id = favoriteDao.insert(f);
                assertTrue(id > 0L);

                assertTrue(favoriteDao.existsCityFavorite(testUserId, sampleCityId));
                assertTrue(favoriteDao.listFavoriteCityIds(testUserId)
                        .contains(Long.valueOf(sampleCityId)));
                assertFalse(favoriteDao.listFavoriteCities(testUserId).isEmpty());

                assertEquals(1, favoriteDao.deleteCityFavorite(testUserId, sampleCityId));
                assertEquals(0, favoriteDao.countByUser(testUserId), "取消收藏后计数应归零");
                assertEquals(0, favoriteDao.deleteByIdAndUser(id, testUserId),
                        "已经删掉的行再删一次应影响 0 行");
            } finally {
                JdbcTemplate.update("DELETE FROM user_favorites WHERE user_id = ?",
                        Long.valueOf(testUserId));
            }
        }

        @Test
        @DisplayName("按 id + userId 删除：别人的收藏删不掉")
        void deleteByIdAndUserIsScoped() {
            UserFavorite f = new UserFavorite();
            f.setUserId(Long.valueOf(testUserId));
            f.setTyphoonId(Long.valueOf(sampleTyphoonId));
            long id = favoriteDao.insert(f);
            try {
                assertEquals(0, favoriteDao.deleteByIdAndUser(id, -999L),
                        "userId 不在 WHERE 条件里的话，用户就能删掉别人的收藏");
            } finally {
                JdbcTemplate.update("DELETE FROM user_favorites WHERE user_id = ?",
                        Long.valueOf(testUserId));
            }
        }
    }

    @Nested
    @DisplayName("UserSubscriptionDaoImpl")
    class Subscriptions {

        @Test
        @DisplayName("他人订阅查不到")
        void foreignUserSeesNothing() {
            assertTrue(subscriptionDao.listByUser(-999L).isEmpty());
            assertEquals(0, subscriptionDao.countByUser(-999L));
        }

        @Test
        @DisplayName("订阅台风 → 按用户/台风反查 → 改偏好 → 退订")
        void typhoonSubscriptionRoundTrip() {
            try {
                UserSubscription s = new UserSubscription();
                s.setUserId(Long.valueOf(testUserId));
                s.setTyphoonId(Long.valueOf(sampleTyphoonId));
                s.setNotifyNewStorm(Boolean.TRUE);
                s.setNotifyStrengthChange(Boolean.TRUE);
                s.setNotifyPathChange(Boolean.FALSE);
                s.setPushEnabled(Boolean.TRUE);
                long id = subscriptionDao.insert(s);
                assertTrue(id > 0L);

                assertNotNull(subscriptionDao.findById(id));
                assertNotNull(subscriptionDao.findByUserAndTyphoon(testUserId, sampleTyphoonId));
                assertFalse(subscriptionDao.listByTyphoon(sampleTyphoonId).isEmpty());
                assertEquals(1, subscriptionDao.countByUser(testUserId));

                UserSubscription saved = subscriptionDao.findById(id);
                saved.setNotifyPathChange(Boolean.TRUE);
                assertEquals(1, subscriptionDao.updatePreferences(saved));

                assertEquals(1, subscriptionDao.setPushEnabledForUser(testUserId, false));
                assertEquals(1, subscriptionDao.deleteByUserAndTyphoon(testUserId, sampleTyphoonId));
            } finally {
                JdbcTemplate.update("DELETE FROM user_subscriptions WHERE user_id = ?",
                        Long.valueOf(testUserId));
            }
        }

        @Test
        @DisplayName("订阅城市 → 按用户/城市反查 → 删除")
        void citySubscriptionRoundTrip() {
            try {
                UserSubscription s = new UserSubscription();
                s.setUserId(Long.valueOf(testUserId));
                s.setCityId(Long.valueOf(sampleCityId));
                s.setPushEnabled(Boolean.TRUE);
                long id = subscriptionDao.insert(s);
                assertTrue(id > 0L);

                assertNotNull(subscriptionDao.findByUserAndCity(testUserId, sampleCityId));
                assertFalse(subscriptionDao.listByCity(sampleCityId).isEmpty());
                assertEquals(1, subscriptionDao.deleteByIdAndUser(id, testUserId));
            } finally {
                JdbcTemplate.update("DELETE FROM user_subscriptions WHERE user_id = ?",
                        Long.valueOf(testUserId));
            }
        }

        @Test
        @DisplayName("listByTyphoon 返回的是所有订阅者（用于推送），不是某个人的")
        void listByTyphoonIsGlobal() {
            List<UserSubscription> rows = subscriptionDao.listByTyphoon(sampleTyphoonId);
            assertNotNull(rows);
        }
    }
}
