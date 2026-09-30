package com.zfz.typhoon.dao.impl;

import com.zfz.typhoon.dao.CityDao;
import com.zfz.typhoon.model.City;
import com.zfz.typhoon.util.JdbcTemplate;
import com.zfz.typhoon.util.RowMapper;
import com.zfz.typhoon.util.RsUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 城市信息表 DAO 实现。
 *
 * @author 追风者项目组
 */
public class CityDaoImpl implements CityDao {

    private static final String COLUMNS =
            "c.city_id, c.city_name, c.province, c.latitude, c.longitude, "
                    + "c.population, c.is_coastal, c.base_risk_level, c.status";

    private static final RowMapper<City> MAPPER = new RowMapper<City>() {
        @Override
        public City map(ResultSet rs) throws SQLException {
            City c = new City();
            c.setCityId(RsUtil.getLong(rs, "city_id"));
            c.setCityName(RsUtil.getString(rs, "city_name"));
            c.setProvince(RsUtil.getString(rs, "province"));
            c.setLatitude(RsUtil.getDouble(rs, "latitude"));
            c.setLongitude(RsUtil.getDouble(rs, "longitude"));
            c.setPopulation(RsUtil.getInt(rs, "population"));
            c.setCoastal(RsUtil.getBoolean(rs, "is_coastal"));
            c.setBaseRiskLevel(RsUtil.getInt(rs, "base_risk_level"));
            c.setStatus(RsUtil.getBoolean(rs, "status"));
            return c;
        }
    };

    @Override
    public City findById(long cityId) {
        String sql = "SELECT " + COLUMNS + " FROM cities c WHERE c.city_id = ?";
        return JdbcTemplate.queryOne(sql, MAPPER, Long.valueOf(cityId));
    }

    @Override
    public City findByNameAndProvince(String cityName, String province) {
        // 命中唯一键 uk_cities_name_province
        String sql = "SELECT " + COLUMNS + " FROM cities c "
                + "WHERE c.city_name = ? AND c.province = ?";
        return JdbcTemplate.queryOne(sql, MAPPER, cityName, province);
    }

    @Override
    public List<City> searchByName(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return listEnabled();
        }
        String sql = "SELECT " + COLUMNS + " FROM cities c "
                + "WHERE c.city_name LIKE ? OR c.province LIKE ? "
                + "ORDER BY c.base_risk_level DESC, c.city_id ASC";
        String like = "%" + keyword.trim() + "%";
        return JdbcTemplate.query(sql, MAPPER, like, like);
    }

    @Override
    public List<City> listEnabled() {
        // ORDER BY base_risk_level 命中 idx_cities_risk_level
        return JdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM cities c WHERE c.status = 1 "
                        + "ORDER BY c.base_risk_level DESC, c.city_id ASC", MAPPER);
    }

    @Override
    public List<City> listAll() {
        return JdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM cities c ORDER BY c.city_id ASC", MAPPER);
    }

    @Override
    public List<City> listByProvince(String province) {
        // 命中 idx_cities_province
        return JdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM cities c WHERE c.province = ? "
                        + "ORDER BY c.base_risk_level DESC, c.city_id ASC",
                MAPPER, province);
    }

    @Override
    public List<City> listByIds(Collection<Long> cityIds) {
        if (cityIds == null || cityIds.isEmpty()) {
            return new ArrayList<City>();
        }
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < cityIds.size(); i++) {
            if (i > 0) {
                in.append(", ");
            }
            in.append('?');
        }
        String sql = "SELECT " + COLUMNS + " FROM cities c "
                + "WHERE c.city_id IN (" + in + ") ORDER BY c.city_id ASC";
        return JdbcTemplate.query(sql, MAPPER, cityIds.toArray());
    }

    @Override
    public List<String> listProvinces() {
        return JdbcTemplate.query(
                "SELECT DISTINCT province FROM cities ORDER BY province",
                new RowMapper<String>() {
                    @Override
                    public String map(ResultSet rs) throws SQLException {
                        return RsUtil.getString(rs, "province");
                    }
                });
    }

    @Override
    public long insert(City c) {
        String sql = "INSERT INTO cities (city_name, province, latitude, longitude, population, "
                + "is_coastal, base_risk_level, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        Long id = JdbcTemplate.insert(sql,
                c.getCityName(), c.getProvince(), c.getLatitude(), c.getLongitude(),
                c.getPopulation(),
                Boolean.valueOf(c.getCoastal() != null && c.getCoastal().booleanValue()),
                c.getBaseRiskLevel() == null ? Integer.valueOf(2) : c.getBaseRiskLevel(),
                Boolean.valueOf(c.getStatus() == null || c.getStatus().booleanValue()));
        return id == null ? 0L : id.longValue();
    }

    @Override
    public int update(City c) {
        String sql = "UPDATE cities SET city_name = ?, province = ?, latitude = ?, longitude = ?, "
                + "population = ?, is_coastal = ?, base_risk_level = ?, status = ? WHERE city_id = ?";
        return JdbcTemplate.update(sql,
                c.getCityName(), c.getProvince(), c.getLatitude(), c.getLongitude(),
                c.getPopulation(),
                Boolean.valueOf(c.getCoastal() != null && c.getCoastal().booleanValue()),
                c.getBaseRiskLevel(),
                Boolean.valueOf(c.getStatus() == null || c.getStatus().booleanValue()),
                c.getCityId());
    }
}
