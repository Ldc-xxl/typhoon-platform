package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.UserSubscriptionDao;
import com.zfz.typhoon.model.UserSubscription;
import com.zfz.typhoon.service.CityService;
import com.zfz.typhoon.service.TyphoonService;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SubscriptionServiceImpl} 单元测试。
 *
 * <p>订阅表是"通知分发"的路由表，写错会导致用户收不到（或收到过多）通知，
 * 因此本类重点验证：
 * <ol>
 *   <li><b>外键前置校验</b>——台风 / 城市不存在时抛可读的 400，而非 500；</li>
 *   <li><b>幂等</b>——重复订阅同一天不新增记录，而是更新偏好；</li>
 *   <li><b>越权防护</b>——操作他人订阅时抛 403。</li>
 * </ol>
 *
 * @author 追风者项目组
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionServiceImpl · 订阅业务")
class SubscriptionServiceImplTest {

    @Mock
    private UserSubscriptionDao subscriptionDao;

    @Mock
    private TyphoonService typhoonService;

    @Mock
    private CityService cityService;

    private SubscriptionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionServiceImpl(subscriptionDao, typhoonService, cityService);
    }

    /* ------------------------------------------------------------------ */

    private UserSubscription sub(Long userId, Long typhoonId, Long cityId) {
        UserSubscription s = new UserSubscription();
        s.setUserId(userId);
        s.setTyphoonId(typhoonId);
        s.setCityId(cityId);
        return s;
    }

    private static void assertBusinessError(int code, String part, Runnable action) {
        BusinessException e = assertThrows(BusinessException.class, action::run);
        assertEquals(code, e.getCode(), "业务码不符：" + e.getMessage());
        assertTrue(e.getMessage() != null && e.getMessage().contains(part),
                "提示应包含「" + part + "」，实际：" + e.getMessage());
    }

    /* ==================================================================== */
    /*  查询                                                                */
    /* ==================================================================== */

    @Nested
    @DisplayName("list / listByTyphoon · 列表查询")
    class Queries {

        @Test
        @DisplayName("按用户查询直接委托 DAO")
        void listByUser() {
            ArrayList<UserSubscription> rows = new ArrayList<UserSubscription>();
            rows.add(sub(Long.valueOf(1L), Long.valueOf(2L), null));
            Mockito.when(subscriptionDao.listByUser(1L)).thenReturn(rows);

            assertEquals(1, service.list(1L).size());
        }

        @Test
        @DisplayName("按台风查询直接委托 DAO")
        void listByTyphoon() {
            Mockito.when(subscriptionDao.listByTyphoon(2L))
                    .thenReturn(Collections.singletonList(sub(Long.valueOf(1L), Long.valueOf(2L), null)));

            assertEquals(1, service.listByTyphoon(2L).size());
        }
    }

    /* ==================================================================== */
    /*  subscribe                                                           */
    /* ==================================================================== */

    @Nested
    @DisplayName("subscribe · 新建订阅")
    class Subscribe {

        @Test
        @DisplayName("订阅对象为 null 抛 400")
        void nullSubscription() {
            assertBusinessError(400, "缺少 userId", () -> service.subscribe(null));
        }

        @Test
        @DisplayName("缺少 userId 抛 400")
        void missingUserId() {
            assertBusinessError(400, "缺少 userId",
                    () -> service.subscribe(sub(null, Long.valueOf(2L), null)));
        }

        @Test
        @DisplayName("typhoonId 与 cityId 都为空抛 400")
        void neitherTarget() {
            assertBusinessError(400, "至少填一个",
                    () -> service.subscribe(sub(Long.valueOf(1L), null, null)));
        }

        @Test
        @DisplayName("订阅台风：先校验台风存在，再插入并补齐默认偏好")
        void subscribeTyphoon() {
            Mockito.when(subscriptionDao.findByUserAndTyphoon(1L, 2L)).thenReturn(null);
            Mockito.when(subscriptionDao.insert(Mockito.any(UserSubscription.class))).thenReturn(77L);

            long id = service.subscribe(sub(Long.valueOf(1L), Long.valueOf(2L), null));

            assertEquals(77L, id);
            Mockito.verify(typhoonService).requireTyphoon(2L);
            ArgumentCaptor<UserSubscription> captor =
                    ArgumentCaptor.forClass(UserSubscription.class);
            Mockito.verify(subscriptionDao).insert(captor.capture());
            UserSubscription saved = captor.getValue();
            assertEquals(Boolean.TRUE, saved.getNotifyNewStorm());
            assertEquals(Boolean.TRUE, saved.getNotifyStrengthChange());
            assertEquals(Boolean.TRUE, saved.getNotifyPathChange());
            assertEquals(Boolean.TRUE, saved.getPushEnabled());
            assertNotNull(saved.getCreatedAt());
        }

        @Test
        @DisplayName("订阅城市：校验城市存在，且不校验台风")
        void subscribeCity() {
            Mockito.when(subscriptionDao.findByUserAndCity(1L, 3L)).thenReturn(null);
            Mockito.when(subscriptionDao.insert(Mockito.any(UserSubscription.class))).thenReturn(9L);

            assertEquals(9L, service.subscribe(sub(Long.valueOf(1L), null, Long.valueOf(3L))));
            Mockito.verify(cityService).requireCity(3L);
            Mockito.verify(typhoonService, Mockito.never()).requireTyphoon(Mockito.anyLong());
        }

        @Test
        @DisplayName("已订阅时视为更新偏好，返回既有订阅ID，不再插入")
        void idempotentUpdate() {
            UserSubscription exists = sub(Long.valueOf(1L), Long.valueOf(2L), null);
            exists.setSubscriptionId(Long.valueOf(50L));
            Mockito.when(subscriptionDao.findByUserAndTyphoon(1L, 2L)).thenReturn(exists);
            Mockito.when(subscriptionDao.updatePreferences(exists)).thenReturn(1);

            UserSubscription incoming = sub(Long.valueOf(1L), Long.valueOf(2L), null);
            incoming.setNotifyPathChange(Boolean.FALSE);

            assertEquals(50L, service.subscribe(incoming));
            assertEquals(Boolean.FALSE, exists.getNotifyPathChange(),
                    "本次提交的偏好应复制到既有记录上");
            Mockito.verify(subscriptionDao, Mockito.never()).insert(Mockito.any(UserSubscription.class));
        }

        @Test
        @DisplayName("已订阅但本次未提交任何偏好：既有值保持不变")
        void idempotentKeepsExistingPreferences() {
            UserSubscription exists = sub(Long.valueOf(1L), Long.valueOf(2L), null);
            exists.setSubscriptionId(Long.valueOf(50L));
            exists.setNotifyNewStorm(Boolean.FALSE);
            Mockito.when(subscriptionDao.findByUserAndTyphoon(1L, 2L)).thenReturn(exists);
            Mockito.when(subscriptionDao.updatePreferences(exists)).thenReturn(1);

            service.subscribe(sub(Long.valueOf(1L), Long.valueOf(2L), null));

            assertEquals(Boolean.FALSE, exists.getNotifyNewStorm());
        }
    }

    /* ==================================================================== */
    /*  updatePreferences / unsubscribe                                     */
    /* ==================================================================== */

    @Nested
    @DisplayName("updatePreferences · 更新偏好")
    class UpdatePreferences {

        @Test
        @DisplayName("缺少 subscriptionId 抛 400")
        void missingId() {
            assertBusinessError(400, "缺少 subscriptionId",
                    () -> service.updatePreferences(sub(Long.valueOf(1L), Long.valueOf(2L), null)));
        }

        @Test
        @DisplayName("缺少 userId 抛 400")
        void missingUserId() {
            UserSubscription s = sub(null, Long.valueOf(2L), null);
            s.setSubscriptionId(Long.valueOf(50L));
            assertBusinessError(400, "缺少 subscriptionId", () -> service.updatePreferences(s));
        }

        @Test
        @DisplayName("订阅不存在抛 404")
        void notFound() {
            UserSubscription s = sub(Long.valueOf(1L), Long.valueOf(2L), null);
            s.setSubscriptionId(Long.valueOf(50L));
            Mockito.when(subscriptionDao.findById(50L)).thenReturn(null);

            assertBusinessError(404, "订阅不存在", () -> service.updatePreferences(s));
        }

        @Test
        @DisplayName("操作他人订阅抛 403")
        void forbidden() {
            UserSubscription s = sub(Long.valueOf(1L), Long.valueOf(2L), null);
            s.setSubscriptionId(Long.valueOf(50L));
            UserSubscription ownedByOther = sub(Long.valueOf(99L), Long.valueOf(2L), null);
            Mockito.when(subscriptionDao.findById(50L)).thenReturn(ownedByOther);

            assertBusinessError(403, "无权操作", () -> service.updatePreferences(s));
        }

        @Test
        @DisplayName("归属对象不可变：台风/城市ID以库中记录为准")
        void targetIsImmutable() {
            UserSubscription s = sub(Long.valueOf(1L), Long.valueOf(999L), null);
            s.setSubscriptionId(Long.valueOf(50L));
            UserSubscription exists = sub(Long.valueOf(1L), Long.valueOf(2L), null);
            Mockito.when(subscriptionDao.findById(50L)).thenReturn(exists);
            Mockito.when(subscriptionDao.updatePreferences(s)).thenReturn(1);

            assertEquals(1, service.updatePreferences(s));
            assertEquals(Long.valueOf(2L), s.getTyphoonId(), "应以库中台风ID覆盖客户端传入值");
            assertEquals(Boolean.TRUE, s.getPushEnabled());
        }
    }

    @Nested
    @DisplayName("unsubscribe / setPushEnabled · 取消与推送开关")
    class Unsubscribe {

        @Test
        @DisplayName("取消订阅前校验归属，存在则删除")
        void unsubscribeOk() {
            Mockito.when(subscriptionDao.findById(50L)).thenReturn(sub(Long.valueOf(1L), Long.valueOf(2L), null));
            Mockito.when(subscriptionDao.deleteByIdAndUser(50L, 1L)).thenReturn(1);

            assertEquals(1, service.unsubscribe(50L, 1L));
        }

        @Test
        @DisplayName("取消不存在的订阅抛 404")
        void unsubscribeNotFound() {
            Mockito.when(subscriptionDao.findById(50L)).thenReturn(null);
            assertBusinessError(404, "订阅不存在", () -> service.unsubscribe(50L, 1L));
        }

        @Test
        @DisplayName("取消他人订阅抛 403")
        void unsubscribeForbidden() {
            Mockito.when(subscriptionDao.findById(50L)).thenReturn(sub(Long.valueOf(9L), Long.valueOf(2L), null));
            assertBusinessError(403, "无权操作", () -> service.unsubscribe(50L, 1L));
        }

        @Test
        @DisplayName("批量开关推送直接委托 DAO")
        void setPushEnabled() {
            Mockito.when(subscriptionDao.setPushEnabledForUser(1L, false)).thenReturn(3);
            assertEquals(3, service.setPushEnabled(1L, false));
        }
    }

    /* ==================================================================== */
    /*  toggleTyphoon                                                       */
    /* ==================================================================== */

    @Nested
    @DisplayName("toggleTyphoon · 一键订阅/取消")
    class Toggle {

        @Test
        @DisplayName("订阅：内部走 subscribe 流程并返回 1")
        void subscribe() {
            Mockito.when(subscriptionDao.findByUserAndTyphoon(1L, 2L)).thenReturn(null);
            Mockito.when(subscriptionDao.insert(Mockito.any(UserSubscription.class))).thenReturn(5L);

            assertEquals(1, service.toggleTyphoon(1L, 2L, true));
            Mockito.verify(typhoonService).requireTyphoon(2L);
            Mockito.verify(subscriptionDao).insert(Mockito.any(UserSubscription.class));
        }

        @Test
        @DisplayName("取消：直接按用户+台风删除")
        void unsubscribe() {
            Mockito.when(subscriptionDao.deleteByUserAndTyphoon(1L, 2L)).thenReturn(1);

            assertEquals(1, service.toggleTyphoon(1L, 2L, false));
            Mockito.verify(subscriptionDao, Mockito.never()).insert(Mockito.any(UserSubscription.class));
        }
    }

    @Test
    @DisplayName("业务码常量取自 Result：400/403/404 与约定一致")
    void codes() {
        assertEquals(400, Result.CODE_BAD_REQUEST);
        assertEquals(403, Result.CODE_FORBIDDEN);
        assertEquals(404, Result.CODE_NOT_FOUND);
    }
}
