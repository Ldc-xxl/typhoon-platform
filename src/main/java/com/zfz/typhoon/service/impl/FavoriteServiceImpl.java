package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.UserFavoriteDao;
import com.zfz.typhoon.dao.impl.UserFavoriteDaoImpl;
import com.zfz.typhoon.model.UserFavorite;
import com.zfz.typhoon.model.vo.CityVO;
import com.zfz.typhoon.model.vo.TyphoonVO;
import com.zfz.typhoon.service.CityService;
import com.zfz.typhoon.service.FavoriteService;
import com.zfz.typhoon.service.TyphoonService;
import com.zfz.typhoon.util.DateUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 收藏业务实现。
 *
 * @author 追风者项目组
 * @see FavoriteService
 */
public class FavoriteServiceImpl implements FavoriteService {

    private final UserFavoriteDao favoriteDao;
    private final TyphoonService typhoonService;
    private final CityService cityService;

    /**
     * 生产用构造：按默认实现装配依赖。
     * <p>之所以把它拆成"无参 + 带参"两个构造，是为了给单元测试留出注入替身的缝隙：
     * 依赖全部在字段初始化里 new 出来的话，测试就必须连上真实 MySQL 才能跑，
     * 单元测试也就退化成了集成测试。
     */
    public FavoriteServiceImpl() {
        this(new UserFavoriteDaoImpl(), new TyphoonServiceImpl(), new CityServiceImpl());
    }

    /**
     * 测试用构造：注入替身依赖（Mockito 生成的 mock 或手写桩）。
     * <p>参数顺序与上方字段声明一致，均为本服务运行所需的协作者。
     */
    public FavoriteServiceImpl(UserFavoriteDao favoriteDao, TyphoonService typhoonService, CityService cityService) {
        this.favoriteDao = favoriteDao;
        this.typhoonService = typhoonService;
        this.cityService = cityService;
    }

    @Override
    public List<TyphoonVO> listTyphoons(long userId) {
        List<Long> ids = favoriteDao.listFavoriteTyphoonIds(userId);
        List<TyphoonVO> result = new ArrayList<TyphoonVO>(ids.size());
        for (Long id : ids) {
            TyphoonVO vo = quiet(id);
            if (vo != null) {
                result.add(vo);
            }
        }
        return result;
    }

    @Override
    public List<CityVO> listCities(long userId) {
        List<Long> ids = favoriteDao.listFavoriteCityIds(userId);
        List<CityVO> result = new ArrayList<CityVO>(ids.size());
        for (Long id : ids) {
            // 城市被停用后收藏仍应保留，前端按 status 自行灰显，所以这里不做过滤
            try {
                result.add(cityService.getVoById(id.longValue()));
            } catch (RuntimeException ignored) {
                // 城市已被删除：跳过该条而不是让整个列表 500
            }
        }
        return result;
    }

    @Override
    public boolean isTyphoonFavorited(long userId, long typhoonId) {
        return favoriteDao.existsTyphoonFavorite(userId, typhoonId);
    }

    @Override
    public boolean isCityFavorited(long userId, long cityId) {
        return favoriteDao.existsCityFavorite(userId, cityId);
    }

    @Override
    public long favoriteTyphoon(long userId, long typhoonId) {
        // 先校验台风存在，否则外键异常会以 500 的形式暴露给前端
        typhoonService.requireTyphoon(typhoonId);
        if (favoriteDao.existsTyphoonFavorite(userId, typhoonId)) {
            return existingTyphoonFavoriteId(userId, typhoonId);
        }
        UserFavorite favorite = new UserFavorite();
        favorite.setUserId(Long.valueOf(userId));
        favorite.setTyphoonId(Long.valueOf(typhoonId));
        // 城市外键必须显式留空：两个外键都有值会违反"二选一"的语义约定
        favorite.setCityId(null);
        favorite.setCreatedAt(DateUtil.now());
        return favoriteDao.insert(favorite);
    }

    @Override
    public long favoriteCity(long userId, long cityId) {
        cityService.requireCity(cityId);
        if (favoriteDao.existsCityFavorite(userId, cityId)) {
            return existingCityFavoriteId(userId, cityId);
        }
        UserFavorite favorite = new UserFavorite();
        favorite.setUserId(Long.valueOf(userId));
        favorite.setTyphoonId(null);
        favorite.setCityId(Long.valueOf(cityId));
        favorite.setCreatedAt(DateUtil.now());
        return favoriteDao.insert(favorite);
    }

    @Override
    public int unfavoriteTyphoon(long userId, long typhoonId) {
        return favoriteDao.deleteTyphoonFavorite(userId, typhoonId);
    }

    @Override
    public int unfavoriteCity(long userId, long cityId) {
        return favoriteDao.deleteCityFavorite(userId, cityId);
    }

    @Override
    public int count(long userId) {
        return favoriteDao.countByUser(userId);
    }

    /* ==================================================================== */
    /*  内部                                                                */
    /* ==================================================================== */

    /**
     * 静默取台风视图。
     *
     * <p>收藏的台风被删除（或归档后已不可见）时不应让收藏列表整体失败，
     * 因此这里吞掉 404 返回 null。注意只吞业务层的"不存在"，
     * 数据库故障仍会向上抛出——那是真的异常，不该被静默。
     *
     * @param typhoonId 台风ID
     * @return 视图；不存在返回 null
     */
    private TyphoonVO quiet(Long typhoonId) {
        try {
            return typhoonService.findById(typhoonId.longValue());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 取既有台风收藏的ID（幂等返回用）。
     *
     * @param userId    用户ID
     * @param typhoonId 台风ID
     * @return 收藏ID；找不到返回 0
     */
    private long existingTyphoonFavoriteId(long userId, long typhoonId) {
        for (UserFavorite f : favoriteDao.listFavoriteTyphoons(userId)) {
            if (f.getTyphoonId() != null && f.getTyphoonId().longValue() == typhoonId) {
                return f.getFavoriteId().longValue();
            }
        }
        return 0L;
    }

    /**
     * 取既有城市收藏的ID（幂等返回用）。
     *
     * @param userId 用户ID
     * @param cityId 城市ID
     * @return 收藏ID；找不到返回 0
     */
    private long existingCityFavoriteId(long userId, long cityId) {
        for (UserFavorite f : favoriteDao.listFavoriteCities(userId)) {
            if (f.getCityId() != null && f.getCityId().longValue() == cityId) {
                return f.getFavoriteId().longValue();
            }
        }
        return 0L;
    }
}
