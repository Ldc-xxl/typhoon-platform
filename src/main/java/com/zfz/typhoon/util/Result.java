package com.zfz.typhoon.util;

import java.io.Serializable;

/**
 * 统一响应封装。
 *
 * <p>课程要求（阶段三 · ③ JSON 交互）：使用 Jackson 实现 Java 对象与 JSON 的
 * 序列化/反序列化，<b>统一响应格式</b>。本类的 JSON 形态即前端约定的：
 *
 * <pre>{@code
 * { "code": 200, "message": "success", "data": { } }
 * }</pre>
 *
 * <p>前端判定成功：{@code code === 200 || code === 0}，此时从 {@code data} 取值；
 * 判定失败：{@code message} 中的文案会作为 Error 抛出，供页面统一 toast 提示。
 *
 * <p>约定：{@code Servlet} 层不写业务判断，所有异常在 Servlet 层统一转换成
 * {@code Result}（见 {@link BusinessException}）。
 *
 * @param <T> 业务数据类型
 * @author 追风者项目组
 * @see BusinessException
 * @see JsonUtil#write(javax.servlet.http.HttpServletResponse, Object)
 */
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成功状态码 */
    public static final int CODE_OK = 200;
    /** 参数校验失败 */
    public static final int CODE_BAD_REQUEST = 400;
    /** 未登录或会话过期 */
    public static final int CODE_UNAUTHORIZED = 401;
    /** 已登录但无权限 */
    public static final int CODE_FORBIDDEN = 403;
    /** 资源不存在 */
    public static final int CODE_NOT_FOUND = 404;
    /** 服务器内部错误 */
    public static final int CODE_SERVER_ERROR = 500;

    /** 业务状态码 */
    private int code;

    /** 提示信息；失败时前端直接 toast 该文案 */
    private String message;

    /** 业务数据；失败时为 null */
    private T data;

    /** Jackson 反序列化需要无参构造 */
    public Result() {
    }

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /* ------------------------------------------------------------------ */
    /*  成功                                                                */
    /* ------------------------------------------------------------------ */

    /**
     * 构造成功响应（带数据）。
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return 成功响应
     */
    public static <T> Result<T> ok(T data) {
        return new Result<T>(CODE_OK, "success", data);
    }

    /**
     * 构造成功响应（无数据），适用于删除、状态切换等只需告知结果的接口。
     *
     * @return 成功响应
     */
    public static Result<Void> ok() {
        return new Result<Void>(CODE_OK, "success", null);
    }

    /**
     * 构造成功响应并自定义提示文案。
     *
     * @param message 提示文案
     * @param data    业务数据
     * @param <T>     数据类型
     * @return 成功响应
     */
    public static <T> Result<T> ok(String message, T data) {
        return new Result<T>(CODE_OK, message, data);
    }

    /* ------------------------------------------------------------------ */
    /*  失败                                                                */
    /* ------------------------------------------------------------------ */

    /**
     * 构造失败响应。
     *
     * @param code    业务状态码，取本类常量
     * @param message 失败原因，会作为前端 toast 文案
     * @param <T>     数据类型
     * @return 失败响应
     */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<T>(code, message, null);
    }

    /**
     * 参数校验失败（400）。
     *
     * @param message 失败原因
     * @param <T>     数据类型
     * @return 失败响应
     */
    public static <T> Result<T> badRequest(String message) {
        return fail(CODE_BAD_REQUEST, message);
    }

    /**
     * 资源不存在（404）。
     *
     * @param message 失败原因
     * @param <T>     数据类型
     * @return 失败响应
     */
    public static <T> Result<T> notFound(String message) {
        return fail(CODE_NOT_FOUND, message);
    }

    /**
     * 服务器内部错误（500）。
     *
     * @param message 失败原因
     * @param <T>     数据类型
     * @return 失败响应
     */
    public static <T> Result<T> error(String message) {
        return fail(CODE_SERVER_ERROR, message);
    }

    /* ------------------------------------------------------------------ */
    /*  getter / setter                                                     */
    /* ------------------------------------------------------------------ */

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

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    /**
     * 判断本响应是否为成功响应。
     *
     * @return 成功返回 true
     */
    public boolean isSuccess() {
        return code == CODE_OK;
    }

    @Override
    public String toString() {
        return "Result{code=" + code + ", message='" + message + "', data=" + data + '}';
    }
}
