package com.zfz.typhoon.util;

import com.fasterxml.jackson.databind.JsonNode;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 中央气象台台风网（{@code typhoon.nmc.cn}）公开接口客户端。
 *
 * <h3>为什么是这样一个类</h3>
 * <p>本平台的台风数据原先只有一次性导入的静态快照。要让数据真正"跟外部平台关联"，
 * 就必须有人去把外部数据取回来 —— 这个类只负责「取」和「解析」，
 * <b>不落后任何数据库操作</b>，落库交给 {@code TyphoonSyncService}。
 * 这样拆分的好处是：解析规则可以脱离数据库单独做单元测试。
 *
 * <h3>接口形状（实测，2026-10-13）</h3>
 * <p>两个端点都是 <b>JSONP</b> 包装，形如 {@code typhoon_jsons_xxx(({...}))}，
 * 因此必须先剥掉外层函数调用再交给 Jackson：
 * <ul>
 *   <li>{@code list_default} —— 台风列表，每行是数组
 *       {@code [接口ID, 英文名, 中文名, 编号, 编号, 国际编号, 名称含义, 状态]}，
 *       状态 {@code start}=活跃、{@code stop}=已结束。</li>
 *   <li>{@code view_{接口ID}} —— 单个台风，外层数组第 9 个元素是轨迹点列表，
 *       每个点依次为
 *       {@code [点ID, 观测时间, 时间戳ms, 强度等级, 经度, 纬度, 气压, 风速, 移向, 移速,
 *       风圈, 多机构预报, 发布信息]}。</li>
 * </ul>
 *
 * <h3>三处必须记住的口径差异</h3>
 * <ol>
 *   <li><b>时间是 UTC</b>。接口给的是世界时（例如 {@code 202610130900}），
 *       而库里存的是北京时间，入库前统一 +8 小时。</li>
 *   <li><b>风是风速（m/s）不是风级</b>。库里 {@code wind_force} 存风级，
 *       因此用 {@link #beaufort(int)} 按国标换算。</li>
 *   <li><b>风圈是四个象限半径</b>，顺序为「东北、东南、西南、西北」
 *       （顺时针，与气象业务发布顺序一致），单位 km，等级以节（KTS）标识：
 *       30KTS≈7 级、50KTS≈10 级、64KTS≈12 级。</li>
 * </ol>
 *
 * <h3>为什么用 HttpURLConnection 而不是 HttpClient</h3>
 * <p>{@code java.net.http.HttpClient} 是 JDK 11 才有的 API，而本项目
 * {@code maven.compiler.source} 声明为 1.8（见 {@code pom.xml}），
 * 且 README 明确写了"换 JDK 只改那三处"。用 JDK 1.1 就存在的
 * {@code HttpURLConnection} 可以让这份代码在 JDK 8/11/17/21 下<b>都</b>能编译，
 * 不给未来的编译环境埋雷。
 *
 * @author 追风者项目组
 */
public class CmaApiClient {

    private static final Logger LOG = Logger.getLogger(CmaApiClient.class.getName());

    /**
     * 随应用分发的附加信任根证书（PEM，可含多张）。
     *
     * <p><b>为什么需要它</b>：国家气象中心的 {@code *.nmc.cn} 用的是中国金融认证中心
     * （CFCA）签发的证书，链路为 {@code *.nmc.cn → CFCA OV OCA → CFCA EV ROOT}，
     * 而 <b>CFCA EV ROOT 不在 JDK 自带的 {@code cacerts} 里</b>（JDK 只内置国际主流 CA）。
     * 后果很隐蔽：命令行 {@code curl} 能取到数据（它用 Windows 证书存储，那里有 CFCA），
     * 但 JVM 里的同步任务会直接抛
     * {@code PKIX path building failed: unable to find valid certification path}。
     *
     * <p><b>为什么不干脆关掉校验</b>：那等于为本客户端放弃整个 TLS 信任体系，
     * 变成"谁来中间人都认"。正确做法是只把这一条真实的 CA 根补进信任集合 ——
     * 这正是 PKI 设计的正常用法。这里用独立 TrustStore 而不去改 JVM 全局
     * {@code cacerts}，好处是影响范围仅限本客户端，且随 WAR 一起分发，换机器不必重配。
     *
     * <p>采集时间 2026-10-13；根证书有效期 2012-08-08 至 2029-12-31；
     * 根证书 SHA-256 指纹
     * {@code 5C:C3:D7:8E:4E:1D:5E:45:54:7A:04:E6:87:3E:64:F9:0C:F9:53:6D:1C:CC:2E:F8:00:F3:55:C4:C5:FD:70:FD}。
     * 该根轮换后需重新导出，否则同步会再次报 PKIX 错误（日志里可直接看到）。
     */
    private static final String EXTRA_TRUST_RESOURCE = "/certs/cfca-nmc-roots.pem";

    /** 接口根地址 */
    private static final String BASE = "https://typhoon.nmc.cn/weatherservice/typhoon/jsons/";

    /** 该站点对无 Referer 的请求不友好，实测带上站点首页可稳定返回 */
    private static final String REFERER = "https://typhoon.nmc.cn/web.html";

    /**
     * 无 UA 的裸请求会被站点前置的 Tengine 拦截，因此固定一个普通浏览器 UA。
     * 这不是伪装，而是站点的正常访问要求。
     */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /** 接口时间格式：yyyyMMddHHmm（UTC） */
    private static final DateTimeFormatter API_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    /** UTC → 北京时间的小时偏移 */
    private static final int UTC_TO_CST_HOURS = 8;

    /**
     * 蒲福风级下界，单位 0.1 m/s，下标 0 对应 8 级。
     * <p>取值依 GB/T 28591—2012：8 级 17.2、9 级 20.8、10 级 24.5、11 级 28.5、
     * 12 级 32.7、13 级 37.0、14 级 41.5、15 级 46.2、16 级 51.0、17 级 56.1、18 级 61.3。
     */
    private static final int[] BEAUFORT_LOWER = {
        172, 208, 245, 285, 327, 370, 415, 462, 510, 561, 613
    };

    /** 十六方位（自北顺时针，每格 22.5°） */
    private static final String[] COMPASS = {
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    };

    /** 风圈「节」标识 → 库里的风级列（7 / 10 / 12 级风圈） */
    private static final Map<String, Integer> CIRCLE_LEVEL = new HashMap<String, Integer>();
    static {
        CIRCLE_LEVEL.put("30KTS", Integer.valueOf(7));
        CIRCLE_LEVEL.put("50KTS", Integer.valueOf(10));
        CIRCLE_LEVEL.put("64KTS", Integer.valueOf(12));
    }

    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    /** 附加信任了 CFCA 根证书的套接字工厂；构建失败时为 null，自动回退 JDK 默认信任库 */
    private final SSLSocketFactory sslSocketFactory;

    /** 生产用构造：默认超时。 */
    public CmaApiClient() {
        this(8000, 15000);
    }

    /**
     * 测试用构造：可注入更短超时（避免网络不可用时单元测试久等）。
     *
     * @param connectTimeoutMs 连接超时（毫秒）
     * @param readTimeoutMs    读取超时（毫秒）
     */
    public CmaApiClient(int connectTimeoutMs, int readTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.sslSocketFactory = buildSslSocketFactory();
    }

    /* ==================================================================== */
    /*  对外接口                                                            */
    /* ==================================================================== */

    /**
     * 拉取台风列表。
     *
     * @return 台风摘要列表（按接口返回顺序）；接口异常时抛 {@link CmaApiException}
     */
    public List<TyphoonBrief> listTyphoons() {
        JsonNode root = readJson("list_default");
        JsonNode arr = root.get("typhoonList");
        List<TyphoonBrief> out = new ArrayList<TyphoonBrief>();
        if (arr == null || !arr.isArray()) {
            return out;
        }
        for (JsonNode row : arr) {
            if (row == null || row.size() < 8) {
                continue;
            }
            TyphoonBrief b = new TyphoonBrief();
            b.apiId = row.get(0).asLong();
            b.englishName = trim(text(row.get(1)));
            b.chineseName = trim(text(row.get(2)));
            b.number = text(row.get(3));
            b.status = trim(text(row.get(7)));
            if (b.apiId > 0L) {
                out.add(b);
            }
        }
        return out;
    }

    /**
     * 拉取单个台风的完整详情（轨迹 + 风圈 + 预报）。
     *
     * @param apiId 接口台风 ID（即列表里的第 1 个字段）
     * @return 详情；接口异常时抛 {@link CmaApiException}
     */
    public TyphoonDetail fetchTyphoon(long apiId) {
        JsonNode root = readJson("view_" + apiId);
        JsonNode t = root.get("typhoon");
        if (t == null || !t.isArray() || t.size() < 9) {
            throw new CmaApiException("台风详情结构异常，apiId=" + apiId);
        }
        TyphoonDetail d = new TyphoonDetail();
        d.apiId = t.get(0).asLong();
        d.englishName = trim(text(t.get(1)));
        d.chineseName = trim(text(t.get(2)));
        d.number = text(t.get(3));
        d.status = trim(text(t.get(7)));

        JsonNode pts = t.get(8);
        if (pts != null && pts.isArray()) {
            for (JsonNode p : pts) {
                TrackPoint tp = parsePoint(p);
                if (tp != null) {
                    d.points.add(tp);
                }
            }
        }
        return d;
    }

    /* ==================================================================== */
    /*  解析                                                                */
    /* ==================================================================== */

    /**
     * 解析单个轨迹点。
     *
     * @param p 接口的点数组
     * @return 轨迹点；结构不完整（缺时间或坐标）时返回 null，由调用方跳过
     */
    private TrackPoint parsePoint(JsonNode p) {
        if (p == null || !p.isArray() || p.size() < 10) {
            return null;
        }
        TrackPoint tp = new TrackPoint();
        tp.obsTime = parseApiTime(text(p.get(1)));
        if (tp.obsTime == null) {
            return null;
        }
        tp.longitude = p.get(4).asDouble();
        tp.latitude = p.get(5).asDouble();
        tp.pressure = p.get(6).asInt();
        tp.windSpeed = p.get(7).asInt();
        tp.windForce = Integer.valueOf(beaufort(tp.windSpeed));
        tp.moveDirection = compassToDegree(text(p.get(8)));
        tp.moveSpeed = Double.valueOf(p.get(9).asDouble());

        // 风圈：[[等级, 东北, 东南, 西南, 西北, 点ID], ...]
        JsonNode circles = p.get(10);
        if (circles != null && circles.isArray()) {
            for (JsonNode c : circles) {
                if (c == null || !c.isArray() || c.size() < 5) {
                    continue;
                }
                Integer level = CIRCLE_LEVEL.get(trim(text(c.get(0))));
                if (level == null) {
                    continue;
                }
                WindCircleRaw w = new WindCircleRaw();
                w.level = level.intValue();
                w.ne = c.get(1).asInt();
                w.se = c.get(2).asInt();
                w.sw = c.get(3).asInt();
                w.nw = c.get(4).asInt();
                tp.circles.add(w);
            }
        }

        // 预报：{"BABJ": [[时效h, 起报时间, 经度, 纬度, 气压, 风速, 机构, 强度], ...]}
        JsonNode forecasts = p.get(11);
        if (forecasts != null && forecasts.isObject()) {
            Iterator<String> names = forecasts.fieldNames();
            while (names.hasNext()) {
                String agencyCode = names.next();
                JsonNode items = forecasts.get(agencyCode);
                if (items == null || !items.isArray()) {
                    continue;
                }
                for (JsonNode f : items) {
                    if (f == null || !f.isArray() || f.size() < 6) {
                        continue;
                    }
                    ForecastPoint fp = new ForecastPoint();
                    fp.agencyCode = trim(agencyCode);
                    fp.hours = f.get(0).asInt();
                    fp.publishTime = parseApiTime(text(f.get(1)));
                    fp.longitude = f.get(2).asDouble();
                    fp.latitude = f.get(3).asDouble();
                    fp.pressure = f.get(4).asInt();
                    fp.windSpeed = f.get(5).asInt();
                    fp.windForce = Integer.valueOf(beaufort(fp.windSpeed));
                    if (fp.publishTime != null) {
                        tp.forecasts.add(fp);
                    }
                }
            }
        }
        return tp;
    }

    /**
     * 读取一个端点并解析为 JSON 树。
     *
     * @param path 端点相对路径，如 {@code list_default} / {@code view_3346033}
     * @return JSON 根节点
     */
    private JsonNode readJson(String path) {
        String body = unwrapJsonp(get(path));
        try {
            return JsonUtil.mapper().readTree(body);
        } catch (IOException e) {
            throw new CmaApiException("接口响应解析失败：" + e.getMessage(), e);
        }
    }

    /**
     * 发起一次 GET 请求。
     *
     * @param path 端点相对路径
     * @return 原始响应文本（可能仍是 JSONP）
     */
    private String get(String path) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(BASE + path);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(connectTimeoutMs);
            conn.setReadTimeout(readTimeoutMs);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Referer", REFERER);
            conn.setRequestProperty("Accept", "*/*");
            if (conn instanceof HttpsURLConnection && sslSocketFactory != null) {
                ((HttpsURLConnection) conn).setSSLSocketFactory(sslSocketFactory);
            }
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new CmaApiException("接口返回 HTTP " + code + "：" + path);
            }
            return readAll(conn.getInputStream());
        } catch (IOException e) {
            throw new CmaApiException("访问中央气象台接口失败（" + path + "）：" + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 读取输入流全部内容（UTF-8）。
     *
     * @param in 输入流
     * @return 文本
     * @throws IOException 读取失败
     */
    private static String readAll(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        try {
            StringBuilder sb = new StringBuilder(8192);
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
            return sb.toString();
        } finally {
            reader.close();
        }
    }

    /* ==================================================================== */
    /*  TLS 信任                                                            */
    /* ==================================================================== */

    /**
     * 构造"JDK 默认信任 + 附加 CFCA 根证书"的 SSL 套接字工厂。
     *
     * <p>两个信任源是<b>并集</b>关系而不是替换：先用 JDK 默认库校验，
     * 失败时再试附加库。这样既不会因为多加了一张根而放宽对其他站点的校验，
     * 也不影响将来 JDK 自己收录 CFCA 之后的表现（那时默认库就能过，附加库自然退居备用）。
     *
     * @return 套接字工厂；附加证书缺失或构建失败时返回 null（调用方回退默认行为）
     */
    private static SSLSocketFactory buildSslSocketFactory() {
        InputStream in = null;
        try {
            TrustManagerFactory defaultFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            defaultFactory.init((KeyStore) null);
            final X509TrustManager defaultTm = firstX509(defaultFactory.getTrustManagers());

            in = CmaApiClient.class.getResourceAsStream(EXTRA_TRUST_RESOURCE);
            if (in == null) {
                LOG.warning("未找到附加信任证书 " + EXTRA_TRUST_RESOURCE + "，将只使用 JDK 默认信任库");
                return null;
            }
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Collection<? extends Certificate> extras = cf.generateCertificates(in);

            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            int i = 0;
            for (Certificate c : extras) {
                ks.setCertificateEntry("extra-" + (i++), c);
            }
            TrustManagerFactory extraFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            extraFactory.init(ks);
            final X509TrustManager extraTm = firstX509(extraFactory.getTrustManagers());

            X509TrustManager merged = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    defaultTm.checkClientTrusted(chain, authType);
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    try {
                        defaultTm.checkServerTrusted(chain, authType);
                    } catch (CertificateException e) {
                        // 默认库不认，再给附加库一次机会；仍不认则原样抛出
                        extraTm.checkServerTrusted(chain, authType);
                    }
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    X509Certificate[] a = defaultTm.getAcceptedIssuers();
                    X509Certificate[] b = extraTm.getAcceptedIssuers();
                    X509Certificate[] all = new X509Certificate[a.length + b.length];
                    System.arraycopy(a, 0, all, 0, a.length);
                    System.arraycopy(b, 0, all, a.length, b.length);
                    return all;
                }
            };

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[] {merged}, new SecureRandom());
            LOG.info("已为气象数据接口加载附加信任根证书 " + extras.size() + " 张");
            return ctx.getSocketFactory();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "构造附加信任 SSL 上下文失败，回退 JDK 默认信任库", e);
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignore) {
                    // 关流失败无需处理
                }
            }
        }
    }

    /**
     * 从 TrustManager 数组里取第一个 X509 实现。
     *
     * @param managers 管理器数组
     * @return X509 管理器
     */
    private static X509TrustManager firstX509(TrustManager[] managers) {
        for (TrustManager m : managers) {
            if (m instanceof X509TrustManager) {
                return (X509TrustManager) m;
            }
        }
        throw new IllegalStateException("JDK 未提供 X509TrustManager");
    }

    /* ==================================================================== */
    /*  纯函数（可单独单元测试）                                             */
    /* ==================================================================== */

    /**
     * 剥掉 JSONP 外层函数调用，取出中间的 JSON 主体。
     *
     * <p>实测两种包装：列表是 {@code fn(({...}))}（两层括号）、
     * 详情是 {@code fn({...})}（一层）。因此不按括号数判断，
     * 统一取「第一个 <code>{</code> 到最后一个 <code>}</code>」，
     * 对两种形状都成立。
     *
     * @param raw 原始响应文本
     * @return 纯 JSON 文本
     */
    public static String unwrapJsonp(String raw) {
        if (raw == null) {
            throw new CmaApiException("接口响应为空");
        }
        int lo = raw.indexOf('{');
        int hi = raw.lastIndexOf('}');
        if (lo < 0 || hi <= lo) {
            throw new CmaApiException("接口响应不是预期格式（未找到 JSON 主体）");
        }
        return raw.substring(lo, hi + 1);
    }

    /**
     * 接口编号归一化为库里的国际编号格式。
     *
     * <p>接口给 4 位（{@code 2629} = 2026 年第 29 号），库里的口径是 6 位
     * （{@code 202629}），因此补前两位世纪前缀；接口偶尔出现 8 位的
     * 热带低压编号（如 {@code 20260027}），那本身就是完整编号，原样保留
     * —— 强行截断会与同序号的正规台风撞号。
     *
     * @param no 接口编号
     * @return 国际编号
     */
    public static String normalizeIntlId(String no) {
        if (no == null) {
            return null;
        }
        String s = no.trim();
        if (s.length() == 4 && s.matches("\\d{4}")) {
            return "20" + s;
        }
        return s;
    }

    /**
     * 风速（m/s）换算为蒲福风级。
     *
     * <p>低于 8 级（17.2 m/s）的一律返回 8 —— 本平台只展示热带风暴及以上，
     * 更弱的热带低压不在业务范围内。
     *
     * @param mps 风速（米/秒）
     * @return 风级（8—18）
     */
    public static int beaufort(int mps) {
        int x = mps * 10;
        if (x >= 613) {
            return 18;
        }
        int level = 8;
        for (int i = 0; i < BEAUFORT_LOWER.length; i++) {
            if (x >= BEAUFORT_LOWER[i]) {
                // 下标 0 对应的是 8 级的下界（17.2 m/s），因此基准是 8 而不是 9
                level = 8 + i;
            } else {
                break;
            }
        }
        return level;
    }

    /**
     * 方位名转方位角。
     *
     * @param dir 十六方位之一（如 {@code NNW}），大小写不敏感
     * @return 方位角（度，北为 0 顺时针）；无法识别返回 null
     */
    public static Double compassToDegree(String dir) {
        if (dir == null) {
            return null;
        }
        String d = dir.trim().toUpperCase();
        for (int i = 0; i < COMPASS.length; i++) {
            if (COMPASS[i].equals(d)) {
                return Double.valueOf(22.5D * i);
            }
        }
        return null;
    }

    /**
     * 解析接口时间并转北京时间。
     *
     * @param raw {@code yyyyMMddHHmm}（UTC）
     * @return 北京时间；入参不合法返回 null
     */
    public static LocalDateTime parseApiTime(String raw) {
        if (raw == null || raw.trim().length() < 12) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.trim().substring(0, 12), API_TIME)
                    .plusHours(UTC_TO_CST_HOURS);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 空安全的取文本 */
    private static String text(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        return n.asText();
    }

    /** 空安全的去空白（接口中文名带尾随换行） */
    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    /* ==================================================================== */
    /*  数据传输对象                                                        */
    /* ==================================================================== */

    /** 台风摘要（列表接口） */
    public static class TyphoonBrief {
        /** 接口台风 ID */
        public long apiId;
        public String englishName;
        public String chineseName;
        /** 接口编号，如 2629 */
        public String number;
        /** start=活跃 / stop=已结束 */
        public String status;

        /** 是否活跃 */
        public boolean isActive() {
            return "start".equalsIgnoreCase(status);
        }
    }

    /** 台风详情（详情接口） */
    public static class TyphoonDetail {
        public long apiId;
        public String englishName;
        public String chineseName;
        public String number;
        public String status;
        /** 轨迹点（按接口顺序，即时间升序） */
        public final List<TrackPoint> points = new ArrayList<TrackPoint>();

        /** 是否活跃 */
        public boolean isActive() {
            return "start".equalsIgnoreCase(status);
        }
    }

    /** 轨迹点 */
    public static class TrackPoint {
        /** 观测时间（已转北京时间） */
        public LocalDateTime obsTime;
        public double latitude;
        public double longitude;
        /** 风级（由风速换算） */
        public Integer windForce;
        /** 风速 m/s */
        public int windSpeed;
        /** 中心气压 hPa */
        public int pressure;
        /** 移动方位角（度） */
        public Double moveDirection;
        /** 移动速度 km/h */
        public Double moveSpeed;
        public final List<WindCircleRaw> circles = new ArrayList<WindCircleRaw>();
        public final List<ForecastPoint> forecasts = new ArrayList<ForecastPoint>();
    }

    /** 一个风圈等级的四个象限半径 */
    public static class WindCircleRaw {
        /** 7 / 10 / 12 */
        public int level;
        public int ne;
        public int se;
        public int sw;
        public int nw;
    }

    /** 一个预报点 */
    public static class ForecastPoint {
        /** 机构代码，如 BABJ */
        public String agencyCode;
        /** 预报时效（小时） */
        public int hours;
        /** 起报时间（已转北京时间） */
        public LocalDateTime publishTime;
        public double latitude;
        public double longitude;
        public int pressure;
        public int windSpeed;
        /** 风级（由风速换算） */
        public Integer windForce;
    }

    /** 采集异常：网络不可达、返回非 200、响应结构异常都归到这里 */
    public static class CmaApiException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public CmaApiException(String message) {
            super(message);
        }

        public CmaApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
