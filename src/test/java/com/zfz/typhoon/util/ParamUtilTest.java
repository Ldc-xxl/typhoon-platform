package com.zfz.typhoon.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ParamUtil} 单元测试。
 *
 * <p>用 Mockito 伪造 {@link HttpServletRequest}，只桩定被测方法真正读到的那几个
 * 参数名；未桩定的参数名 Mockito 天然返回 {@code null}，"参数未传"这个分支
 * 因此不需要额外造数据。
 *
 * <p>本类覆盖的重点是<b>错误语义</b>：课程要求 Servlet 里不写业务逻辑，
 * 所有"缺参数 / 类型不对 / 越界"都必须在 ParamUtil 里变成 code=400 的
 * {@link BusinessException}，而不是让 {@code NumberFormatException} 冒泡成 500。
 */
@DisplayName("ParamUtil · 请求参数解析")
class ParamUtilTest {

    private HttpServletRequest req;

    @BeforeEach
    void setUp() {
        req = Mockito.mock(HttpServletRequest.class);
    }

    /** 桩定一个参数值。 */
    private void given(String name, String value) {
        Mockito.when(req.getParameter(name)).thenReturn(value);
    }

    @Nested
    @DisplayName("str · 字符串")
    class Str {

        @Test
        @DisplayName("未传参返回 null")
        void absentReturnsNull() {
            assertNull(ParamUtil.str(req, "keyword"));
        }

        @Test
        @DisplayName("空串与纯空白视为未传，返回 null")
        void blankReturnsNull() {
            given("a", "");
            given("b", "   ");
            assertNull(ParamUtil.str(req, "a"));
            assertNull(ParamUtil.str(req, "b"));
        }

        @Test
        @DisplayName("有值时裁掉首尾空白")
        void trimsValue() {
            given("keyword", "  海葵  ");
            assertEquals("海葵", ParamUtil.str(req, "keyword"));
        }

        @Test
        @DisplayName("缺省值在未传或为空白时生效")
        void defaultValueUsed() {
            given("blank", "   ");
            assertEquals("全部", ParamUtil.str(req, "missing", "全部"));
            assertEquals("全部", ParamUtil.str(req, "blank", "全部"));
            assertEquals(ParamUtil.str(req, "missing"), ParamUtil.str(req, "alsoMissing"));
        }

        @Test
        @DisplayName("有值时缺省值不生效")
        void defaultValueIgnoredWhenPresent() {
            given("status", "active");
            assertEquals("active", ParamUtil.str(req, "status", "全部"));
        }
    }

    @Nested
    @DisplayName("requireStr / requireNonEmpty · 必填校验")
    class Require {

        @Test
        @DisplayName("requireStr 缺参数抛 400，提示带上参数名")
        void requireStrMissing() {
            BusinessException e = assertThrows(BusinessException.class,
                    () -> ParamUtil.requireStr(req, "username"));
            assertEquals(Result.CODE_BAD_REQUEST, e.getCode());
            assertEquals("缺少必填参数：username", e.getMessage());
        }

        @Test
        @DisplayName("requireStr 有值则原样返回（已裁剪）")
        void requireStrPresent() {
            given("username", "  admin01 ");
            assertEquals("admin01", ParamUtil.requireStr(req, "username"));
        }

        @Test
        @DisplayName("requireNonEmpty 校验 JSON 体字段，null / 空白抛 400")
        void requireNonEmptyRejectsBlank() {
            assertEquals("缺少必填字段：password",
                    assertThrows(BusinessException.class,
                            () -> ParamUtil.requireNonEmpty(null, "password")).getMessage());
            assertEquals(Result.CODE_BAD_REQUEST,
                    assertThrows(BusinessException.class,
                            () -> ParamUtil.requireNonEmpty("   ", "password")).getCode());
        }

        @Test
        @DisplayName("requireNonEmpty 返回裁剪后的值")
        void requireNonEmptyTrims() {
            assertEquals("追风者", ParamUtil.requireNonEmpty("  追风者  ", "org"));
        }
    }

    @Nested
    @DisplayName("intVal / requireInt / checkRange · 整数")
    class Integers {

        @Test
        @DisplayName("未传时返回缺省值")
        void absentUsesDefault() {
            assertEquals(1, ParamUtil.intVal(req, "page", 1));
        }

        @Test
        @DisplayName("正常解析，并容忍首尾空白")
        void parsesValue() {
            given("page", "  3  ");
            assertEquals(3, ParamUtil.intVal(req, "page", 1));
            given("negative", "-7");
            assertEquals(-7, ParamUtil.intVal(req, "negative", 0));
        }

        @Test
        @DisplayName("非数字抛 400（而不是 NumberFormatException 冒泡成 500）")
        void nonNumericThrowsBadRequest() {
            given("page", "abc");
            BusinessException e = assertThrows(BusinessException.class,
                    () -> ParamUtil.intVal(req, "page", 1));
            assertEquals(Result.CODE_BAD_REQUEST, e.getCode());
            assertTrue(e.getMessage().contains("应为整数"));
        }

        @Test
        @DisplayName("带区间版本：越界抛 400，边界值（含端点）放行")
        void rangeCheckedVariant() {
            given("pageSize", "20");
            assertEquals(20, ParamUtil.intVal(req, "pageSize", 10, 1, 100));

            given("tooBig", "101");
            assertEquals(Result.CODE_BAD_REQUEST,
                    assertThrows(BusinessException.class,
                            () -> ParamUtil.intVal(req, "tooBig", 10, 1, 100)).getCode());

            given("tooSmall", "0");
            assertEquals(Result.CODE_BAD_REQUEST,
                    assertThrows(BusinessException.class,
                            () -> ParamUtil.intVal(req, "tooSmall", 10, 1, 100)).getCode());
        }

        @Test
        @DisplayName("requireInt 缺参数抛 400，提示用「缺少必填参数」")
        void requireIntMissing() {
            assertEquals("缺少必填参数：typhoonId",
                    assertThrows(BusinessException.class,
                            () -> ParamUtil.requireInt(req, "typhoonId")).getMessage());
        }

        @Test
        @DisplayName("requireInt 非数字抛 400")
        void requireIntNonNumeric() {
            given("typhoonId", "13a");
            assertTrue(assertThrows(BusinessException.class,
                    () -> ParamUtil.requireInt(req, "typhoonId")).getMessage().contains("应为整数"));
        }

        @Test
        @DisplayName("requireInt 正常取值")
        void requireIntOk() {
            given("typhoonId", "13");
            assertEquals(13, ParamUtil.requireInt(req, "typhoonId"));
        }

        @Test
        @DisplayName("checkRange 端点含入，越界抛 400 且提示里带区间")
        void checkRangeMessageContainsBounds() {
            assertEquals(1, ParamUtil.checkRange(1, "pageSize", 1, 100));
            assertEquals(100, ParamUtil.checkRange(100, "pageSize", 1, 100));
            BusinessException e = assertThrows(BusinessException.class,
                    () -> ParamUtil.checkRange(0, "pageSize", 1, 100));
            assertTrue(e.getMessage().contains("[1, 100]"));
            assertTrue(e.getMessage().contains("pageSize"));
        }
    }

    @Nested
    @DisplayName("longVal / doubleVal / boolVal · 其它标量")
    class Scalars {

        @Test
        @DisplayName("longVal 正常解析与缺省")
        void longVal() {
            assertEquals(9L, ParamUtil.longVal(req, "missing", 9L));
            given("id", "12345678901");
            assertEquals(12345678901L, ParamUtil.longVal(req, "id", 0L));
        }

        @Test
        @DisplayName("longVal 非数字抛 400")
        void longValInvalid() {
            given("id", "12x");
            assertTrue(assertThrows(BusinessException.class,
                    () -> ParamUtil.longVal(req, "id", 0L)).getMessage().contains("应为长整数"));
        }

        @Test
        @DisplayName("doubleVal 正常解析小数与缺省")
        void doubleVal() {
            assertEquals(1.5D, ParamUtil.doubleVal(req, "missing", 1.5D), 1e-9);
            given("lat", "20.5");
            assertEquals(20.5D, ParamUtil.doubleVal(req, "lat", 0D), 1e-9);
        }

        @Test
        @DisplayName("doubleVal 非数字抛 400")
        void doubleValInvalid() {
            given("lat", "北纬20");
            assertTrue(assertThrows(BusinessException.class,
                    () -> ParamUtil.doubleVal(req, "lat", 0D)).getMessage().contains("应为数字"));
        }

        @Test
        @DisplayName("boolVal 接受 true/1/yes/on，大小写不敏感")
        void boolValTruthy() {
            for (String truthy : new String[]{"true", "TRUE", "1", "yes", "YES", "on", "ON"}) {
                given("flag", truthy);
                assertTrue(ParamUtil.boolVal(req, "flag", false), truthy + " 应判定为 true");
            }
        }

        @Test
        @DisplayName("boolVal 其余值一律为 false（含拼写错误）")
        void boolValFalsy() {
            for (String falsy : new String[]{"false", "0", "no", "off", "yes1", "是"}) {
                given("flag", falsy);
                assertFalse(ParamUtil.boolVal(req, "flag", true), falsy + " 应判定为 false");
            }
        }

        @Test
        @DisplayName("boolVal 未传时返回缺省值")
        void boolValDefault() {
            assertTrue(ParamUtil.boolVal(req, "missing", true));
            assertFalse(ParamUtil.boolVal(req, "missing", false));
        }

        @Test
        @DisplayName("nullableBool 原样返回，保留三态语义")
        void nullableBool() {
            assertNull(ParamUtil.nullableBool(null));
            assertEquals(Boolean.TRUE, ParamUtil.nullableBool(Boolean.TRUE));
            assertEquals(Boolean.FALSE, ParamUtil.nullableBool(Boolean.FALSE));
        }
    }

    @Nested
    @DisplayName("firstNonEmpty / requirePathId · 回退与路径段")
    class Fallback {

        @Test
        @DisplayName("firstNonEmpty 返回第一个非空白值（已裁剪）")
        void returnsFirstNonBlank() {
            assertEquals("13", ParamUtil.firstNonEmpty(null, "", "   ", " 13 ", "99"));
        }

        @Test
        @DisplayName("firstNonEmpty 全空或整体为 null 时返回 null")
        void returnsNullWhenAllBlank() {
            assertNull(ParamUtil.firstNonEmpty(null, "", "  "));
            assertNull(ParamUtil.firstNonEmpty((String[]) null));
            assertNull(ParamUtil.firstNonEmpty());
        }

        @Test
        @DisplayName("requirePathId 正常解析路径段，并容忍空白")
        void parsesPathId() {
            assertEquals(13L, ParamUtil.requirePathId("13", "台风 id"));
            assertEquals(13L, ParamUtil.requirePathId("  13  ", "台风 id"));
        }

        @Test
        @DisplayName("requirePathId 段为空抛 400，提示「缺少台风 id」")
        void requiresSegment() {
            assertEquals("缺少台风 id",
                    assertThrows(BusinessException.class,
                            () -> ParamUtil.requirePathId(null, "台风 id")).getMessage());
            assertEquals(Result.CODE_BAD_REQUEST,
                    assertThrows(BusinessException.class,
                            () -> ParamUtil.requirePathId("   ", "台风 id")).getCode());
        }

        @Test
        @DisplayName("requirePathId 非数字抛 400，提示里带上原始值")
        void rejectsNonNumericSegment() {
            BusinessException e = assertThrows(BusinessException.class,
                    () -> ParamUtil.requirePathId("abc", "台风 id"));
            assertTrue(e.getMessage().contains("格式错误"));
            assertTrue(e.getMessage().contains("abc"));
        }
    }
}
