package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.NotificationDao;
import com.zfz.typhoon.dao.UserDao;
import com.zfz.typhoon.dao.UserSubscriptionDao;
import com.zfz.typhoon.model.Notification;
import com.zfz.typhoon.model.User;
import com.zfz.typhoon.model.UserSubscription;
import com.zfz.typhoon.model.vo.NotificationVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NotificationServiceImpl} 单元测试。
 *
 * <p>本服务是"通知分发"的核心：把一次台风变化按订阅关系扇形分发出去，
 * 并依赖去重窗口避免把通知面板刷爆。测试重点覆盖：
 * <ol>
 *   <li><b>分发范围</b>——只发给订阅者，且要按推送开关与类型偏好过滤；</li>
 *   <li><b>去重窗口</b>——窗口内不重复发同台风同类型通知；</li>
 *   <li><b>系统消息</b>——数据源同步失败只发给管理员。</li>
 * </ol>
 *
 * @author 追风者项目组
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationServiceImpl · 通知分发")
class NotificationServiceImplTest {

    @Mock
    private NotificationDao notificationDao;

    @Mock
    private UserSubscriptionDao subscriptionDao;

    @Mock
    private UserDao userDao;

    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(notificationDao, subscriptionDao, userDao);
    }

    /* ------------------------------------------------------------------ */
    /*  测试数据构造                                                        */
    /* ------------------------------------------------------------------ */

    /** 构造一条"订阅了某台风且开关全开"的订阅。 */
    private UserSubscription sub(Long userId, long typhoonId) {
        UserSubscription s = new UserSubscription();
        s.setUserId(userId);
        s.setTyphoonId(Long.valueOf(typhoonId));
        return s;
    }

    private Notification notification(String type, Long typhoonId, LocalDateTime createdAt) {
        Notification n = new Notification();
        n.setNotifyType(type);
        n.setTyphoonId(typhoonId);
        n.setCreatedAt(createdAt);
        return n;
    }

    /* ==================================================================== */
    /*  只读查询                                                            */
    /* ==================================================================== */

    @Nested
    @DisplayName("list · 通知列表")
    class ListNotifications {

        @Test
        @DisplayName("正常转换：实体逐条转成视图对象")
        void convertsRows() {
            Notification n = notification(Notification.TYPE_NEW_STORM, Long.valueOf(1L), LocalDateTime.now());
            n.setNotificationId(Long.valueOf(9L));
            n.setTitle("新台风生成");
            n.setContent("内容");
            Mockito.when(notificationDao.listByUser(5L, 20)).thenReturn(Collections.singletonList(n));

            List<NotificationVO> list = service.list(5L, 20);

            assertEquals(1, list.size());
            assertEquals(Long.valueOf(9L), list.get(0).getId());
            assertEquals(Notification.TYPE_NEW_STORM, list.get(0).getType());
        }

        @Test
        @DisplayName("limit<=0 时回退到默认 20 条")
        void defaultLimitWhenNonPositive() {
            Mockito.when(notificationDao.listByUser(5L, 20)).thenReturn(new ArrayList<Notification>());

            assertTrue(service.list(5L, 0).isEmpty());
            assertTrue(service.list(5L, -3).isEmpty());
        }

        @Test
        @DisplayName("limit 超过 100 时被截断为 100，防止一次拉满内存")
        void capsAtMaxLimit() {
            Mockito.when(notificationDao.listByUser(5L, 100)).thenReturn(new ArrayList<Notification>());

            assertTrue(service.list(5L, 100000).isEmpty());
        }

        @Test
        @DisplayName("DAO 返回空列表时结果为空且不抛异常")
        void emptyRows() {
            Mockito.when(notificationDao.listByUser(5L, 5)).thenReturn(Collections.<Notification>emptyList());

            assertTrue(service.list(5L, 5).isEmpty());
        }
    }

    @Nested
    @DisplayName("countUnread / markRead / markAllRead · 已读状态")
    class ReadState {

        @Test
        @DisplayName("未读数与标记均已读都直接委托 DAO")
        void delegates() {
            Mockito.when(notificationDao.countUnread(5L)).thenReturn(3);
            Mockito.when(notificationDao.markRead(7L, 5L)).thenReturn(1);
            Mockito.when(notificationDao.markAllRead(5L)).thenReturn(4);

            assertEquals(3, service.countUnread(5L));
            // WHERE 里带 user_id，天然防越权；0 表示不属于该用户
            assertEquals(1, service.markRead(7L, 5L));
            assertEquals(4, service.markAllRead(5L));
        }
    }

    /* ==================================================================== */
    /*  按订阅分发的三类通知                                                 */
    /* ==================================================================== */

    @Nested
    @DisplayName("notifyStrengthChange · 强度变化")
    class StrengthChange {

        @Test
        @DisplayName("增强：标题含起止级别，正文提示关注预警")
        void intensifies() {
            Mockito.when(subscriptionDao.listByTyphoon(1L))
                    .thenReturn(Collections.singletonList(sub(Long.valueOf(11L), 1L)));
            Mockito.when(notificationDao.listByUser(11L, 100)).thenReturn(new ArrayList<Notification>());
            Mockito.when(notificationDao.batchInsert(Mockito.anyList())).thenReturn(1);

            int n = service.notifyStrengthChange(1L, "海葵", 10, 14);

            assertEquals(1, n);
            ArgumentCaptor<List<Notification>> captor = listCaptor();
            Mockito.verify(notificationDao).batchInsert(captor.capture());
            Notification sent = captor.getValue().get(0);
            assertEquals("强度变化：海葵 由 10 级变为 14 级", sent.getTitle());
            assertTrue(sent.getContent().contains("强度增强"));
            assertEquals(Notification.TYPE_STRENGTH_CHANGE, sent.getNotifyType());
        }

        @Test
        @DisplayName("减弱：正文切换到减弱文案")
        void weakens() {
            Mockito.when(subscriptionDao.listByTyphoon(1L))
                    .thenReturn(Collections.singletonList(sub(Long.valueOf(11L), 1L)));
            Mockito.when(notificationDao.listByUser(11L, 100)).thenReturn(new ArrayList<Notification>());
            Mockito.when(notificationDao.batchInsert(Mockito.anyList())).thenReturn(1);

            service.notifyStrengthChange(1L, "海葵", 14, 10);

            ArgumentCaptor<List<Notification>> captor = listCaptor();
            Mockito.verify(notificationDao).batchInsert(captor.capture());
            assertTrue(captor.getValue().get(0).getContent().contains("强度减弱"));
        }

        @Test
        @DisplayName("台风名为 null / 空白时用「该台风」兜底")
        void blankNameFallback() {
            Mockito.when(subscriptionDao.listByTyphoon(1L))
                    .thenReturn(Collections.singletonList(sub(Long.valueOf(11L), 1L)));
            Mockito.when(notificationDao.listByUser(11L, 100)).thenReturn(new ArrayList<Notification>());
            Mockito.when(notificationDao.batchInsert(Mockito.anyList())).thenReturn(1);

            service.notifyStrengthChange(1L, "   ", 10, 8);

            ArgumentCaptor<List<Notification>> captor = listCaptor();
            Mockito.verify(notificationDao).batchInsert(captor.capture());
            assertEquals("强度变化：该台风 由 10 级变为 8 级", captor.getValue().get(0).getTitle());
        }
    }

    @Nested
    @DisplayName("notifyPathChange / notifyNewStorm · 其它事件")
    class OtherEvents {

        @Test
        @DisplayName("路径分歧：标题与正文包含分歧指数百分比")
        void pathChange() {
            Mockito.when(subscriptionDao.listByTyphoon(2L))
                    .thenReturn(Collections.singletonList(sub(Long.valueOf(21L), 2L)));
            Mockito.when(notificationDao.listByUser(21L, 100)).thenReturn(new ArrayList<Notification>());
            Mockito.when(notificationDao.batchInsert(Mockito.anyList())).thenReturn(1);

            int n = service.notifyPathChange(2L, "杜苏芮", 0.7341, "偏大");

            assertEquals(1, n);
            ArgumentCaptor<List<Notification>> captor = listCaptor();
            Mockito.verify(notificationDao).batchInsert(captor.capture());
            Notification sent = captor.getValue().get(0);
            assertTrue(sent.getTitle().contains("各机构路径差异偏大"));
            assertTrue(sent.getContent().contains("73%"));
            assertEquals(Notification.TYPE_PATH_CHANGE, sent.getNotifyType());
        }

        @Test
        @DisplayName("新台风：类型为 NEW_STORM")
        void newStorm() {
            Mockito.when(subscriptionDao.listByTyphoon(3L))
                    .thenReturn(Collections.singletonList(sub(Long.valueOf(31L), 3L)));
            Mockito.when(notificationDao.listByUser(31L, 100)).thenReturn(new ArrayList<Notification>());
            Mockito.when(notificationDao.batchInsert(Mockito.anyList())).thenReturn(1);

            service.notifyNewStorm(3L, "苏拉");

            ArgumentCaptor<List<Notification>> captor = listCaptor();
            Mockito.verify(notificationDao).batchInsert(captor.capture());
            assertEquals("新台风生成：苏拉", captor.getValue().get(0).getTitle());
            assertEquals(Notification.TYPE_NEW_STORM, captor.getValue().get(0).getNotifyType());
        }
    }

    @Nested
    @DisplayName("fanOut · 订阅过滤与去重")
    class FanOut {

        @Test
        @DisplayName("无人订阅该台风：不发任何通知")
        void noSubscribers() {
            Mockito.when(subscriptionDao.listByTyphoon(1L)).thenReturn(new ArrayList<UserSubscription>());

            assertEquals(0, service.notifyNewStorm(1L, "海葵"));
            Mockito.verify(notificationDao, Mockito.never()).batchInsert(Mockito.anyList());
        }

        @Test
        @DisplayName("userId 为空、或关闭推送的订阅被跳过")
        void skipsInvalidSubscribers() {
            UserSubscription noUser = sub(null, 1L);
            UserSubscription pushOff = sub(Long.valueOf(12L), 1L);
            pushOff.setPushEnabled(Boolean.FALSE);
            Mockito.when(subscriptionDao.listByTyphoon(1L)).thenReturn(Arrays.asList(noUser, pushOff));

            assertEquals(0, service.notifyNewStorm(1L, "海葵"));
            Mockito.verify(notificationDao, Mockito.never()).batchInsert(Mockito.anyList());
        }

        @Test
        @DisplayName("未开启「新台风提醒」的订阅收不到新台风通知")
        void filtersByPreference() {
            UserSubscription off = sub(Long.valueOf(12L), 1L);
            off.setNotifyNewStorm(Boolean.FALSE);
            Mockito.when(subscriptionDao.listByTyphoon(1L)).thenReturn(Collections.singletonList(off));

            assertEquals(0, service.notifyNewStorm(1L, "海葵"));
            Mockito.verify(notificationDao, Mockito.never()).batchInsert(Mockito.anyList());
        }

        @Test
        @DisplayName("同一用户的多条订阅只发一条（去重）")
        void deduplicatesRecipients() {
            Mockito.when(subscriptionDao.listByTyphoon(1L)).thenReturn(Arrays.asList(
                    sub(Long.valueOf(11L), 1L), sub(Long.valueOf(11L), 1L)));
            Mockito.when(notificationDao.listByUser(11L, 100)).thenReturn(new ArrayList<Notification>());
            Mockito.when(notificationDao.batchInsert(Mockito.anyList())).thenReturn(1);

            service.notifyNewStorm(1L, "海葵");

            ArgumentCaptor<List<Notification>> captor = listCaptor();
            Mockito.verify(notificationDao).batchInsert(captor.capture());
            assertEquals(1, captor.getValue().size());
        }

        @Test
        @DisplayName("2 小时窗口内已发过同台风同类型通知：本轮静默")
        void deduplicatedWithinWindow() {
            Mockito.when(subscriptionDao.listByTyphoon(1L))
                    .thenReturn(Collections.singletonList(sub(Long.valueOf(11L), 1L)));
            Notification recent = notification(Notification.TYPE_NEW_STORM, Long.valueOf(1L),
                    LocalDateTime.now().minusMinutes(30));
            Mockito.when(notificationDao.listByUser(11L, 100)).thenReturn(Collections.singletonList(recent));

            assertEquals(0, service.notifyNewStorm(1L, "海葵"));
            Mockito.verify(notificationDao, Mockito.never()).batchInsert(Mockito.anyList());
        }

        @Test
        @DisplayName("超过窗口的旧通知不触发静默")
        void oldNotificationDoesNotSilence() {
            Mockito.when(subscriptionDao.listByTyphoon(1L))
                    .thenReturn(Collections.singletonList(sub(Long.valueOf(11L), 1L)));
            Notification old = notification(Notification.TYPE_NEW_STORM, Long.valueOf(1L),
                    LocalDateTime.now().minusHours(5));
            Mockito.when(notificationDao.listByUser(11L, 100)).thenReturn(Collections.singletonList(old));
            Mockito.when(notificationDao.batchInsert(Mockito.anyList())).thenReturn(1);

            assertEquals(1, service.notifyNewStorm(1L, "海葵"));
        }

        @Test
        @DisplayName("类型不同 / 台风不同 / typhoonId 为空的历史通知都不算重复")
        void nonMatchingHistoryIgnored() {
            Mockito.when(subscriptionDao.listByTyphoon(1L))
                    .thenReturn(Collections.singletonList(sub(Long.valueOf(11L), 1L)));
            List<Notification> history = Arrays.asList(
                    notification(Notification.TYPE_STRENGTH_CHANGE, Long.valueOf(1L), LocalDateTime.now()),
                    notification(Notification.TYPE_NEW_STORM, Long.valueOf(2L), LocalDateTime.now()),
                    notification(Notification.TYPE_NEW_STORM, null, LocalDateTime.now()));
            Mockito.when(notificationDao.listByUser(11L, 100)).thenReturn(history);
            Mockito.when(notificationDao.batchInsert(Mockito.anyList())).thenReturn(1);

            assertEquals(1, service.notifyNewStorm(1L, "海葵"));
        }

        @Test
        @DisplayName("写入批次中的通知字段完整：接收者、台风、类型、时间、未读")
        void batchFieldsAreComplete() {
            Mockito.when(subscriptionDao.listByTyphoon(1L))
                    .thenReturn(Collections.singletonList(sub(Long.valueOf(11L), 1L)));
            Mockito.when(notificationDao.listByUser(11L, 100)).thenReturn(new ArrayList<Notification>());
            Mockito.when(notificationDao.batchInsert(Mockito.anyList())).thenReturn(1);

            service.notifyNewStorm(1L, "海葵");

            ArgumentCaptor<List<Notification>> captor = listCaptor();
            Mockito.verify(notificationDao).batchInsert(captor.capture());
            Notification sent = captor.getValue().get(0);
            assertEquals(Long.valueOf(11L), sent.getUserId());
            assertEquals(Long.valueOf(1L), sent.getTyphoonId());
            assertEquals(Boolean.FALSE, sent.getRead());
            assertNotNull(sent.getCreatedAt());
        }
    }

    /* ==================================================================== */
    /*  系统消息                                                            */
    /* ==================================================================== */

    @Nested
    @DisplayName("notifySyncFailure · 数据源同步失败")
    class SyncFailure {

        @Test
        @DisplayName("没有管理员时返回 0，不写库")
        void noAdmins() {
            Mockito.when(userDao.listByRole(User.ROLE_ADMIN)).thenReturn(new ArrayList<User>());

            assertEquals(0, service.notifySyncFailure("CMA", "连接超时"));
            Mockito.verify(notificationDao, Mockito.never()).batchInsert(Mockito.anyList());
        }

        @Test
        @DisplayName("逐个管理员生成系统消息，且不带台风/城市关联")
        void fanOutToAdmins() {
            Mockito.when(userDao.listByRole(User.ROLE_ADMIN))
                    .thenReturn(Arrays.asList(admin(1L), admin(2L)));
            Mockito.when(notificationDao.batchInsert(Mockito.anyList())).thenReturn(2);

            int n = service.notifySyncFailure("CMA", "连接超时");

            assertEquals(2, n);
            ArgumentCaptor<List<Notification>> captor = listCaptor();
            Mockito.verify(notificationDao).batchInsert(captor.capture());
            assertEquals(2, captor.getValue().size());
            Notification sent = captor.getValue().get(0);
            assertEquals(Notification.TYPE_SYSTEM, sent.getNotifyType());
            assertEquals(null, sent.getTyphoonId());
            assertTrue(sent.getContent().contains("连接超时"));
            assertEquals("数据源同步失败：CMA", sent.getTitle());
        }

        @Test
        @DisplayName("失败原因为空时用「原因未知」兜底")
        void blankMessageFallback() {
            Mockito.when(userDao.listByRole(User.ROLE_ADMIN))
                    .thenReturn(Collections.singletonList(admin(1L)));
            Mockito.when(notificationDao.batchInsert(Mockito.anyList())).thenReturn(1);

            service.notifySyncFailure("CMA", "   ");

            ArgumentCaptor<List<Notification>> captor = listCaptor();
            Mockito.verify(notificationDao).batchInsert(captor.capture());
            assertTrue(captor.getValue().get(0).getContent().contains("原因未知"));
        }

        private User admin(long id) {
            User u = new User();
            u.setUserId(Long.valueOf(id));
            u.setRole(User.ROLE_ADMIN);
            return u;
        }
    }

    /* ==================================================================== */
    /*  push                                                                */
    /* ==================================================================== */

    @Nested
    @DisplayName("push · 直接写入一条通知")
    class Push {

        @Test
        @DisplayName("补齐默认时间与未读标记后写库")
        void fillsDefaults() {
            Notification n = new Notification();
            Mockito.when(notificationDao.insert(n)).thenReturn(88L);

            long id = service.push(n);

            assertEquals(88L, id);
            assertNotNull(n.getCreatedAt());
            assertEquals(Boolean.FALSE, n.getRead());
        }

        @Test
        @DisplayName("已有时间与已读状态时不覆盖调用方传入的值")
        void keepsProvidedValues() {
            LocalDateTime when = LocalDateTime.of(2026, 9, 1, 8, 0, 0);
            Notification n = new Notification();
            n.setCreatedAt(when);
            n.setRead(Boolean.TRUE);
            Mockito.when(notificationDao.insert(n)).thenReturn(1L);

            service.push(n);

            assertEquals(when, n.getCreatedAt());
            assertEquals(Boolean.TRUE, n.getRead());
        }
    }

    /* ------------------------------------------------------------------ */

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<Notification>> listCaptor() {
        return ArgumentCaptor.forClass((Class<List<Notification>>) (Class<?>) List.class);
    }

    /** 说明：{@link Notification#getRead()} 默认 false 语义由本服务保证。 */
    @Test
    @DisplayName("内部 newNotification 生成的实体默认为未读（经由分发间接验证）")
    void generatedAlwaysUnread() {
        Mockito.when(subscriptionDao.listByTyphoon(1L))
                .thenReturn(Collections.singletonList(sub(Long.valueOf(11L), 1L)));
        Mockito.when(notificationDao.listByUser(11L, 100)).thenReturn(new ArrayList<Notification>());
        Mockito.when(notificationDao.batchInsert(Mockito.anyList())).thenReturn(1);

        service.notifyNewStorm(1L, "海葵");

        ArgumentCaptor<List<Notification>> captor = listCaptor();
        Mockito.verify(notificationDao).batchInsert(captor.capture());
        assertFalse(captor.getValue().get(0).getRead().booleanValue());
    }
}
