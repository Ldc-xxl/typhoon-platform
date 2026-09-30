package com.zfz.typhoon.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JsonUtil} 单元测试。
 *
 * <p>这是 Servlet 层的统一出入口，序列化配置一旦被改动（比如把
 * {@code WRITE_DATES_AS_TIMESTAMPS} 打开，或在别处 {@code new ObjectMapper()}），
 * 页面上所有时间会变成 {@code [2026,9,13,8,0]} 形式的数组。因此这里对
 * <b>时间格式、空值策略、未知字段宽容</b>三件事分别立断言。
 *
 * <p>{@link HttpServletRequest} / {@link HttpServletResponse} 用 Mockito 伪造，
 * 响应体写进 {@link StringWriter} 后直接断言其内容。
 */
@DisplayName("JsonUtil · JSON 序列化")
class JsonUtilTest {

    /** 用于序列化断言的可变 POJO（字段顺序即声明顺序）。 */
    @SuppressWarnings("unused")
    public static class Demo {

        private int code;
        private String message;
        private LocalDateTime time;
        private LocalDate day;

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public LocalDateTime getTime() {
            return time;
        }

        public void setTime(LocalDateTime time) {
            this.time = time;
        }

        public LocalDate getDay() {
            return day;
        }

        public void setDay(LocalDate day) {
            this.day = day;
        }
    }

    @Nested
    @DisplayName("序列化")
    class Serialization {

        @Test
        @DisplayName("null 对象输出字面量 null（而不是抛异常）")
        void nullObjectBecomesLiteralNull() {
            assertEquals("null", JsonUtil.toJson(null));
        }

        @Test
        @DisplayName("LocalDateTime 按 yyyy-MM-dd HH:mm:ss 输出，不是 ISO 也不是时间戳数组")
        void dateTimeUsesProjectPattern() {
            assertEquals("\"2026-09-13 08:00:00\"",
                    JsonUtil.toJson(LocalDateTime.of(2026, 9, 13, 8, 0, 0)));
            assertFalse(JsonUtil.toJson(LocalDateTime.of(2026, 9, 13, 8, 0, 0)).contains("T"),
                    "不应输出 ISO 的 T 分隔符");
        }

        @Test
        @DisplayName("LocalDate 按 yyyy-MM-dd 输出")
        void dateUsesProjectPattern() {
            assertEquals("\"2026-09-13\"", JsonUtil.toJson(LocalDate.of(2026, 9, 13)));
        }

        @Test
        @DisplayName("POJO 输出字段名与值，时间按统一格式")
        void pojoFields() {
            Demo d = new Demo();
            d.setCode(200);
            d.setTime(LocalDateTime.of(2026, 9, 13, 8, 0, 0));
            String json = JsonUtil.toJson(d);
            assertTrue(json.contains("\"code\":200"), json);
            assertTrue(json.contains("\"time\":\"2026-09-13 08:00:00\""), json);
        }

        @Test
        @DisplayName("NON_NULL 策略：值为 null 的字段不出现在输出里")
        void nullFieldsOmitted() {
            Demo d = new Demo();
            d.setCode(200);
            String json = JsonUtil.toJson(d);
            assertFalse(json.contains("message"), json);
            assertFalse(json.contains("time"), json);
            assertFalse(json.contains("null"), json);
        }

        @Test
        @DisplayName("Result 的 JSON 形态即前后端契约 {code, message, data, success}")
        void resultShape() {
            // success 来自 Result.isSuccess()，Jackson 按 getter 规则展开成布尔字段；
            // 前端 api.js 的成功判定同时看 code 与 success，故这里必须钉住四个字段。
            assertEquals("{\"code\":200,\"message\":\"success\",\"data\":\"ok\",\"success\":true}",
                    JsonUtil.toJson(Result.ok("ok")));
            assertEquals("{\"code\":404,\"message\":\"台风不存在\",\"success\":false}",
                    JsonUtil.toJson(Result.notFound("台风不存在")));
        }
    }

    @Nested
    @DisplayName("反序列化")
    class Deserialization {

        @Test
        @DisplayName("null / 空串 / 纯空白返回 null，不抛异常")
        void blankInputReturnsNull() {
            assertNull(JsonUtil.parse(null, Result.class));
            assertNull(JsonUtil.parse("", Result.class));
            assertNull(JsonUtil.parse("   ", Result.class));
        }

        @Test
        @DisplayName("标准 JSON 可反序列化到 Result")
        void parsesResult() {
            Result<?> r = JsonUtil.parse("{\"code\":200,\"message\":\"ok\"}", Result.class);
            assertNotNull(r);
            assertEquals(200, r.getCode());
            assertEquals("ok", r.getMessage());
            assertNull(r.getData());
        }

        @Test
        @DisplayName("未知字段被忽略（前端多传参数不应报 400）")
        void ignoresUnknownProperties() {
            Demo d = JsonUtil.parse(
                    "{\"code\":1,\"unknownField\":\"x\",\"nested\":{\"a\":1}}", Demo.class);
            assertNotNull(d);
            assertEquals(1, d.getCode());
        }

        @Test
        @DisplayName("时间字符串按本项目格式反序列化回 LocalDateTime")
        void parsesProjectDateTime() {
            Demo d = JsonUtil.parse(
                    "{\"time\":\"2026-09-13 08:00:00\",\"day\":\"2026-09-13\"}", Demo.class);
            assertNotNull(d);
            assertEquals(LocalDateTime.of(2026, 9, 13, 8, 0, 0), d.getTime());
            assertEquals(LocalDate.of(2026, 9, 13), d.getDay());
        }

        @Test
        @DisplayName("格式错误的 JSON 抛 400，由 Servlet 统一转成响应")
        void malformedJsonThrowsBadRequest() {
            BusinessException e = assertThrows(BusinessException.class,
                    () -> JsonUtil.parse("{\"code\":", Result.class));
            assertEquals(Result.CODE_BAD_REQUEST, e.getCode());
            assertTrue(e.getMessage().contains("JSON 格式错误"));
        }
    }

    @Nested
    @DisplayName("readBody · 读取请求体")
    class ReadBody {

        private HttpServletRequest reqWithBody(String body) throws Exception {
            HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
            Mockito.when(req.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
            return req;
        }

        @Test
        @DisplayName("正常请求体反序列化为对象")
        void readsJsonBody() throws Exception {
            HttpServletRequest req = reqWithBody("{\"code\":200,\"message\":\"ok\"}");
            Result<?> r = JsonUtil.readBody(req, Result.class);
            assertNotNull(r);
            assertEquals(200, r.getCode());
            assertEquals("ok", r.getMessage());
            Mockito.verify(req).setCharacterEncoding("UTF-8");
        }

        @Test
        @DisplayName("空请求体返回 null（等价于未传参数）")
        void emptyBodyReturnsNull() throws Exception {
            HttpServletRequest req = reqWithBody("");
            assertNull(JsonUtil.readBody(req, Result.class));
        }

        @Test
        @DisplayName("多行请求体（含换行）会被拼接后再解析")
        void multiLineBodyIsJoined() throws Exception {
            HttpServletRequest req = reqWithBody("{\n  \"code\": 7\n}\n");
            Result<?> r = JsonUtil.readBody(req, Result.class);
            assertNotNull(r);
            assertEquals(7, r.getCode());
        }
    }

    @Nested
    @DisplayName("write · 写出响应")
    class Write {

        @Test
        @DisplayName("写出 JSON 正文并设置 UTF-8 与 no-store 头")
        void writesJsonWithHeaders() throws Exception {
            HttpServletResponse resp = Mockito.mock(HttpServletResponse.class);
            StringWriter sw = new StringWriter();
            Mockito.when(resp.getWriter()).thenReturn(new PrintWriter(sw));

            JsonUtil.write(resp, Result.ok("ok"));

            assertEquals("{\"code\":200,\"message\":\"success\",\"data\":\"ok\",\"success\":true}",
                    sw.toString());
            Mockito.verify(resp).setContentType("application/json;charset=UTF-8");
            Mockito.verify(resp).setCharacterEncoding("UTF-8");
            Mockito.verify(resp).setHeader("Cache-Control", "no-store");
        }

        @Test
        @DisplayName("写出失败响应时 data 不出现（NON_NULL），success 为 false")
        void writesFailureWithoutData() throws Exception {
            HttpServletResponse resp = Mockito.mock(HttpServletResponse.class);
            StringWriter sw = new StringWriter();
            Mockito.when(resp.getWriter()).thenReturn(new PrintWriter(sw));

            JsonUtil.write(resp, Result.notFound("台风不存在"));

            assertEquals("{\"code\":404,\"message\":\"台风不存在\",\"success\":false}", sw.toString());
        }
    }

    @Test
    @DisplayName("mapper() 返回全局单例，多次调用是同一个实例")
    void mapperIsSingleton() {
        assertNotNull(JsonUtil.mapper());
        assertSame(JsonUtil.mapper(), JsonUtil.mapper());
    }

    @Test
    @DisplayName("日期格式常量与前端约定一致")
    void patternConstants() {
        assertEquals("yyyy-MM-dd HH:mm:ss", JsonUtil.PATTERN_DATETIME);
        assertEquals("yyyy-MM-dd", JsonUtil.PATTERN_DATE);
    }
}
