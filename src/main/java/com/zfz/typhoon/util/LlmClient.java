package com.zfz.typhoon.util;

/**
 * 大模型调用客户端接口。
 *
 * <p>抽成接口的唯一理由是<b>可测</b>：真实调用依赖外网与密钥，
 * 单元测试必须能注入一个"假客户端"来验证解析、落库、失败降级等分支，
 * 否则这些逻辑只能靠线上碰运气。
 *
 * @author 追风者项目组
 */
public interface LlmClient {

    /**
     * 发起一次对话补全。
     *
     * @param req 请求参数
     * @return 调用结果（失败也是返回值，不抛异常 —— 调用方需要把失败原因展示给管理员）
     */
    Result chat(Request req);

    /**
     * 调用请求。
     *
     * <p>字段刻意用简单类型而非 Map：拼错字段名会在编译期报错，
     * 而不是等到线上收到一个 400。
     */
    class Request {

        /** API Key 明文（只在内存中出现，不落库、不进日志） */
        private String apiKey;

        /** 模型/接入点标识，作为 body 里的 model 字段 */
        private String model;

        /** 完整请求地址（已含 /chat/completions） */
        private String url;

        /** 用户消息 */
        private String prompt;

        /** 最大输出 token */
        private int maxTokens = 1200;

        /** 采样温度；路径测算取低值，减少随机性 */
        private double temperature = 0.2D;

        public Request() {
        }

        public Request(String apiKey, String model, String url, String prompt, int maxTokens) {
            this.apiKey = apiKey;
            this.model = model;
            this.url = url;
            this.prompt = prompt;
            this.maxTokens = maxTokens;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getPrompt() {
            return prompt;
        }

        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }
    }

    /**
     * 调用结果。
     *
     * <p>{@link #error} 已经过脱敏与截断，可以直接展示给管理员 ——
     * 但<b>绝不能</b>包含 API Key 或完整请求头。
     */
    class Result {

        private boolean ok;

        /** 模型返回的文本（ok=true 时） */
        private String content;

        /** 往返耗时（毫秒） */
        private long latencyMs;

        /** 失败原因（ok=false 时） */
        private String error;

        /** HTTP 状态码；未拿到响应时为 0 */
        private int httpStatus;

        public static Result success(String content, long latencyMs) {
            Result r = new Result();
            r.ok = true;
            r.content = content;
            r.latencyMs = latencyMs;
            r.httpStatus = 200;
            return r;
        }

        public static Result failure(String error, long latencyMs, int httpStatus) {
            Result r = new Result();
            r.ok = false;
            r.error = error;
            r.latencyMs = latencyMs;
            r.httpStatus = httpStatus;
            return r;
        }

        public boolean isOk() {
            return ok;
        }

        public String getContent() {
            return content;
        }

        public long getLatencyMs() {
            return latencyMs;
        }

        public String getError() {
            return error;
        }

        public int getHttpStatus() {
            return httpStatus;
        }
    }
}
