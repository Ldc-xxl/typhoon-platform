package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.SystemSettingDao;
import com.zfz.typhoon.model.SystemSetting;
import com.zfz.typhoon.service.SystemSettingService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SystemSettingServiceImpl} 单元测试。
 *
 * <p>系统参数是全站算法的"旋钮"，写坏一个值不会报错、只会安静地用默认值跑，
 * 因此写入侧必须有类型试解析。本类重点验证：
 * <ol>
 *   <li><b>读取退默认值</b>——空值 / 不可解析时返回调用方给的默认值；</li>
 *   <li><b>写入类型校验</b>——NUMBER / BOOLEAN / JSON 试解析失败抛 400；</li>
 *   <li><b>批量更新原子性</b>——先全量校验再统一落库，避免改了一半。</li>
 * </ol>
 *
 * @author 追风者项目组
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SystemSettingServiceImpl · 系统参数")
class SystemSettingServiceImplTest {

    @Mock
    private SystemSettingDao settingDao;

    private SystemSettingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SystemSettingServiceImpl(settingDao);
    }

    /* ------------------------------------------------------------------ */

    private SystemSetting setting(String key, String type, String description) {
        SystemSetting s = new SystemSetting();
        s.setSettingKey(key);
        s.setSettingType(type);
        s.setDescription(description);
        return s;
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
    @DisplayName("读取 · listAll / getString / asMap")
    class Reads {

        @Test
        @DisplayName("listAll 直接委托 DAO")
        void listAll() {
            List<SystemSetting> rows = new ArrayList<SystemSetting>();
            rows.add(setting("a", SystemSettingService.TYPE_STRING, "说明"));
            Mockito.when(settingDao.listAll()).thenReturn(rows);

            assertEquals(1, service.listAll().size());
        }

        @Test
        @DisplayName("取值成功时去掉首尾空白")
        void getStringTrimmed() {
            Mockito.when(settingDao.getValue("k", null)).thenReturn("  v  ");

            assertEquals("v", service.getString("k", "def"));
        }

        @Test
        @DisplayName("值为 null / 空串 / 纯空白时返回默认值")
        void getStringFallsBack() {
            Mockito.when(settingDao.getValue("a", null)).thenReturn(null);
            Mockito.when(settingDao.getValue("b", null)).thenReturn("");
            Mockito.when(settingDao.getValue("c", null)).thenReturn("   ");

            assertEquals("def", service.getString("a", "def"));
            assertEquals("def", service.getString("b", "def"));
            assertEquals("def", service.getString("c", "def"));
        }

        @Test
        @DisplayName("asMap 由配置行拼装，保持列表顺序")
        void asMap() {
            List<SystemSetting> rows = new ArrayList<SystemSetting>();
            SystemSetting s1 = setting("k1", SystemSettingService.TYPE_STRING, null);
            s1.setSettingValue("v1");
            SystemSetting s2 = setting("k2", SystemSettingService.TYPE_STRING, null);
            s2.setSettingValue("v2");
            rows.add(s1);
            rows.add(s2);
            Mockito.when(settingDao.listAll()).thenReturn(rows);

            Map<String, String> m = service.asMap();

            assertEquals(2, m.size());
            assertEquals(Arrays.asList("k1", "k2"), new ArrayList<String>(m.keySet()));
            assertEquals("v1", m.get("k1"));
        }
    }

    @Nested
    @DisplayName("读取 · getDouble / getInt 委托 DAO")
    class NumericReads {

        @Test
        @DisplayName("getDouble 与 getInt 直接委托 DAO（含解析失败退默认值）")
        void delegates() {
            Mockito.when(settingDao.getDouble("t", 0.7D)).thenReturn(0.85D);
            Mockito.when(settingDao.getInt("n", 12)).thenReturn(24);

            assertEquals(0.85D, service.getDouble("t", 0.7D), 1e-9);
            assertEquals(24, service.getInt("n", 12));
        }
    }

    @Nested
    @DisplayName("读取 · getBoolean")
    class BooleanRead {

        @Test
        @DisplayName("1 / true / 是 / 开启 都视为真")
        void truthyForms() {
            Mockito.when(settingDao.getValue("a", null)).thenReturn("1");
            Mockito.when(settingDao.getValue("b", null)).thenReturn("TRUE");
            Mockito.when(settingDao.getValue("c", null)).thenReturn("是");
            Mockito.when(settingDao.getValue("d", null)).thenReturn("开启");

            assertTrue(service.getBoolean("a", "false"));
            assertTrue(service.getBoolean("b", "false"));
            assertTrue(service.getBoolean("c", "false"));
            assertTrue(service.getBoolean("d", "false"));
        }

        @Test
        @DisplayName("0 / false / 否 / 关闭 都视为假")
        void falsyForms() {
            Mockito.when(settingDao.getValue("a", null)).thenReturn("0");
            Mockito.when(settingDao.getValue("b", null)).thenReturn("False");
            Mockito.when(settingDao.getValue("c", null)).thenReturn("否");
            Mockito.when(settingDao.getValue("d", null)).thenReturn("关闭");

            assertFalse(service.getBoolean("a", "true"));
            assertFalse(service.getBoolean("b", "true"));
            assertFalse(service.getBoolean("c", "true"));
            assertFalse(service.getBoolean("d", "true"));
        }

        @Test
        @DisplayName("值不可解析时用调用方给的默认值")
        void unparsableUsesDefault() {
            Mockito.when(settingDao.getValue("a", null)).thenReturn("maybe");

            assertTrue(service.getBoolean("a", "true"));
            assertFalse(service.getBoolean("a", "false"));
            assertFalse(service.getBoolean("a", "nonsense"));
        }

        @Test
        @DisplayName("值为空时按默认值字符串解析")
        void nullValueUsesDefault() {
            Mockito.when(settingDao.getValue("a", null)).thenReturn(null);

            assertTrue(service.getBoolean("a", "true"));
            assertFalse(service.getBoolean("a", "0"));
        }
    }

    /* ==================================================================== */
    /*  update                                                              */
    /* ==================================================================== */

    @Nested
    @DisplayName("update · 单参数更新")
    class Update {

        @Test
        @DisplayName("参数名为 null / 空串 / 纯空白抛 400，且不查库")
        void blankKey() {
            assertBusinessError(400, "参数名不能为空", () -> service.update(null, "1", Long.valueOf(1L)));
            assertBusinessError(400, "参数名不能为空", () -> service.update("   ", "1", Long.valueOf(1L)));
            Mockito.verifyNoInteractions(settingDao);
        }

        @Test
        @DisplayName("参数不存在抛 404")
        void notFound() {
            Mockito.when(settingDao.findByKey("ghost")).thenReturn(null);
            assertBusinessError(404, "参数不存在", () -> service.update("ghost", "1", Long.valueOf(1L)));
        }

        @Test
        @DisplayName("值为 null 抛 400")
        void nullValue() {
            Mockito.when(settingDao.findByKey("k")).thenReturn(setting("k", SystemSettingService.TYPE_STRING, null));
            assertBusinessError(400, "值不能为空", () -> service.update("k", null, Long.valueOf(1L)));
        }

        @Test
        @DisplayName("NUMBER 类型：非数值抛 400，合法数值通过")
        void numberType() {
            Mockito.when(settingDao.findByKey("t")).thenReturn(setting("t", "number", "阈值"));
            assertBusinessError(400, "应为数值", () -> service.update("t", "0.7O", Long.valueOf(1L)));

            Mockito.when(settingDao.upsertValue("t", "0.85", "number", Long.valueOf(1L))).thenReturn(1);
            assertEquals(1, service.update("t", "0.85", Long.valueOf(1L)));
        }

        @Test
        @DisplayName("BOOLEAN 类型：非法值抛 400（提示带上参数说明）")
        void booleanType() {
            SystemSetting s = setting("flag", SystemSettingService.TYPE_BOOLEAN, "总开关");
            Mockito.when(settingDao.findByKey("flag")).thenReturn(s);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> service.update("flag", "yes", Long.valueOf(1L)));
            assertEquals(400, e.getCode());
            assertTrue(e.getMessage().contains("总开关"), "提示应带上参数说明");
        }

        @Test
        @DisplayName("JSON 类型：非法 JSON 抛 400，合法 JSON 通过")
        void jsonType() {
            Mockito.when(settingDao.findByKey("j")).thenReturn(setting("j", SystemSettingService.TYPE_JSON, null));
            assertBusinessError(400, "应为合法 JSON", () -> service.update("j", "not-json", Long.valueOf(1L)));

            Mockito.when(settingDao.upsertValue("j", "{\"a\":1}", SystemSettingService.TYPE_JSON, null))
                    .thenReturn(1);
            assertEquals(1, service.update("j", "{\"a\":1}", null));
        }

        @Test
        @DisplayName("STRING 类型：不做类型校验，任意值可直接落库")
        void stringType() {
            Mockito.when(settingDao.findByKey("k")).thenReturn(setting("k", SystemSettingService.TYPE_STRING, null));
            Mockito.when(settingDao.upsertValue("k", "任意文本", SystemSettingService.TYPE_STRING, Long.valueOf(2L)))
                    .thenReturn(1);

            assertEquals(1, service.update("k", "任意文本", Long.valueOf(2L)));
        }

        @Test
        @DisplayName("settingType 为 null 时跳过类型校验")
        void nullTypeSkipsValidation() {
            Mockito.when(settingDao.findByKey("k")).thenReturn(setting("k", null, null));
            Mockito.when(settingDao.upsertValue("k", "whatever", null, null)).thenReturn(1);

            assertEquals(1, service.update("  k  ", "whatever", null));
        }
    }

    /* ==================================================================== */
    /*  updateBatch                                                         */
    /* ==================================================================== */

    @Nested
    @DisplayName("updateBatch · 批量更新")
    class UpdateBatch {

        @Test
        @DisplayName("入参为 null / 空 Map 抛 400")
        void emptyInput() {
            assertBusinessError(400, "没有需要更新的参数",
                    () -> service.updateBatch(null, Long.valueOf(1L)));
            assertBusinessError(400, "没有需要更新的参数",
                    () -> service.updateBatch(new LinkedHashMap<String, String>(), Long.valueOf(1L)));
        }

        @Test
        @DisplayName("全部键为空串：没有可更新的参数，抛 400")
        void allBlankKeys() {
            Map<String, String> values = new LinkedHashMap<String, String>();
            values.put("  ", "1");
            assertBusinessError(400, "没有需要更新的参数",
                    () -> service.updateBatch(values, Long.valueOf(1L)));
        }

        @Test
        @DisplayName("任一参数不存在则整体抛 404，且不落任何库")
        void anyNotFoundAbortsAll() {
            Map<String, String> values = new LinkedHashMap<String, String>();
            values.put("a", "1");
            values.put("b", "2");
            Mockito.when(settingDao.findByKey("a")).thenReturn(setting("a", SystemSettingService.TYPE_NUMBER, null));
            Mockito.when(settingDao.findByKey("b")).thenReturn(null);

            assertBusinessError(404, "参数不存在", () -> service.updateBatch(values, Long.valueOf(1L)));
            Mockito.verify(settingDao, Mockito.never()).upsertValue(
                    Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any(Long.class));
        }

        @Test
        @DisplayName("任一参数校验失败则整体抛 400，且不落库")
        void anyInvalidAbortsAll() {
            Map<String, String> values = new LinkedHashMap<String, String>();
            values.put("a", "1");
            values.put("b", "bad");
            Mockito.when(settingDao.findByKey("a")).thenReturn(setting("a", SystemSettingService.TYPE_NUMBER, null));
            Mockito.when(settingDao.findByKey("b")).thenReturn(setting("b", SystemSettingService.TYPE_NUMBER, null));

            assertBusinessError(400, "应为数值", () -> service.updateBatch(values, Long.valueOf(1L)));
            Mockito.verify(settingDao, Mockito.never()).upsertValue(
                    Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any(Long.class));
        }

        @Test
        @DisplayName("全部合法：逐个落库并汇总受影响行数")
        void updatesAll() {
            Map<String, String> values = new LinkedHashMap<String, String>();
            values.put("a", "1");
            values.put("b", "true");
            Mockito.when(settingDao.findByKey("a")).thenReturn(setting("a", SystemSettingService.TYPE_NUMBER, null));
            Mockito.when(settingDao.findByKey("b")).thenReturn(setting("b", SystemSettingService.TYPE_BOOLEAN, null));
            Mockito.when(settingDao.upsertValue("a", "1", SystemSettingService.TYPE_NUMBER, Long.valueOf(9L)))
                    .thenReturn(1);
            Mockito.when(settingDao.upsertValue("b", "true", SystemSettingService.TYPE_BOOLEAN, Long.valueOf(9L)))
                    .thenReturn(1);

            assertEquals(2, service.updateBatch(values, Long.valueOf(9L)));
        }

        @Test
        @DisplayName("键首尾空白会被裁剪后查库")
        void trimsKeys() {
            Map<String, String> values = new LinkedHashMap<String, String>();
            values.put("  a  ", "1");
            Mockito.when(settingDao.findByKey("a")).thenReturn(setting("a", SystemSettingService.TYPE_NUMBER, null));
            Mockito.when(settingDao.upsertValue("a", "1", SystemSettingService.TYPE_NUMBER, null)).thenReturn(1);

            assertEquals(1, service.updateBatch(values, null));
        }
    }

    @Test
    @DisplayName("参数说明为空时，校验提示里不带括号说明")
    void emptyDescriptionNoHint() {
        Mockito.when(settingDao.findByKey("n")).thenReturn(setting("n", SystemSettingService.TYPE_NUMBER, "   "));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.update("n", "abc", null));
        assertEquals(400, e.getCode());
        assertFalse(e.getMessage().contains("（）"));
    }

    @Test
    @DisplayName("全量配置为空时 asMap 返回空映射")
    void emptyAsMap() {
        Mockito.when(settingDao.listAll()).thenReturn(Collections.<SystemSetting>emptyList());

        assertTrue(service.asMap().isEmpty());
    }
}
