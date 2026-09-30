package com.zfz.typhoon.dao.impl;

import com.zfz.typhoon.dao.SimilarCaseMatchDao;
import com.zfz.typhoon.model.SimilarCaseMatch;
import com.zfz.typhoon.util.JdbcTemplate;
import com.zfz.typhoon.util.RowMapper;
import com.zfz.typhoon.util.RsUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 相似案例匹配记录表 DAO 实现。
 *
 * @author 追风者项目组
 */
public class SimilarCaseMatchDaoImpl implements SimilarCaseMatchDao {

    private static final String COLUMNS =
            "m.match_id, m.current_typhoon_id, m.history_typhoon_id, m.similarity_score, "
                    + "m.path_similarity, m.peak_intensity_similarity, m.season_similarity, "
                    + "m.move_speed_similarity, m.wind_circle_similarity, m.match_result, m.created_at";

    private static final RowMapper<SimilarCaseMatch> MAPPER = new RowMapper<SimilarCaseMatch>() {
        @Override
        public SimilarCaseMatch map(ResultSet rs) throws SQLException {
            SimilarCaseMatch m = new SimilarCaseMatch();
            m.setMatchId(RsUtil.getLong(rs, "match_id"));
            m.setCurrentTyphoonId(RsUtil.getLong(rs, "current_typhoon_id"));
            m.setHistoryTyphoonId(RsUtil.getLong(rs, "history_typhoon_id"));
            m.setSimilarityScore(RsUtil.getDouble(rs, "similarity_score"));
            m.setPathSimilarity(RsUtil.getDouble(rs, "path_similarity"));
            m.setPeakIntensitySimilarity(RsUtil.getDouble(rs, "peak_intensity_similarity"));
            m.setSeasonSimilarity(RsUtil.getDouble(rs, "season_similarity"));
            m.setMoveSpeedSimilarity(RsUtil.getDouble(rs, "move_speed_similarity"));
            m.setWindCircleSimilarity(RsUtil.getDouble(rs, "wind_circle_similarity"));
            m.setMatchResult(ForecastDisagreeRecordDaoImpl.readJson(RsUtil.getString(rs, "match_result")));
            m.setCreatedAt(RsUtil.getDateTime(rs, "created_at"));
            return m;
        }
    };

    private static final String INSERT_COLUMNS =
            "(current_typhoon_id, history_typhoon_id, similarity_score, path_similarity, "
                    + "peak_intensity_similarity, season_similarity, move_speed_similarity, "
                    + "wind_circle_similarity, match_result) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_SQL = "INSERT INTO similar_case_matches " + INSERT_COLUMNS;

    /** 命中 uk_scm_pair 时改为更新分数（重算同一对台风不产生重复行） */
    private static final String UPSERT_SQL = "INSERT INTO similar_case_matches " + INSERT_COLUMNS
            + " ON DUPLICATE KEY UPDATE "
            + "similarity_score = VALUES(similarity_score), "
            + "path_similarity = VALUES(path_similarity), "
            + "peak_intensity_similarity = VALUES(peak_intensity_similarity), "
            + "season_similarity = VALUES(season_similarity), "
            + "move_speed_similarity = VALUES(move_speed_similarity), "
            + "wind_circle_similarity = VALUES(wind_circle_similarity), "
            + "match_result = VALUES(match_result)";

    @Override
    public List<SimilarCaseMatch> listByCurrentTyphoon(long currentTyphoonId) {
        // 命中 uk_scm_pair 首列 + idx_scm_score
        String sql = "SELECT " + COLUMNS + " FROM similar_case_matches m "
                + "WHERE m.current_typhoon_id = ? "
                + "ORDER BY m.similarity_score DESC, m.match_id ASC";
        return JdbcTemplate.query(sql, MAPPER, Long.valueOf(currentTyphoonId));
    }

    @Override
    public List<SimilarCaseMatch> listTopMatches(long currentTyphoonId, int topN) {
        if (topN <= 0) {
            topN = 3;
        }
        // 无条件构造，直接绑定 LIMIT 参数（MySQL 允许 LIMIT ?）
        String sql = "SELECT " + COLUMNS + " FROM similar_case_matches m "
                + "WHERE m.current_typhoon_id = ? "
                + "ORDER BY m.similarity_score DESC, m.match_id ASC LIMIT ?";
        return JdbcTemplate.query(sql, MAPPER, Long.valueOf(currentTyphoonId), Integer.valueOf(topN));
    }

    @Override
    public SimilarCaseMatch findByPair(long currentTyphoonId, long historyTyphoonId) {
        String sql = "SELECT " + COLUMNS + " FROM similar_case_matches m "
                + "WHERE m.current_typhoon_id = ? AND m.history_typhoon_id = ?";
        return JdbcTemplate.queryOne(sql, MAPPER,
                Long.valueOf(currentTyphoonId), Long.valueOf(historyTyphoonId));
    }

    @Override
    public List<SimilarCaseMatch> listByHistoryTyphoon(long historyTyphoonId) {
        // 命中 idx_scm_history
        String sql = "SELECT " + COLUMNS + " FROM similar_case_matches m "
                + "WHERE m.history_typhoon_id = ? ORDER BY m.similarity_score DESC";
        return JdbcTemplate.query(sql, MAPPER, Long.valueOf(historyTyphoonId));
    }

    @Override
    public long insert(SimilarCaseMatch m) {
        Long id = JdbcTemplate.insert(INSERT_SQL, paramsOf(m));
        return id == null ? 0L : id.longValue();
    }

    @Override
    public int upsert(SimilarCaseMatch m) {
        return JdbcTemplate.update(UPSERT_SQL, paramsOf(m));
    }

    @Override
    public int upsertAll(List<SimilarCaseMatch> matches) {
        if (matches == null || matches.isEmpty()) {
            return 0;
        }
        List<Object[]> rows = new ArrayList<Object[]>(matches.size());
        for (SimilarCaseMatch m : matches) {
            rows.add(paramsOf(m));
        }
        return JdbcTemplate.batchUpdate(UPSERT_SQL, rows);
    }

    @Override
    public int deleteByCurrentTyphoon(long currentTyphoonId) {
        return JdbcTemplate.update(
                "DELETE FROM similar_case_matches WHERE current_typhoon_id = ?",
                Long.valueOf(currentTyphoonId));
    }

    /**
     * 摊平参数（顺序与 INSERT_COLUMNS 一致）。
     *
     * @param m 匹配记录
     * @return 参数数组
     */
    private static Object[] paramsOf(SimilarCaseMatch m) {
        return new Object[]{
                m.getCurrentTyphoonId(), m.getHistoryTyphoonId(),
                m.getSimilarityScore() == null ? Double.valueOf(0d) : m.getSimilarityScore(),
                m.getPathSimilarity(), m.getPeakIntensitySimilarity(), m.getSeasonSimilarity(),
                m.getMoveSpeedSimilarity(), m.getWindCircleSimilarity(),
                ForecastDisagreeRecordDaoImpl.toJsonText(m.getMatchResult())
        };
    }
}
