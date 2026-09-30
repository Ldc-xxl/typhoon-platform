package com.zfz.typhoon.util;

import com.zfz.typhoon.util.Result;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Excel（.xlsx）导出工具 —— 手写 OOXML，零第三方依赖。
 *
 * <p>课程要求阶段七①「数据导出 Excel」，同时要求「禁用框架 / 纯手写」。
 * 因此这里不引入 Apache POI，而是直接拼出符合 ECMA-376 规范的
 * SpreadsheetML 包（一个 ZIP，内含 workbook / worksheet / 关系等部件），
 * 生成的文件可被 Excel / WPS / Numbers 正常打开。
 *
 * <p>设计取舍：
 * <ul>
 *   <li>单元格采用 <b>inline string</b>（内联字符串）而非 sharedStrings，
 *       省去一张共享字符串表，文件更短、逻辑更简单；</li>
 *   <li>形如 {@code -12.5} / {@code 1308} 的纯数字内容按 <b>数值</b> 写入，
 *       保留列的数值类型，下游可直接做图表与求和；</li>
 *   <li>中文无需 BOM（xlsx 是二进制 ZIP，编码由各部件 XML 的 {@code encoding} 声明决定）。</li>
 * </ul>
 *
 * @author 追风者项目组
 * @see CsvUtil
 */
public final class XlsxUtil {

    /** 数值判定：可选负号 + 整数或小数，不含千分位/科学计数法 */
    private static final Pattern NUMBER = Pattern.compile("^-?\\d+(\\.\\d+)?$");

    private XlsxUtil() {
        // 工具类禁止实例化
    }

    /* ====================================================================== */
    /*  部件模板（OOXML 最小可用集）                                          */
    /* ====================================================================== */

    private static final String CONTENT_TYPES =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
            + "</Types>";

    private static final String ROOT_RELS =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
            + "</Relationships>";

    private static final String WORKBOOK =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
            + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
            + "<sheets><sheet name=\"轨迹数据\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
            + "</workbook>";

    private static final String WORKBOOK_RELS =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
            + "</Relationships>";

    /* ====================================================================== */
    /*  对外方法                                                              */
    /* ====================================================================== */

    /**
     * 生成导出文件名：{@code <编号>-<含义>-<日期>.xlsx}。
     *
     * @param code   台风国际编号，如 {@code 202615}
     * @param suffix 含义后缀，如 {@code tracks}
     * @return 文件名
     */
    public static String fileName(String code, String suffix) {
        String date = DateUtil.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String safeCode = code == null || code.trim().isEmpty() ? "typhoon" : code.trim();
        return safeCode + "-" + suffix + "-" + date + ".xlsx";
    }

    /**
     * 把表头 + 数据行组装成一个合法的 .xlsx 字节数组。
     *
     * @param header 表头列名（可为空）
     * @param rows   数据行（每行列数可与表头不同，按实际生成单元格）
     * @return xlsx 文件的二进制内容
     */
    public static byte[] build(List<String> header, List<List<String>> rows) {
        StringBuilder sheet = new StringBuilder();
        sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sheet.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        sheet.append("<sheetData>");

        int r = 0;
        if (header != null && !header.isEmpty()) {
            r++;
            sheet.append("<row r=\"").append(r).append("\">");
            for (int c = 0; c < header.size(); c++) {
                sheet.append(cellXml(r, c, header.get(c)));
            }
            sheet.append("</row>");
        }
        if (rows != null) {
            for (List<String> row : rows) {
                if (row == null) {
                    continue;
                }
                r++;
                sheet.append("<row r=\"").append(r).append("\">");
                for (int c = 0; c < row.size(); c++) {
                    sheet.append(cellXml(r, c, row.get(c)));
                }
                sheet.append("</row>");
            }
        }
        sheet.append("</sheetData></worksheet>");

        return zip(sheet.toString());
    }

    /**
     * 以附件形式把 xlsx 写入响应。
     *
     * @param resp      HTTP 响应
     * @param fileName  建议文件名（不含路径），如 {@code 202615-tracks-20260930.xlsx}
     * @param content   xlsx 字节内容
     */
    public static void write(HttpServletResponse resp, String fileName, byte[] content) {
        resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        try {
            String encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
            resp.setHeader("Content-Disposition",
                    "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encoded);
            ServletOutputStream out = resp.getOutputStream();
            out.write(content);
            out.flush();
        } catch (IOException e) {
            throw new BusinessException(Result.CODE_SERVER_ERROR, "Excel 导出失败：" + e.getMessage(), e);
        }
    }

    /* ====================================================================== */
    /*  内部：单元格 / XML 转义 / 列名 / 打包                                  */
    /* ====================================================================== */

    /**
     * 生成单个单元格 XML。
     *
     * @param rowIdx 行号（从 1 开始）
     * @param colIdx 列号（从 0 开始，内部转成 A/B/.../AA）
     * @param value  原始值
     * @return 单元格 XML 片段
     */
    private static String cellXml(int rowIdx, int colIdx, String value) {
        String ref = colRef(colIdx) + rowIdx;
        if (value != null && NUMBER.matcher(value).matches()) {
            return "<c r=\"" + ref + "\"><v>" + value + "</v></c>";
        }
        return "<c r=\"" + ref + "\" t=\"inlineStr\"><is><t xml:space=\"preserve\">"
                + xmlEscape(value == null ? "" : value) + "</t></is></c>";
    }

    /**
     * 0 基列号转 Excel 列名（A, B, ..., Z, AA, AB ...）。
     *
     * @param index 0 基列号
     * @return 列名
     */
    private static String colRef(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index + 1;
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }

    /**
     * XML 文本转义（单元格内联字符串内容只需转义 & < >，控制字符降级为空格）。
     *
     * @param s 原始文本
     * @return 转义后的文本
     */
    private static String xmlEscape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&') {
                sb.append("&amp;");
            } else if (c == '<') {
                sb.append("&lt;");
            } else if (c == '>') {
                sb.append("&gt;");
            } else if (c < 0x20) {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 把 worksheet XML 连同必需的关系部件打包成 xlsx（ZIP）字节数组。
     *
     * @param sheetXml worksheet 部件内容
     * @return xlsx 字节数组
     */
    private static byte[] zip(String sheetXml) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            addEntry(zos, "[Content_Types].xml", CONTENT_TYPES);
            addEntry(zos, "_rels/.rels", ROOT_RELS);
            addEntry(zos, "xl/workbook.xml", WORKBOOK);
            addEntry(zos, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS);
            addEntry(zos, "xl/worksheets/sheet1.xml", sheetXml);
        } catch (IOException e) {
            throw new BusinessException(Result.CODE_SERVER_ERROR, "生成 xlsx 失败：" + e.getMessage(), e);
        }
        return bos.toByteArray();
    }

    /**
     * 向 ZIP 写入一个文本部件（UTF-8）。
     *
     * @param zos     ZIP 输出流
     * @param name    部件路径
     * @param content 文本
     * @throws IOException 写出失败
     */
    private static void addEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        byte[] bytes = content.getBytes("UTF-8");
        zos.write(bytes, 0, bytes.length);
        zos.closeEntry();
    }
}
