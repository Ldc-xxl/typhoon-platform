package com.zfz.typhoon.dao;

import com.zfz.typhoon.dao.query.TyphoonQuery;
import com.zfz.typhoon.model.Typhoon;

import java.util.List;

/**
 * 台风主表（{@code typhoons}）数据访问接口。
 *
 * <p>课程要求（阶段三 · ①「DAO 层必须实现接口与实现分离」）。
 * 本接口只描述<b>能力</b>，不含任何 JDBC / SQL 细节；
 * 实现见 {@code com.zfz.typhoon.dao.impl.TyphoonDaoImpl}。
 * Service 层只依赖本接口，后续若要换成连接池以外的实现（如缓存装饰器）
 * 无需改动上层代码——这正是"面向接口编程"的价值。
 *
 * <p>对应接口文档：{@code docs/api.md} 中
 * {@code GET /api/typhoons}（列表 / 筛选）、{@code GET /api/typhoons/{id}}（详情）。
 *
 * @author 追风者项目组
 * @see com.zfz.typhoon.dao.impl.TyphoonDaoImpl
 */
public interface TyphoonDao {

    /** 生命周期状态：活跃 */
    String STATUS_ACTIVE = "ACTIVE";

    /** 生命周期状态：已结束 */
    String STATUS_FINISHED = "FINISHED";

    /* ------------------------------------------------------------------ */
    /*  单条查询                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * 按主键查询台风。
     *
     * @param typhoonId 台风ID
     * @return 台风；不存在返回 null
     */
    Typhoon findById(long typhoonId);

    /**
     * 按国际编号查询台风（如 202615）。
     *
     * @param internationalId 国际编号
     * @return 台风；不存在返回 null
     */
    Typhoon findByInternationalId(String internationalId);

    /* ------------------------------------------------------------------ */
    /*  列表与检索                                                          */
    /* ------------------------------------------------------------------ */

    /**
     * 查询全部台风（按生成时间倒序）。
     *
     * @return 台风列表，永不为 null
     */
    List<Typhoon> listAll();

    /**
     * 按状态查询台风（按生成时间倒序）。
     *
     * @param status ACTIVE / FINISHED
     * @return 台风列表
     */
    List<Typhoon> listByStatus(String status);

    /**
     * 多条件组合检索（总览页筛选栏 / 档案页）。
     *
     * @param query 查询条件，各字段均可为 null
     * @return 台风列表
     */
    List<Typhoon> search(TyphoonQuery query);

    /**
     * 统计满足条件的台风数量（供分页使用）。
     *
     * @param query 查询条件
     * @return 记录数
     */
    int countByCondition(TyphoonQuery query);

    /**
     * 查询出现过台风的年份清单（降序），供档案页年份下拉框。
     *
     * @return 年份列表
     */
    List<Integer> listYears();

    /**
     * 查询出现过的海域清单，供筛选下拉框。
     *
     * @return 海域列表
     */
    List<String> listBasins();

    /* ------------------------------------------------------------------ */
    /*  统计                                                               */
    /* ------------------------------------------------------------------ */

    /**
     * 统计全部台风数量。
     *
     * @return 总数
     */
    int countAll();

    /**
     * 按状态统计数量。
     *
     * @param status ACTIVE / FINISHED
     * @return 数量
     */
    int countByStatus(String status);

    /**
     * 统计已归档台风数量。
     *
     * @return 数量
     */
    int countArchived();

    /* ------------------------------------------------------------------ */
    /*  写入                                                               */
    /* ------------------------------------------------------------------ */

    /**
     * 新增台风。
     *
     * @param typhoon 台风（新增时忽略 createTime / updateTime，由数据库默认值填充）
     * @return 新生成的主键
     */
    long insert(Typhoon typhoon);

    /**
     * 更新台风基础信息（不含历史极值统计）。
     *
     * @param typhoon 台风（必须含 typhoonId）
     * @return 受影响行数
     */
    int update(Typhoon typhoon);

    /**
     * 依据轨迹点重算并回写历史极值（max_wind_force / max_wind_speed / min_pressure）。
     *
     * <p>课程要求（阶段五 ·「数据同步后需自动维护台风极值」）。
     * 用一条 UPDATE ... SELECT 完成，避免应用层多轮查询。
     *
     * @param typhoonId 台风ID
     * @return 受影响行数
     */
    int refreshIntensityExtremes(long typhoonId);

    /**
     * 归档台风：置为 FINISHED 并标记历史档案。
     *
     * @param typhoonId 台风ID
     * @param dissipateTime 消亡时间
     * @return 受影响行数
     */
    int archive(long typhoonId, java.time.LocalDateTime dissipateTime);

    /**
     * 按主键删除台风（外键级联会一并删除轨迹、风圈等）。
     *
     * @param typhoonId 台风ID
     * @return 受影响行数
     */
    int deleteById(long typhoonId);
}
