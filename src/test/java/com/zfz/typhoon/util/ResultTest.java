package com.zfz.typhoon.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Result} 单元测试。
 *
 * <p>被测对象是前后端之间唯一的响应契约载体。它的四个字段
 * （{@code code / message / data / success}）与常量取值一旦漂移，
 * 前端 {@code api.js} 的成功判定会整体失效，页面会集体显示"请求失败"，
 * 因此这里把常量值与工厂方法的默认文案都钉死。
 */
@DisplayName("Result · 统一响应封装")
class ResultTest {

    @Nested
    @DisplayName("状态码常量")
    class Codes {

        @Test
        @DisplayName("六个状态码取值与前端约定一致")
        void constantValues() {
            assertEquals(200, Result.CODE_OK);
            assertEquals(400, Result.CODE_BAD_REQUEST);
            assertEquals(401, Result.CODE_UNAUTHORIZED);
            assertEquals(403, Result.CODE_FORBIDDEN);
            assertEquals(404, Result.CODE_NOT_FOUND);
            assertEquals(500, Result.CODE_SERVER_ERROR);
        }
    }

    @Nested
    @DisplayName("成功响应工厂方法")
    class SuccessFactories {

        @Test
        @DisplayName("ok(data) 的 code 为 200、默认文案 success、isSuccess 为真")
        void okWithData() {
            List<String> payload = Arrays.asList("a", "b");
            Result<List<String>> r = Result.ok(payload);
            assertEquals(Result.CODE_OK, r.getCode());
            assertEquals("success", r.getMessage());
            assertEquals(payload, r.getData());
            assertTrue(r.isSuccess());
        }

        @Test
        @DisplayName("ok() 无数据版本用于删除、状态切换等接口")
        void okWithoutData() {
            Result<Void> r = Result.ok();
            assertEquals(Result.CODE_OK, r.getCode());
            assertNull(r.getData());
            assertTrue(r.isSuccess());
        }

        @Test
        @DisplayName("ok(message, data) 允许自定义提示文案")
        void okWithCustomMessage() {
            Result<Integer> r = Result.ok("已收藏", 1);
            assertEquals("已收藏", r.getMessage());
            assertEquals(Integer.valueOf(1), r.getData());
            assertTrue(r.isSuccess());
        }

        @Test
        @DisplayName("data 本身为 null 时仍算成功（区别于失败）")
        void okWithNullData() {
            Result<String> r = Result.ok((String) null);
            assertTrue(r.isSuccess());
            assertEquals(Result.CODE_OK, r.getCode());
        }
    }

    @Nested
    @DisplayName("失败响应工厂方法")
    class FailureFactories {

        @Test
        @DisplayName("fail(code, message) 保留 code 且 data 恒为 null")
        void failCarriesCodeAndNullData() {
            Result<String> r = Result.fail(Result.CODE_FORBIDDEN, "没有权限");
            assertEquals(Result.CODE_FORBIDDEN, r.getCode());
            assertEquals("没有权限", r.getMessage());
            assertNull(r.getData());
            assertFalse(r.isSuccess());
        }

        @Test
        @DisplayName("badRequest / notFound / error 分别对应 400 / 404 / 500")
        void shorthandFailures() {
            assertEquals(Result.CODE_BAD_REQUEST, Result.badRequest("参数错误").getCode());
            assertEquals(Result.CODE_NOT_FOUND, Result.notFound("台风不存在").getCode());
            assertEquals(Result.CODE_SERVER_ERROR, Result.error("服务异常").getCode());
        }

        @Test
        @DisplayName("每一种失败响应 isSuccess 都为假")
        void failuresAreNotSuccess() {
            assertFalse(Result.badRequest("x").isSuccess());
            assertFalse(Result.notFound("x").isSuccess());
            assertFalse(Result.error("x").isSuccess());
            assertFalse(Result.fail(401, "x").isSuccess());
        }
    }

    @Nested
    @DisplayName("序列化与访问器")
    class Accessors {

        @Test
        @DisplayName("Jackson 反序列化所需的无参构造可用，setter 可改写全部字段")
        void mutableThroughSetters() {
            Result<String> r = new Result<String>();
            r.setCode(Result.CODE_UNAUTHORIZED);
            r.setMessage("请先登录");
            r.setData("x");
            assertEquals(401, r.getCode());
            assertEquals("请先登录", r.getMessage());
            assertEquals("x", r.getData());
            assertFalse(r.isSuccess());
        }

        @Test
        @DisplayName("三参构造直接落值")
        void threeArgConstructor() {
            Result<Integer> r = new Result<Integer>(Result.CODE_OK, "success", 7);
            assertEquals(Result.CODE_OK, r.getCode());
            assertEquals("success", r.getMessage());
            assertEquals(Integer.valueOf(7), r.getData());
            assertTrue(r.isSuccess());
        }

        @Test
        @DisplayName("toString 含 code 与 message，便于日志排查")
        void toStringContainsKeyFields() {
            String s = Result.notFound("台风 99 不存在").toString();
            assertTrue(s.contains("404"));
            assertTrue(s.contains("台风 99 不存在"));
        }
    }
}
