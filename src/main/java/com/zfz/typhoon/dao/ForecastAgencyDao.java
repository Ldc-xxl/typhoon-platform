package com.zfz.typhoon.dao;

import com.zfz.typhoon.model.ForecastAgency;

import java.util.Collection;
import java.util.List;

/**
 * 预报机构表（{@code forecast_agencies}）数据访问接口。
 *
 * <p>对应接口文档：{@code GET /api/agencies}（机构清单，用于分歧面板图例、
 * 地图路径配色、管理端机构维护）。
 *
 * <p>课程要求（阶段三 · ①「主数据表需提供下拉选项接口」）。
 * 本表是典型的主数据（master data）：条数少、变动少、被大量引用，
 * 因此除写入外全部按 {@code status} 过滤为启用项。
 *
 * @author 追风者项目组
 */
public interface ForecastAgencyDao {

    /**
     * 按主键查询机构。
     *
     * @param agencyId 机构ID
     * @return 机构；不存在返回 null
     */
    ForecastAgency findById(long agencyId);

    /**
     * 按机构代码查询（如 CMA / JMA / JTWC）。
     *
     * @param agencyCode 机构代码
     * @return 机构；不存在返回 null
     */
    ForecastAgency findByCode(String agencyCode);

    /**
     * 查询全部机构（按机构ID升序）。
     *
     * @return 机构列表
     */
    List<ForecastAgency> listAll();

    /**
     * 查询启用的机构（管理端"停用"的机构不出现在前端图例）。
     *
     * @return 机构列表
     */
    List<ForecastAgency> listEnabled();

    /**
     * 按机构代码批量查询（用于把多条预报路径的 agencyCode 一次性换成机构实体）。
     *
     * @param codes 机构代码集合
     * @return 机构列表
     */
    List<ForecastAgency> listByCodes(Collection<String> codes);

    /**
     * 新增机构。
     *
     * @param agency 机构
     * @return 新生成的主键
     */
    long insert(ForecastAgency agency);

    /**
     * 更新机构信息与启用状态。
     *
     * @param agency 机构（必须含 agencyId）
     * @return 受影响行数
     */
    int update(ForecastAgency agency);

    /**
     * 启用 / 停用机构。
     *
     * @param agencyId 机构ID
     * @param enabled  是否启用
     * @return 受影响行数
     */
    int setEnabled(long agencyId, boolean enabled);
}
