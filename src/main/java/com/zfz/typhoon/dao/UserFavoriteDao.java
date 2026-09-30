package com.zfz.typhoon.dao;

import com.zfz.typhoon.model.UserFavorite;

import java.util.List;

/**
 * 用户收藏表（{@code user_favorites}）数据访问接口。
 *
 * <p>课程要求（阶段四 ·「用户可收藏台风档案与常用城市，形成快捷入口」）。
 * 对应接口文档：{@code GET /api/favorites}、{@code POST /api/favorites}、
 * {@code DELETE /api/favorites/{id}}。
 *
 * <h3>NULL 语义（本表设计要点）</h3>
 * {@code typhoon_id} 与 {@code city_id} 都可为 NULL，通过"一列 NULL、
 * 另一列有值"来表达收藏的是台风还是城市。
 * 唯一键 {@code uk_fav_user_typhoon_city (user_id, typhoon_id, city_id)} 因此
 * 在同一用户下分别约束"同一台风只能收一次""同一城市只能收一次"。
 *
 * <p>注意：MySQL 中 NULL 不参与唯一性比较，所以"同用户 + 同台风"的两条记录
 * （city_id 都是 NULL）会被唯一键正确拦截——这正是需要的语义；
 * 但 Service 在插入前仍应查一次，以便给出友好提示而不是抛数据库异常。
 *
 * @author 追风者项目组
 */
public interface UserFavoriteDao {

    /**
     * 查询某用户全部收藏（按收藏时间倒序）。
     *
     * @param userId 用户ID
     * @return 收藏列表
     */
    List<UserFavorite> listByUser(long userId);

    /**
     * 查询某用户收藏的台风记录（{@code typhoon_id} 非空、{@code city_id} 空）。
     *
     * @param userId 用户ID
     * @return 收藏列表
     */
    List<UserFavorite> listFavoriteTyphoons(long userId);

    /**
     * 查询某用户收藏的城市记录（{@code city_id} 非空、{@code typhoon_id} 空）。
     *
     * @param userId 用户ID
     * @return 收藏列表
     */
    List<UserFavorite> listFavoriteCities(long userId);

    /**
     * 查询某用户收藏的台风ID集合（供列表页标记"已收藏"星标）。
     *
     * @param userId 用户ID
     * @return 台风ID列表
     */
    List<Long> listFavoriteTyphoonIds(long userId);

    /**
     * 查询某用户收藏的城市ID集合。
     *
     * @param userId 用户ID
     * @return 城市ID列表
     */
    List<Long> listFavoriteCityIds(long userId);

    /**
     * 判断是否已收藏某台风。
     *
     * @param userId    用户ID
     * @param typhoonId 台风ID
     * @return 已收藏返回 true
     */
    boolean existsTyphoonFavorite(long userId, long typhoonId);

    /**
     * 判断是否已收藏某城市。
     *
     * @param userId 用户ID
     * @param cityId 城市ID
     * @return 已收藏返回 true
     */
    boolean existsCityFavorite(long userId, long cityId);

    /**
     * 统计某用户的收藏总数。
     *
     * @param userId 用户ID
     * @return 数量
     */
    int countByUser(long userId);

    /**
     * 新增收藏。
     *
     * @param favorite 收藏记录
     * @return 新生成的主键
     */
    long insert(UserFavorite favorite);

    /**
     * 取消收藏台风。
     *
     * @param userId    用户ID
     * @param typhoonId 台风ID
     * @return 受影响行数
     */
    int deleteTyphoonFavorite(long userId, long typhoonId);

    /**
     * 取消收藏城市。
     *
     * @param userId 用户ID
     * @param cityId 城市ID
     * @return 受影响行数
     */
    int deleteCityFavorite(long userId, long cityId);

    /**
     * 按主键取消收藏（需同时匹配用户，防越权）。
     *
     * @param favoriteId 收藏ID
     * @param userId     当前用户ID
     * @return 受影响行数
     */
    int deleteByIdAndUser(long favoriteId, long userId);
}
