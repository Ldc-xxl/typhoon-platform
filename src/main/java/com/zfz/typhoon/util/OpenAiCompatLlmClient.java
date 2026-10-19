package com.zfz.typhoon.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * OpenAI 兼容协议的通用大模型客户端。
 *
 * <p>为什么一个类能覆盖四家：火山方舟（豆包）、百度千帆 v2、
 * DeepSeek、阿里百炼的兼容模式都实现了同一套
 * {@code POST {base}/chat/completions} + {@code Authorization: Bearer <key>}
 * + {@code {model, messages, max_tokens}} 的契约。差异只在<b>网关地址</b>与
 * <b>model 取值</b>，两者都是配置项，不该写死成四个类。
 *
 * <p>之所以用 {@link HttpURLConnection} 而不是 Apache HttpClient：
 * 本工程 {@code pom.xml} 的 {@code source/target=1.8}，而
 * {@code java.net.http.HttpClient} 是 Java 11 才有的，
 * 编译不过（详见 {@code docs/} 里关于 JDK 版本约束的说明）。
 *
 * <p><b>安全</b>：本类不打印任何请求头；所有返回给调用方的错误文本
 * 都会把 API Key 替换成 {@code ***}（少数网关会把 Key 回显在错误报文里）。
 *
 * @author 追风者项目组
 */
public class OpenAiCompatLlmClient implements LlmClient {

    private static final Logger LOG = Logger.getLogger(OpenAiCompatLlmClient.class.getName());

    /** 建连超时：短一些，网络不通要快速失败，不能把 Tomcat 线程占满 */
    private static final int CONNECT_TIMEOUT_MS = 8000;

    /** 读超时：大模型生成路径点较慢，给足 40s */
    private static final int READ_TIMEOUT_MS = 40000;

    /** 错误文本最多保留的字符数（落库列是 varchar(500)，还要留前缀） */
    private static final int MAX_ERROR_TEXT = 200;

    @Override
    public Result chat(Request req) {
        if (req == null || isBlank(req.getUrl())) {
            return Result.failure("缺少请求地址（base_url 未配置）", 0L, 0);
        }
        if (isBlank(req.getApiKey())) {
            return Result.failure("尚未配置 API Key", 0L, 0);
        }
        if (isBlank(req.getModel())) {
            return Result.failure("尚未配置推理接入点（model 为空）", 0L, 0);
        }

        long start = System.currentTimeMillis();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(req.getUrl()).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + req.getApiKey().trim());

            byte[] payload = buildBody(req).getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(payload.length);
            OutputStream out = conn.getOutputStream();
            try {
                out.write(payload);
            } finally {
                out.close();
            }

            int status = conn.getResponseCode();
            long cost = System.currentTimeMillis() - start;
            boolean okStatus = status >= 200 && status < 300;
            String body = readAll(okStatus ? conn.getInputStream() : conn.getErrorStream());

            if (!okStatus) {
                return Result.failure(describeError(status, body, req.getApiKey()), cost, status);
            }
            String content = extractContent(body);
            if (isBlank(content)) {
                // 有些网关在额度耗尽时仍返回 200，但 choices 为空。
                // 这种情况必须当失败处理，绝不能拿空内容去当"模型没意见"。
                return Result.failure("模型返回内容为空（HTTP " + status + "），可能是额度耗尽或触发了内容过滤",
                        cost, status);
            }
            return Result.success(content, cost);

        } catch (SocketTimeoutException e) {
            long cost = System.currentTimeMillis() - start;
            LOG.log(Level.WARNING, "大模型调用超时：" + e.getMessage());
            return Result.failure("请求超时（建连 " + (CONNECT_TIMEOUT_MS / 1000) + "s / 读取 "
                    + (READ_TIMEOUT_MS / 1000) + "s），请检查网络后重试", cost, 0);
        } catch (IOException e) {
            long cost = System.currentTimeMillis() - start;
            LOG.log(Level.WARNING, "大模型调用网络异常：" + e.getMessage());
            return Result.failure("网络异常：" + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : " " + mask(e.getMessage(), req.getApiKey())), cost, 0);
        } catch (RuntimeException e) {
            long cost = System.currentTimeMillis() - start;
            LOG.log(Level.WARNING, "大模型调用异常：" + e.getMessage());
            return Result.failure("调用失败：" + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : " " + mask(e.getMessage(), req.getApiKey())), cost, 0);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 拼请求体。
     *
     * @param req 请求
     * @return JSON 字符串
     */
    String buildBody(Request req) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode root = JsonUtil.mapper().createObjectNode();
            root.put("model", req.getModel());
            root.put("max_tokens", req.getMaxTokens());
            root.put("temperature", req.getTemperature());
            // 明确要求 JSON 输出：豆包/千帆在 response_format 上支持度不一，
            // 这里不滥用该字段，仍以 prompt 约束为准，解析侧做容错。
            com.fasterxml.jackson.databind.node.ArrayNode messages = root.putArray("messages");
            com.fasterxml.jackson.databind.node.ObjectNode user = messages.addObject();
            user.put("role", "user");
            user.put("content", req.getPrompt());
            return JsonUtil.mapper().writeValueAsString(root);
        } catch (Exception e) {
            // 拼不出 JSON 属于代码缺陷，直接抛出由上层转成 500，不要静默发一个空请求
            throw new IllegalStateException("构造大模型请求体失败", e);
        }
    }

    /**
     * 从响应里取 {@code choices[0].message.content}。
     *
     * @param body 响应体
     * @return 文本；结构不符合预期返回 null
     */
    String extractContent(String body) {
        if (isBlank(body)) {
            return null;
        }
        try {
            JsonNode root = JsonUtil.mapper().readTree(body);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.size() == 0) {
                return null;
            }
            JsonNode content = choices.get(0).path("message").path("content");
            return content.isMissingNode() || content.isNull() ? null : content.asText();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "解析大模型响应失败：" + e.getMessage());
            return null;
        }
    }

    /**
     * 把 HTTP 错误翻译成管理员能看懂的话。
     *
     * @param status HTTP 状态码
     * @param body   响应体
     * @param apiKey API Key（仅用于脱敏替换，不写进结果）
     * @return 描述文本
     */
    String describeError(int status, String body, String apiKey) {
        String hint;
        if (status == 401) {
            hint = "鉴权失败（HTTP 401）：API Key 无效，或该 Key 未开通此模型";
        } else if (status == 403) {
            hint = "无权限（HTTP 403）：请确认该 Key 已开通对应模型/接入点";
        } else if (status == 404) {
            hint = "接口或模型不存在（HTTP 404）：请检查接入点 ID 与网关地址";
        } else if (status == 429) {
            hint = "请求过于频繁或额度已用尽（HTTP 429）";
        } else if (status >= 500) {
            hint = "厂商服务端错误（HTTP " + status + "），稍后重试";
        } else {
            hint = "调用失败（HTTP " + status + "）";
        }

        String detail = extractErrorMessage(body);
        if (!isBlank(detail)) {
            hint = hint + "；服务端返回：" + mask(trim(detail, MAX_ERROR_TEXT), apiKey);
        }
        return hint;
    }

    /**
     * 抽取服务端的 error.message（兼容 OpenAI / 火山 / 千帆 的几种包装）。
     *
     * @param body 响应体
     * @return 错误描述；抽不到返回 null
     */
    String extractErrorMessage(String body) {
        if (isBlank(body)) {
            return null;
        }
        try {
            JsonNode root = JsonUtil.mapper().readTree(body);
            JsonNode msg = root.path("error").path("message");
            if (!msg.isMissingNode() && !msg.isNull()) {
                return msg.asText();
            }
            msg = root.path("message");
            if (!msg.isMissingNode() && !msg.isNull()) {
                return msg.asText();
            }
            return null;
        } catch (Exception e) {
            // 不是 JSON（比如网关返回 HTML 错误页）：截一段原文，仍然是有效线索
            return body;
        }
    }

    /**
     * 把 API Key 从待展示文本里抹掉。
     *
     * @param text   原始文本
     * @param apiKey 密钥
     * @return 脱敏后的文本
     */
    String mask(String text, String apiKey) {
        if (text == null) {
            return null;
        }
        if (isBlank(apiKey)) {
            return text;
        }
        return text.replace(apiKey, "***");
    }

    /**
     * 读尽输入流。
     *
     * @param in 流（可为 null）
     * @return 文本；流为空返回空串
     */
    private String readAll(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        try {
            char[] buf = new char[2048];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
        } finally {
            reader.close();
        }
        return sb.toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim().replaceAll("\\s+", " ");
        return t.length() > max ? t.substring(0, max) + "…" : t;
    }
}
