package com.zfz.typhoon.util;

/**
 * 业务异常。
 *
 * <p>课程要求（阶段三 · ① Service 层）：<b>Service 层需处理业务异常并向上抛出统一异常，
 * Servlet 不包含业务逻辑</b>。本类即那个"统一异常"：
 *
 * <ul>
 *   <li>Service 层做业务校验（如台风 id 不存在、参数越界），不满足时抛本异常；</li>
 *   <li>Servlet 层 {@code catch (BusinessException e)} 后转成
 *       {@link Result#fail(int, String)}，不再做任何业务判断；</li>
 *   <li>{@link #getCode()} 直接作为响应 code，前端据此决定提示文案或跳转。</li>
 * </ul>
 *
 * <p>继承 {@code RuntimeException} 而非受检异常，是为了不污染 DAO / Service 接口签名，
 * 同时保持"抛出点即终止点"的语义。
 *
 * @author 追风者项目组
 * @see Result
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 业务状态码，取 {@link Result} 中的常量 */
    private final int code;

    /**
     * 以默认的 400（参数校验失败）构造业务异常。
     *
     * @param message 失败原因
     */
    public BusinessException(String message) {
        this(Result.CODE_BAD_REQUEST, message);
    }

    /**
     * 构造业务异常。
     *
     * @param code    业务状态码
     * @param message 失败原因
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 构造业务异常并保留根因。
     *
     * @param code    业务状态码
     * @param message 失败原因
     * @param cause   根因
     */
    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * 获取业务状态码。
     *
     * @return 业务状态码
     */
    public int getCode() {
        return code;
    }

    /**
     * 资源不存在（404）。
     *
     * @param message 失败原因
     * @return 业务异常
     */
    public static BusinessException notFound(String message) {
        return new BusinessException(Result.CODE_NOT_FOUND, message);
    }

    /**
     * 参数校验失败（400）。
     *
     * @param message 失败原因
     * @return 业务异常
     */
    public static BusinessException badRequest(String message) {
        return new BusinessException(Result.CODE_BAD_REQUEST, message);
    }

    /**
     * 无权限（403）。
     *
     * @param message 失败原因
     * @return 业务异常
     */
    public static BusinessException forbidden(String message) {
        return new BusinessException(Result.CODE_FORBIDDEN, message);
    }
}
