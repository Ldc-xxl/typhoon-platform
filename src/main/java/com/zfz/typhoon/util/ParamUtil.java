package com.zfz.typhoon.util;

import javax.servlet.http.HttpServletRequest;

/**
 * HTTP 请求参数解析工具。
 *
 * <p>课程要求（阶段三 · ② Servlet 开发）：Servlet 负责处理 GET/POST/PUT/DELETE 请求，
 * 但<b>不包含业务逻辑</b>。因此"取参数 + 必填校验 + 类型转换"这类纯技术动作
 * 全部收敛到本类，Servlet 里只允许出现一行式调用：
 *
 * <pre>{@code
 * int id = ParamUtil.requireInt(req, "typhoonId");
 * String status = ParamUtil.str(req, "status", null);
 * }</pre>
 *
 * <p>{@link #intVal} 系列对"空串 / 非数字"一律按参数错误处理并抛出
 * {@link BusinessException}（code = 400），由 Servlet 统一转成响应，
 * 避免 {@code NumberFormatException} 直接冒泡成 500。
 *
 * @author 追风者项目组
 */
public final class ParamUtil {

    private ParamUtil() {
        // 工具类禁止实例化
    }

    /* ------------------------------------------------------------------ */
    /*  字符串                                                              */
    /* ------------------------------------------------------------------ */

    /**
     * 取字符串参数并去除首尾空白。
     *
     * @param req  HTTP 请求
     * @param name 参数名
     * @return 参数值；不存在或为空白时返回 null
     */
    public static String str(HttpServletRequest req, String name) {
        return str(req, name, null);
    }

    /**
     * 取字符串参数并去除首尾空白，缺省时返回默认值。
     *
     * @param req          HTTP 请求
     * @param name         参数名
     * @param defaultValue 默认值
     * @return 参数值
     */
    public static String str(HttpServletRequest req, String name, String defaultValue) {
        String v = req.getParameter(name);
        if (v == null) {
            return defaultValue;
        }
        v = v.trim();
        return v.isEmpty() ? defaultValue : v;
    }

    /**
     * 取必填字符串参数。
     *
     * @param req  HTTP 请求
     * @param name 参数名
     * @return 参数值
     * @throws BusinessException 参数缺失时抛出（400），message 为「缺少必填参数：xxx」
     */
    public static String requireStr(HttpServletRequest req, String name) {
        String v = str(req, name);
        if (v == null) {
            throw BusinessException.badRequest("缺少必填参数：" + name);
        }
        return v;
    }

    /**
     * 校验字符串非空，空则抛业务异常。用于校验 JSON 请求体中的字段。
     *
     * @param value 待校验值
     * @param name  字段名（用于提示文案）
     * @return 原值
     * @throws BusinessException 为空时抛出（400）
     */
    public static String requireNonEmpty(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw BusinessException.badRequest("缺少必填字段：" + name);
        }
        return value.trim();
    }

    /* ------------------------------------------------------------------ */
    /*  整数                                                                */
    /* ------------------------------------------------------------------ */

    /**
     * 取整数参数。
     *
     * @param req          HTTP 请求
     * @param name         参数名
     * @param defaultValue 缺省值
     * @return 参数值；未传时返回默认值
     * @throws BusinessException 传入但格式非法时抛出（400）
     */
    public static int intVal(HttpServletRequest req, String name, int defaultValue) {
        String v = str(req, name);
        if (v == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw BusinessException.badRequest("参数 " + name + " 应为整数，实际收到：" + v);
        }
    }

    /**
     * 取整数参数并做区间校验。
     *
     * @param req          HTTP 请求
     * @param name         参数名
     * @param defaultValue 缺省值
     * @param min          最小值（含）
     * @param max          最大值（含）
     * @return 参数值
     * @throws BusinessException 格式非法或越界时抛出（400）
     */
    public static int intVal(HttpServletRequest req, String name, int defaultValue, int min, int max) {
        int v = intVal(req, name, defaultValue);
        return checkRange(v, name, min, max);
    }

    /**
     * 取必填整数参数。
     *
     * @param req  HTTP 请求
     * @param name 参数名
     * @return 参数值
     * @throws BusinessException 缺失或格式非法时抛出（400）
     */
    public static int requireInt(HttpServletRequest req, String name) {
        String v = requireStr(req, name);
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw BusinessException.badRequest("参数 " + name + " 应为整数，实际收到：" + v);
        }
    }

    /**
     * 校验整数区间。
     *
     * @param value 待校验值
     * @param name  字段名
     * @param min   最小值（含）
     * @param max   最大值（含）
     * @return 原值
     * @throws BusinessException 越界时抛出（400）
     */
    public static int checkRange(int value, String name, int min, int max) {
        if (value < min || value > max) {
            throw BusinessException.badRequest(
                    "参数 " + name + " 超出允许范围 [" + min + ", " + max + "]，实际：" + value);
        }
        return value;
    }

    /* ------------------------------------------------------------------ */
    /*  长整数 / 浮点 / 布尔                                                 */
    /* ------------------------------------------------------------------ */

    /**
     * 取长整数参数。
     *
     * @param req          HTTP 请求
     * @param name         参数名
     * @param defaultValue 缺省值
     * @return 参数值
     * @throws BusinessException 格式非法时抛出（400）
     */
    public static long longVal(HttpServletRequest req, String name, long defaultValue) {
        String v = str(req, name);
        if (v == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            throw BusinessException.badRequest("参数 " + name + " 应为长整数，实际收到：" + v);
        }
    }

    /**
     * 取浮点参数。
     *
     * @param req          HTTP 请求
     * @param name         参数名
     * @param defaultValue 缺省值
     * @return 参数值
     * @throws BusinessException 格式非法时抛出（400）
     */
    public static double doubleVal(HttpServletRequest req, String name, double defaultValue) {
        String v = str(req, name);
        if (v == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            throw BusinessException.badRequest("参数 " + name + " 应为数字，实际收到：" + v);
        }
    }

    /**
     * 取布尔参数。接受 {@code true/false、1/0、yes/no}。
     *
     * @param req          HTTP 请求
     * @param name         参数名
     * @param defaultValue 缺省值
     * @return 参数值
     */
    public static boolean boolVal(HttpServletRequest req, String name, boolean defaultValue) {
        String v = str(req, name);
        if (v == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(v) || "1".equals(v)
                || "yes".equalsIgnoreCase(v) || "on".equalsIgnoreCase(v);
    }

    /**
     * 取 BOOLEAN 包装类型（可为 null，用于区分"未填写"与"填了 false"）。
     *
     * @param value 原始值
     * @return 包装后的 Boolean；入参为 null 时返回 null
     */
    public static Boolean nullableBool(Boolean value) {
        return value;
    }

    /* ------------------------------------------------------------------ */
    /*  其他                                                                */
    /* ------------------------------------------------------------------ */

    /**
     * 返回第一个非空白字符串，用于参数回退（如先取 id 再取 typhoonId）。
     *
     * @param values 候选值
     * @return 第一个非空白值；全部为空时返回 null
     */
    public static String firstNonEmpty(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        }
        return null;
    }

    /**
     * 解析并校验路径中的 id 段。
     *
     * <p>Servlet 3.0+ 不支持 {@code /api/typhoons/{id}} 占位符，需用通配符 +
     * {@code pathInfo} 手动解析（接口文档第 5 节的统一范式）。本方法把
     * "取段 + 转数字 + 报错" 收敛成一行。
     *
     * @param segment 路径段，例："13"
     * @param name    资源名，用于提示文案，例："台风 id"
     * @return 解析出的主键
     * @throws BusinessException 段为空或格式非法时抛出（400）
     */
    public static long requirePathId(String segment, String name) {
        if (segment == null || segment.trim().isEmpty()) {
            throw BusinessException.badRequest("缺少" + name);
        }
        try {
            return Long.parseLong(segment.trim());
        } catch (NumberFormatException e) {
            throw BusinessException.badRequest(name + "格式错误，应为数字，实际收到：" + segment);
        }
    }
}
