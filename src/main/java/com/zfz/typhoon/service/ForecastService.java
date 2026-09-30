package com.zfz.typhoon.service;

import com.zfz.typhoon.model.ForecastPath;
import com.zfz.typhoon.model.vo.AgencyVO;
import com.zfz.typhoon.model.vo.ForecastPathVO;

import java.util.List;
import java.util.Map;

/**
 * 预报路径与机构业务接口。
 *
 * <p>对应接口文档：{@code GET /api/typhoons/{id}/forecast-paths}（多机构路径对比）、
 * {@code GET /api/agencies}（机构清单，用于图例与配色）。
 *
 * <h3>最关键的业务约束：多条路径必须共用同一起点</h3>
 * 这是气象业务常识——<b>已经走过的路径各机构不会有分歧</b>。
 * 各机构发布的预报路径，起点都是"发布时刻的实测位置"。但数据库里
 * 不同机构的 {@code publish_time} 可能相差几小时（例如 CMA 08:00 发布、
 * JMA 06:00 发布），若直接画出来，会出现三条线在海上"分叉成三根"的
 * 错误图形，让用户误以为"机构之间连已走过的路径都不一致"。
 *
 * <p>因此本服务输出 {@link ForecastPathVO} 时统一执行<b>起点校正</b>：
 * 用台风<b>当前实测位置</b>替换各机构路径的第一点，
 * 并丢弃预报时刻早于该实测时刻的点（那部分已被实况覆盖）。
 *
 * <h3>关于「登陆点」文案</h3>
 * {@code landing} 不是数据库字段，而是<b>由末点位置反推</b>：
 * 取距离末点最近的城市，结合其是否沿海生成"厦门沿海"或"南平一带"。
 * 这样保证文案与几何数据永远一致——不会出现"路径画到汕头、
 * 文案却写厦门"的矛盾。
 *
 * @author 追风者项目组
 */
public interface ForecastService {

    /** 预报路径默认线型（与设计稿一致，长虚线） */
    String DEFAULT_DASH = "7 5";

    /**
     * 查询台风的多机构预报路径（已完成起点校正）。
     *
     * @param typhoonId 台风ID
     * @return 各机构路径视图列表，按机构ID升序；无数据返回空列表
     */
    List<ForecastPathVO> listPaths(long typhoonId);

    /**
     * 查询台风各机构最新发布的原始预报点（未组装为 VO，供分歧分析使用）。
     *
     * @param typhoonId 台风ID
     * @return 预报点列表
     */
    List<ForecastPath> listLatestRaw(long typhoonId);

    /**
     * 查询某机构最新发布的预报点。
     *
     * @param typhoonId  台风ID
     * @param agencyCode 机构代码
     * @return 预报点列表（按预报时刻升序）
     */
    List<ForecastPath> listLatestByAgency(long typhoonId, String agencyCode);

    /**
     * 按机构代码把预报点归组（机构代码 → 该机构的有序预报点）。
     *
     * @param typhoonId 台风ID
     * @return 有序映射（LinkedHashMap，保持机构ID顺序）
     */
    Map<String, List<ForecastPath>> groupByAgency(long typhoonId);

    /**
     * 查询全部启用的预报机构（前端图例 / 配色）。
     *
     * @return 机构视图列表
     */
    List<AgencyVO> listAgencies();

    /**
     * 统计某台风的预报点数量。
     *
     * @param typhoonId 台风ID
     * @return 数量
     */
    int countByTyphoon(long typhoonId);

    /**
     * 取台风当前实测位置作为路径起点（经度、纬度）。
     *
     * @param typhoonId 台风ID
     * @return 长度 2 的数组 {@code [lon, lat]}；无实况返回 null
     */
    double[] currentPosition(long typhoonId);
}
