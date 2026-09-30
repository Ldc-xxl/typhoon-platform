package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.UserFavoriteDao;
import com.zfz.typhoon.model.UserFavorite;
import com.zfz.typhoon.model.vo.CityVO;
import com.zfz.typhoon.model.vo.TyphoonVO;
import com.zfz.typhoon.service.CityService;
import com.zfz.typhoon.service.TyphoonService;
import com.zfz.typhoon.util.BusinessException;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FavoriteServiceImpl} 单元测试。
 *
 * <p>收藏列表是"快捷入口"，其可用性优先级高于单条数据的正确性：
 * 收藏目标被删除时只跳过该条，而不是让整个列表 500。本类重点验证：
 * <ol>
 *   <li><b>容错</b>——目标不存在 / 抛业务异常时静默跳过；</li>
 *   <li><b>幂等</b>——重复收藏返回既有收藏ID，不再新增；</li>
 *   <li><b>二选一外键</b>——收藏台风时城市外键必须留空，反之亦然。</li>
 * </ol>
 *
 * @author 追风者项目组
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FavoriteServiceImpl · 收藏业务")
class FavoriteServiceImplTest {

    @Mock
    private UserFavoriteDao favoriteDao;

    @Mock
    private TyphoonService typhoonService;

    @Mock
    private CityService cityService;

    private FavoriteServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FavoriteServiceImpl(favoriteDao, typhoonService, cityService);
    }

    /* ------------------------------------------------------------------ */

    private UserFavorite favorite(Long favoriteId, Long typhoonId, Long cityId) {
        UserFavorite f = new UserFavorite();
        f.setFavoriteId(favoriteId);
        f.setUserId(Long.valueOf(1L));
        f.setTyphoonId(typhoonId);
        f.setCityId(cityId);
        return f;
    }

    /* ==================================================================== */
    /*  列表                                                                */
    /* ==================================================================== */

    @Nested
    @DisplayName("listTyphoons · 收藏的台风列表")
    class ListTyphoons {

        @Test
        @DisplayName("逐条取视图，取不到的（已删除）静默跳过")
        void skipsDeleted() {
            Mockito.when(favoriteDao.listFavoriteTyphoonIds(1L))
                    .thenReturn(Arrays.asList(Long.valueOf(2L), Long.valueOf(3L)));
            Mockito.when(typhoonService.findById(2L)).thenReturn(new TyphoonVO());
            Mockito.when(typhoonService.findById(3L))
                    .thenThrow(BusinessException.notFound("台风不存在"));

            List<TyphoonVO> list = service.listTyphoons(1L);

            assertEquals(1, list.size());
        }

        @Test
        @DisplayName("返回 null 的条目也不会进列表")
        void skipsNullVo() {
            Mockito.when(favoriteDao.listFavoriteTyphoonIds(1L))
                    .thenReturn(Collections.singletonList(Long.valueOf(2L)));
            Mockito.when(typhoonService.findById(2L)).thenReturn(null);

            assertTrue(service.listTyphoons(1L).isEmpty());
        }

        @Test
        @DisplayName("没有任何收藏时返回空列表")
        void empty() {
            Mockito.when(favoriteDao.listFavoriteTyphoonIds(1L)).thenReturn(new ArrayList<Long>());

            assertTrue(service.listTyphoons(1L).isEmpty());
        }
    }

    @Nested
    @DisplayName("listCities · 收藏的城市列表")
    class ListCities {

        @Test
        @DisplayName("城市已被删除时跳过该条而不是整体失败")
        void skipsDeleted() {
            Mockito.when(favoriteDao.listFavoriteCityIds(1L))
                    .thenReturn(Arrays.asList(Long.valueOf(2L), Long.valueOf(3L)));
            Mockito.when(cityService.getVoById(2L)).thenReturn(new CityVO());
            Mockito.when(cityService.getVoById(3L))
                    .thenThrow(BusinessException.notFound("城市不存在：id=3"));

            assertEquals(1, service.listCities(1L).size());
        }

        @Test
        @DisplayName("空收藏返回空列表")
        void empty() {
            Mockito.when(favoriteDao.listFavoriteCityIds(1L)).thenReturn(new ArrayList<Long>());

            assertTrue(service.listCities(1L).isEmpty());
        }
    }

    /* ==================================================================== */
    /*  收藏状态判断                                                        */
    /* ==================================================================== */

    @Nested
    @DisplayName("isXxxFavorited · 状态判断")
    class Existence {

        @Test
        @DisplayName("台风与城市的收藏状态都委托 DAO")
        void delegates() {
            Mockito.when(favoriteDao.existsTyphoonFavorite(1L, 2L)).thenReturn(true);
            Mockito.when(favoriteDao.existsCityFavorite(1L, 3L)).thenReturn(false);

            assertTrue(service.isTyphoonFavorited(1L, 2L));
            assertEquals(false, service.isCityFavorited(1L, 3L));
        }
    }

    /* ==================================================================== */
    /*  新增收藏                                                            */
    /* ==================================================================== */

    @Nested
    @DisplayName("favoriteTyphoon · 收藏台风")
    class FavoriteTyphoon {

        @Test
        @DisplayName("未收藏：校验台风存在后插入，城市外键显式留空")
        void inserts() {
            Mockito.when(favoriteDao.existsTyphoonFavorite(1L, 2L)).thenReturn(false);
            Mockito.when(favoriteDao.insert(Mockito.any(UserFavorite.class))).thenReturn(66L);

            assertEquals(66L, service.favoriteTyphoon(1L, 2L));
            Mockito.verify(typhoonService).requireTyphoon(2L);
            ArgumentCaptor<UserFavorite> captor = ArgumentCaptor.forClass(UserFavorite.class);
            Mockito.verify(favoriteDao).insert(captor.capture());
            assertEquals(Long.valueOf(1L), captor.getValue().getUserId());
            assertEquals(Long.valueOf(2L), captor.getValue().getTyphoonId());
            assertNull(captor.getValue().getCityId(), "收藏台风时城市外键必须留空");
            assertTrue(captor.getValue().getCreatedAt() != null);
        }

        @Test
        @DisplayName("已收藏：幂等返回既有收藏ID，不重复插入")
        void idempotent() {
            Mockito.when(favoriteDao.existsTyphoonFavorite(1L, 2L)).thenReturn(true);
            Mockito.when(favoriteDao.listFavoriteTyphoons(1L))
                    .thenReturn(Collections.singletonList(favorite(Long.valueOf(70L), Long.valueOf(2L), null)));

            assertEquals(70L, service.favoriteTyphoon(1L, 2L));
            Mockito.verify(favoriteDao, Mockito.never()).insert(Mockito.any(UserFavorite.class));
        }

        @Test
        @DisplayName("已收藏但列表里找不到匹配（数据不一致）：返回 0 而不抛异常")
        void idempotentNoMatch() {
            Mockito.when(favoriteDao.existsTyphoonFavorite(1L, 2L)).thenReturn(true);
            Mockito.when(favoriteDao.listFavoriteTyphoons(1L))
                    .thenReturn(Collections.singletonList(favorite(Long.valueOf(70L), Long.valueOf(9L), null)));

            assertEquals(0L, service.favoriteTyphoon(1L, 2L));
        }
    }

    @Nested
    @DisplayName("favoriteCity · 收藏城市")
    class FavoriteCity {

        @Test
        @DisplayName("未收藏：校验城市存在后插入，台风外键留空")
        void inserts() {
            Mockito.when(favoriteDao.existsCityFavorite(1L, 3L)).thenReturn(false);
            Mockito.when(favoriteDao.insert(Mockito.any(UserFavorite.class))).thenReturn(88L);

            assertEquals(88L, service.favoriteCity(1L, 3L));
            Mockito.verify(cityService).requireCity(3L);
            ArgumentCaptor<UserFavorite> captor = ArgumentCaptor.forClass(UserFavorite.class);
            Mockito.verify(favoriteDao).insert(captor.capture());
            assertNull(captor.getValue().getTyphoonId(), "收藏城市时台风外键必须留空");
            assertEquals(Long.valueOf(3L), captor.getValue().getCityId());
        }

        @Test
        @DisplayName("已收藏：幂等返回既有收藏ID")
        void idempotent() {
            Mockito.when(favoriteDao.existsCityFavorite(1L, 3L)).thenReturn(true);
            Mockito.when(favoriteDao.listFavoriteCities(1L))
                    .thenReturn(Collections.singletonList(favorite(Long.valueOf(80L), null, Long.valueOf(3L))));

            assertEquals(80L, service.favoriteCity(1L, 3L));
            Mockito.verify(favoriteDao, Mockito.never()).insert(Mockito.any(UserFavorite.class));
        }
    }

    /* ==================================================================== */
    /*  取消与统计                                                          */
    /* ==================================================================== */

    @Nested
    @DisplayName("unfavorite / count · 取消与统计")
    class RemoveAndCount {

        @Test
        @DisplayName("取消收藏台风与城市都委托 DAO")
        void unfavorite() {
            Mockito.when(favoriteDao.deleteTyphoonFavorite(1L, 2L)).thenReturn(1);
            Mockito.when(favoriteDao.deleteCityFavorite(1L, 3L)).thenReturn(0);

            assertEquals(1, service.unfavoriteTyphoon(1L, 2L));
            assertEquals(0, service.unfavoriteCity(1L, 3L));
        }

        @Test
        @DisplayName("统计收藏数委托 DAO")
        void count() {
            Mockito.when(favoriteDao.countByUser(1L)).thenReturn(5);

            assertEquals(5, service.count(1L));
        }
    }

    @Test
    @DisplayName("收藏目标不存在时，异常向上抛出（不静默）")
    void favoritePropagatesNotFound() {
        Mockito.when(typhoonService.requireTyphoon(2L))
                .thenThrow(BusinessException.notFound("台风不存在：2"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.favoriteTyphoon(1L, 2L));
        assertEquals(404, e.getCode());
    }
}
