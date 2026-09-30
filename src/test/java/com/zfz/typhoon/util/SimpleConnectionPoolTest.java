package com.zfz.typhoon.util;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SimpleConnectionPool} 单元测试。
 *
 * <h3>为什么这是"集成型"单测</h3>
 * <p>连接池的<b>全部价值</b>就是"真实数据库连接的生命周期管理"——
 * 用 mock 把 {@code DriverManager} 换掉之后，剩下的断言只能证明
 * "我调用了 setAutoCommit(false)"，证明不了"连接真的被复用了"。
 * 因此这里直接连本机 3306：池的复用、探活、上限、归还语义都能被真实断言。
 *
 * <p>连不上库时用 {@code assumeTrue} 整体跳过，避免把环境差异报成缺陷。
 *
 * <p>每个用例在 {@code finally} 里调用自己那个池的
 * {@link SimpleConnectionPool#shutdown()}，防止测试进程结束时残留连接。
 *
 * @author 追风者项目组
 */
@DisplayName("SimpleConnectionPool：手写连接池")
class SimpleConnectionPoolTest {

    /** 取自 db.properties，避免在测试源码里重复写连接串 */
    private static String driver;
    private static String url;
    private static String username;
    private static String password;

    @BeforeAll
    static void readConfig() {
        driver = DBUtil.getConfig("jdbc.driver", "com.mysql.cj.jdbc.Driver");
        url = DBUtil.getConfig("jdbc.url", "");
        username = DBUtil.getConfig("jdbc.username", "root");
        password = DBUtil.getConfig("jdbc.password", "");
        Assumptions.assumeTrue(DBUtil.isAvailable(),
                "本机数据库不可用（" + DBUtil.getInitError() + "），跳过连接池测试");
    }

    private SimpleConnectionPool newPool(int initial, int maxActive, int maxIdle, int minIdle, long maxWait)
            throws SQLException {
        return new SimpleConnectionPool(driver, url, username, password,
                initial, maxActive, maxIdle, minIdle, maxWait);
    }

    /** 反射读取池内部的空闲队列（元素为 IdleConn 载体），用于构造故障场景 */
    @SuppressWarnings("unchecked")
    private static BlockingQueue<?> idleQueueOf(SimpleConnectionPool pool) {
        return (BlockingQueue<?>) readField(pool, "idle");
    }

    /** 反射读取私有字段（maxWait 无公开读取口，只能这样验证兜底值） */
    private static Object readField(SimpleConnectionPool pool, String name) {
        try {
            java.lang.reflect.Field f = SimpleConnectionPool.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(pool);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法读取连接池字段 " + name, e);
        }
    }

    /** 反射读取任意对象的私有字段（用于读取 IdleConn.conn / returnedNanos） */
    private static Object readField(Object target, String name) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法读取字段 " + name, e);
        }
    }

    /** 反射写入 long 私有字段（用于把 IdleConn.returnedNanos 拨旧） */
    private static void setLongField(Object target, String name, long value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法写入字段 " + name, e);
        }
    }

    private static long readLongField(SimpleConnectionPool pool, String name) {
        return ((Long) readField(pool, name)).longValue();
    }

    /* ================================================================== */
    /*  构造与参数兜底                                                     */
    /* ================================================================== */

    @Nested
    @DisplayName("构造：参数兜底与预热")
    class Construction {

        @Test
        @DisplayName("按 initialSize 预热，空闲数等于预热数")
        void warmsUpInitialConnections() throws SQLException {
            SimpleConnectionPool pool = newPool(3, 10, 5, 2, 2000L);
            try {
                assertEquals(3, pool.getIdleCount(), "预热 3 个连接后空闲队列应有 3 个");
                assertEquals(3, pool.getCreatedCount());
                assertEquals(0, pool.getActiveCount());
                assertEquals(0, pool.getTotalGets(), "预热不算「获取」，累计获取数应为 0");
            } finally {
                pool.shutdown();
            }
        }

        @Test
        @DisplayName("initialSize 为 0 时不做预热，首次取值按需建连")
        void zeroInitialSizeCreatesOnDemand() throws SQLException {
            SimpleConnectionPool pool = newPool(0, 5, 3, 0, 2000L);
            try {
                assertEquals(0, pool.getIdleCount());
                Connection conn = pool.getConnection();
                try {
                    assertNotNull(conn);
                    assertEquals(1, pool.getTotalGets());
                    assertEquals(1, pool.getActiveCount());
                    assertEquals(1, pool.getCreatedCount());
                } finally {
                    conn.close();
                }
            } finally {
                pool.shutdown();
            }
        }

        @Test
        @DisplayName("maxActive 下限为 1：传 0 被抬到 1")
        void maxActiveHasLowerBound() throws SQLException {
            SimpleConnectionPool pool = newPool(0, 0, 0, 0, 1000L);
            try {
                assertEquals(1, pool.getMaxActive());
            } finally {
                pool.shutdown();
            }
        }

        @Test
        @DisplayName("maxIdle 为负时被夹到 1：归还的连接能入池，不落进「直接销毁」分支")
        void maxIdleHasLowerBound() throws SQLException {
            SimpleConnectionPool pool = newPool(0, 4, -5, 0, 1000L);
            try {
                Connection c = pool.getConnection();
                c.close();
                assertEquals(1, pool.getIdleCount(), "maxIdle 为负应被抬到 1");
                assertEquals(1, pool.getCreatedCount());
            } finally {
                pool.shutdown();
            }
        }

        @Test
        @DisplayName("maxIdle 超过 maxActive 时被夹到 maxActive")
        void maxIdleIsCappedByMaxActive() throws SQLException {
            SimpleConnectionPool pool = newPool(0, 2, 99, 0, 1000L);
            try {
                List<Connection> held = new ArrayList<Connection>();
                for (int i = 0; i < 2; i++) {
                    held.add(pool.getConnection());
                }
                for (Connection c : held) {
                    c.close();
                }
                // 若 maxIdle 真被当成 99，两个连接都会留在池里；
                // 被夹到 maxActive=2 后同样是 2 —— 因此这里转而验证
                // 「不会超过 maxActive」这一不变量
                assertTrue(pool.getIdleCount() <= pool.getMaxActive());
                assertEquals(2, pool.getIdleCount());
            } finally {
                pool.shutdown();
            }
        }

        @Test
        @DisplayName("initialSize 超过 maxActive 时按 maxActive 截断")
        void initialSizeIsCappedByMaxActive() throws SQLException {
            SimpleConnectionPool pool = newPool(10, 2, 2, 1, 1000L);
            try {
                assertEquals(2, pool.getIdleCount(), "最多只预热 maxActive 个");
                assertEquals(2, pool.getCreatedCount());
            } finally {
                pool.shutdown();
            }
        }

        @Test
        @DisplayName("maxWait 传 0（或负数）时回落到默认 5000ms")
        void maxWaitFallsBackToDefault() throws SQLException {
            SimpleConnectionPool pool = newPool(1, 1, 1, 0, 0L);
            try {
                assertEquals(5000L, readLongField(pool, "maxWait"));
            } finally {
                pool.shutdown();
            }
            SimpleConnectionPool negative = newPool(1, 1, 1, 0, -1L);
            try {
                assertEquals(5000L, readLongField(negative, "maxWait"));
            } finally {
                negative.shutdown();
            }
        }

        @Test
        @DisplayName("池满时等待被中断：抛「线程被中断」并保留中断标志")
        void waitInterruptedThrows() throws SQLException {
            SimpleConnectionPool pool = newPool(1, 1, 1, 0, 3000L);
            try {
                Connection holder = pool.getConnection();   // 占满唯一的连接
                Thread.currentThread().interrupt();         // 预设中断标志，poll 会立刻抛
                try {
                    SQLException e = assertThrows(SQLException.class, pool::getConnection);
                    assertTrue(e.getMessage().contains("被中断"), "实际：" + e.getMessage());
                } finally {
                    Thread.interrupted();                   // 清掉标志，避免污染后续用例
                    holder.close();
                }
            } finally {
                pool.shutdown();
            }
        }

        @Test
        @DisplayName("驱动类不存在时构造直接抛 SQLException，且文案指明驱动名")
        void unknownDriverFailsFast() {
            SQLException e = assertThrows(SQLException.class,
                    () -> new SimpleConnectionPool("no.such.Driver", url, username, password,
                            0, 2, 1, 0, 500L));
            assertTrue(e.getMessage().contains("未找到 JDBC 驱动"), "实际：" + e.getMessage());
            assertTrue(e.getMessage().contains("no.such.Driver"), "实际：" + e.getMessage());
        }

        @Test
        @DisplayName("账号口令错误时预热失败但不阻断启动（优雅降级）")
        void warmUpFailureDoesNotBreakConstruction() throws SQLException {
            SimpleConnectionPool pool = new SimpleConnectionPool(
                    driver, url, username, password + "-definitely-wrong",
                    3, 3, 2, 0, 800L);
            try {
                assertEquals(0, pool.getIdleCount(), "所有预热连接都建不起来");
                assertEquals(0, pool.getCreatedCount());
            } finally {
                pool.shutdown();
            }
        }
    }

    /* ================================================================== */
    /*  借出与归还                                                        */
    /* ================================================================== */

    @Nested
    @DisplayName("借出与归还")
    class BorrowAndRelease {

        @Test
        @DisplayName("close() 被拦截为归还：借出数回落、空闲数回升")
        void closeReturnsToPool() throws SQLException {
            SimpleConnectionPool pool = newPool(2, 5, 3, 1, 2000L);
            try {
                Connection conn = pool.getConnection();
                assertEquals(1, pool.getActiveCount());
                assertEquals(1, pool.getIdleCount(), "预热的 2 个被借走 1 个");
                conn.close();
                assertEquals(0, pool.getActiveCount(), "归还后借出数应归零");
                assertEquals(2, pool.getIdleCount(), "归还后空闲数应恢复");
                assertEquals(2, pool.getCreatedCount(), "归还不是销毁，物理连接数不变");
            } finally {
                pool.shutdown();
            }
        }

        @Test
        @DisplayName("归还的连接可再次借出（真实复用）")
        void connectionIsReused() throws SQLException {
            SimpleConnectionPool pool = newPool(1, 5, 3, 1, 2000L);
            try {
                Connection first = pool.getConnection();
                first.close();
                Connection second = pool.getConnection();
                try {
                    assertEquals(1, pool.getCreatedCount(), "复用的是同一个物理连接，不该新建");
                    assertEquals(2, pool.getTotalGets());
                    assertNotSame(first, second, "每次借出都是新的代理壳");
                } finally {
                    second.close();
                }
            } finally {
                pool.shutdown();
            }
        }

        @Test
        @DisplayName("归还时若仍有未提交事务，先回滚再恢复自动提交")
        void releaseRollsBackDanglingTransaction() throws SQLException {
            SimpleConnectionPool pool = newPool(1, 3, 2, 0, 2000L);
            try {
                Connection conn = pool.getConnection();
                conn.setAutoCommit(false);
                conn.close();   // 归还时应自动 rollback + setAutoCommit(true)

                Connection again = pool.getConnection();
                try {
                    assertTrue(again.getAutoCommit(),
                            "归还时必须恢复自动提交，否则下一个借用者会拿到脏事务态");
                } finally {
                    again.close();
                }
                assertEquals(1, pool.getCreatedCount());
            } finally {
                pool.shutdown();
            }
        }

        @Test
        @DisplayName("空闲队列已满时，多出来的归还者被真正关闭")
        void excessConnectionsAreClosedOnRelease() throws SQLException {
            SimpleConnectionPool pool = newPool(0, 3, 1, 0, 2000L);
            try {
                Connection a = pool.getConnection();
                Connection b = pool.getConnection();
                assertEquals(2, pool.getCreatedCount());
                a.close();
                assertEquals(1, pool.getIdleCount());
                b.close();   // 空闲队列已满（maxIdle=1），应被真正关闭
                assertEquals(1, pool.getIdleCount());
                assertEquals(1, pool.getCreatedCount(), "超出空闲上限的连接应被销毁");
            } finally {
                pool.shutdown();
            }
        }

        @Test
        @DisplayName("池满时等待 maxWait 后抛超时异常，文案带等待时长与上限")
        void waitsThenTimesOut() throws SQLException {
            SimpleConnectionPool pool = newPool(1, 1, 1, 0, 200L);
            try {
                Connection holder = pool.getConnection();
                long start = System.currentTimeMillis();
                SQLException e = assertThrows(SQLException.class, pool::getConnection);
                long cost = System.currentTimeMillis() - start;

                assertTrue(e.getMessage().contains("超时"), "实际：" + e.getMessage());
                assertTrue(e.getMessage().contains("200ms"), "实际：" + e.getMessage());
                assertTrue(cost >= 150L, "应当真的等待了 maxWait，实际耗时 " + cost + "ms");
                holder.close();
            } finally {
                pool.shutdown();
            }
        }

        @Test
        @DisplayName("空闲过久的连接被服务端断开后，取出时探活失败并自动重建")
        void deadIdleConnectionIsReplaced() throws SQLException {
            SimpleConnectionPool pool = newPool(1, 3, 2, 0, 2000L);
            try {
                // 取出池里那个已预热的空闲载体，把它的"归还时间戳"拨回到 5s 之前，
                // 再关掉其物理连接放回空闲队列 —— 模拟"连接空闲太久被 MySQL 单方面断开"。
                // 注意：性能优化后，池只对"空闲超过安全窗口"的连接探活，
                // 因此必须把时间戳拨旧，否则刚归还的热连接不会被探活（符合预期）。
                BlockingQueue idle = (BlockingQueue) idleQueueOf(pool);
                Object ic = idle.poll();
                assertNotNull(ic, "预热 1 个，队列里应有 1 个空闲载体");
                setLongField(ic, "returnedNanos", System.nanoTime() - 10_000_000_000L);
                Connection physical = (Connection) readField(ic, "conn");
                physical.close();
                idle.offer(ic);

                Connection fresh = pool.getConnection();
                try {
                    assertFalse(fresh.isClosed(), "取到的应是探活失败后重建的可用连接");
                } finally {
                    fresh.close();
                }
                assertEquals(1, pool.getCreatedCount(), "失效连接被剔除、新连接补位，净数不变");
            } finally {
                pool.shutdown();
            }
        }
    }

    /* ================================================================== */
    /*  关闭与监控                                                        */
    /* ================================================================== */

    @Nested
    @DisplayName("关闭与监控指标")
    class ShutdownAndMetrics {

        @Test
        @DisplayName("shutdown 释放空闲连接，之后取连接抛「池已关闭」")
        void shutdownReleasesAndBlocksFurtherBorrow() throws SQLException {
            SimpleConnectionPool pool = newPool(2, 5, 3, 0, 500L);
            pool.shutdown();
            assertEquals(0, pool.getIdleCount());
            assertEquals(0, pool.getCreatedCount(), "空闲连接应被全部销毁");

            SQLException e = assertThrows(SQLException.class, pool::getConnection);
            assertTrue(e.getMessage().contains("连接池已关闭"), "实际：" + e.getMessage());
        }

        @Test
        @DisplayName("shutdown 幂等：重复调用不抛异常")
        void shutdownIsIdempotent() throws SQLException {
            SimpleConnectionPool pool = newPool(1, 2, 1, 0, 500L);
            pool.shutdown();
            pool.shutdown();
            assertEquals(0, pool.getCreatedCount());
        }

        @Test
        @DisplayName("已借出的连接在池关闭后归还，不会被重新入池")
        void releaseAfterShutdownClosesConnection() throws SQLException {
            SimpleConnectionPool pool = newPool(1, 2, 1, 0, 500L);
            Connection conn = pool.getConnection();
            pool.shutdown();
            conn.close();   // closed == true → 直接物理关闭
            assertEquals(0, pool.getIdleCount(), "池已关闭，不该再接受归还");
            assertEquals(0, pool.getCreatedCount());
        }

        @Test
        @DisplayName("监控指标：累计获取次数只增不减，借出数为瞬时值")
        void metricsReflectUsage() throws SQLException {
            SimpleConnectionPool pool = newPool(1, 4, 2, 1, 2000L);
            try {
                List<Connection> held = new ArrayList<Connection>();
                for (int i = 0; i < 3; i++) {
                    held.add(pool.getConnection());
                }
                assertEquals(3, pool.getTotalGets());
                assertEquals(3, pool.getActiveCount());
                assertEquals(3, pool.getCreatedCount());
                for (Connection c : held) {
                    c.close();
                }
                assertEquals(3, pool.getTotalGets(), "归还不会减少累计获取数");
                assertEquals(0, pool.getActiveCount());
                assertEquals(4, pool.getMaxActive());
            } finally {
                pool.shutdown();
            }
        }

        @Test
        @DisplayName("describe 输出可读的池状态，toString 与之等价")
        void describeIsHumanReadable() throws SQLException {
            SimpleConnectionPool pool = newPool(1, 5, 3, 1, 2000L);
            try {
                String desc = pool.describe();
                assertTrue(desc.startsWith("连接池["), "实际：" + desc);
                assertTrue(desc.contains("空闲=1"), "实际：" + desc);
                assertTrue(desc.contains("借出=0"), "实际：" + desc);
                assertTrue(desc.contains("/5"), "应体现 maxActive，实际：" + desc);
                assertEquals(desc, pool.toString());
            } finally {
                pool.shutdown();
            }
        }

        @Test
        @DisplayName("代理连接：isClosed 透传真实状态，equals 自反、hashCode 与 toString 有标记")
        void proxyDelegatesIdentityMethods() throws SQLException {
            SimpleConnectionPool pool = newPool(1, 3, 2, 0, 2000L);
            try {
                Connection conn = pool.getConnection();
                assertFalse(conn.isClosed());
                assertTrue(conn.equals(conn), "equals 应满足自反性");
                assertEquals(conn.hashCode(), conn.hashCode(), "hashCode 应稳定");
                assertTrue(conn.toString().startsWith("PooledConnection["), conn.toString());
                assertNotNull(conn.getMetaData(), "未拦截的方法应委托给真实连接");
                conn.close();
            } finally {
                pool.shutdown();
            }
        }

        @Test
        @DisplayName("重复 close 同一个代理连接：不再销毁物理连接，计数保持一致")
        void doubleReleaseKeepsCountersConsistent() throws SQLException {
            SimpleConnectionPool pool = newPool(1, 3, 2, 0, 2000L);
            try {
                Connection conn = pool.getConnection();
                conn.close();
                int created = pool.getCreatedCount();
                conn.close();   // try-with-resources 后手动再关一次，是常见写法
                assertEquals(created, pool.getCreatedCount(), "重复 close 不应再销毁物理连接");
            } finally {
                pool.shutdown();
            }
        }
    }
}
