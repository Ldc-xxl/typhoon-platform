package com.zfz.typhoon.service;

import com.zfz.typhoon.model.vo.CityVO;
import com.zfz.typhoon.model.vo.TyphoonVO;

import java.util.List;

/**
 * 收藏业务接口。
 *
 * <p>对应接口文档：{@code GET /api/favorites}、
 * {@code POST /api/favorites}、{@code DELETE /api/favorites/{id}}。
 *
 * <h3>收藏的两类对象</h3>
 * 台风档案（一次性的历史记录）与常用城市（长期关注的居住地）。
 * 表 {@code user_favorites} 用可空的两个外键表示这两种形态，
 * 并靠唯一键 {@code (user_id, typhoon_id, city_id)} 保证不重复收藏。
 *
 * <p>服务层因此要额外守住一件事：<b>同一行不能既收藏台风又收藏城市</b>。
 * 虽然唯一键能挡住重复，但挡不住"两个外键都有值"这种语义错误。
 *
 * @author 追风者项目组
 */
public interface FavoriteService {

    /**
     * 查询用户收藏的台风。
     *
     * @param userId 用户ID
     * @return 台风视图列表
     */
    List<TyphoonVO> listTyphoons(long userId);

    /**
     * 查询用户收藏的城市。
     *
     * @param userId 用户ID
     * @return 城市视图列表
     */
    List<CityVO> listCities(long userId);

    /**
     * 判断是否已收藏某台风（详情页心形图标状态）。
     *
     * @param userId    用户ID
     * @param typhoonId 台风ID
     * @return 已收藏返回 true
     */
    boolean isTyphoonFavorited(long userId, long typhoonId);

    /**
     * 判断是否已收藏某城市。
     *
     * @param userId 用户ID
     * @param cityId 城市ID
     * @return 已收藏返回 true
     */
    boolean isCityFavorited(long userId, long cityId);

    /**
     * 收藏台风（幂等：已收藏时直接返回，不报错）。
     *
     * @param userId    用户ID
     * @param typhoonId 台风ID
     * @return 新收藏ID；已存在时返回既有ID
     */
    long favoriteTyphoon(long userId, long typhoonId);

    /**
     * 收藏城市（幂等）。
     *
     * @param userId 用户ID
     * @param cityId 城市ID
     * @return 新收藏ID；已存在时返回既有ID
     */
    long favoriteCity(long userId, long cityId);

    /**
     * 取消收藏台风。
     *
     * @param userId    用户ID
     * @param typhoonId 台风ID
     * @return 受影响行数
     */
    int unfavoriteTyphoon(long userId, long typhoonId);

    /**
     * 取消收藏城市。
     *
     * @param userId 用户ID
     * @param cityId 城市ID
     * @return 受影响行数
     */
    int unfavoriteCity(long userId, long cityId);

    /**
     * 收藏总数。
     *
     * @param userId 用户ID
     * @return 条数
     */
    int count(long userId);
}
