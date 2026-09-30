package com.zfz.typhoon.util;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * 结果集字段读取工具：把 JDBC 的原始类型安全转换为 Java 8 类型。
 *
 * <p>课程要求（阶段二 · ③「工具类」）。设计动机：
 * <ol>
 *   <li><b>空值安全</b>——{@code rs.getInt()} 对 NULL 返回 0，会把"无数据"
 *       悄悄变成"值为 0"，本类统一改为返回 {@code null}；</li>
 *   <li><b>类型统一</b>——数据库 datetime → {@link LocalDateTime}，
 *       tinyint(1) → {@link Boolean}，decimal → {@link Double}，
 *       避免每个映射器重复写 {@code new Timestamp(...).toLocalDateTime()}；</li>
 *   <li><b>可读性</b>——调用处只写 {@code RsUtil.getLong(rs, "typhoon_id")}，
 *       字段名一目了然，改表结构时易于定位。</li>
 * </ol>
 *
 * <p>注意：{@code tinyint} 在 MySQL 驱动下默认按整数读取，
 * 本类的 {@link #getBoolean} 用 {@code getInt() != 0} 判定，
 * 因此既能读 {@code tinyint(1)} 也能读 {@code tinyint(4)}。
 *
 * @author 追风者项目组
 * @see RowMapper
 */
public final class RsUtil {

    private RsUtil() {
        // 工具类禁止实例化
    }

    /**
     * 读取 bigint / int 列，NULL 返回 null。
     *
     * @param rs    结果集
     * @param column 列名
     * @return Long 值或 null
     * @throws SQLException 列不存在或读取失败
     */
    public static Long getLong(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : Long.valueOf(v);
    }

    /**
     * 读取 int / tinyint 列，NULL 返回 null。
     *
     * @param rs    结果集
     * @param column 列名
     * @return Integer 值或 null
     * @throws SQLException 列不存在或读取失败
     */
    public static Integer getInt(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : Integer.valueOf(v);
    }

    /**
     * 读取 decimal / double 列，NULL 返回 null。
     *
     * @param rs    结果集
     * @param column 列名
     * @return Double 值或 null
     * @throws SQLException 列不存在或读取失败
     */
    public static Double getDouble(ResultSet rs, String column) throws SQLException {
        double v = rs.getDouble(column);
        return rs.wasNull() ? null : Double.valueOf(v);
    }

    /**
     * 读取 decimal 列并保留精度（用于金额，避免二进制浮点误差）。
     *
     * @param rs    结果集
     * @param column 列名
     * @return BigDecimal 或 null
     * @throws SQLException 列不存在或读取失败
     */
    public static BigDecimal getDecimal(ResultSet rs, String column) throws SQLException {
        return rs.getBigDecimal(column);
    }

    /**
     * 读取 varchar / text / json 列，NULL 返回 null。
     *
     * @param rs    结果集
     * @param column 列名
     * @return 字符串或 null
     * @throws SQLException 列不存在或读取失败
     */
    public static String getString(ResultSet rs, String column) throws SQLException {
        return rs.getString(column);
    }

    /**
     * 读取 tinyint 布尔语义列，NULL 返回 null。
     *
     * @param rs    结果集
     * @param column 列名
     * @return Boolean 值或 null
     * @throws SQLException 列不存在或读取失败
     */
    public static Boolean getBoolean(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : Boolean.valueOf(v != 0);
    }

    /**
     * 读取 datetime 列为 {@link LocalDateTime}，NULL 返回 null。
     *
     * @param rs    结果集
     * @param column 列名
     * @return LocalDateTime 或 null
     * @throws SQLException 列不存在或读取失败
     */
    public static LocalDateTime getDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toLocalDateTime();
    }
}
