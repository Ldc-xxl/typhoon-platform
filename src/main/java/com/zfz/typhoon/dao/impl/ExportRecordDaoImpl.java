package com.zfz.typhoon.dao.impl;

import com.zfz.typhoon.dao.ExportRecordDao;
import com.zfz.typhoon.model.ExportRecord;
import com.zfz.typhoon.util.DateUtil;
import com.zfz.typhoon.util.JdbcTemplate;
import com.zfz.typhoon.util.RowMapper;
import com.zfz.typhoon.util.RsUtil;
import com.zfz.typhoon.util.SqlBuilder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 导出记录表 DAO 实现。
 *
 * @author 追风者项目组
 */
public class ExportRecordDaoImpl implements ExportRecordDao {

    private static final String COLUMNS =
            "e.export_id, e.user_id, e.typhoon_id, e.export_type, e.file_url, "
                    + "e.file_size, e.created_at";

    private static final RowMapper<ExportRecord> MAPPER = new RowMapper<ExportRecord>() {
        @Override
        public ExportRecord map(ResultSet rs) throws SQLException {
            ExportRecord e = new ExportRecord();
            e.setExportId(RsUtil.getLong(rs, "export_id"));
            e.setUserId(RsUtil.getLong(rs, "user_id"));
            e.setTyphoonId(RsUtil.getLong(rs, "typhoon_id"));
            e.setExportType(RsUtil.getString(rs, "export_type"));
            e.setFileUrl(RsUtil.getString(rs, "file_url"));
            e.setFileSize(RsUtil.getInt(rs, "file_size"));
            e.setCreatedAt(RsUtil.getDateTime(rs, "created_at"));
            return e;
        }
    };

    private static final String INSERT_SQL =
            "INSERT INTO export_records (user_id, typhoon_id, export_type, file_url, file_size) "
                    + "VALUES (?, ?, ?, ?, ?)";

    @Override
    public List<ExportRecord> listByUser(long userId, int limit) {
        SqlBuilder sb = SqlBuilder.select(COLUMNS).from("export_records e")
                .eq("e.user_id", Long.valueOf(userId))
                .orderBy("e.created_at DESC, e.export_id DESC");
        if (limit > 0) {
            sb.limit(limit, 0);
        }
        SqlBuilder.Built built = sb.build();
        return JdbcTemplate.query(built.getSql(), MAPPER, built.getParams());
    }

    @Override
    public List<ExportRecord> listByTyphoon(long typhoonId, int limit) {
        // 命中 idx_export_typhoon
        SqlBuilder sb = SqlBuilder.select(COLUMNS).from("export_records e")
                .eq("e.typhoon_id", Long.valueOf(typhoonId))
                .orderBy("e.created_at DESC, e.export_id DESC");
        if (limit > 0) {
            sb.limit(limit, 0);
        }
        SqlBuilder.Built built = sb.build();
        return JdbcTemplate.query(built.getSql(), MAPPER, built.getParams());
    }

    @Override
    public List<ExportRecord> listByUserAndType(long userId, String exportType) {
        // 同时命中 idx_export_user 与 idx_export_type，优化器择优
        SqlBuilder sb = SqlBuilder.select(COLUMNS).from("export_records e")
                .eq("e.user_id", Long.valueOf(userId))
                .eq("e.export_type", exportType)
                .orderBy("e.created_at DESC, e.export_id DESC");
        SqlBuilder.Built built = sb.build();
        return JdbcTemplate.query(built.getSql(), MAPPER, built.getParams());
    }

    @Override
    public ExportRecord findById(long exportId) {
        String sql = "SELECT " + COLUMNS + " FROM export_records e WHERE e.export_id = ?";
        return JdbcTemplate.queryOne(sql, MAPPER, Long.valueOf(exportId));
    }

    @Override
    public int countByUser(long userId) {
        Integer n = JdbcTemplate.queryInt(
                "SELECT COUNT(*) FROM export_records WHERE user_id = ?", Long.valueOf(userId));
        return n == null ? 0 : n.intValue();
    }

    @Override
    public int countTodayByUser(long userId) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Integer n = JdbcTemplate.queryInt(
                "SELECT COUNT(*) FROM export_records WHERE user_id = ? AND created_at >= ?",
                Long.valueOf(userId), todayStart);
        return n == null ? 0 : n.intValue();
    }

    @Override
    public long insert(ExportRecord e) {
        Long id = JdbcTemplate.insert(INSERT_SQL,
                e.getUserId(), e.getTyphoonId(),
                e.getExportType() == null ? TYPE_CSV : e.getExportType(),
                e.getFileUrl(), e.getFileSize());
        return id == null ? 0L : id.longValue();
    }

    @Override
    public int deleteByIdAndUser(long exportId, long userId) {
        // 双条件防止越权删除他人导出记录
        return JdbcTemplate.update(
                "DELETE FROM export_records WHERE export_id = ? AND user_id = ?",
                Long.valueOf(exportId), Long.valueOf(userId));
    }

    /**
     * 生成"导出文件名的时间戳部分"（供 Service 组织文件名）。
     *
     * @return yyyyMMdd_HHmmss 形式的时间戳
     */
    public static String timestampForFileName() {
        return DateUtil.format(DateUtil.now()).replace("-", "").replace(":", "").replace(" ", "_");
    }
}
