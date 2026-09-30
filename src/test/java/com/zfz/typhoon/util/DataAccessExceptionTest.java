package com.zfz.typhoon.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DataAccessException} 单元测试。
 *
 * <p>覆盖两件事：一是保留出错 SQL 供服务端日志用，
 * 二是 {@link DataAccessException#toUserMessage()} 返回的文案
 * <b>绝不能含 SQL 与库表结构</b>（安全要求），故对其做反向断言。
 */
@DisplayName("DataAccessException · 数据访问异常")
class DataAccessExceptionTest {

    @Test
    @DisplayName("带 SQL 构造时保留 SQL 上下文与根因")
    void keepsSqlAndCause() {
        SQLException cause = new SQLException("Table 'typhoon_monitor.xxx' doesn't exist");
        DataAccessException e = new DataAccessException(
                "查询台风列表失败", "SELECT t.* FROM typhoons t WHERE t.status = ?", cause);

        assertEquals("查询台风列表失败", e.getMessage());
        assertEquals("SELECT t.* FROM typhoons t WHERE t.status = ?", e.getSql());
        assertSame(cause, e.getCause());
    }

    @Test
    @DisplayName("无 SQL 构造时 getSql 返回 null")
    void sqlMayBeNull() {
        DataAccessException e = new DataAccessException("连接池获取连接失败",
                new SQLException("Communications link failure"));
        assertNull(e.getSql());
        assertTrue(e.getMessage().contains("连接池"));
    }

    @Test
    @DisplayName("面向用户的提示是固定文案，不泄漏 SQL 与库表结构")
    void userMessageHidesInternals() {
        String msg = new DataAccessException("查询失败",
                "SELECT password_hash FROM users WHERE username = ?",
                new SQLException("Unknown column 'password_hash'")).toUserMessage();

        assertEquals("数据服务暂时不可用，请稍后重试", msg);
        assertFalse(msg.contains("SELECT"), "用户提示不应包含 SQL");
        assertFalse(msg.contains("password_hash"), "用户提示不应包含库表字段");
        assertFalse(msg.contains("users"), "用户提示不应包含表名");
    }

    @Test
    @DisplayName("继承自 RuntimeException，DAO 接口因此无需声明 throws")
    void isUnchecked() {
        assertTrue(RuntimeException.class.isAssignableFrom(DataAccessException.class));
    }
}
