package com.zfz.typhoon.dao.impl;

import com.zfz.typhoon.dao.TyphoonAlbumDao;
import com.zfz.typhoon.model.TyphoonAlbum;
import com.zfz.typhoon.util.JdbcTemplate;
import com.zfz.typhoon.util.RowMapper;
import com.zfz.typhoon.util.RsUtil;
import com.zfz.typhoon.util.SqlBuilder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 台风云图表 DAO 实现。
 *
 * @author 追风者项目组
 */
public class TyphoonAlbumDaoImpl implements TyphoonAlbumDao {

    private static final String COLUMNS =
            "al.album_id, al.typhoon_id, al.image_type, al.image_url, al.shot_time, al.description";

    private static final RowMapper<TyphoonAlbum> MAPPER = new RowMapper<TyphoonAlbum>() {
        @Override
        public TyphoonAlbum map(ResultSet rs) throws SQLException {
            TyphoonAlbum a = new TyphoonAlbum();
            a.setAlbumId(RsUtil.getLong(rs, "album_id"));
            a.setTyphoonId(RsUtil.getLong(rs, "typhoon_id"));
            a.setImageType(RsUtil.getString(rs, "image_type"));
            a.setImageUrl(RsUtil.getString(rs, "image_url"));
            a.setShotTime(RsUtil.getDateTime(rs, "shot_time"));
            a.setDescription(RsUtil.getString(rs, "description"));
            return a;
        }
    };

    private static final String INSERT_SQL =
            "INSERT INTO typhoon_albums (typhoon_id, image_type, image_url, shot_time, description) "
                    + "VALUES (?, ?, ?, ?, ?)";

    @Override
    public List<TyphoonAlbum> listByTyphoon(long typhoonId) {
        // 命中 idx_album_typhoon；shot_time 倒序展示最新影像
        String sql = "SELECT " + COLUMNS + " FROM typhoon_albums al "
                + "WHERE al.typhoon_id = ? ORDER BY al.shot_time DESC, al.album_id DESC";
        return JdbcTemplate.query(sql, MAPPER, Long.valueOf(typhoonId));
    }

    @Override
    public List<TyphoonAlbum> listByTyphoonAndType(long typhoonId, String imageType) {
        SqlBuilder sb = SqlBuilder.select(COLUMNS).from("typhoon_albums al")
                .eq("al.typhoon_id", Long.valueOf(typhoonId))
                .eq("al.image_type", imageType)
                .orderBy("al.shot_time DESC, al.album_id DESC");
        SqlBuilder.Built built = sb.build();
        return JdbcTemplate.query(built.getSql(), MAPPER, built.getParams());
    }

    @Override
    public TyphoonAlbum findLatest(long typhoonId, String imageType) {
        String sql = "SELECT " + COLUMNS + " FROM typhoon_albums al "
                + "WHERE al.typhoon_id = ? AND al.image_type = ? "
                + "ORDER BY al.shot_time DESC, al.album_id DESC LIMIT 1";
        return JdbcTemplate.queryOne(sql, MAPPER, Long.valueOf(typhoonId), imageType);
    }

    @Override
    public long insert(TyphoonAlbum a) {
        Long id = JdbcTemplate.insert(INSERT_SQL, paramsOf(a));
        return id == null ? 0L : id.longValue();
    }

    @Override
    public int batchInsert(List<TyphoonAlbum> albums) {
        if (albums == null || albums.isEmpty()) {
            return 0;
        }
        List<Object[]> rows = new ArrayList<Object[]>(albums.size());
        for (TyphoonAlbum a : albums) {
            rows.add(paramsOf(a));
        }
        return JdbcTemplate.batchUpdate(INSERT_SQL, rows);
    }

    @Override
    public int deleteByTyphoon(long typhoonId) {
        return JdbcTemplate.update(
                "DELETE FROM typhoon_albums WHERE typhoon_id = ?", Long.valueOf(typhoonId));
    }

    /**
     * 摊平参数（顺序与 INSERT_SQL 一致）。
     *
     * @param a 影像
     * @return 参数数组
     */
    private static Object[] paramsOf(TyphoonAlbum a) {
        return new Object[]{
                a.getTyphoonId(), a.getImageType(), a.getImageUrl(),
                a.getShotTime(), a.getDescription()
        };
    }
}
