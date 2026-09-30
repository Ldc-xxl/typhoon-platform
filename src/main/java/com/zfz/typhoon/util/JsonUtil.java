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
     * @param req   HTTP 请求
     * @param clazz 目标类型
     * @param <T>   目标类型
     * @return 反序列化结果；请求体为空时返回 null
     */
    public static <T> T readBody(HttpServletRequest req, Class<T> clazz) {
        StringBuilder sb = new StringBuilder();
        try {
            req.setCharacterEncoding("UTF-8");
            BufferedReader reader = req.getReader();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
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
     * @param resp   HTTP 响应
     * @param object 待写出的对象（通常是 {@link Result}）
     */
    public static void write(HttpServletResponse resp, Object object) {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        // 接口响应一律不缓存，避免浏览器拿到旧的台风数据
        resp.setHeader("Cache-Control", "no-store");
        try {
            PrintWriter writer = resp.getWriter();
            writer.write(toJson(object));
            writer.flush();
        } catch (IOException e) {
            throw new BusinessException(Result.CODE_SERVER_ERROR,
                    "响应写出失败：" + e.getMessage(), e);
        }
    }
}
