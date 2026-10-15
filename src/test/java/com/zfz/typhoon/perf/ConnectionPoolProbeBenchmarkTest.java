package com.zfz.typhoon.perf;

import com.zfz.typhoon.util.SimpleConnectionPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 连接池「探活对称性」压测（对应 {@code docs/code-review-round3.md} 的 N8）。
 *
 * <h3>要回答的问题</h3>
 * {@code SimpleConnectionPool} 借出侧做了优化：连接归还后未满
 * {@code pool.idleSafeNs} 就再次借出时，跳过 {@code isValid} 探活；
 * 但归还侧 {@code release()} 的短路顺序是
 * {@code closed || idle.size() >= maxIdle || !isValid(real)}——
 * 中间那一项在池未满时为假，于是<b>每次归还都必然探活一次</b>。
 *
 * <p>于是出现一个"优化被自己对冲掉"的局面：借出侧省下的往返，
 * 归还侧又付了回去。本压测把这件事量化成两组对照数据：
 * <table border="1">
 *   <caption>对照设置</caption>
 *   <tr><th>轮次</th><th>pool.idleSafeNs</th><th>借出侧探活</th></tr>
 *   <tr><td>A</td><td>0</td><td>每次都探活（优化前的行为）</td></tr>
 *   <tr><td>B</td><td>{@code Long.MAX_VALUE}</td><td>从不探活（优化的理论上限）</td></tr>
 * </table>
 *
 * <h3>为什么断言的是次数而不是时间</h3>
 * 墙钟耗时受 JIT、调度、机器负载影响，
 * 写进断言必然成为随机失败的来源。
 * 因此本类<b>断言探活次数</b>（确定性），把耗时打印出来仅供人读。
 * 每次 {@code isValid} 由 {@link FakeJdbcDriver} 模拟固定的网络往返时延，
 * 因此"次数差"可以直接换算成"省下/多付了几次往返"。
 *
 * <h3>这个用例同时是一道"绊线"</h3>
 * <b>2026-10-11 已裁定：维持现状（方案①），代码不改</b> —— 依据是本类量出的
 * "归还侧探活是坏连接唯一的拦截点"这一事实（完整裁定见
 * {@code docs/code-review-round3.md} 的「N8 压测结论与裁定」）。
 * 因此本类的断言是<b>对当前决策的固化</b>，不是"顺手写死"。
 *
 * <p>若将来真要落地对称化（上表方案②），
 * {@code assertEquals(ROUNDS, current.returnProbes)} 会立刻失败。
 * 这是刻意设计的：<b>改动连接池的探活语义，必须显式回到那条决策记录上</b>，
 * 并同步补上"归还后连接被外部关闭、再次借出必须探活到"的用例
 * （可用 {@link FakeJdbcDriver} 制造 {@code isClosed() == true}），
 * 而不是顺手把断言改绿。
 *
 * <h3>为什么放在默认测试集里</h3>
 * 断言的是确定性计数、不是耗时，所以不会成为随机失败源；
 * 代价是每轮全量测试多约 16s。若日后嫌慢，可用 surefire 的 excludes
 * 把 {@code perf} 包整包排除（注意写排除通配时别把 {@code *} 与 {@code /}
 * 直接连写成注释终止符），<b>但不要删掉用例本身</b>。
 *
 * @author 追风者项目组
 */
@DisplayName("连接池探活压测 · idleSafeNs 对照实验")
class ConnectionPoolProbeBenchmarkTest {

    /** 每轮借还次数 */
    private static final int ROUNDS = 2000;

    /** 模拟一次探活的往返时延（微秒）。200µs 约等于同机房/本地网络的量级 */
    private static final long PROBE_MICROS = 200L;

    /** 配置键：连接池按 DBUtil.getConfig 读取，系统属性优先级最高，正好用来做对照 */
    private static final String CONFIG_KEY = "pool.idleSafeNs";

    @BeforeAll
    static void registerFakeDriver() {
        FakeJdbcDriver.ensureRegistered();
        FakeJdbcDriver.probeMicros = PROBE_MICROS;
    }

    @AfterEach
    void clearConfigOverride() {
        System.clearProperty(CONFIG_KEY);
    }

    @Test
    @DisplayName("借出侧优化确实生效，但归还侧每次都探活，把收益对折")
    void borrowOptimizationIsHalvedByReturnProbe() throws Exception {
        Result old = runRound("A · idleSafeNs=0（优化前：借出必探活）", 0L);
        Result current = runRound("B · idleSafeNs=极大（优化后：借出不探活）", Long.MAX_VALUE);

        printReport(old, current);

        assertEquals(ROUNDS, old.borrowProbes, "A 轮借出侧应每次都探活");
        assertEquals(0, current.borrowProbes, "B 轮借出侧应完全不探活——这就是本次优化的收益");
        assertEquals(ROUNDS, old.returnProbes, "A 轮归还侧每次都探活");
        assertEquals(ROUNDS, current.returnProbes,
                "B 轮归还侧仍然每次都探活：优化只做了一半，这一半的往返抵消了借出侧的节省");
        assertEquals(old.borrowProbes + old.returnProbes, old.totalProbes());
        assertTrue(current.totalProbes() < old.totalProbes(),
                "总探活次数应下降，否则优化没有任何净收益");
    }

    @Test
    @DisplayName("稳态下每轮净探活次数：优化前 2 次，优化后仍有 1 次")
    void probesPerRoundInSteadyState() throws Exception {
        Result old = runRound("A · idleSafeNs=0", 0L);
        Result current = runRound("B · idleSafeNs=极大", Long.MAX_VALUE);

        assertEquals(2.0D, old.probesPerRound(), 1e-9, "优化前每轮 2 次（借出 1 + 归还 1）");
        assertEquals(1.0D, current.probesPerRound(), 1e-9, "优化后每轮仍有 1 次（全在归还侧）");
    }

    /**
     * 跑一轮：反复"借出 → 立刻归还"，分别统计两侧的探活次数与耗时。
     *
     * @param label      轮次标签（打印用）
     * @param idleSafeNs 本次覆盖的 {@code pool.idleSafeNs}
     * @return 统计结果
     */
    private Result runRound(String label, long idleSafeNs) throws Exception {
        System.setProperty(CONFIG_KEY, String.valueOf(idleSafeNs));
        FakeJdbcDriver.reset();

        // maxActive=maxIdle=1：池里只有一条连接，每次归还都会走到
        // release() 的 "idle 队列未满 → then isValid" 判断，正是要观测的那条路径
        SimpleConnectionPool pool = new SimpleConnectionPool(
                FakeJdbcDriver.class.getName(), FakeJdbcDriver.URL, "u", "p",
                1, 1, 1, 0, 2000L);

        long borrowProbes = 0L;
        long returnProbes = 0L;
        long start = System.nanoTime();
        try {
            for (int i = 0; i < ROUNDS; i++) {
                int before = FakeJdbcDriver.VALIDATIONS.get();
                Connection conn = pool.getConnection();
                int afterBorrow = FakeJdbcDriver.VALIDATIONS.get();
                borrowProbes += afterBorrow - before;

                conn.close();
                int afterReturn = FakeJdbcDriver.VALIDATIONS.get();
                returnProbes += afterReturn - afterBorrow;
            }
        } finally {
            pool.shutdown();
        }
        long elapsedNanos = System.nanoTime() - start;

        return new Result(label, borrowProbes, returnProbes, elapsedNanos,
                FakeJdbcDriver.CONNECTS.get());
    }

    private void printReport(Result old, Result current) {
        StringBuilder sb = new StringBuilder();
        sb.append(System.lineSeparator());
        sb.append("================ 连接池探活压测（每轮 ").append(ROUNDS)
                .append(" 次借还，单次探活模拟 ").append(PROBE_MICROS).append("µs）================\n");
        sb.append(String.format("%-34s %10s %10s %10s %12s %10s%n",
                "轮次", "借出探活", "归还探活", "探活合计", "耗时(ms)", "新建连接"));
        for (Result r : new Result[]{old, current}) {
            sb.append(String.format("%-34s %10d %10d %10d %12.2f %10d%n",
                    r.label, r.borrowProbes, r.returnProbes, r.totalProbes(),
                    r.elapsedNanos / 1_000_000.0D, r.connects));
        }
        long savedTotal = old.totalProbes() - current.totalProbes();
        long savedNanos = old.elapsedNanos - current.elapsedNanos;
        sb.append("----------------------------------------------------------------------\n");
        sb.append("优化净收益：少 ").append(savedTotal).append(" 次探活（")
                .append(old.totalProbes() == 0 ? "0" : String.format("%.1f%%",
                        savedTotal * 100.0D / old.totalProbes()))
                .append("），少耗时 ").append(String.format("%.2f ms", savedNanos / 1_000_000.0D))
                .append('\n');
        sb.append("剩余成本：归还侧每轮仍固定 ").append(current.returnProbes)
                .append(" 次探活，占优化后总探活的 100%\n");
        sb.append("若把归还侧也改成窗口判定（对称化），稳态下两侧都可不探活，\n");
        sb.append("可再省 ").append(current.returnProbes).append(" 次探活（约 ")
                .append(String.format("%.2f ms", current.returnProbes * PROBE_MICROS / 1000.0D))
                .append("）——即当前实现只拿到了理论上限的一半收益。\n");
        sb.append("========================================================================\n");
        System.out.print(sb);
    }

    /** 单轮压测结果 */
    private static final class Result {
        final String label;
        final long borrowProbes;
        final long returnProbes;
        final long elapsedNanos;
        final int connects;

        Result(String label, long borrowProbes, long returnProbes, long elapsedNanos, int connects) {
            this.label = label;
            this.borrowProbes = borrowProbes;
            this.returnProbes = returnProbes;
            this.elapsedNanos = elapsedNanos;
            this.connects = connects;
        }

        long totalProbes() {
            return borrowProbes + returnProbes;
        }

        double probesPerRound() {
            return (double) totalProbes() / ROUNDS;
        }
    }
}
