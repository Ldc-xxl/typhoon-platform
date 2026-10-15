package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.SandboxScenarioDao;
import com.zfz.typhoon.dao.SystemSettingDao;
import com.zfz.typhoon.dao.TyphoonDao;
import com.zfz.typhoon.model.SandboxScenario;
import com.zfz.typhoon.model.vo.SandboxScenarioVO;
import com.zfz.typhoon.service.CityService;
import com.zfz.typhoon.service.ForecastService;
import com.zfz.typhoon.service.TyphoonService;
import com.zfz.typhoon.service.WindCircleService;
import com.zfz.typhoon.util.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SandboxServiceImpl} 单元测试。
 *
 * <p>本类只覆盖<b>情景库的可见性与归属</b>这一块——它是第三轮复核里
 * 唯一"接口齐全但用户根本用不上"的缺陷（N3）：
 * <ol>
 *   <li>默认口径必须包含<b>自己存的方案</b>。新保存的情景 {@code is_public} 默认为 0，
 *       若默认只列公开情景，用户点完「另存为情景」会当场找不到它，只能认定保存失败；</li>
 *   <li>{@code mine=1} 要能单独拿出"只看自己的"（含未公开的私有方案）；</li>
 *   <li>匿名请求只能看到公开情景；</li>
 *   <li>{@code mine} / {@code shared} 两个标记要如实回传——前端靠它们画标签与公开开关，
 *       而"能不能看到"这件事反推不出公开状态（可见性已经把 is_public 混进去了）；</li>
 *   <li>公开开关的权限是<b>创建者本人或管理员</b>，不是"仅管理员"。
 *       并要求越权时抛 403 而不是静默返回 0 行——后者会让前端以为设置成功了。</li>
 * </ol>
 *
 * <p>推演算法（路径偏移 / 风圈缩放 / 城市影响）的验证在接口层
 * （{@code PublicEndpointTest}）与沙盘页面联调时做，这里不重复造数据。
 *
 * @author 追风者项目组
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SandboxServiceImpl · 情景库可见性与归属")
class SandboxServiceImplTest {

    private static final long ME = 7L;
    private static final long OTHER = 8L;

    @Mock
    private SandboxScenarioDao scenarioDao;
    @Mock
    private SystemSettingDao settingDao;
    @Mock
    private TyphoonDao typhoonDao;
    @Mock
    private TyphoonService typhoonService;
    @Mock
    private CityService cityService;
    @Mock
    private ForecastService forecastService;
    @Mock
    private WindCircleService windCircleService;

    private SandboxServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SandboxServiceImpl(scenarioDao, settingDao, typhoonDao,
                typhoonService, cityService, forecastService, windCircleService);
    }

    /* ------------------------------------------------------------------ */
    /*  测试数据                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * 造一条情景实体。
     *
     * @param id       情景ID
     * @param ownerId  创建者ID
     * @param shared   是否已公开
     * @param name     情景名
     * @return 实体
     */
    private SandboxScenario scenario(long id, Long ownerId, boolean shared, String name) {
        SandboxScenario s = new SandboxScenario();
        s.setScenarioId(Long.valueOf(id));
        s.setUserId(ownerId);
        s.setScenarioName(name);
        s.setScenarioType(SandboxScenario.TYPE_BASELINE);
        s.setPublicShared(Boolean.valueOf(shared));
        s.setCreatedAt(LocalDateTime.of(2026, 10, 1, 9, 30));
        return s;
    }

    /* ================================================================== */
    /*  一、可见性                                                          */
    /* ================================================================== */

    @Nested
    @DisplayName("情景库列表可见性")
    class Visibility {

        @Test
        @DisplayName("已登录默认口径：公开的 ∪ 自己的，私有方案不会被漏掉")
        void defaultIncludesOwnPrivate() {
            Mockito.when(scenarioDao.listVisible(Long.valueOf(ME), null, 20))
                    .thenReturn(Arrays.asList(
                            scenario(1, OTHER, true, "平台示例"),
                            scenario(2, Long.valueOf(ME), false, "我刚存但没公开的")));

            List<SandboxScenarioVO> rows = service.list(Long.valueOf(ME), null, 20, false);

            assertEquals(2, rows.size(), "自己的私有方案也必须出现在情景库里");
            assertEquals("我刚存但没公开的", rows.get(1).getName());
            assertTrue(rows.get(1).getMine().booleanValue(), "自己创建的应标记 mine=true");
            assertFalse(rows.get(1).getShared().booleanValue(), "未公开的应标记 shared=false");
        }

        @Test
        @DisplayName("mine=1：只取自己创建的，他人公开的不要混进来")
        void mineOnly() {
            Mockito.when(scenarioDao.listByUser(ME))
                    .thenReturn(Collections.singletonList(
                            scenario(2, Long.valueOf(ME), false, "只看我的")));

            List<SandboxScenarioVO> rows = service.list(Long.valueOf(ME), null, 20, true);

            assertEquals(1, rows.size());
            assertEquals("只看我的", rows.get(0).getName());
            /* "只看我的"必须走 listByUser（含私有），不能复用 listVisible 让别人的方案混进来 */
            Mockito.verify(scenarioDao).listByUser(ME);
            Mockito.verify(scenarioDao, Mockito.never())
                    .listVisible(Mockito.any(), Mockito.any(), Mockito.anyInt());
        }

        @Test
        @DisplayName("匿名：DAO 收到 userId = null（退化为只看公开）")
        void anonymous() {
            Mockito.when(scenarioDao.listVisible(null, null, 20))
                    .thenReturn(Collections.singletonList(scenario(1, OTHER, true, "公开示例")));

            List<SandboxScenarioVO> rows = service.list(null, null, 20, false);

            assertEquals(1, rows.size());
            assertFalse(rows.get(0).getMine().booleanValue(), "匿名访客的 mine 恒为 false");
            assertTrue(rows.get(0).getShared().booleanValue());
        }

        @Test
        @DisplayName("mine 标记按会话用户比对，而不是「有 user_id 就算我的」")
        void mineComparesAgainstViewer() {
            Mockito.when(scenarioDao.listVisible(Long.valueOf(ME), null, 20))
                    .thenReturn(Arrays.asList(
                            scenario(1, Long.valueOf(OTHER), true, "别人的"),
                            scenario(2, Long.valueOf(ME), true, "我的")));

            List<SandboxScenarioVO> rows = service.list(Long.valueOf(ME), null, 20, false);

            assertFalse(rows.get(0).getMine().booleanValue(), "他人创建的不应标成我的");
            assertTrue(rows.get(1).getMine().booleanValue());
        }

        @Test
        @DisplayName("limit 上限在 Service 侧再次生效（DAO 返回过多也不会全吐出去）")
        void limitIsEnforced() {
            Mockito.when(scenarioDao.listVisible(Long.valueOf(ME), null, 2))
                    .thenReturn(Arrays.asList(
                            scenario(1, Long.valueOf(ME), false, "a"),
                            scenario(2, Long.valueOf(ME), false, "b"),
                            scenario(3, Long.valueOf(ME), false, "c")));

            assertEquals(2, service.list(Long.valueOf(ME), null, 2, false).size());
        }

        @Test
        @DisplayName("mine 且匿名时退化为公开口径，不会 NPE")
        void mineWithoutUserFallsBack() {
            Mockito.when(scenarioDao.listVisible(null, null, 20))
                    .thenReturn(Collections.singletonList(scenario(1, OTHER, true, "公开示例")));

            List<SandboxScenarioVO> rows = service.list(null, null, 20, true);

            assertEquals(1, rows.size(), "没有 userId 就无从谈「我的」，应退化为公开列表");
            assertFalse(rows.get(0).getMine().booleanValue());
        }
    }

    /* ================================================================== */
    /*  二、公开开关权限                                                    */
    /* ================================================================== */

    @Nested
    @DisplayName("公开 / 取消公开的权限")
    class Sharing {

        @Test
        @DisplayName("创建者本人可以公开自己的方案（这正是原先缺的那条链路）")
        void ownerCanShare() {
            Mockito.when(scenarioDao.findById(2L)).thenReturn(scenario(2, Long.valueOf(ME), false, "我的"));
            Mockito.when(scenarioDao.setPublic(2L, true)).thenReturn(1);

            assertEquals(1, service.setShared(2L, true, ME, false));
            Mockito.verify(scenarioDao).setPublic(2L, true);
        }

        @Test
        @DisplayName("管理员可以代为公开任意方案")
        void adminCanShare() {
            Mockito.when(scenarioDao.findById(1L)).thenReturn(scenario(1, Long.valueOf(OTHER), false, "别人的"));
            Mockito.when(scenarioDao.setPublic(1L, true)).thenReturn(1);

            assertEquals(1, service.setShared(1L, true, ME, true));
        }

        @Test
        @DisplayName("非创建者且非管理员 → 403，而不是静默返回 0 行")
        void strangerForbidden() {
            Mockito.when(scenarioDao.findById(1L)).thenReturn(scenario(1, Long.valueOf(OTHER), false, "别人的"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.setShared(1L, true, ME, false));

            assertEquals(403, ex.getCode(), "越权必须是 403：返回 0 行会让前端以为设置成功");
            Mockito.verify(scenarioDao, Mockito.never()).setPublic(Mockito.anyLong(), Mockito.anyBoolean());
        }

        @Test
        @DisplayName("情景不存在 → 404")
        void missingScenario() {
            Mockito.when(scenarioDao.findById(99L)).thenReturn(null);

            assertEquals(404, assertThrows(BusinessException.class,
                    () -> service.setShared(99L, true, ME, true)).getCode());
        }

        @Test
        @DisplayName("记录没有 user_id（历史脏数据）时只有管理员能动")
        void ownerlessRowNeedsAdmin() {
            Mockito.when(scenarioDao.findById(5L)).thenReturn(scenario(5, null, false, "无主"));

            assertEquals(403, assertThrows(BusinessException.class,
                    () -> service.setShared(5L, false, ME, false)).getCode());
        }
    }

    /* ================================================================== */
    /*  三、单条查询                                                        */
    /* ================================================================== */

    @Test
    @DisplayName("getById 会带出 mine 标记（详情接口同样要能分辨归属）")
    void getByIdCarriesMine() {
        Mockito.when(scenarioDao.findById(2L)).thenReturn(scenario(2, Long.valueOf(ME), true, "我的"));

        SandboxScenarioVO vo = service.getById(2L, Long.valueOf(ME));

        assertTrue(vo.getMine().booleanValue());
        assertTrue(vo.getShared().booleanValue());
    }

    @Test
    @DisplayName("getById 匿名时 mine 为 false，且 404 文案可读")
    void getByIdAnonymous() {
        Mockito.when(scenarioDao.findById(2L)).thenReturn(scenario(2, Long.valueOf(ME), false, "我的"));
        assertFalse(service.getById(2L, null).getMine().booleanValue());

        Mockito.when(scenarioDao.findById(3L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(3L, null));
        assertEquals(404, ex.getCode());
        assertTrue(ex.getMessage().contains("3"), ex.getMessage());
    }
}
