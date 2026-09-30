package com.zfz.typhoon.util;

/**
 * 数据访问异常 —— SQL 异常处理类。
 *
 * <p>课程要求（阶段二 · ③ 工具类）：编写"<b>SQL 异常处理类</b>"。
 *
 * <p>设计取舍：
 * <ul>
 *   <li>{@link java.sql.SQLException} 是受检异常。若让 DAO 接口声明
 *       {@code throws SQLException}，则每一层（Service、Servlet）都要跟着写
 *       {@code throws} 或 try-catch，代码会被样板语句淹没；</li>
 *   <li>因此 DAO 实现内部捕获 {@link java.sql.SQLException}，
 *       在此处<b>统一包装、补全上下文（SQL 语句 + 参数）后重新抛出</b>；</li>
 *   <li>DAO 接口签名因此保持干净；Service 层只需关心业务异常；
 *       最外层 {@code ExceptionHandler} 把它映射成 HTTP 500 与统一响应。</li>
 * </ul>
 *
 * <p>不向客户端暴露原始 SQL 语句与数据库结构（安全要求），
 * 完整信息只写入服务端日志。
 *
 * @author 追风者项目组
 * @see DBUtil
 */
public class DataAccessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 出错时执行的 SQL（仅用于服务端日志，不返回前端） */
    private final String sql;

    /**
     * 构造数据访问异常。
     *
     * @param message 说明
     * @param sql     出错的 SQL
     * @param cause   根因
     */
    public DataAccessException(String message, String sql, Throwable cause) {
        super(message, cause);
        this.sql = sql;
    }

    /**
     * 构造数据访问异常（无 SQL 上下文）。
     *
     * @param message 说明
     * @param cause   根因
     */
    public DataAccessException(String message, Throwable cause) {
        this(message, null, cause);
    }

    /**
     * 获取出错的 SQL（服务端日志用）。
     *
     * @return SQL 语句；可能为 null
     */
    public String getSql() {
        return sql;
    }

    /**
     * 面向用户的安全提示（不含 SQL 与库表结构）。
     *
     * @return 安全提示文案
     */
    public String toUserMessage() {
        return "数据服务暂时不可用，请稍后重试";
    }
}
