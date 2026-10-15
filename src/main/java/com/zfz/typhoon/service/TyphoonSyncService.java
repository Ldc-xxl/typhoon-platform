package com.zfz.typhoon.service;

/**
 * 台风数据同步服务：把中央气象台台风网的公开数据采集并落库。
 *
 * <h3>它解决什么问题</h3>
 * <p>本平台的台风数据原本是静态快照，页面上写的"每 10 分钟同步一次"只是文案。
 * 本服务是让这句话变成事实的那一环：由 {@code SyncSchedulerListener} 周期性调用，
 * 把外部真实数据拉回来，写进 {@code typhoons / typhoon_tracks / wind_circles /
 * forecast_paths} 四张表，并如实登记一条 {@code sync_logs}。
 *
 * <h3>同步策略（这三条是重点）</h3>
 * <ol>
 *   <li><b>只插入没见过的</b>：按国际编号查重，库里已有的台风不会重复插入；
 *       编号在数据库上有唯一约束（{@code uk_typhoons_intl_id}），重复插入会直接报错。</li>
 *   <li><b>活跃台风持续追新</b>：接口标记为 {@code start} 的台风，每轮都重新拉详情并
 *       按「台风 + 观测时间」幂等写入轨迹与风圈 —— 这样它新发布的观测点会被追加进来，
 *       而不是停留在第一次采集到的那一版。</li>
 *   <li><b>既有档案只读不动</b>：库里已有、且接口未列为活跃的台风（含人工准备的
 *       历史档案）一律跳过。宁可不更新，也不覆盖别人整理好的数据。</li>
 * </ol>
 *
 * <h3>已知限制</h3>
 * <p>若一个台风的活跃期结束后，其状态不会自动从 {@code ACTIVE} 收尾为
 * {@code FINISHED} —— 因为"由本服务创建"与"人工录入"在表结构上没有区分标记，
 * 贸然批量收尾会误伤人工数据。这类台风会被计入结果的 {@code stale} 数，
 * 由运维人工确认。这是刻意的保守取舍，不是遗漏。
 *
 * @author 追风者项目组
 */
public interface TyphoonSyncService {

    /**
     * 执行一轮同步。
     *
     * <p>本方法<b>不抛异常</b>：网络不通、接口改版、数据库异常都会折算进返回结果，
     * 因为调用方是定时任务，让它因异常而中断会造成"任务悄悄死掉"这种最难排查的故障。
     *
     * @return 本轮同步的统计结果，永不为 null
     */
    SyncResult syncAll();

    /**
     * 一轮同步的统计结果。
     *
     * <p>字段命名直接对应 {@code sync_logs} 与页面上的数字，
     * 便于运维对照"日志里写了什么、页面上看到了什么"。
     */
    class SyncResult {

        /** 接口返回的台风条数 */
        public int fetched;
        /** 本轮新增的台风数 */
        public int inserted;
        /** 本轮被追踪更新的活跃台风数 */
        public int updated;
        /** 因"库里已有且非活跃"而跳过的台风数 */
        public int skipped;
        /** 处理失败的台风数 */
        public int failed;
        /** 库里为活跃、但接口未列为活跃的台风数（需人工确认，见类注释"已知限制"） */
        public int stale;
        /** 写入/更新的轨迹点数 */
        public int tracks;
        /** 写入/更新的风圈条数 */
        public int circles;
        /** 写入的预报点数 */
        public int forecasts;
        /** 耗时（毫秒） */
        public long durationMs;
        /** 首个失败原因（供日志展示，null 表示无失败） */
        public String firstError;

        /**
         * 是否整轮成功。
         *
         * @return 无失败为 true
         */
        public boolean ok() {
            return failed == 0;
        }

        /**
         * 登记到同步日志的"记录条数"。
         *
         * <p>取轨迹点数而非台风数：运维关心的是搬运了多少数据量，
         * 与 {@code DataSourceServiceImpl} 里"今日请求次数按 record_count 求和"的口径一致。
         *
         * @return 轨迹点数
         */
        public int recordCount() {
            return tracks;
        }

        /**
         * 供日志与页面展示的单行摘要。
         *
         * @return 摘要文本
         */
        public String summary() {
            StringBuilder sb = new StringBuilder(96);
            sb.append("接口 ").append(fetched).append(" 条")
              .append("，新增 ").append(inserted)
              .append("，更新 ").append(updated)
              .append("，跳过 ").append(skipped);
            if (failed > 0) {
                sb.append("，失败 ").append(failed);
            }
            if (stale > 0) {
                sb.append("，待确认 ").append(stale);
            }
            sb.append("；轨迹 ").append(tracks)
              .append("，风圈 ").append(circles)
              .append("，预报 ").append(forecasts)
              .append("，耗时 ").append(durationMs).append("ms");
            return sb.toString();
        }
    }
}
