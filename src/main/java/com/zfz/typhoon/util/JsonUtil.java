package com.zfz.typhoon.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JSON 序列化 / 反序列化工具。
 *
 * <p>课程要求（阶段三 · ③）：使用 <b>Jackson</b> 实现 Java 对象与 JSON 的互转。
 * 本类持有全局唯一的 {@link ObjectMapper}，避免各处 {@code new ObjectMapper()}
 * 导致的时间格式、空值策略不一致。
 *
 * <p>统一约定：
 * <ul>
 *   <li>输出字符集 UTF-8，Content-Type {@code application/json;charset=UTF-8}；</li>
 *   <li>{@link LocalDateTime} 序列化为 {@code yyyy-MM-dd HH:mm:ss}
 *       （与前端 mock 数据、接口文档示例完全一致）；</li>
 *   <li>空值不输出（{@code NON_NULL}），前端可据此判断字段是否存在；</li>
 *   <li>反序列化忽略未知字段，前端多传参数不会导致 400。</li>
 * </ul>
 *
 * @author 追风者项目组
 */
public final class JsonUtil {

    /** 日期时间格式，与前端 mock 数据保持一致 */
    public static final String PATTERN_DATETIME = "yyyy-MM-dd HH:mm:ss";
    /** 日期格式 */
    public static final String PATTERN_DATE = "yyyy-MM-dd";

    /**
     * 请求体字节数上限（1 MB）。
     *
     * <p>本项目 POST/PUT 接口大多是公开接口，且报文都很小（表单 / 推演参数）。
     * 早先 {@link #readBody} 无任何长度校验，一个几十 MB 的请求体就能直接吃掉堆内存，
     * 属于零成本的 DoS 面。这里按"声明长度"先挡一道。
     */
    private static final long MAX_BODY_BYTES = 1024L * 1024L;

    /**
     * 读取过程中的字符数兜底上限。
     *
     * <p>分块传输（{@code Transfer-Encoding: chunked}）时 {@code Content-Length} 为 -1，
     * 上面的字节检查挡不住，因此在边读边累计字符数时再兜一道。
     */
    private static final int MAX_BODY_CHARS = 1024 * 1024;

    /** 序列化彻底失败时的兜底响应体（仍保持 JSON 契约，绝不返回 HTML 500 页） */
    private static final String FALLBACK_JSON =
            "{\"code\":500,\"message\":\"服务器内部错误\",\"success\":false}";

    private static final Logger LOG = Logger.getLogger(JsonUtil.class.getName());

    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern(PATTERN_DATETIME);
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern(PATTERN_DATE);

    /** 全局唯一 ObjectMapper，线程安全（Jackson 官方保证 Mapper 实例线程安全） */
    private static final ObjectMapper MAPPER = createMapper();

    private JsonUtil() {
        // 工具类禁止实例化
    }

    /**
     * 构建并配置全局 ObjectMapper。
     *
     * @return 配置完成的 ObjectMapper
     */
    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // 注册 JSR-310（java.time）支持：LocalDateTime / LocalDate
        JavaTimeModule timeModule = new JavaTimeModule();
        timeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATETIME_FORMATTER));
        timeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DATETIME_FORMATTER));
        timeModule.addSerializer(LocalDate.class, new LocalDateSerializer(DATE_FORMATTER));
        timeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(DATE_FORMATTER));
        mapper.registerModule(timeModule);

        // 日期不要序列化成时间戳数组
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 空值不输出，减少响应体积；前端按字段缺失处理
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        // 反序列化宽松：前端多传字段不报错；空的 JSON 字符串按 null 处理
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);

        return mapper;
    }

    /**
     * 获取全局 ObjectMapper（供确有特殊序列化需求的场景使用）。
     *
     * @return 全局 ObjectMapper
     */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param obj 待序列化对象
     * @return JSON 字符串；序列化失败时返回 {@code "null"}
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new BusinessException(Result.CODE_SERVER_ERROR,
                    "JSON 序列化失败：" + e.getOriginalMessage(), e);
        }
    }

    /**
     * 将 JSON 字符串反序列化为指定类型。
     *
     * @param json  JSON 字符串
     * @param clazz 目标类型
     * @param <T>   目标类型
     * @return 反序列化结果；入参为空时返回 null
     */
    public static <T> T parse(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, clazz);
        } catch (IOException e) {
            throw BusinessException.badRequest("请求体 JSON 格式错误：" + e.getMessage());
        }
    }

    /**
     * 读取请求体并反序列化为指定类型。
     *
     * <p>POST / PUT 接口统一走本方法，避免各 Servlet 各写一套 IO 代码。
     *
     * <p><b>请求体有大小上限</b>（见 {@link #MAX_BODY_BYTES} / {@link #MAX_BODY_CHARS}）：
     * 无上限时，一个超大请求体即可耗尽堆内存，因此先按 {@code Content-Length} 拦截，
     * 分块传输再按累计字符数兜底。
     *
     * @param req   HTTP 请求
     * @param clazz 目标类型
     * @param <T>   目标类型
     * @return 反序列化结果；请求体为空时返回 null
     */
    public static <T> T readBody(HttpServletRequest req, Class<T> clazz) {
        long declared = req.getContentLengthLong();
        if (declared > MAX_BODY_BYTES) {
            throw BusinessException.badRequest("请求体过大：" + declared
                    + " 字节，上限 " + MAX_BODY_BYTES + " 字节");
        }
        StringBuilder sb = new StringBuilder();
        try {
            req.setCharacterEncoding("UTF-8");
            BufferedReader reader = req.getReader();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                // chunked 传输时 Content-Length 为 -1，上面的检查挡不住，
                // 这里按累计字符数再兜一道（BusinessException 是运行时异常，
                // 不会被下面的 IOException 分支捕获，会正常向上抛出）
                if (sb.length() > MAX_BODY_CHARS) {
                    throw BusinessException.badRequest("请求体过大：超过 "
                            + MAX_BODY_CHARS + " 字符");
                }
            }
        } catch (IOException e) {
            throw BusinessException.badRequest("读取请求体失败：" + e.getMessage());
        }
        return parse(sb.toString(), clazz);
    }

    /**
     * 将对象以 JSON 写入响应。
     *
     * <p>统一出口：后端所有接口的响应都经由此方法写出，保证字符编码与格式一致。
     *
     * <p><b>本方法保证不抛异常</b>。它是 {@code BaseServlet} 异常兜底分支的调用目标，
     * 若自身再抛异常（序列化失败、响应已开始写出、客户端断连），异常会无人接管、
     * 一路冒到容器变成 HTML 格式的 500 页 —— 恰好破坏本项目"HTTP 200 + JSON 契约"
     * 的约定。因此这里对两类失败都只记日志：序列化失败降级为通用错误响应，
     * 写出失败（此时已无法再写任何东西）静默记录。
     *
     * @param resp   HTTP 响应
     * @param object 待写出的对象（通常是 {@link Result}）
     */
    public static void write(HttpServletResponse resp, Object object) {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        // 接口响应一律不缓存，避免浏览器拿到旧的台风数据
        resp.setHeader("Cache-Control", "no-store");

        String json;
        try {
            json = toJson(object);
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "响应对象序列化失败，降级为通用错误响应", e);
            json = FALLBACK_JSON;
        }

        try {
            PrintWriter writer = resp.getWriter();
            writer.write(json);
            writer.flush();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "响应写出失败（客户端可能已断开）：" + e.getMessage());
        }
    }
}
