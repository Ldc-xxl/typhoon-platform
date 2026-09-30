package com.zfz.typhoon.dao;

import com.zfz.typhoon.model.ExportRecord;

import java.util.List;

/**
 * 导出记录表（{@code export_records}）数据访问接口。
 *
 * <p>课程要求（阶段六 ·「支持 CSV 导出 / 截图导出 / 沙盘快照导出，并留存导出历史」）。
 * 对应接口文档：{@code GET /api/exports}（我的导出历史）、
 * {@code POST /api/typhoons/{id}/export}（导出并留痕）。
 *
 * <p>本表是审计型表：<b>只增不改</b>，因此没有 update 方法。
 * 导出动作本身由 Service 完成（生成 CSV / 截图 URL），
 * 落库的是"谁、何时、导出了哪个台风的哪种格式、文件多大"。
 *
 * @author 追风者项目组
 * @see com.zfz.typhoon.model.vo.ExportVO
 */
public interface ExportRecordDao {

    /** 导出类型：CSV 数据表 */
    String TYPE_CSV = ExportRecord.TYPE_CSV;

    /** 导出类型：地图截图 */
    String TYPE_SCREENSHOT = ExportRecord.TYPE_SCREENSHOT;

    /** 导出类型：沙盘情景快照 */
    String TYPE_SANDBOX = ExportRecord.TYPE_SANDBOX;

    /**
     * 查询某用户的导出历史（按导出时间倒序）。
     *
     * @param userId 用户ID
     * @param limit  最多返回条数（&lt;=0 表示不限）
     * @return 导出记录列表
     */
    List<ExportRecord> listByUser(long userId, int limit);

    /**
     * 查询某台风被导出的历史（管理端审计）。
     *
     * @param typhoonId 台风ID
     * @param limit     最多返回条数
     * @return 导出记录列表
     */
    List<ExportRecord> listByTyphoon(long typhoonId, int limit);

    /**
     * 按类型查询某用户的导出历史。
     *
     * @param userId     用户ID
     * @param exportType CSV / SCREENSHOT / SANDBOX_SNAPSHOT
     * @return 导出记录列表
     */
    List<ExportRecord> listByUserAndType(long userId, String exportType);

    /**
     * 按主键查询。
     *
     * @param exportId 导出记录ID
     * @return 导出记录；不存在返回 null
     */
    ExportRecord findById(long exportId);

    /**
     * 统计某用户的导出次数（管理端用户画像）。
     *
     * @param userId 用户ID
     * @return 次数
     */
    int countByUser(long userId);

    /**
     * 统计某用户今日导出次数（用于限流：如每日上限 20 次）。
     *
     * <p>用范围条件而非 {@code DATE(created_at) = CURDATE()}，
     * 保证 {@code idx_export_user} 可用。
     *
     * @param userId 用户ID
     * @return 次数
     */
    int countTodayByUser(long userId);

    /**
     * 新增导出记录。
     *
     * @param record 导出记录
     * @return 新生成的主键
     */
    long insert(ExportRecord record);

    /**
     * 按主键删除（用户清理自己的导出历史）。
     *
     * @param exportId 导出记录ID
     * @param userId   当前用户ID（防止越权删除他人记录）
     * @return 受影响行数
     */
    int deleteByIdAndUser(long exportId, long userId);
}
