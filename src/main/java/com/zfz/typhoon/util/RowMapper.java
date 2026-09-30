package com.zfz.typhoon.util;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 结果集行映射器：把 {@link ResultSet} 的当前行转换为一个对象。
 *
 * <p>课程要求（阶段二 · ③「工具类」）：DAO 层应消除重复的
 * {@code while (rs.next()) { ... }} 样板代码。本接口是
 * {@link JdbcTemplate} 与各 DAO 实现之间的约定——
 * <b>SQL 与字段映射写在 DAO 实现里，连接与异常处理交给模板</b>。
 *
 * <p>实现示例：
 * <pre>{@code
 * public static final RowMapper<Typhoon> MAPPER = new RowMapper<Typhoon>() {
 *     @Override
 *     public Typhoon map(ResultSet rs) throws SQLException {
 *         Typhoon t = new Typhoon();
 *         t.setTyphoonId(RsUtil.getLong(rs, "typhoon_id"));
 *         t.setChineseName(RsUtil.getString(rs, "chinese_name"));
 *         return t;
 *     }
 * };
 * }</pre>
 *
 * @param <T> 映射出的对象类型
 * @author 追风者项目组
 * @see JdbcTemplate
 * @see RsUtil
 */
public interface RowMapper<T> {

    /**
     * 把结果集当前行映射为对象。
     *
     * <p>调用方（{@link JdbcTemplate}）负责 {@code rs.next()} 与关闭资源，
     * 实现方<b>不要</b>调用 {@code next()} 或 {@code close()}。
     *
     * @param rs 已定位到当前行的结果集
     * @return 映射出的对象（可为 null，表示跳过该行）
     * @throws SQLException 读取字段失败
     */
    T map(ResultSet rs) throws SQLException;
}
