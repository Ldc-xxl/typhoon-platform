package com.zfz.typhoon.dao;

import com.zfz.typhoon.model.CityImpactRecord;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 城市影响记录表（{@code city_impact_records}）数据访问接口。
 *
 * <p>对应接口文档：{@code GET /api/typhoons/{id}/affected-cities}
 * （详情页"受影响城市"清单与影响时段）。
 *
 * <p>课程要求（阶段四 ·「依据风圈与城市距离自动生成受影响城市」）。
 * 本表是<b>派生数据</b>：不是人工录入，而是由风圈几何分析算出后落库，
 * 因此写入策略是"重算即覆盖"——每次分析先按台风清除旧记录再批量写入，
 * 保证前端读到的永远是最近一次分析的结果，不会新旧混杂。
 *
 * @author 追风者项目组
 * @see com.zfz.typhoon.util.GeoUtil
 */
public interface CityImpactRecordDao {

    /**
     * 按台风查询影响记录（按风圈等级降序、距离升序）。
     *
     * <p>排序即业务语义：先 12 级风圈城市，同级内先近后远，
     * 前端直接按序渲染无需再排。
     *
     * @param typhoonId 台风ID
     * @return 影响记录列表
     */
    List<CityImpactRecord> listByTyphoon(long typhoonId);

    /**
     * 按台风 + 城市查询影响记录。
     *
     * @param typhoonId 台风ID
     * @param cityId    城市ID
     * @return 影响记录列表
     */
    List<CityImpactRecord> listByTyphoonAndCity(long typhoonId, long cityId);

    /**
     * 查询某台风落在指定风圈等级内的城市记录（如"所有 12 级风圈内城市"）。
     *
     * @param typhoonId      台风ID
     * @param windCircleLevel 风圈等级：7 / 10 / 12
     * @return 影响记录列表
     */
    List<CityImpactRecord> listByWindCircleLevel(long typhoonId, int windCircleLevel);

    /**
     * 统计某台风的影响城市数量。
     *
     * @param typhoonId 台风ID
     * @return 数量
     */
    int countByTyphoon(long typhoonId);

    /**
     * 查询最近一次分析的时间（前端显示"分析于 xx:xx"）。
     *
     * @param typhoonId 台风ID
     * @return 时间；无数据返回 null
     */
    LocalDateTime findLatestGeneratedAt(long typhoonId);

    /**
     * 批量新增影响记录。
     *
     * @param records 影响记录集合
     * @return 实际写入行数
     */
    int batchInsert(List<CityImpactRecord> records);

    /**
     * 幂等批量写入：命中唯一键
     * {@code uk_impact_typhoon_city (typhoon_id, city_id, wind_circle_level, generated_at)}
     * 时更新风力与时段。
     *
     * @param records 影响记录集合
     * @return 受影响行数
     */
    int upsertAll(List<CityImpactRecord> records);

    /**
     * 删除某台风的全部影响记录（重算前清理，见类注释"重算即覆盖"）。
     *
     * @param typhoonId 台风ID
     * @return 受影响行数
     */
    int deleteByTyphoon(long typhoonId);
}
