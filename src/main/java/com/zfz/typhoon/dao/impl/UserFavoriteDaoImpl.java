package com.zfz.typhoon.dao.impl;

import com.zfz.typhoon.dao.UserFavoriteDao;
import com.zfz.typhoon.model.UserFavorite;
import com.zfz.typhoon.util.JdbcTemplate;
import com.zfz.typhoon.util.RowMapper;
import com.zfz.typhoon.util.RsUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 用户收藏表 DAO 实现。
 *
 * <p>注意 {@code typhoon_id IS NOT NULL} / {@code IS NULL} 的判断：
 * 这是本表区分"收藏台风"与"收藏城市"的唯一依据，
 * 不能用 {@code != ''}（NULL 与空串在 SQL 中语义不同）。
 *
 * @author 追风者项目组
 */
public class UserFavoriteDaoImpl implements UserFavoriteDao {

    private static final String COLUMNS =
            "f.favorite_id, f.user_id, f.typhoon_id, f.city_id, f.created_at";

    private static final RowMapper<UserFavorite> MAPPER = new RowMapper<UserFavorite>() {
        @Override
        public UserFavorite map(ResultSet rs) throws SQLException {
            UserFavorite f = new UserFavorite();
            f.setFavoriteId(RsUtil.getLong(rs, "favorite_id"));
            f.setUserId(RsUtil.getLong(rs, "user_id"));
            f.setTyphoonId(RsUtil.getLong(rs, "typhoon_id"));
            f.setCityId(RsUtil.getLong(rs, "city_id"));
            f.setCreatedAt(RsUtil.getDateTime(rs, "created_at"));
            return f;
        }
    };

    @Override
    public List<UserFavorite> listByUser(long userId) {
        return JdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM user_favorites f WHERE f.user_id = ? "
                        + "ORDER BY f.created_at DESC, f.favorite_id DESC",
                MAPPER, Long.valueOf(userId));
    }

    @Override
    public List<UserFavorite> listFavoriteTyphoons(long userId) {
        // 命中 uk_fav_user_typhoon_city 前缀 (user_id)
        return JdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM user_favorites f "
                        + "WHERE f.user_id = ? AND f.typhoon_id IS NOT NULL "
                        + "ORDER BY f.created_at DESC",
                MAPPER, Long.valueOf(userId));
    }

    @Override
    public List<UserFavorite> listFavoriteCities(long userId) {
        return JdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM user_favorites f "
                        + "WHERE f.user_id = ? AND f.city_id IS NOT NULL "
                        + "ORDER BY f.created_at DESC",
                MAPPER, Long.valueOf(userId));
    }

    @Override
    public List<Long> listFavoriteTyphoonIds(long userId) {
        return JdbcTemplate.query(
                "SELECT typhoon_id FROM user_favorites "
                        + "WHERE user_id = ? AND typhoon_id IS NOT NULL ORDER BY typhoon_id",
                new RowMapper<Long>() {
                    @Override
                    public Long map(ResultSet rs) throws SQLException {
                        return RsUtil.getLong(rs, "typhoon_id");
                    }
                }, Long.valueOf(userId));
    }

    @Override
    public List<Long> listFavoriteCityIds(long userId) {
        return JdbcTemplate.query(
                "SELECT city_id FROM user_favorites "
                        + "WHERE user_id = ? AND city_id IS NOT NULL ORDER BY city_id",
                new RowMapper<Long>() {
                    @Override
                    public Long map(ResultSet rs) throws SQLException {
                        return RsUtil.getLong(rs, "city_id");
                    }
                }, Long.valueOf(userId));
    }

    @Override
    public boolean existsTyphoonFavorite(long userId, long typhoonId) {
        Integer n = JdbcTemplate.queryInt(
                "SELECT COUNT(*) FROM user_favorites WHERE user_id = ? AND typhoon_id = ?",
                Long.valueOf(userId), Long.valueOf(typhoonId));
        return n != null && n.intValue() > 0;
    }

    @Override
    public boolean existsCityFavorite(long userId, long cityId) {
        Integer n = JdbcTemplate.queryInt(
                "SELECT COUNT(*) FROM user_favorites WHERE user_id = ? AND city_id = ?",
                Long.valueOf(userId), Long.valueOf(cityId));
        return n != null && n.intValue() > 0;
    }

    @Override
    public int countByUser(long userId) {
        Integer n = JdbcTemplate.queryInt(
                "SELECT COUNT(*) FROM user_favorites WHERE user_id = ?", Long.valueOf(userId));
        return n == null ? 0 : n.intValue();
    }

    @Override
    public long insert(UserFavorite f) {
        Long id = JdbcTemplate.insert(
                "INSERT INTO user_favorites (user_id, typhoon_id, city_id) VALUES (?, ?, ?)",
                f.getUserId(), f.getTyphoonId(), f.getCityId());
        return id == null ? 0L : id.longValue();
    }

    @Override
    public int deleteTyphoonFavorite(long userId, long typhoonId) {
        return JdbcTemplate.update(
                "DELETE FROM user_favorites WHERE user_id = ? AND typhoon_id = ?",
                Long.valueOf(userId), Long.valueOf(typhoonId));
    }

    @Override
    public int deleteCityFavorite(long userId, long cityId) {
        return JdbcTemplate.update(
                "DELETE FROM user_favorites WHERE user_id = ? AND city_id = ?",
                Long.valueOf(userId), Long.valueOf(cityId));
    }

    @Override
    public int deleteByIdAndUser(long favoriteId, long userId) {
        return JdbcTemplate.update(
                "DELETE FROM user_favorites WHERE favorite_id = ? AND user_id = ?",
                Long.valueOf(favoriteId), Long.valueOf(userId));
    }
}
