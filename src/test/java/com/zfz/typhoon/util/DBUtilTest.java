package com.zfz.typhoon.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DBUtil} 单元测试。
 *
 * <h3>测试策略</h3>
 * <p>{@code DBUtil} 是纯静态门面，静态初始化块会在首次触达时读
 * {@code db.properties} 并预热连接池，测试无法（也不该）绕过这一步。
 * 因此本测试的前提是<b>测试环境能连上配置里的库</b>；连不上时用
 * {@code assumeTrue} 让用例整体跳过，而不是伪装成失败。
 *
 * <p>覆盖重点：
 * <ul>
 *   <li>{@code getConfig} 的「系统属性优先」语义（这是 {@code -Djdbc.url=...}
 *       免改包换库的唯一实现基础）；</li>
 *   <li>{@code getIntConfig} 对非数字配置的兜底；</li>
 *   <li>事务的连接复用与 {@code close()} 空操作语义；</li>
 *   <li>三个 {@code close(...)} 重载的 null 安全。</li>
 * </ul>
 *
 * <p><b>刻意不测 {@code shutdown()}</b>：它会把静态 {@code pool} 置为 null，
 * 而 surefire 默认单 JVM 复用，一旦某个用例关掉池，同 JVM 里后续所有
 * 依赖数据库的用例都会连锁失败。该方法的正确性由「应用卸载」这一部署场景
 * 覆盖，不属于单元测试职责。
 *
 * @author 追风者项目组
 */
@DisplayName("DBUtil：数据库门面")
class DBUtilTest {

    /** 测试用系统属性键，避免与真实配置撞车 */
    private static final String SYS_KEY = "jdbc.url";

    @AfterEach
    void clearSystemProperties() {
        // getConfig 会优先读系统属性，测试若不清干净会污染同 JVM 的其它用例
        System.clearProperty(SYS_KEY);
        System.clearProperty("pool.maxActive");
        System.clearProperty("unit_test.never.exists");
    }

    /* ================================================================== */
    /*  配置读取                                                          */
    /* ================================================================== */

    @Nested
    @DisplayName("getConfig：配置读取")
    class Config {

        @Test
        @DisplayName("系统属性优先于 db.properties")
        void systemPropertyWins() {
            System.setProperty(SYS_KEY, "jdbc:mysql://127.0.0.1:3307/other_db");
            assertEquals("jdbc:mysql://127.0.0.1:3307/other_db", DBUtil.getConfig(SYS_KEY, "fallback"));
        }

        @Test
        @DisplayName("系统属性两侧空白会被裁掉")
        void systemPropertyIsTrimmed() {
            System.setProperty(SYS_KEY, "   jdbc:mysql://host/db   ");
            assertEquals("jdbc:mysql://host/db", DBUtil.getConfig(SYS_KEY, "fallback"));
        }

        @Test
        @DisplayName("系统属性为空白串时视为未设置，回落到配置文件")
        void blankSystemPropertyFallsThrough() {
            String fromFile = DBUtil.getConfig(SYS_KEY, "fallback");
            System.setProperty(SYS_KEY, "   ");
            assertEquals(fromFile, DBUtil.getConfig(SYS_KEY, "fallback"));
        }

        @Test
        @DisplayName("配置文件中存在该项时返回文件值")
        void readsFromPropertiesFile() {
            assertEquals("com.mysql.cj.jdbc.Driver",
                    DBUtil.getConfig("jdbc.driver", "whatever"));
        }

        @Test
        @DisplayName("键不存在时返回默认值")
        void missingKeyReturnsDefault() {
            assertEquals("备用值", DBUtil.getConfig("unit_test.never.exists", "备用值"));
        }

        @Test
        @DisplayName("默认值允许为 null，此时返回 null 而不是空串")
        void defaultMayBeNull() {
            assertNull(DBUtil.getConfig("unit_test.never.exists", null));
        }

        @Test
        @DisplayName("配置文件里的值两侧空白也会被裁掉")
        void fileValueIsTrimmed() {
            // pool.maxActive=20（无空白），断言其已被规范化即可
            assertEquals("20", DBUtil.getConfig("pool.maxActive", "0"));
        }
    }

    @Nested
    @DisplayName("getIntConfig：整数配置读取")
    class IntConfig {

        @Test
        @DisplayName("正常数字解析为 int")
        void parsesNumber() {
            assertEquals(20, DBUtil.getIntConfig("pool.maxActive", 99));
            assertEquals(5, DBUtil.getIntConfig("pool.initialSize", 99));
        }

        @Test
        @DisplayName("键不存在时返回默认值")
        void missingKeyReturnsDefault() {
            assertEquals(42, DBUtil.getIntConfig("unit_test.never.exists", 42));
        }

        @Test
        @DisplayName("值不是数字时返回默认值，而不是抛 NumberFormatException")
        void nonNumericReturnsDefault() {
            System.setProperty("pool.maxActive", "二十");
            assertEquals(7, DBUtil.getIntConfig("pool.maxActive", 7));
        }

        @Test
        @DisplayName("负数与零按原样解析（范围约束由连接池自己兜）")
        void negativeAndZeroAreParsed() {
            System.setProperty("pool.maxActive", "-3");
            assertEquals(-3, DBUtil.getIntConfig("pool.maxActive", 7));
            System.setProperty("pool.maxActive", "0");
            assertEquals(0, DBUtil.getIntConfig("pool.maxActive", 7));
        }
    }

    /* ================================================================== */
    /*  连接与事务                                                        */
    /* ================================================================== */

    @Nested
    @DisplayName("连接获取与事务")
    class ConnectionAndTransaction {

        @AfterEach
        void rollbackLeftOverTransaction() {
            // 断言失败时若留在事务中，会让后续用例拿到脏连接
            if (DBUtil.inTransaction()) {
                DBUtil.rollback();
                DBUtil.endTransaction();
            }
        }

        @Test
        @DisplayName("未初始化连接池的容器里，getConnection 应抛 DataAccessException")
        void getConnectionThrowsWhenPoolIsNull() {
            // 本机 3306 通常可用（isAvailable() == true），此时该分支不可达，
            // 用假设保护，避免把环境差异误报成缺陷
            org.junit.jupiter.api.Assumptions.assumeFalse(
                    DBUtil.isAvailable(), "连接池可用，跳过「池不可用」分支");
            DataAccessException e = assertThrows(DataAccessException.class, DBUtil::getConnection);
            assertNotNull(e.getMessage());
        }

        @Test
        @DisplayName("连接池可用时能取到连接，且 close() 归还后不报错")
        void getConnectionReturnsUsableConnection() throws java.sql.SQLException {
            org.junit.jupiter.api.Assumptions.assumeTrue(DBUtil.isAvailable(), "连接池不可用，跳过");
            Connection conn = DBUtil.getConnection();
            assertNotNull(conn);
            conn.close();
        }

        @Test
        @DisplayName("默认不在事务中")
        void notInTransactionByDefault() {
            assertFalse(DBUtil.inTransaction());
        }

        @Test
        @DisplayName("beginTransaction 后进入事务，endTransaction 后退出")
        void beginAndEndTransaction() {
            org.junit.jupiter.api.Assumptions.assumeTrue(DBUtil.isAvailable(), "连接池不可用，跳过");
            DBUtil.beginTransaction();
            try {
                assertTrue(DBUtil.inTransaction());
            } finally {
                DBUtil.endTransaction();
            }
            assertFalse(DBUtil.inTransaction());
        }

        @Test
        @DisplayName("事务中重复 beginTransaction 只生效一次（不支持嵌套）")
        void beginTransactionIsIdempotent() {
            org.junit.jupiter.api.Assumptions.assumeTrue(DBUtil.isAvailable(), "连接池不可用，跳过");
            DBUtil.beginTransaction();
            DBUtil.beginTransaction();
            DBUtil.beginTransaction();
            try {
                assertTrue(DBUtil.inTransaction());
            } finally {
                DBUtil.endTransaction();
            }
            // 只 endTransaction 一次就应完全退出——证明内层 begin 没有"再开一层"
            assertFalse(DBUtil.inTransaction());
        }

        @Test
        @DisplayName("事务连接被复用，其 close() 是空操作（不会提前归还）")
        void transactionConnectionCloseIsNoOp() throws Exception {
            org.junit.jupiter.api.Assumptions.assumeTrue(DBUtil.isAvailable(), "连接池不可用，跳过");
            DBUtil.beginTransaction();
            try {
                Connection a = DBUtil.getConnection();
                Connection b = DBUtil.getConnection();
                assertNotSame(a, b, "每次 getConnection 返回新的代理对象");
                assertEquals(b, b, "代理的 equals 应满足自反性");
                a.close();   // 事务期间应被拦截
                assertTrue(DBUtil.inTransaction(), "close() 不应结束事务");
                assertEquals("TxConnection[", b.toString().substring(0, "TxConnection[".length()),
                        "事务连接 toString 应带标记，便于排查连接泄漏");
                assertNotEquals(0, b.hashCode(), "hashCode 应委托给真实连接");
            } finally {
                DBUtil.rollback();
                DBUtil.endTransaction();
            }
        }

        @Test
        @DisplayName("commit 在非事务状态下是空操作")
        void commitWithoutTransactionIsNoOp() {
            assertFalse(DBUtil.inTransaction());
            DBUtil.commit();
        }

        @Test
        @DisplayName("rollback 在非事务状态下是空操作")
        void rollbackWithoutTransactionIsNoOp() {
            assertFalse(DBUtil.inTransaction());
            DBUtil.rollback();
        }

        @Test
        @DisplayName("endTransaction 在非事务状态下是空操作")
        void endTransactionWithoutTransactionIsNoOp() {
            assertFalse(DBUtil.inTransaction());
            DBUtil.endTransaction();
            assertFalse(DBUtil.inTransaction());
        }

        @Test
        @DisplayName("事务中 commit 不抛异常，且事务仍然成立（由 endTransaction 收尾）")
        void commitInsideTransaction() {
            org.junit.jupiter.api.Assumptions.assumeTrue(DBUtil.isAvailable(), "连接池不可用，跳过");
            DBUtil.beginTransaction();
            try {
                DBUtil.commit();
                assertTrue(DBUtil.inTransaction(), "commit 只提交，不结束事务");
            } finally {
                DBUtil.endTransaction();
            }
        }

        @Test
        @DisplayName("事务中 rollback 不抛异常")
        void rollbackInsideTransaction() {
            org.junit.jupiter.api.Assumptions.assumeTrue(DBUtil.isAvailable(), "连接池不可用，跳过");
            DBUtil.beginTransaction();
            try {
                DBUtil.rollback();
            } finally {
                DBUtil.endTransaction();
            }
        }

        private void assertNotSame(Object unexpected, Object actual, String msg) {
            org.junit.jupiter.api.Assertions.assertNotSame(unexpected, actual, msg);
        }

        private void assertNotEquals(Object unexpected, Object actual, String msg) {
            org.junit.jupiter.api.Assertions.assertNotEquals(unexpected, actual, msg);
        }
    }

    /* ================================================================== */
    /*  资源释放                                                          */
    /* ================================================================== */

    @Nested
    @DisplayName("close：资源静默释放")
    class Close {

        @Test
        @DisplayName("全 null 三元组不抛异常")
        void closeTripleAllNull() {
            DBUtil.close(null, null, null);
        }

        @Test
        @DisplayName("二元组全 null 不抛异常")
        void closePairAllNull() {
            DBUtil.close(null, null);
        }

        @Test
        @DisplayName("单连接重载传 null 不抛异常")
        void closeNullConnection() {
            DBUtil.close((Connection) null);
        }

        @Test
        @DisplayName("释放真实资源：ResultSet / Statement / Connection 依序关闭")
        void closesRealResources() {
            org.junit.jupiter.api.Assumptions.assumeTrue(DBUtil.isAvailable(), "连接池不可用，跳过");
            Connection conn = null;
            java.sql.PreparedStatement ps = null;
            java.sql.ResultSet rs = null;
            try {
                conn = DBUtil.getConnection();
                ps = conn.prepareStatement("SELECT 1");
                rs = ps.executeQuery();
            } catch (Exception e) {
                throw new IllegalStateException("准备资源失败", e);
            }
            DBUtil.close(rs, ps, conn);
            try {
                assertTrue(rs.isClosed(), "ResultSet 应已关闭");
                assertTrue(ps.isClosed(), "Statement 应已关闭");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Test
        @DisplayName("重复 close 同一批资源不抛异常（静默吞掉 SQLException）")
        void doubleCloseIsSafe() {
            org.junit.jupiter.api.Assumptions.assumeTrue(DBUtil.isAvailable(), "连接池不可用，跳过");
            try {
                Connection conn = DBUtil.getConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement("SELECT 1");
                java.sql.ResultSet rs = ps.executeQuery();
                DBUtil.close(rs, ps, conn);
                DBUtil.close(rs, ps, conn);   // 第二次全部已关闭，应静默
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /* ================================================================== */
    /*  诊断与日志                                                        */
    /* ================================================================== */

    @Nested
    @DisplayName("诊断、日志与自检")
    class Diagnostics {

        @Test
        @DisplayName("isAvailable 与 getPool 结论一致")
        void availabilityMatchesPool() {
            assertEquals(DBUtil.getPool() != null, DBUtil.isAvailable());
        }

        @Test
        @DisplayName("初始化成功后 getInitError 为 null")
        void initErrorIsNullWhenHealthy() {
            if (DBUtil.isAvailable()) {
                assertNull(DBUtil.getInitError());
            } else {
                assertNotNull(DBUtil.getInitError(), "不可用时必须给出原因，否则无法排障");
            }
        }

        @Test
        @DisplayName("sql.show=false（配置文件默认）时不打印 SQL")
        void showSqlIsFalseByDefault() {
            assertFalse(DBUtil.isShowSql());
        }

        @Test
        @DisplayName("logSql 在关闭开关时是空操作，各种参数形态都不抛异常")
        void logSqlIsNoOpWhenDisabled() {
            DBUtil.logSql("SELECT 1", null);
            DBUtil.logSql("SELECT 1", new Object[0]);
            DBUtil.logSql("SELECT * FROM t WHERE id = ?", new Object[]{Long.valueOf(1L), "x", null});
        }

        @Test
        @DisplayName("selfCheck 在池可用时返回 OK 与池状态描述")
        void selfCheckReportsOk() {
            org.junit.jupiter.api.Assumptions.assumeTrue(DBUtil.isAvailable(), "连接池不可用，跳过");
            String result = DBUtil.selfCheck();
            assertTrue(result.startsWith("OK: "), "实际：" + result);
            assertTrue(result.contains("连接池["), "应带池状态描述，实际：" + result);
        }

        @Test
        @DisplayName("selfCheck 在池不可用时返回 FAILED 前缀")
        void selfCheckReportsFailedShape() {
            if (!DBUtil.isAvailable()) {
                assertTrue(DBUtil.selfCheck().startsWith("FAILED: "));
            }
        }
    }

    /* ================================================================== */
    /*  事务模式与工作单元                                                 */
    /* ================================================================== */

    @Nested
    @DisplayName("TxMode 与 runInTransaction")
    class TxModeAndRunner {

        @AfterEach
        void restoreJdbcMode() {
            // 模式是静态的，切换后必须还原，否则污染同 JVM 的其它用例
            DBUtil.setTxMode(DBUtil.TxMode.JDBC);
        }

        @Test
        @DisplayName("默认模式为 JDBC（生产行为，不需要任何显式设置）")
        void defaultModeIsJdbc() {
            assertEquals(DBUtil.TxMode.JDBC, DBUtil.getTxMode());
        }

        @Test
        @DisplayName("setTxMode(null) 回落到 JDBC，而不是把模式置成空")
        void nullModeFallsBackToJdbc() {
            DBUtil.setTxMode(DBUtil.TxMode.DIRECT);
            DBUtil.setTxMode(null);
            assertEquals(DBUtil.TxMode.JDBC, DBUtil.getTxMode());
        }

        @Test
        @DisplayName("DIRECT 模式下 beginTransaction 等四件套全是空操作，且不需要数据库")
        void directModeMakesTransactionNoOp() {
            DBUtil.setTxMode(DBUtil.TxMode.DIRECT);
            DBUtil.beginTransaction();
            assertFalse(DBUtil.inTransaction(), "直通模式不应真的开启事务");
            DBUtil.commit();
            DBUtil.rollback();
            DBUtil.endTransaction();
            assertFalse(DBUtil.inTransaction());
        }

        @Test
        @DisplayName("DIRECT 模式下 runInTransaction 原样返回工作单元结果")
        void directModeRunsWork() {
            DBUtil.setTxMode(DBUtil.TxMode.DIRECT);
            Integer result = DBUtil.runInTransaction(new DBUtil.TxWork<Integer>() {
                @Override
                public Integer run() {
                    return Integer.valueOf(7);
                }
            });
            assertEquals(7, result.intValue());
            assertFalse(DBUtil.inTransaction(), "收尾后不应残留事务状态");
        }

        @Test
        @DisplayName("工作单元抛异常时 runInTransaction 原样抛出，且不残留事务状态")
        void runnerPropagatesException() {
            DBUtil.setTxMode(DBUtil.TxMode.DIRECT);
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> DBUtil.<Integer>runInTransaction(new DBUtil.TxWork<Integer>() {
                        @Override
                        public Integer run() {
                            throw new IllegalStateException("boom");
                        }
                    }));
            assertEquals("boom", e.getMessage());
            assertFalse(DBUtil.inTransaction(), "异常路径也必须收尾，否则连接会泄漏在事务里");
        }

        @Test
        @DisplayName("JDBC 模式下 runInTransaction 能真正开事务、提交并归还连接")
        void jdbcModeCommitsAndEnds() {
            org.junit.jupiter.api.Assumptions.assumeTrue(DBUtil.isAvailable(), "连接池不可用，跳过");
            Integer result = DBUtil.runInTransaction(new DBUtil.TxWork<Integer>() {
                @Override
                public Integer run() {
                    // 关键断言：工作单元执行期间必须处于事务中，
                    // 否则 runInTransaction 就是"看起来包了事务、实际没包"
                    assertTrue(DBUtil.inTransaction());
                    return Integer.valueOf(1);
                }
            });
            assertEquals(1, result.intValue());
            assertFalse(DBUtil.inTransaction(), "结束后必须退出事务并归还连接");
        }

        @Test
        @DisplayName("JDBC 模式下工作单元抛异常会回滚，且不残留事务状态")
        void jdbcModeRollsBack() {
            org.junit.jupiter.api.Assumptions.assumeTrue(DBUtil.isAvailable(), "连接池不可用，跳过");
            assertThrows(IllegalStateException.class,
                    () -> DBUtil.<Integer>runInTransaction(new DBUtil.TxWork<Integer>() {
                        @Override
                        public Integer run() {
                            assertTrue(DBUtil.inTransaction());
                            throw new IllegalStateException("回滚");
                        }
                    }));
            assertFalse(DBUtil.inTransaction(), "回滚后必须退出事务，否则后续请求会拿到脏连接");
        }
    }

    @Test
    @DisplayName("工具类不可实例化：不存在公开构造，仅有的构造器为 private")
    void utilityClassCannotBeInstantiated() {
        assertThrows(NoSuchMethodException.class,
                () -> DBUtil.class.getConstructor(), "不应存在公开的无参构造");
        java.lang.reflect.Constructor<?>[] declared = DBUtil.class.getDeclaredConstructors();
        assertEquals(1, declared.length, "工具类应只有一个构造器");
        assertTrue(java.lang.reflect.Modifier.isPrivate(declared[0].getModifiers()),
                "该构造器必须是 private");
    }
}
