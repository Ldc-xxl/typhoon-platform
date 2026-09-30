package com.zfz.typhoon.service;

import com.zfz.typhoon.model.vo.ExportVO;

import java.util.List;

/**
 * 数据导出业务接口。
 *
 * <p>对应接口文档：{@code GET /api/export/tracks}（返回下载地址）、
 * {@code GET /api/export/history}（导出记录）。
 *
 * <h3>为什么导出是"两段式"而不是直接回文件流</h3>
 * 前端 {@code api.js} 的契约是 {@code exportTracks() → { url }}：
 * 它需要一个可点的下载地址，且页面要提示"已导出 N 条记录"。
 * 如果接口直接回 CSV 字节流，前端就拿不到这两个信息。
 * 因此本服务把流程拆成：
 * <ol>
 *   <li><b>准备</b>：{@link #prepareTracks} 统计条数、写导出记录、返回可下载 URL；</li>
 *   <li><b>下载</b>：{@link #buildTracksCsv} 现场生成 CSV 内容。</li>
 * </ol>
 * 好处是服务端<b>不落任何临时文件</b>——不需要清理任务，也不会有
 * "导出目录被写满"或"用户拿到过期的旧文件"的问题。
 * 代价是 CSV 会生成两次（一次算大小、一次真下载），
 * 对这个数据量（单台风几十到几百个轨迹点）完全可以接受。
 *
 * <h3>为什么要写导出记录</h3>
 * 轨迹数据虽非敏感，但批量导出是典型的"数据带走"行为。
 * 留下 {@code export_records} 记录，既满足课程要求的审计能力，
 * 也能在后期限流（例如每人每天 20 次）时有依据。
 *
 * @author 追风者项目组
 */
public interface ExportService {

    /** 导出类型：CSV（与 {@code export_records.export_type} 的取值域一致） */
    String TYPE_CSV = "CSV";

    /** 导出类型：XLSX（手写 OOXML 生成，零依赖） */
    String TYPE_XLSX = "XLSX";

    /** 输出格式：CSV */
    String FORMAT_CSV = "csv";

    /** 输出格式：XLSX */
    String FORMAT_XLSX = "xlsx";

    /**
     * 准备一次轨迹导出：统计条数、登记导出记录、给出下载地址。
     *
     * @param typhoonId   台风ID
     * @param userId      操作用户ID；为 null 表示匿名（不写记录）
     * @param contextPath 应用上下文（由 Servlet 传入 {@code req.getContextPath()}），
     *                    用于拼出在任意部署路径下都可用的下载地址
     * @param format      输出格式：{@code csv} 或 {@code xlsx}
     * @return 导出视图（含 url / fileName / rows / size）
     */
    ExportVO prepareTracks(long typhoonId, Long userId, String contextPath, String format);

    /**
     * 生成轨迹 CSV 文本。
     *
     * @param typhoonId 台风ID
     * @return CSV 内容
     */
    String buildTracksCsv(long typhoonId);

    /**
     * 生成轨迹 Excel（.xlsx）二进制内容（手写 OOXML，不含 POI 等第三方库）。
     *
     * @param typhoonId 台风ID
     * @return xlsx 文件的字节数组
     */
    byte[] buildTracksXlsx(long typhoonId);

    /**
     * 查询某用户的导出历史。
     *
     * @param userId 用户ID
     * @param limit  条数上限
     * @return 导出记录视图列表
     */
    List<ExportVO> history(long userId, int limit);

    /**
     * 删除导出记录。
     *
     * @param exportId 导出记录ID
     * @param userId   用户ID（防止删除他人记录）
     * @return 受影响行数
     */
    int deleteRecord(long exportId, long userId);

    /**
     * 统计今日导出次数（供限流使用）。
     *
     * @param userId 用户ID
     * @return 次数
     */
    int countToday(long userId);
}
