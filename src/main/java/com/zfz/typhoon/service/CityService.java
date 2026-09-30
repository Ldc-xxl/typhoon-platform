package com.zfz.typhoon.service;

import com.zfz.typhoon.model.City;
import com.zfz.typhoon.model.vo.CityVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 城市业务接口。
 *
 * <p>对应接口文档：{@code GET /api/cities}（地图打点与筛选下拉）。
 *
 * <h3>为什么单独抽一个服务并加缓存</h3>
 * 城市是全站最热的<b>主数据</b>：地图打点、风圈影响分析、风险评估、
 * 问卷聚合、通知订阅都要用到，而它<b>几乎不变</b>（课程场景下 14 条）。
 * 因此本服务在进程内维护一份缓存，把"每请求查库"降为"启动读一次 + 主动失效"，
 * 这是课程要求「性能优化」中<b>应用层缓存</b>的落地示例。
 *
 * <p>缓存一致性策略：写操作（{@link #create} / {@link #updateCity}）后
 * <b>主动调用 {@link #refresh()}</b> 重建，而不是设置过期时间——
 * 因为课程场景下写操作极低频，主动刷新既简单又不会读到脏数据。
 *
 * @author 追风者项目组
 */
public interface CityService {

    /**
     * 查询全部启用城市（走缓存）。
     *
     * @return 城市视图列表
     */
    List<CityVO> listEnabled();

    /**
     * 查询全部城市（含停用，走缓存）。
     *
     * @return 城市视图列表
     */
    List<CityVO> listAll();

    /**
     * 按主键查询城市（走缓存）。
     *
     * @param cityId 城市ID
     * @return 城市；不存在返回 null
     */
    City getById(long cityId);

    /**
     * 按主键查询城市视图。
     *
     * @param cityId 城市ID
     * @return 城市视图；不存在时抛 404 业务异常
     */
    CityVO getVoById(long cityId);

    /**
     * 取"ID → 城市"映射（走缓存），供批量组装时避免 N 次查询。
     *
     * @return 不可变映射
     */
    Map<Long, City> asMap();

    /**
     * 按名称模糊查询城市。
     *
     * @param keyword 关键词
     * @return 城市视图列表
     */
    List<CityVO> search(String keyword);

    /**
     * 查询省份清单。
     *
     * @return 省份列表
     */
    List<String> listProvinces();

    /**
     * 判断城市是否存在（供 Service 层做外键前置校验，把数据库异常转成友好提示）。
     *
     * @param cityId 城市ID
     * @return 存在返回 true
     */
    boolean exists(long cityId);

    /**
     * 校验城市ID是否有效，无效则抛 400。
     *
     * @param cityId 城市ID
     * @return 校验通过的城市
     */
    City requireCity(long cityId);

    /**
     * 新增城市并刷新缓存。
     *
     * @param city 城市
     * @return 新城市ID
     */
    long create(City city);

    /**
     * 更新城市并刷新缓存。
     *
     * @param city 城市
     * @return 受影响行数
     */
    int updateCity(City city);

    /**
     * 按省份取城市清单（走缓存过滤，不再查库）。
     *
     * @param province 省份
     * @return 城市列表
     */
    List<City> listByProvince(String province);

    /**
     * 按ID集合批量取城市（走缓存）。
     *
     * @param cityIds ID集合
     * @return 城市列表
     */
    List<City> listByIds(Collection<Long> cityIds);

    /**
     * 重建缓存（管理端修改城市后调用）。
     */
    void refresh();
}
