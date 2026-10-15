package com.zfz.typhoon.listener;

import com.zfz.typhoon.service.TyphoonSyncService;
import com.zfz.typhoon.service.impl.TyphoonSyncServiceImpl;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 台风数据定时同步调度器。
 *
 * <h3>它是怎么被启动的</h3>
 * <p>用 {@link WebListener} 注解注册（与项目里 {@code @WebServlet / @WebFilter}
 * 的做法一致，不必改 {@code web.xml} 的注册段）。Tomcat 启动本应用时回调
 * {@link #contextInitialized}，在这里起一个单线程调度器；关闭应用时回调
 * {@link #contextDestroyed}，必须把调度器收干净。
 *
 * <h3>三处刻意的设计</h3>
 * <ol>
 *   <li><b>守护线程 + 显式关闭，双保险</b>。线程设为 daemon 只是兜底，
 *       真正避免"内存泄漏告警"的是 {@code shutdownNow} + {@code awaitTermination}。
 *       不关闭的话，Tomcat 会在停止时打印
 *       「appears to have started a thread ... but has failed to stop it」，
 *       并在热部署时不断堆积旧应用的线程。</li>
 *   <li><b>单线程</b>。同步是"拉取—解析—写库"的整体，并发跑多轮只会让同一批
 *       数据互相覆盖（{@code forecast_paths} 那步是"先删后写"）。单线程天然串行。</li>
 *   <li><b>异常绝不外抛</b>。{@code scheduleWithFixedDelay} 的语义是：一旦任务抛出
 *       未捕获异常，后续调度<b>永久停止</b>，且不会报错 —— 这是最隐蔽的故障形态
 *       （"同步怎么不跑了"）。因此 {@link #runOnce()} 里用 {@code Throwable} 兜底。</li>
 * </ol>
 *
 * <h3>配置项</h3>
 * <p>在 {@code web.xml} 里以 {@code <context-param>} 声明，改配置不必重新编译：
 * <ul>
 *   <li>{@code sync.enabled} —— 是否启用，默认 true。演示时想临时关掉就改它。</li>
 *   <li>{@code sync.intervalMinutes} —— 同步间隔（分钟），默认 30。</li>
 *   <li>{@code sync.initialDelaySeconds} —— 启动后延迟多久跑第一轮（秒），默认 20。
 *       留出延迟是为了让应用先把连接池、Servlet 都初始化完，不与启动流程抢资源。</li>
 * </ul>
 *
 * @author 追风者项目组
 */
@WebListener
public class SyncSchedulerListener implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(SyncSchedulerListener.class.getName());

    /** 配置项名 */
    private static final String PARAM_ENABLED = "sync.enabled";
    private static final String PARAM_INTERVAL_MINUTES = "sync.intervalMinutes";
    private static final String PARAM_INITIAL_DELAY_SECONDS = "sync.initialDelaySeconds";

    /** 线程名会出现在线程转储与 Tomcat 的泄漏告警里，起个可辨识的 */
    private static final String THREAD_NAME = "zfz-typhoon-sync";

    /** 默认值 */
    private static final boolean DEFAULT_ENABLED = true;
    private static final int DEFAULT_INTERVAL_MINUTES = 30;
    private static final int DEFAULT_INITIAL_DELAY_SECONDS = 20;

    /** 关闭时等待在途任务结束的上限（秒） */
    private static final int SHUTDOWN_WAIT_SECONDS = 10;

    /**
     * 当前实例。应用级清理监听器（{@link ShutdownCleanupListener}）要在关连接池
     * <b>之前</b>先把同步停下来，否则在途的同步会拿到已关闭的连接池；
     * 而 Servlet 规范里各监听器 {@code contextDestroyed} 的先后顺序并不由我们决定，
     * 所以这里留一个静态入口，让"谁先被回调"不再影响正确性。
     */
    private static volatile SyncSchedulerListener instance;

    private ScheduledExecutorService pool;
    private TyphoonSyncService syncService;
    private volatile long round;

    /** 幂等门闩：本监听器与清理监听器可能都调 {@link #stop()}，只能真正停一次 */
    private final java.util.concurrent.atomic.AtomicBoolean stopped =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext ctx = sce.getServletContext();
        instance = this;

        if (!readBoolean(ctx, PARAM_ENABLED, DEFAULT_ENABLED)) {
            LOG.info("台风数据同步任务未启动（" + PARAM_ENABLED + "=false）");
            return;
        }

        int intervalMinutes = readInt(ctx, PARAM_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES, 1, 24 * 60);
        int initialDelaySeconds = readInt(ctx, PARAM_INITIAL_DELAY_SECONDS, DEFAULT_INITIAL_DELAY_SECONDS, 0, 3600);

        syncService = new TyphoonSyncServiceImpl();
        pool = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, THREAD_NAME);
                t.setDaemon(true);
                return t;
            }
        });

        pool.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                runOnce();
            }
        }, initialDelaySeconds, intervalMinutes * 60L, TimeUnit.SECONDS);

        LOG.info("台风数据同步任务已启动：首次延迟 " + initialDelaySeconds
                + " 秒，之后每 " + intervalMinutes + " 分钟同步一次（数据源：中央气象台台风网）");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        stop();
    }

    /**
     * 幂等停止调度器，并等待在途的那一轮同步真正结束。
     *
     * <p>可以被重复调用（本监听器的 {@code contextDestroyed} 与应用级清理监听器都会调），
     * 只有第一次会真正执行。等待是必要的：{@code shutdownNow} 只是"发中断信号"，
     * 立刻返回并不能保证那轮"拉取—解析—写库"已经收尾，而调用方紧接着就要关连接池。
     *
     * @return 本次调用是否真正执行了停止动作
     */
    public boolean stop() {
        if (!stopped.compareAndSet(false, true)) {
            return false;
        }
        ScheduledExecutorService p = pool;
        pool = null;
        syncService = null;
        if (instance == this) {
            instance = null;
        }
        if (p == null) {
            return false;                 /* sync.enabled=false：本来就没起，不打误导性日志 */
        }

        p.shutdownNow();
        try {
            if (!p.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                LOG.warning("台风数据同步任务在 " + SHUTDOWN_WAIT_SECONDS + " 秒内未结束"
                        + "（可能正卡在一次网络请求上）");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        LOG.info("台风数据同步任务已停止");
        return true;
    }

    /**
     * 静态入口：供应用级清理监听器在关闭连接池之前先停下同步任务。
     *
     * <p>应用未启用同步、或该监听器尚未初始化时调用它是安全的（空操作）。
     */
    public static void stopScheduler() {
        SyncSchedulerListener s = instance;
        if (s != null) {
            s.stop();
        }
    }

    /**
     * 执行一轮同步。任何异常都就地消化，绝不让调度被取消。
     */
    private void runOnce() {
        long current = ++round;
        TyphoonSyncService service = syncService;
        if (service == null) {
            return;
        }
        long started = System.currentTimeMillis();
        try {
            TyphoonSyncService.SyncResult r = service.syncAll();
            long cost = System.currentTimeMillis() - started;
            if (r.ok()) {
                LOG.info("第 " + current + " 轮同步完成（" + cost + "ms）：" + r.summary());
            } else {
                LOG.warning("第 " + current + " 轮同步存在失败（" + cost + "ms）：" + r.summary());
            }
        } catch (Throwable t) {
            // 兜底：scheduleWithFixedDelay 遇到未捕获异常会静默停掉后续所有调度
            LOG.log(Level.SEVERE, "第 " + current + " 轮同步发生未预期异常", t);
        }
    }

    /* ==================================================================== */
    /*  配置读取                                                            */
    /* ==================================================================== */

    /**
     * 读布尔配置。
     *
     * @param ctx       上下文
     * @param name      参数名
     * @param fallback  缺省值
     * @return 配置值；未配置或非法时返回缺省值
     */
    private static boolean readBoolean(ServletContext ctx, String name, boolean fallback) {
        String v = ctx.getInitParameter(name);
        if (v == null || v.trim().isEmpty()) {
            return fallback;
        }
        return Boolean.parseBoolean(v.trim());
    }

    /**
     * 读整数配置，并夹到合法区间 —— 配置写错（比如间隔写成 0）不该让调度器变成空转。
     *
     * @param ctx      上下文
     * @param name     参数名
     * @param fallback 缺省值
     * @param min      下限
     * @param max      上限
     * @return 合法值
     */
    private static int readInt(ServletContext ctx, String name, int fallback, int min, int max) {
        String v = ctx.getInitParameter(name);
        if (v == null || v.trim().isEmpty()) {
            return fallback;
        }
        try {
            int n = Integer.parseInt(v.trim());
            if (n < min) {
                LOG.warning(name + "=" + n + " 小于下限 " + min + "，已按下限取值");
                return min;
            }
            if (n > max) {
                LOG.warning(name + "=" + n + " 超过上限 " + max + "，已按上限取值");
                return max;
            }
            return n;
        } catch (NumberFormatException e) {
            LOG.warning(name + " 取值非法（" + v + "），改用默认值 " + fallback);
            return fallback;
        }
    }
}
