package com.zfz.typhoon.dao;

import com.zfz.typhoon.model.City;

import java.util.Collection;
import java.util.List;

/**
 * 城市信息表（{@code cities}）数据访问接口。
 *
 * <p>对应接口文档：{@code GET /api/cities}（城市清单）、
 * {@code GET /api/typhoons/{id}/affected-cities}（受影响城市，需与城市表关联）。
 *
 * <p>课程要求（阶段四 ·「城市为风险评估与问卷统计的维度主体」）。
 * 本表是全站最热的主数据：地图打点、风险评估、问卷聚合、通知订阅
 * 都要读它，因此 Service 层会做进程内缓存（见 {@code CityServiceImpl}），
 * DAO 只负责提供稳定的全量查询与按主键查询。
 *
 * @author 追风者项目组
 */
public interface CityDao {

    /**
     * 按主键查询城市。
     *
     * @param cityId 城市ID
     * @return 城市；不存在返回 null
     */
    City findById(long cityId);

    /**
     * 按"城市名 + 省份"精确查询（命中唯一键 {@code uk_cities_name_province}）。
     *
     * @param cityName 城市名称
     * @param province 所属省份
     * @return 城市；不存在返回 null
     */
    City findByNameAndProvince(String cityName, String province);

    /**
     * 按名称模糊查询（重名城市可能返回多条）。
     *
     * @param keyword 名称关键词
     * @return 城市列表
     */
    List<City> searchByName(String keyword);

    /**
     * 查询全部启用城市（按风险等级降序、城市ID升序）。
     *
     * @return 城市列表
     */
    List<City> listEnabled();

    /**
     * 查询全部城市（含停用，供管理端维护）。
     *
     * @return 城市列表
     */
    List<City> listAll();

    /**
     * 按省份查询城市。
     *
     * @param province 省份
     * @return 城市列表
     */
    List<City> listByProvince(String province);

    /**
     * 按主键集合批量查询（用于把影响记录 / 问卷聚合结果一次换成城市实体）。
     *
     * @param cityIds 城市ID集合
     * @return 城市列表
     */
    List<City> listByIds(Collection<Long> cityIds);

    /**
     * 查询全部省份清单（去重），供筛选下拉框。
     *
     * @return 省份列表
     */
    List<String> listProvinces();

    /**
     * 新增城市。
     *
     * @param city 城市
     * @return 新生成的主键
     */
    long insert(City city);

    /**
     * 更新城市基础信息。
     *
     * @param city 城市（必须含 cityId）
     * @return 受影响行数
     */
    int update(City city);
}
