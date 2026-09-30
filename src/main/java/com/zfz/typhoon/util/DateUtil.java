package com.zfz.typhoon.util;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * 日期格式化工具。
 *
 * <p>课程要求（阶段二 · ③ 工具类）：编写日期格式化工具。
 *
 * <p>统一口径：<b>前后端所有的日期时间字符串都是 {@code yyyy-MM-dd HH:mm:ss}</b>。
 * 前端 mock 数据、接口文档示例、Jackson 序列化输出三处必须一致，否则页面上的
 * 时间轴、轨迹时刻会显示成 {@code 2026-09-13T08:00} 这类 ISO 格式。
 *
 * <p>与 JDBC 的衔接：MySQL 8 驱动支持
 * {@code rs.getObject("obs_time", LocalDateTime.class)}，
 * 因此 DAO 层优先直接取 {@code LocalDateTime}；仅在必要时才用
 * {@link #toLocalDateTime(Timestamp)} 转换。
 *
 * @author 追风者项目组
 */
public final class DateUtil {

    /** 统一的日期时间格式 */
    public static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern(JsonUtil.PATTERN_DATETIME);
    /** 统一的日期格式 */
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern(JsonUtil.PATTERN_DATE);

    private DateUtil() {
        // 工具类禁止实例化
    }

    /**
     * 当前时间。
     *
     * @return 当前 LocalDateTime
     */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /**
     * 格式化为 {@code yyyy-MM-dd HH:mm:ss}。
     *
     * @param time 时间
     * @return 格式化字符串；入参为 null 时返回 null
     */
    public static String format(LocalDateTime time) {
        return time == null ? null : time.format(DATETIME);
    }

    /**
     * 格式化为 {@code yyyy-MM-dd}。
     *
     * @param date 日期
     * @return 格式化字符串；入参为 null 时返回 null
     */
    public static String format(LocalDate date) {
        return date == null ? null : date.format(DATE);
    }

    /**
     * 解析 {@code yyyy-MM-dd HH:mm:ss}。
     *
     * @param text 时间字符串
     * @return 解析结果；入参为空白时返回 null
     * @throws BusinessException 格式非法时抛出（400）
     */
    public static LocalDateTime parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim(), DATETIME);
        } catch (DateTimeParseException e) {
            throw BusinessException.badRequest(
                    "时间格式应为 yyyy-MM-dd HH:mm:ss，实际收到：" + text);
        }
    }

    /**
     * 宽松解析：先按 {@code yyyy-MM-dd HH:mm:ss}，失败再按 {@code yyyy-MM-dd}。
     *
     * <p>用于接收前端表单里可能只填了日期的情况。
     *
     * @param text 时间字符串
     * @return 解析结果；入参为空白时返回 null
     * @throws BusinessException 两种格式都不匹配时抛出（400）
     */
    public static LocalDateTime parseLenient(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String t = text.trim();
        try {
            return LocalDateTime.parse(t, DATETIME);
        } catch (DateTimeParseException ignored) {
            // 继续尝试纯日期
        }
        try {
            return LocalDate.parse(t, DATE).atStartOfDay();
        } catch (DateTimeParseException e) {
            throw BusinessException.badRequest(
                    "时间格式应为 yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd，实际收到：" + text);
        }
    }

    /**
     * 取出日期所在年份。
     *
     * @param time 时间
     * @return 年份；入参为 null 时返回 0
     */
    public static int yearOf(LocalDateTime time) {
        return time == null ? 0 : time.getYear();
    }

    /**
     * 计算 两个时刻相差的小时数（可为负）。
     *
     * @param from 起始时刻
     * @param to   结束时刻
     * @return 相差小时数
     */
    public static double hoursBetween(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            return 0D;
        }
        return ChronoUnit.MINUTES.between(from, to) / 60.0D;
    }

    /**
     * 在给定时刻上增加小时数。
     *
     * @param base  基准时刻
     * @param hours 小时数（可为负）
     * @return 计算后的时刻；入参为 null 时返回 null
     */
    public static LocalDateTime plusHours(LocalDateTime base, double hours) {
        if (base == null) {
            return null;
        }
        return base.plusMinutes(Math.round(hours * 60));
    }

    /**
     * {@link Timestamp} 转 {@link LocalDateTime}。
     *
     * @param ts JDBC 时间戳
     * @return 转换结果；入参为 null 时返回 null
     */
    public static LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    /**
     * {@link LocalDateTime} 转 {@link Timestamp}。
     *
     * @param time 时间
     * @return JDBC 时间戳；入参为 null 时返回 null
     */
    public static Timestamp toTimestamp(LocalDateTime time) {
        return time == null ? null : Timestamp.valueOf(time);
    }
}
