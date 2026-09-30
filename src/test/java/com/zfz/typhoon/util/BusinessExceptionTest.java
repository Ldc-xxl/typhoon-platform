package com.zfz.typhoon.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BusinessException} 单元测试。
 *
 * <p>它是 Service 层与 Servlet 层之间唯一的"业务失败"通道：
 * Service 抛它，Servlet 把 {@link #getCode()} 原样抄进 {@link Result}。
 * 因此断言集中在两处——<b>默认 code 是否为 400</b>，
 * 以及<b>三个静态工厂是否映射到 400 / 403 / 404</b>。
 */
@DisplayName("BusinessException · 业务异常")
class BusinessExceptionTest {

    @Nested
    @DisplayName("构造与默认状态码")
    class Construction {

        @Test
        @DisplayName("单参构造默认取 400（参数校验失败）")
        void defaultCodeIsBadRequest() {
            BusinessException e = new BusinessException("台风 id 不合法");
            assertEquals(Result.CODE_BAD_REQUEST, e.getCode());
            assertEquals("台风 id 不合法", e.getMessage());
            assertNull(e.getCause());
        }

        @Test
        @DisplayName("双参构造可指定任意业务状态码")
        void explicitCode() {
            assertEquals(Result.CODE_FORBIDDEN, new BusinessException(Result.CODE_FORBIDDEN, "无权限").getCode());
            assertEquals(Result.CODE_NOT_FOUND, new BusinessException(Result.CODE_NOT_FOUND, "不存在").getCode());
        }

        @Test
        @DisplayName("三参构造保留根因，便于日志追溯")
        void keepsCause() {
            RuntimeException cause = new IllegalStateException("底层故障");
            BusinessException e = new BusinessException(Result.CODE_SERVER_ERROR, "处理失败", cause);
            assertSame(cause, e.getCause());
            assertEquals(Result.CODE_SERVER_ERROR, e.getCode());
        }

        @Test
        @DisplayName("继承自 RuntimeException，因此不污染 DAO / Service 接口签名")
        void isUnchecked() {
            assertTrue(RuntimeException.class.isAssignableFrom(BusinessException.class));
        }
    }

    @Nested
    @DisplayName("静态工厂")
    class Factories {

        @Test
        @DisplayName("badRequest → 400")
        void badRequest() {
            BusinessException e = BusinessException.badRequest("缺少必填参数：username");
            assertEquals(Result.CODE_BAD_REQUEST, e.getCode());
            assertEquals("缺少必填参数：username", e.getMessage());
        }

        @Test
        @DisplayName("notFound → 404")
        void notFound() {
            BusinessException e = BusinessException.notFound("未找到编号 99 的台风");
            assertEquals(Result.CODE_NOT_FOUND, e.getCode());
        }

        @Test
        @DisplayName("forbidden → 403")
        void forbidden() {
            BusinessException e = BusinessException.forbidden("仅管理员可执行该操作");
            assertEquals(Result.CODE_FORBIDDEN, e.getCode());
            assertEquals("仅管理员可执行该操作", e.getMessage());
        }

        @Test
        @DisplayName("工厂方法产出的 code 可直接作为 Result 的 code")
        void codeIsUsableAsResultCode() {
            Result<Void> r = Result.fail(BusinessException.notFound("x").getCode(), "x");
            assertEquals(Result.CODE_NOT_FOUND, r.getCode());
        }
    }
}
