package com.zfz.typhoon.listener;

import com.zfz.typhoon.util.DBUtil;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 应用关闭时的资源清理监听器。
 *
 * <h3>为什么需要它</h3>
 * <p>{@code DBUtil.shutdown()} 的注释一直写着「由 ContextListener 在应用卸载时调用」，
 * 但实际上<b>从来没有这样一个监听器</b> —— 于是每次停应用（含热部署）Tomcat 都会刷两条告警：
 * <ul>
 *   <li>{@code clearReferencesJdbc}：注册了 JDBC 驱动但停止时无法注销它；</li>
 *   <li>{@code clearReferencesThreads}：应用启动了名为
 *       {@code mysql-cj-abandoned-connection-cleanup} 的线程却没能停止它。</li>
 * </ul>
 * 驱动与清理线程都属于「应用自己拉起来、却没人负责收尾」的资源，本类负责收尾。
 * （这两条告警在加上定时同步功能之前就已存在，与本项目的同步任务无关 ——
 * 同步用的是自己的 {@code zfz-typhoon-sync} 线程，已由 {@link SyncSchedulerListener} 自行关闭。）
 *
 * <h3>清理顺序不能乱</h3>
 * <ol>
 *   <li><b>先停后台任务</b>：在途的同步任务如果还持有连接，紧接着关池会让它拿到已关闭的池。
 *       因此这里显式调 {@link SyncSchedulerListener#stopScheduler()}，
 *       而不依赖容器回调各监听器的先后顺序（Servlet 规范没有约定这个顺序）。</li>
 *   <li><b>再关连接池</b>：{@link DBUtil#shutdown()}。</li>
 *   <li><b>然后终止驱动自建的清理线程</b>：MySQL Connector/J 的
 *       {@code AbandonedConnectionCleanupThread}。用反射调用，避免与某一家驱动硬耦合。</li>
 *   <li><b>最后注销驱动</b>：只注销<b>由本应用类加载器加载</b>的驱动，
 *       不动容器或其它应用注册的驱动。</li>
 * </ol>
 *
 * <h3>双保险</h3>
 * <p>除了容器的 {@code contextDestroyed}，还注册一个 JVM 关闭钩子。两者共用同一个
 * 幂等实现（{@link AtomicBoolean} 门闩），且门闩在每次 {@code contextInitialized}
 * 时复位 —— 否则热部署后新实例会因为"上次已经清理过"而什么都不做。
 *
 * @author 追风者项目组
 */
@WebListener
public class ShutdownCleanupListener implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(ShutdownCleanupListener.class.getName());

    /** 幂等门闩：容器回调与 JVM 关闭钩子可能都触发，只允许真正清理一次 */
    private static final AtomicBoolean CLEANED = new AtomicBoolean(false);

    /** MySQL Connector/J 自动注册的清理线程（通过反射停止，避免编译期依赖具体驱动） */
    private static final String ABANDONED_CLEANUP_CLASS =
            "com.mysql.cj.jdbc.AbandonedConnectionCleanupThread";

    private static volatile Thread shutdownHook;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        CLEANED.set(false);                       /* 复位，保证热部署后仍能释放 */
        if (shutdownHook == null) {
            Thread hook = new Thread(new Runnable() {
                @Override
                public void run() {
                    cleanup();
                }
            }, "zfz-shutdown-cleanup");
            shutdownHook = hook;
            try {
                Runtime.getRuntime().addShutdownHook(hook);
            } catch (IllegalStateException e) {
                /* JVM 已经在关闭中：那就直接用容器回调这条路径 */
                shutdownHook = null;
            }
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        cleanup();
        removeShutdownHook();
    }

    /**
     * 幂等清理。任何一步失败都不影响后续步骤，也绝不向外抛 ——
     * 关闭阶段抛异常只会换来又一片刷屏日志。
     */
    private static void cleanup() {
        if (!CLEANED.compareAndSet(false, true)) {
            return;
        }

        SyncSchedulerListener.stopScheduler();          /* ① 先停后台任务 */

        try {
            DBUtil.shutdown();                          /* ② 再关连接池 */
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "关闭连接池时发生异常（已忽略，继续注销驱动）", t);
        }

        stopAbandonedCleanupThread();                   /* ③ 终止驱动自建的清理线程 */
        int n = deregisterOwnDrivers();                 /* ④ 注销本应用注册的驱动 */

        LOG.info("应用关闭清理完成：连接池已关闭，MySQL 清理线程已停止，注销 JDBC 驱动 " + n + " 个");
    }

    /**
     * 停止 MySQL Connector/J 的 {@code AbandonedConnectionCleanupThread}。
     *
     * <p>该方法在驱动的不同版本里叫过 {@code checkedShutdown} / {@code uncheckedShutdown} /
     * {@code shutdown}，因此按顺序尝试，命中即止。驱动不在 classpath 上时直接跳过。
     */
    private static void stopAbandonedCleanupThread() {
        Class<?> c;
        try {
            c = Class.forName(ABANDONED_CLEANUP_CLASS);
        } catch (ClassNotFoundException e) {
            return;                                    /* 不是 MySQL 驱动，无需处理 */
        }
        String[] methods = {"checkedShutdown", "uncheckedShutdown", "shutdown"};
        for (String m : methods) {
            try {
                c.getMethod(m).invoke(null);
                return;
            } catch (NoSuchMethodException e) {
                /* 换下一个名字 */
            } catch (Throwable t) {
                LOG.log(Level.WARNING, "调用 " + ABANDONED_CLEANUP_CLASS + "." + m + " 失败", t);
                return;
            }
        }
    }

    /**
     * 注销由本应用的类加载器注册的 JDBC 驱动。
     *
     * <p>只处理类加载器是自己的那些：容器或其它应用注册的驱动不归我们管，
     * 一并注销会打断别人。
     *
     * @return 实际注销的驱动数量
     */
    private static int deregisterOwnDrivers() {
        int n = 0;
        ClassLoader own = ShutdownCleanupListener.class.getClassLoader();
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver d = drivers.nextElement();
            if (d.getClass().getClassLoader() != own) {
                continue;
            }
            try {
                DriverManager.deregisterDriver(d);
                n++;
            } catch (Throwable t) {
                LOG.log(Level.WARNING, "注销 JDBC 驱动失败：" + d.getClass().getName(), t);
            }
        }
        return n;
    }

    /** 移除自己注册的关闭钩子，避免热部署时不断堆积旧应用的钩子 */
    private static void removeShutdownHook() {
        Thread hook = shutdownHook;
        shutdownHook = null;
        if (hook == null) {
            return;
        }
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException e) {
            /* JVM 已在关闭中，钩子马上会被执行，无需移除 */
        } catch (Throwable t) {
            LOG.log(Level.FINE, "移除关闭钩子失败（不影响运行）", t);
        }
    }
}
