package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.CityDao;
import com.zfz.typhoon.model.City;
import com.zfz.typhoon.model.vo.CityVO;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CityServiceImpl} 单元测试。
 *
 * <p>城市服务维护一份进程内快照缓存，本类重点验证：
 * <ol>
 *   <li><b>缓存读取正确性</b>——启用过滤、关键词检索、省份去重都基于快照；</li>
 *   <li><b>写后刷新</b>——新增 / 更新后缓存被重建；</li>
 *   <li><b>错误码区分</b>——{@code getVoById} 用 404，{@code requireCity} 用 400。</li>
 * </ol>
 *
 * @author 追风者项目组
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CityServiceImpl · 城市缓存服务")
class CityServiceImplTest {

    @Mock
    private CityDao cityDao;

    private CityServiceImpl service;

    @BeforeEach
    void setUp() {
        // 构造时触发一次 refresh()，未打桩的 listAll() 返回空列表，缓存即为空
        service = new CityServiceImpl(cityDao);
    }

    /* ------------------------------------------------------------------ */

    private City city(long id, String name, String province, Boolean status) {
        City c = new City();
        c.setCityId(Long.valueOf(id));
        c.setCityName(name);
        c.setProvince(province);
        c.setStatus(status);
        c.setLatitude(Double.valueOf(24.5));
        c.setLongitude(Double.valueOf(118.1));
        c.setBaseRiskLevel(Integer.valueOf(3));
        return c;
    }

    /** 用给定城市重建缓存。 */
    private void seed(City... cities) {
        Mockito.when(cityDao.listAll()).thenReturn(Arrays.asList(cities));
        service.refresh();
    }

    private static City xiamen() {
        City c = new City();
        c.setCityId(Long.valueOf(1L));
        c.setCityName("厦门");
        c.setProvince("福建");
        c.setStatus(Boolean.TRUE);
        c.setBaseRiskLevel(Integer.valueOf(3));
        return c;
    }

    private static void assertBusinessError(int code, String part, Runnable action) {
        BusinessException e = assertThrows(BusinessException.class, action::run);
        assertEquals(code, e.getCode(), "业务码不符：" + e.getMessage());
        assertTrue(e.getMessage() != null && e.getMessage().contains(part),
                "提示应包含「" + part + "」，实际：" + e.getMessage());
    }

    /* ==================================================================== */
    /*  读取                                                                */
    /* ==================================================================== */

    @Nested
    @DisplayName("listEnabled / listAll · 列表")
    class Lists {

        @Test
        @DisplayName("listEnabled 过滤停用，status 为 null 视为启用")
        void listEnabled() {
            seed(city(1, "厦门", "福建", Boolean.TRUE),
                    city(2, "泉州", "福建", Boolean.FALSE),
                    city(3, "福州", "福建", null));

            List<CityVO> list = service.listEnabled();

            assertEquals(2, list.size());
            assertEquals(Long.valueOf(1L), list.get(0).getId());
            assertEquals(Long.valueOf(3L), list.get(1).getId());
        }

        @Test
        @DisplayName("listAll 含停用城市")
        void listAll() {
            seed(city(1, "厦门", "福建", Boolean.TRUE),
                    city(2, "泉州", "福建", Boolean.FALSE));

            assertEquals(2, service.listAll().size());
        }
    }

    @Nested
    @DisplayName("getById / getVoById / asMap / exists")
    class Lookup {

        @Test
        @DisplayName("getById 命中返回实体，未命中返回 null")
        void getById() {
            seed(xiamen());

            assertEquals("厦门", service.getById(1L).getCityName());
            assertNull(service.getById(99L));
        }

        @Test
        @DisplayName("getVoById 未命中抛 404")
        void getVoByIdNotFound() {
            seed(xiamen());

            assertBusinessError(404, "城市不存在", () -> service.getVoById(99L));
        }

        @Test
        @DisplayName("getVoById 命中返回视图")
        void getVoById() {
            seed(xiamen());

            CityVO vo = service.getVoById(1L);

            assertEquals("厦门", vo.getName());
            assertEquals("福建", vo.getProvince());
        }

        @Test
        @DisplayName("asMap 返回 ID → 城市 映射")
        void asMap() {
            seed(xiamen());

            Map<Long, City> m = service.asMap();

            assertEquals(1, m.size());
            assertEquals("厦门", m.get(Long.valueOf(1L)).getCityName());
        }

        @Test
        @DisplayName("exists 判断城市是否在缓存中")
        void exists() {
            seed(xiamen());

            assertTrue(service.exists(1L));
            assertFalse(service.exists(99L));
        }
    }

    /* ==================================================================== */
    /*  搜索与省份                                                          */
    /* ==================================================================== */

    @Nested
    @DisplayName("search / listProvinces · 检索")
    class Search {

        @Test
        @DisplayName("关键词为空时等价于 listEnabled")
        void blankKeyword() {
            seed(city(1, "厦门", "福建", Boolean.TRUE),
                    city(2, "泉州", "福建", Boolean.FALSE));

            assertEquals(1, service.search(null).size());
            assertEquals(1, service.search("   ").size());
        }

        @Test
        @DisplayName("按城市名命中")
        void byName() {
            seed(city(1, "厦门", "福建", Boolean.TRUE),
                    city(2, "汕头", "广东", Boolean.TRUE));

            List<CityVO> list = service.search("厦");

            assertEquals(1, list.size());
            assertEquals("厦门", list.get(0).getName());
        }

        @Test
        @DisplayName("按省份命中（含停用城市也参与检索）")
        void byProvince() {
            seed(city(1, "厦门", "福建", Boolean.TRUE),
                    city(2, "泉州", "福建", Boolean.FALSE),
                    city(3, "汕头", "广东", Boolean.TRUE));

            assertEquals(2, service.search("福建").size());
        }

        @Test
        @DisplayName("无命中返回空列表")
        void noMatch() {
            seed(xiamen());

            assertTrue(service.search("不存在的城市").isEmpty());
        }

        @Test
        @DisplayName("省份去重且保持城市ID顺序，null 省份被跳过")
        void provinces() {
            seed(city(1, "厦门", "福建", Boolean.TRUE),
                    city(2, "泉州", "福建", Boolean.TRUE),
                    city(3, "汕头", "广东", Boolean.TRUE),
                    city(4, "无名", null, Boolean.TRUE));

            assertEquals(Arrays.asList("福建", "广东"), service.listProvinces());
        }
    }

    /* ==================================================================== */
    /*  校验与筛选                                                          */
    /* ==================================================================== */

    @Nested
    @DisplayName("requireCity / listByProvince / listByIds")
    class Validate {

        @Test
        @DisplayName("requireCity 未命中抛 400（这是参数错误而非资源不存在）")
        void requireCity() {
            seed(xiamen());

            assertBusinessError(400, "城市不存在：cityId=", () -> service.requireCity(99L));
            assertEquals("厦门", service.requireCity(1L).getCityName());
        }

        @Test
        @DisplayName("listByProvince 过滤省份；province 为 null 返回空列表")
        void listByProvince() {
            seed(city(1, "厦门", "福建", Boolean.TRUE),
                    city(2, "汕头", "广东", Boolean.TRUE));

            assertEquals(1, service.listByProvince("福建").size());
            assertTrue(service.listByProvince(null).isEmpty());
        }

        @Test
        @DisplayName("listByIds 跳过不存在的ID；入参为 null 返回空列表")
        void listByIds() {
            seed(city(1, "厦门", "福建", Boolean.TRUE),
                    city(2, "汕头", "广东", Boolean.TRUE));

            List<City> found = service.listByIds(Arrays.asList(Long.valueOf(1L), Long.valueOf(99L)));
            assertEquals(1, found.size());
            assertEquals("厦门", found.get(0).getCityName());

            assertTrue(service.listByIds(null).isEmpty());
        }
    }

    /* ==================================================================== */
    /*  新增与更新                                                          */
    /* ==================================================================== */

    @Nested
    @DisplayName("create / updateCity · 写入并刷新缓存")
    class Writes {

        @Test
        @DisplayName("城市名或省份为空抛 400，且不写库")
        void createValidation() {
            City noName = city(1, null, "福建", Boolean.TRUE);
            City noProvince = city(2, "厦门", null, Boolean.TRUE);

            assertBusinessError(400, "城市名称与所属省份不能为空", () -> service.create(noName));
            assertBusinessError(400, "城市名称与所属省份不能为空", () -> service.create(noProvince));
            Mockito.verify(cityDao, Mockito.never()).insert(Mockito.any(City.class));
        }

        @Test
        @DisplayName("新增成功：返回新ID并刷新缓存（新城市立即可查）")
        void createOk() {
            Mockito.when(cityDao.insert(Mockito.any(City.class))).thenReturn(55L);
            seed(xiamen());

            long id = service.create(city(55, "宁德", "福建", Boolean.TRUE));

            assertEquals(55L, id);
            ArgumentCaptor<City> captor = ArgumentCaptor.forClass(City.class);
            Mockito.verify(cityDao).insert(captor.capture());
            assertEquals("宁德", captor.getValue().getCityName());
        }

        @Test
        @DisplayName("更新缺少 cityId 抛 400")
        void updateMissingId() {
            City noId = new City();
            noId.setCityName("厦门");

            assertBusinessError(400, "缺少 cityId", () -> service.updateCity(noId));
        }

        @Test
        @DisplayName("更新影响 0 行抛 404（城市不存在）")
        void updateNotFound() {
            Mockito.when(cityDao.update(Mockito.any(City.class))).thenReturn(0);
            seed(xiamen());

            City c = city(99, "不存在的城市", "福建", Boolean.TRUE);

            assertBusinessError(404, "城市不存在", () -> service.updateCity(c));
        }

        @Test
        @DisplayName("更新成功：返回受影响行数")
        void updateOk() {
            Mockito.when(cityDao.update(Mockito.any(City.class))).thenReturn(1);
            seed(xiamen());

            assertEquals(1, service.updateCity(city(1, "厦门市", "福建", Boolean.TRUE)));
        }
    }

    @Test
    @DisplayName("refresh 用 DAO 的全量列表重建快照（列表与映射同步）")
    void refreshReplacesSnapshot() {
        Mockito.when(cityDao.listAll())
                .thenReturn(Arrays.asList(xiamen(), city(2, "汕头", "广东", Boolean.TRUE)));

        service.refresh();

        assertEquals(2, service.listAll().size());
        assertEquals(2, service.asMap().size());
        assertEquals(Collections.emptyList(), service.search("不存在"));
    }
}
