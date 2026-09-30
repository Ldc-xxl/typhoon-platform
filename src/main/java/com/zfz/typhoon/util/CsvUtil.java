package com.zfz.typhoon.util;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.util.List;

/**
 * CSV 导出工具。
 *
 * <p>课程要求（阶段七 · ① 功能完善）：完成剩余功能模块，如"<b>数据导出 Excel</b>"。
 * 本平台实现为 CSV：Excel 可直接打开，且不引入 POI 等第三方库
 * （课程要求"禁用框架 / 纯手写"）。
 *
 * <p>两个容易被忽略但必须处理的点：
 * <ol>
 *   <li><b>UTF-8 BOM</b>：不加 BOM 时 Excel 会按 GBK 解析，中文表头变乱码，
 *       故在输出首部写入 {@code EF BB BF}；</li>
 *   <li><b>字段转义</b>：内容含逗号、双引号、换行时按 RFC 4180 用双引号包裹，
 *       内部双引号翻倍，否则列会错位。</li>
 * </ol>
 *
 * @author 追风者项目组
 */
public final class CsvUtil {

    /** UTF-8 BOM，保证 Excel 正确识别中文 */
    private static final String UTF8_BOM = "\uFEFF";

    private CsvUtil() {
        // 工具类禁止实例化
    }

    /**
     * 生成 CSV 文本。
     *
     * @param header 表头列名
     * @param rows   数据行，每行的列数应与表头一致
     * @return CSV 文本（不含 BOM，含 BOM 的写出见 {@link #write}）
     */
    public static String build(List<String> header, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        if (header != null && !header.isEmpty()) {
            for (int i = 0; i < header.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(escape(header.get(i)));
            }
            sb.append("\r\n");
        }
        if (rows != null) {
            for (List<String> row : rows) {
                if (row == null) {
                    continue;
                }
                for (int i = 0; i < row.size(); i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    sb.append(escape(row.get(i)));
                }
                sb.append("\r\n");
            }
        }
        return sb.toString();
    }

    /**
     * 按 RFC 4180 转义单个字段。
     *
     * @param value 原始值
     * @return 转义后的字段
     */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needQuote = value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        if (!needQuote) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    /**
     * 以附件形式把 CSV 写入响应。
     *
     * @param resp      HTTP 响应
     * @param fileName  建议文件名（不含路径），如 {@code 2613-tracks-20260929.csv}
     * @param csv       CSV 文本
     */
    public static void write(HttpServletResponse resp, String fileName, String csv) {
        resp.setContentType("text/csv;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        try {
            String encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
            resp.setHeader("Content-Disposition",
                    "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encoded);
            PrintWriter writer = resp.getWriter();
            writer.write(UTF8_BOM);
            writer.write(csv);
            writer.flush();
        } catch (IOException e) {
            throw new BusinessException(Result.CODE_SERVER_ERROR, "CSV 导出失败：" + e.getMessage(), e);
        }
    }

    /**
     * 生成导出文件名：{@code <编号>-<含义>-<日期>.csv}。
     *
     * @param code   台风国际编号，如 {@code 202615}
     * @param suffix 含义后缀，如 {@code tracks}
     * @return 文件名
     */
    public static String fileName(String code, String suffix) {
        String date = DateUtil.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String safeCode = code == null || code.trim().isEmpty() ? "typhoon" : code.trim();
        return safeCode + "-" + suffix + "-" + date + ".csv";
    }
}
