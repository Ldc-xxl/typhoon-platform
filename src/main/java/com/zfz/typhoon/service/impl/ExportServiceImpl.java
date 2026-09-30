package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.ExportRecordDao;
import com.zfz.typhoon.dao.TyphoonDao;
import com.zfz.typhoon.dao.impl.ExportRecordDaoImpl;
import com.zfz.typhoon.dao.impl.TyphoonDaoImpl;
import com.zfz.typhoon.model.ExportRecord;
import com.zfz.typhoon.model.Typhoon;
import com.zfz.typhoon.model.TyphoonTrack;
import com.zfz.typhoon.model.vo.ExportVO;
import com.zfz.typhoon.service.ExportService;
import com.zfz.typhoon.service.TrackService;
import com.zfz.typhoon.service.TyphoonService;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.CsvUtil;
import com.zfz.typhoon.util.DateUtil;
import com.zfz.typhoon.util.XlsxUtil;
import com.zfz.typhoon.util.GeoUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 数据导出业务实现。
 *
 * <h3>CSV 的两处细节</h3>
 * <ol>
 *   <li><b>BOM</b>：CSV 里含中文表头，Excel 打开无 BOM 的 UTF-8 文件会乱码。
 *       本实现把 BOM 交由 {@code CsvUtil} 处理（它负责写响应头时的编码约定），
 *       因此这里只产出纯文本，不在字符串里硬塞 {@code \\uFEFF}——
 *       否则用文本编辑器打开会看到多余字符。</li>
 *   <li><b>数值精度</b>：经纬度保留 4 位小数（约 11 米），气压风速取整。
 *       多写几位在小数上没有意义，反而让文件变大、肉眼难读。</li>
 * </ol>
 *
 * @author 追风者项目组
 * @see ExportService
 */
public class ExportServiceImpl implements ExportService {

    /** 经纬度保留小数位 */
    private static final int COORD_SCALE = 4;

    /** 单文件最大行数，防止超大台风撑爆响应（轨迹点极稀疏时也不会超） */
    private static final int MAX_ROWS = 20000;

    private final ExportRecordDao exportDao;
    private final TyphoonDao typhoonDao;
    private final TyphoonService typhoonService;
    private final TrackService trackService;

    /**
     * 生产用构造：按默认实现装配依赖。
     * <p>之所以把它拆成"无参 + 带参"两个构造，是为了给单元测试留出注入替身的缝隙：
     * 依赖全部在字段初始化里 new 出来的话，测试就必须连上真实 MySQL 才能跑，
     * 单元测试也就退化成了集成测试。
     */
    public ExportServiceImpl() {
        this(new ExportRecordDaoImpl(), new TyphoonDaoImpl(), new TyphoonServiceImpl(), new TrackServiceImpl());
    }

    /**
     * 测试用构造：注入替身依赖（Mockito 生成的 mock 或手写桩）。
     * <p>参数顺序与上方字段声明一致，均为本服务运行所需的协作者。
     */
    public ExportServiceImpl(ExportRecordDao exportDao, TyphoonDao typhoonDao, TyphoonService typhoonService, TrackService trackService) {
        this.exportDao = exportDao;
        this.typhoonDao = typhoonDao;
        this.typhoonService = typhoonService;
        this.trackService = trackService;
    }

    @Override
    public ExportVO prepareTracks(long typhoonId, Long userId, String contextPath, String format) {
        Typhoon typhoon = typhoonService.requireTyphoon(typhoonId);
        List<TyphoonTrack> tracks = trackService.listEntities(typhoonId);
        if (tracks.isEmpty()) {
            throw BusinessException.notFound("该台风暂无可导出的实况轨迹数据");
        }

        boolean xlsx = ExportService.FORMAT_XLSX.equalsIgnoreCase(format);
        String suffix = xlsx ? ExportService.FORMAT_XLSX : ExportService.FORMAT_CSV;
        String type = xlsx ? ExportRecord.TYPE_XLSX : ExportRecord.TYPE_CSV;
        String code = typhoon.getInternationalId() == null ? ("T" + typhoonId) : typhoon.getInternationalId();
        String fileName = xlsx
                ? XlsxUtil.fileName(code, ExportService.FORMAT_XLSX)
                : CsvUtil.fileName(code, ExportService.FORMAT_CSV);
        String path = (contextPath == null ? "" : contextPath)
                + "/api/export/tracks?typhoonId=" + typhoonId
                + "&format=" + suffix + "&download=1";

        ExportVO vo = new ExportVO();
        vo.setUrl(path);
        vo.setFileName(fileName);
        vo.setFormat(suffix);
        vo.setRows(Integer.valueOf(Math.min(tracks.size(), MAX_ROWS)));
        vo.setTyphoonId(Long.valueOf(typhoonId));
        vo.setTyphoonCode(code);
        vo.setSize(Integer.valueOf(estimateSize(tracks.size())));

        if (userId != null) {
            ExportRecord record = new ExportRecord();
            record.setUserId(userId);
            record.setTyphoonId(Long.valueOf(typhoonId));
            record.setExportType(type);
            record.setFileUrl(path);
            record.setFileSize(vo.getSize());
            record.setCreatedAt(DateUtil.now());
            exportDao.insert(record);
        }
        return vo;
    }

    @Override
    public String buildTracksCsv(long typhoonId) {
        Typhoon typhoon = typhoonService.requireTyphoon(typhoonId);
        List<List<String>> rows = trackDataRows(typhoonId);

        // 表头之外再加两行元信息，让导出文件脱离平台后依然能被读懂是谁的轨迹
        StringBuilder meta = new StringBuilder();
        meta.append("# 台风编号：").append(safe(typhoon.getInternationalId())).append('\n');
        meta.append("# 中文名：").append(safe(typhoon.getChineseName())).append('\n');
        meta.append("# 英文名：").append(safe(typhoon.getEnglishName())).append('\n');
        meta.append("# 生成时间：").append(DateUtil.format(typhoon.getGenerateTime())).append('\n');
        meta.append("# 导出时间：").append(DateUtil.format(DateUtil.now())).append('\n');
        meta.append("# 轨迹点数量：").append(rows.size()).append('\n');
        meta.append("# 数据来源：中央气象台等机构公开发布的台风实况资料\n");

        return meta + CsvUtil.build(trackHeader(), rows);
    }

    @Override
    public byte[] buildTracksXlsx(long typhoonId) {
        Typhoon typhoon = typhoonService.requireTyphoon(typhoonId);
        List<List<String>> rows = trackDataRows(typhoonId);

        // 元信息作为首列若干行，数据行随后，整体交给手写 OOXML 生成真实 .xlsx
        List<List<String>> all = new ArrayList<List<String>>(rows.size() + 8);
        all.add(Arrays.asList("# 台风编号：" + safe(typhoon.getInternationalId())));
        all.add(Arrays.asList("# 中文名：" + safe(typhoon.getChineseName())));
        all.add(Arrays.asList("# 英文名：" + safe(typhoon.getEnglishName())));
        all.add(Arrays.asList("# 生成时间：" + DateUtil.format(typhoon.getGenerateTime())));
        all.add(Arrays.asList("# 导出时间：" + DateUtil.format(DateUtil.now())));
        all.add(Arrays.asList("# 轨迹点数量：" + rows.size()));
        all.add(Arrays.asList("# 数据来源：中央气象台等机构公开发布的台风实况资料"));
        all.add(Arrays.asList(""));
        all.addAll(rows);

        return XlsxUtil.build(trackHeader(), all);
    }

    /**
     * 轨迹导出表头（CSV 与 Excel 共用）。
     *
     * @return 表头列名
     */
    private List<String> trackHeader() {
        return Arrays.asList(
                "观测时间", "纬度(°N)", "经度(°E)", "风力等级", "风速(m/s)",
                "中心气压(hPa)", "移向(°, 气象方向)", "移速(km/h)");
    }

    /**
     * 取台风实况轨迹数据行（CSV 与 Excel 共用）。
     * 同时完成"台风存在 + 有轨迹数据"的校验，空数据统一抛 404。
     *
     * @param typhoonId 台风ID
     * @return 数据行（受 {@code MAX_ROWS} 截断）
     */
    private List<List<String>> trackDataRows(long typhoonId) {
        typhoonService.requireTyphoon(typhoonId);
        List<TyphoonTrack> tracks = trackService.listEntities(typhoonId);
        if (tracks.isEmpty()) {
            throw BusinessException.notFound("该台风暂无可导出的实况轨迹数据");
        }
        List<List<String>> rows = new ArrayList<List<String>>(Math.min(tracks.size(), MAX_ROWS));
        int n = 0;
        for (TyphoonTrack t : tracks) {
            if (n++ >= MAX_ROWS) {
                break;
            }
            rows.add(Arrays.asList(
                    DateUtil.format(t.getObsTime()),
                    coord(t.getLatitude()),
                    coord(t.getLongitude()),
                    integer(t.getWindForce()),
                    integer(t.getWindSpeed()),
                    integer(t.getPressure()),
                    oneDecimal(t.getMoveDirection()),
                    oneDecimal(t.getMoveSpeed())));
        }
        return rows;
    }

    @Override
    public List<ExportVO> history(long userId, int limit) {
        int n = limit <= 0 ? 20 : Math.min(limit, 100);
        List<ExportRecord> rows = exportDao.listByUser(userId, n);
        List<ExportVO> result = new ArrayList<ExportVO>(rows.size());
        for (ExportRecord r : rows) {
            ExportVO vo = new ExportVO();
            vo.setUrl(r.getFileUrl());

            // 台风编号要回查台风表：export_records 只存了 typhoon_id，并没有存编号。
            // 此前这里把 export_type（取值域是 "CSV"）当编号返回，
            // 导致导出历史里每一行的"台风编号"都显示成 CSV，与文件名也对不上。
            // 台风被删除时 findById 返回 null，此时退化为用 id 生成文件名，
            // 但绝不把 export_type 当作编号兜底——那样只会把错误数据藏起来。
            String code = null;
            if (r.getTyphoonId() != null) {
                Typhoon t = typhoonDao.findById(r.getTyphoonId().longValue());
                // 实体字段叫 internationalId（国际编号，如 202615），VO 层才映射为 code
                code = t == null ? null : t.getInternationalId();
            }
            String nameSeed = code != null ? code : String.valueOf(r.getTyphoonId());

            // 导出类型决定扩展名与格式标识（CSV / XLSX 复用同一套编号命名）
            boolean xlsx = ExportRecord.TYPE_XLSX.equals(r.getExportType());
            String suffix = xlsx ? ExportService.FORMAT_XLSX : ExportService.FORMAT_CSV;
            vo.setFileName(xlsx ? XlsxUtil.fileName(nameSeed, suffix) : CsvUtil.fileName(nameSeed, suffix));
            vo.setFormat(suffix);
            vo.setTyphoonId(r.getTyphoonId());
            vo.setSize(r.getFileSize());
            vo.setTyphoonCode(code);
            result.add(vo);
        }
        return result;
    }

    @Override
    public int deleteRecord(long exportId, long userId) {
        return exportDao.deleteByIdAndUser(exportId, userId);
    }

    @Override
    public int countToday(long userId) {
        return exportDao.countTodayByUser(userId);
    }

    /* ==================================================================== */
    /*  内部格式化                                                           */
    /* ==================================================================== */

    /**
     * 经纬度格式化。
     *
     * @param value 原值
     * @return 4 位小数字符串；null 返回空串
     */
    private String coord(Double value) {
        return value == null ? "" : String.valueOf(GeoUtil.round(value.doubleValue(), COORD_SCALE));
    }

    /**
     * 整数值格式化。
     *
     * @param value 原值
     * @return 字符串；null 返回空串
     */
    private String integer(Integer value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 一位小数格式化。
     *
     * @param value 原值
     * @return 字符串；null 返回空串
     */
    private String oneDecimal(Double value) {
        return value == null ? "" : String.valueOf(GeoUtil.round(value.doubleValue(), 1));
    }

    /**
     * 空文本兜底。
     *
     * @param text 原文本
     * @return 非空文本
     */
    private String safe(String text) {
        return text == null || text.trim().isEmpty() ? "—" : text.trim();
    }

    /**
     * 预估 CSV 大小（KB，对齐 {@code export_records.file_size} 的单位）。
     *
     * <p>每行约 90 字节（8 个字段 + 分隔符 + 换行），加上表头元信息按 300 字节计。
     * 只用于给前端一个"约 xx KB"的提示，不追求精确，因此向上取整避免出现 0 KB。
     *
     * @param rows 行数
     * @return 预估 KB 数
     */
    private int estimateSize(int rows) {
        long bytes = 300L + (long) rows * 90L;
        int kb = (int) ((bytes + 1023L) / 1024L);
        return Math.max(1, kb);
    }
}
