package com.zfz.typhoon.dao;

import com.zfz.typhoon.dao.impl.CityDaoImpl;
import com.zfz.typhoon.dao.impl.UserDaoImpl;
import com.zfz.typhoon.model.City;
import com.zfz.typhoon.model.User;
import com.zfz.typhoon.util.DBUtil;
import com.zfz.typhoon.util.JdbcTemplate;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户与城市 DAO 的集成测试。
 *
 * <p>{@code users} 是登录、鉴权、后台管理的地基；{@code cities} 决定了
 * 风险评估算哪些城市、地图上标哪些点。两者一旦映射出错，
 * 表现都是"页面能开但数据不对"，很难从界面上定位，所以要在这里钉死。
 *
 * @author 追风者项目组
 */
@DisplayName("用户与城市 DAO 集成测试")
class UserAndCityDaoTest {

    private static UserDao userDao;
    private static CityDao cityDao;
    private static long sampleUserId;
    private static long sampleCityId;

    @BeforeAll
    static void setUp() {
        Assumptions.assumeTrue(DBUtil.isAvailable(),
                "本机数据库不可用（" + DBUtil.getInitError() + "），跳过");
        userDao = new UserDaoImpl();
        cityDao = new CityDaoImpl();
        Long uid = JdbcTemplate.queryLong("SELECT MIN(user_id) FROM users");
        sampleUserId = uid == null ? 0L : uid.longValue();
        Long cid = JdbcTemplate.queryLong("SELECT MIN(city_id) FROM cities");
        sampleCityId = cid == null ? 0L : cid.longValue();
    }

    @Nested
    @DisplayName("UserDaoImpl")
    class Users {

        @Test
        @DisplayName("findById：存在的用户能取到，不存在的返回 null")
        void findById() {
            User u = userDao.findById(sampleUserId);
            assertNotNull(u);
            assertEquals(Long.valueOf(sampleUserId), u.getUserId());
            assertNotNull(u.getUsername());
            assertNull(userDao.findById(-999L));
        }

        @Test
        @DisplayName("findByUsername / findByUsernameForAuth：后者才带口令摘要")
        void findByUsername() {
            String name = JdbcTemplate.queryString(
                    "SELECT username FROM users WHERE user_id = ?", Long.valueOf(sampleUserId));
            User plain = userDao.findByUsername(name);
            assertNotNull(plain);
            assertEquals(name, plain.getUsername());

            User auth = userDao.findByUsernameForAuth(name);
            assertNotNull(auth, "登录链路必须能取到用户");
            assertNotNull(auth.getPasswordHash(),
                    "鉴权查询必须返回口令摘要，否则任何口令都会通过校验");
            assertNull(userDao.findByUsernameForAuth("no-such-user-9999"));
        }

        @Test
        @DisplayName("existsUsername：已存在返回 true，不存在返回 false")
        void existsUsername() {
            String name = JdbcTemplate.queryString(
                    "SELECT username FROM users WHERE user_id = ?", Long.valueOf(sampleUserId));
            assertTrue(userDao.existsUsername(name));
            assertFalse(userDao.existsUsername("no-such-user-9999"),
                    "用户名唯一性校验依赖此方法，误报 true 会导致注册被无故拒绝");
        }

        @Test
        @DisplayName("listAll / listByRole / search：列表与筛选")
        void lists() {
            List<User> all = userDao.listAll();
            assertNotNull(all);
            assertFalse(all.isEmpty(), "演示库里应有用户");
            for (User u : all) {
                assertNotNull(u.getUserId());
                assertNotNull(u.getUsername());
            }
            assertNotNull(userDao.listByRole("ADMIN"));
            assertNotNull(userDao.search(null, null, null));
            assertNotNull(userDao.search("user", "USER", Boolean.TRUE));
        }

        @Test
        @DisplayName("统计口径：countAll 与 listAll 一致")
        void counts() {
            assertEquals(userDao.listAll().size(), userDao.countAll());
            assertTrue(userDao.countByRole("USER") >= 0);
            assertTrue(userDao.countEnabled() >= 0);
        }

        @Test
        @DisplayName("写入：insert → 改资料 / 改口令 / 记登录 / 启停用 → delete")
        void writeRoundTrip() {
            User u = new User();
            u.setUsername("ut_dao_user");
            u.setPasswordHash("sha256$0000000000000000000000000000000000000000000000000000000000000000");
            u.setNickname("DAO测试用户");
            u.setRole("USER");
            u.setStatus(Boolean.TRUE);

            JdbcTemplate.update("DELETE FROM users WHERE username = ?", "ut_dao_user");
            long id = userDao.insert(u);
            try {
                assertNotNull(userDao.findById(id));
                assertTrue(userDao.existsUsername("ut_dao_user"));

                User saved = userDao.findById(id);
                saved.setNickname("改过昵称");
                assertEquals(1, userDao.updateProfile(saved));
                assertEquals("改过昵称", userDao.findById(id).getNickname());

                assertEquals(1, userDao.updatePassword(id, "sha256$1111"));
                assertEquals("sha256$1111", userDao.findByUsernameForAuth("ut_dao_user").getPasswordHash());

                assertEquals(1, userDao.updateLastLogin(id),
                        "登录成功后必须能刷新 last_login，否则后台会一直显示「—」");
                assertNotNull(userDao.findById(id).getLastLogin(),
                        "更新时间由数据库 NOW() 产生，回读应能取到");

                assertEquals(1, userDao.setEnabled(id, false));
                assertEquals(1, userDao.setRole(id, "ADMIN"));
                assertEquals("ADMIN", userDao.findById(id).getRole());
            } finally {
                userDao.deleteById(id);
            }
            assertNull(userDao.findById(id));
        }
    }

    @Nested
    @DisplayName("CityDaoImpl")
    class Cities {

        @Test
        @DisplayName("findById：城市基础信息（含经纬度）")
        void findById() {
            City c = cityDao.findById(sampleCityId);
            assertNotNull(c);
            assertEquals(Long.valueOf(sampleCityId), c.getCityId());
            assertNotNull(c.getCityName());
            assertNotNull(c.getLatitude(), "没有经纬度就没法在地图上标点、也没法算距离");
            assertNotNull(c.getLongitude());
            assertNull(cityDao.findById(-999L));
        }

        @Test
        @DisplayName("findByNameAndProvince：按「城市名 + 省」唯一定位")
        void findByNameAndProvince() {
            City c = cityDao.findById(sampleCityId);
            City again = cityDao.findByNameAndProvince(c.getCityName(), c.getProvince());
            assertNotNull(again);
            assertEquals(c.getCityId(), again.getCityId());
            assertNull(cityDao.findByNameAndProvince("不存在的城市", "不存在的省"));
        }

        @Test
        @DisplayName("listEnabled / listAll / searchByName / listByProvince")
        void lists() {
            List<City> all = cityDao.listAll();
            assertNotNull(all);
            assertFalse(all.isEmpty());
            assertNotNull(cityDao.listEnabled());
            assertTrue(cityDao.listEnabled().size() <= all.size(),
                    "启用列表是全集的子集");
            assertNotNull(cityDao.searchByName("海"));
            assertNotNull(cityDao.listByProvince("广东"));
        }

        @Test
        @DisplayName("listByIds：批量取城市；空集合返回空列表（不能拼出非法 IN()）")
        void listByIds() {
            List<City> rows = cityDao.listByIds(
                    Arrays.asList(Long.valueOf(sampleCityId), Long.valueOf(sampleCityId + 1)));
            assertNotNull(rows);
            assertTrue(cityDao.listByIds(new ArrayList<Long>()).isEmpty(),
                    "空 IN() 会让 MySQL 报语法错误，必须在 DAO 层拦掉");
        }

        @Test
        @DisplayName("listProvinces：省份下拉的数据来源，不能有重复或 null")
        void listProvinces() {
            List<String> provinces = cityDao.listProvinces();
            assertNotNull(provinces);
            for (String p : provinces) {
                assertNotNull(p, "省份为 null 会让前端下拉渲染出空白项");
            }
            assertEquals(new java.util.HashSet<String>(provinces).size(), provinces.size(),
                    "省份列表应已去重");
        }

        @Test
        @DisplayName("写入：insert → update → delete")
        void writeRoundTrip() {
            City c = new City();
            c.setCityName("UT测试市");
            c.setProvince("UT省");
            c.setLatitude(Double.valueOf(22.5D));
            c.setLongitude(Double.valueOf(114.0D));
            c.setPopulation(Integer.valueOf(1000));
            c.setCoastal(Boolean.TRUE);
            c.setBaseRiskLevel(Integer.valueOf(3));
            c.setStatus(Boolean.TRUE);

            JdbcTemplate.update("DELETE FROM cities WHERE city_name = ? AND province = ?",
                    "UT测试市", "UT省");
            long id = cityDao.insert(c);
            try {
                City saved = cityDao.findById(id);
                assertNotNull(saved);
                saved.setBaseRiskLevel(Integer.valueOf(5));
                assertEquals(1, cityDao.update(saved));
                assertEquals(Integer.valueOf(5), cityDao.findById(id).getBaseRiskLevel());
            } finally {
                JdbcTemplate.update("DELETE FROM cities WHERE city_id = ?", Long.valueOf(id));
            }
            assertNull(cityDao.findById(id));
        }
    }
}
