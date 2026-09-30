package com.zfz.typhoon.dao;

import com.zfz.typhoon.model.DisasterReport;

import java.util.List;

/**
 * 灾情报告表（{@code disaster_reports}）数据访问接口。
 *
 * <p>对应接口文档：{@code GET /api/typhoons/{id}/disaster}（历史灾情复盘）。
 * 同时被"相似案例"模块复用：匹配到历史台风后要展示其灾情摘要。
 *
 * <p>课程要求（阶段四 ·「历史台风需提供灾情复盘」）。
 * 本表的 {@code main_city_name} 不在表中，由联表 {@code cities} 取得，
 * 因此查询方法统一返回<b>已填充城市名</b>的实体，避免上层再查一次。
 *
 * @author 追风者项目组
 */
public interface DisasterReportDao {

    /**
     * 按台风查询灾情报告（通常一条，历史台风可能有多条更正报告）。
     *
     * @param typhoonId 台风ID
     * @return 灾情报告列表（按报告时间倒序）
     */
    List<DisasterReport> listByTyphoon(long typhoonId);

    /**
     * 取某台风最新一份灾情报告（详情页 / 相似案例面板使用）。
     *
     * @param typhoonId 台风ID
     * @return 灾情报告；无数据返回 null
     */
    DisasterReport findLatestByTyphoon(long typhoonId);

    /**
     * 按主键查询。
     *
     * @param reportId 灾情报告ID
     * @return 灾情报告；不存在返回 null
     */
    DisasterReport findById(long reportId);

    /**
     * 查询"有灾情记录"的台风ID集合（供档案页标记"含灾情"角标）。
     *
     * @return 台风ID列表
     */
    List<Long> listTyphoonIdsWithReport();

    /**
     * 新增灾情报告。
     *
     * @param report 灾情报告
     * @return 新生成的主键
     */
    long insert(DisasterReport report);

    /**
     * 更新灾情报告（数据更正场景）。
     *
     * @param report 灾情报告（必须含 reportId）
     * @return 受影响行数
     */
    int update(DisasterReport report);

    /**
     * 按台风删除灾情报告。
     *
     * @param typhoonId 台风ID
     * @return 受影响行数
     */
    int deleteByTyphoon(long typhoonId);
}
