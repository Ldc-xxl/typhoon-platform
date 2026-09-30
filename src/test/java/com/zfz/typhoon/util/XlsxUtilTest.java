package com.zfz.typhoon.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link XlsxUtil} 单元测试。
 *
 * <p>本工具手写 OOXML（不依赖 Apache POI），风险比 CSV 高得多：
 * 任何一个部件名写错、关系 Target 指错，文件都会"生成成功但 Excel 打不开"。
 * 因此这里的首要目标是<b>验证产物确实是一个结构合法的 .xlsx 包</b>——
 * 能用 {@link ZipInputStream} 解出来、且包含 OOXML 规范要求的最小部件集合。
 */
@DisplayName("XlsxUtil · 手写 OOXML 导出")
class XlsxUtilTest {

    /** OOXML 最小可用包必须包含的 5 个部件。 */
    private static final java.util.Set<String> REQUIRED_PARTS = new java.util.HashSet<String>(Arrays.asList(
            "[Content_Types].xml",
            "_rels/.rels",
            "xl/workbook.xml",
            "xl/_rels/workbook.xml.rels",
            "xl/worksheets/sheet1.xml"));

    /** 解压 xlsx 字节，返回「部件名 -> 内容」映射。 */
    private static java.util.Map<String, String> unzip(byte[] xlsx) throws Exception {
        java.util.Map<String, String> parts = new java.util.LinkedHashMap<String, String>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = zis.read(buf)) != -1) {
                    bos.write(buf, 0, n);
                }
                parts.put(e.getName(), new String(bos.toByteArray(), StandardCharsets.UTF_8));
            }
        }
        return parts;
    }

    @Nested
    @DisplayName("build · 生成结构合法的 xlsx")
    class Build {

        @Test
        @DisplayName("产物含全部必需部件，且 workbook 引用了 sheet1")
        void hasRequiredParts() throws Exception {
            byte[] xlsx = XlsxUtil.build(
                    Arrays.asList("时间", "风速"),
                    Arrays.asList(Arrays.asList("2026-09-01 08:00", "38")));

            java.util.Map<String, String> parts = unzip(xlsx);
            for (String required : REQUIRED_PARTS) {
                assertTrue(parts.containsKey(required), "缺少必需部件：" + required);
            }
            // workbook.xml 必须声明 sheet 并通过 r:id 关联；真正的 Target 路径在 workbook.xml.rels 里
            String wb = parts.get("xl/workbook.xml");
            assertTrue(wb.contains("<sheet"), "workbook 应声明 sheet");
            assertTrue(wb.contains("r:id=\"rId1\""), "workbook 的 sheet 应通过 r:id 关联");
            String rels = parts.get("xl/_rels/workbook.xml.rels");
            assertTrue(rels.contains("worksheets/sheet1.xml"), "workbook.rels Target 应指向 sheet1.xml");
        }

        @Test
        @DisplayName("表头 + 数据行被正确写入 worksheet")
        void headerAndRowsWritten() throws Exception {
            byte[] xlsx = XlsxUtil.build(
                    Arrays.asList("编号", "风速"),
                    Arrays.asList(
                            Arrays.asList("202615", "38"),
                            Arrays.asList("202616", "42")));
            String sheet = unzip(xlsx).get("xl/worksheets/sheet1.xml");

            assertTrue(sheet.contains("编号"), "应含表头「编号」");
            assertTrue(sheet.contains("风速"), "应含表头「风速」");
            assertTrue(sheet.contains("202615"), "应含数据「202615」");
            // 两行数据 + 一行表头 = 3 个 <row>
            assertEquals(3, countOccurrences(sheet, "<row "), "应有 3 个 row");
        }

        @Test
        @DisplayName("纯数字内容写数值单元格 <v>，而非内联字符串")
        void numericCellIsValue() throws Exception {
            byte[] xlsx = XlsxUtil.build(
                    Arrays.asList("风速"),
                    Arrays.asList(Arrays.asList("38"), Arrays.asList("-12.5")));
            String sheet = unzip(xlsx).get("xl/worksheets/sheet1.xml");

            // 数值单元格精确断言：<c r="A2"><v>38</v></c>，自身不带 t="inlineStr"
            assertTrue(sheet.contains("<c r=\"A2\"><v>38</v></c>"), "整数应走数值单元格且无 inlineStr");
            assertTrue(sheet.contains("<c r=\"A3\"><v>-12.5</v></c>"), "负数小数应走数值单元格且无 inlineStr");
        }

        @Test
        @DisplayName("中文与特殊字符正确转义（& < >），且保留中文表头")
        void chineseAndSpecialCharsEscaped() throws Exception {
            byte[] xlsx = XlsxUtil.build(
                    Arrays.asList("说明"),
                    Arrays.asList(Arrays.asList("A & B < C > D")));
            String sheet = unzip(xlsx).get("xl/worksheets/sheet1.xml");

            assertTrue(sheet.contains("说明"), "中文表头应原样保留");
            assertTrue(sheet.contains("&amp;"), "应转义 &");
            assertTrue(sheet.contains("&lt;"), "应转义 <");
            assertTrue(sheet.contains("&gt;"), "应转义 >");
        }

        @Test
        @DisplayName("空输入（无表头无数据）仍生成可打开的空表")
        void emptyInputStillValid() throws Exception {
            byte[] xlsx = XlsxUtil.build(null, null);
            java.util.Map<String, String> parts = unzip(xlsx);
            for (String required : REQUIRED_PARTS) {
                assertTrue(parts.containsKey(required), "空表也须含：" + required);
            }
        }

        @Test
        @DisplayName("缺列号从 0 起按 A/B/.../AA 递增，列引用正确")
        void columnRefSequence() throws Exception {
            // 28 列，最后一列应为 AB（0基第27列）
            List<String> header = new java.util.ArrayList<String>();
            for (int i = 0; i < 28; i++) {
                header.add("c" + i);
            }
            byte[] xlsx = XlsxUtil.build(header, Collections.<List<String>>emptyList());
            String sheet = unzip(xlsx).get("xl/worksheets/sheet1.xml");

            assertTrue(sheet.contains(" r=\"A1\""), "首列应为 A");
            assertTrue(sheet.contains(" r=\"Z1\""), "第 26 列应为 Z");
            assertTrue(sheet.contains(" r=\"AA1\""), "第 27 列应为 AA");
            assertTrue(sheet.contains(" r=\"AB1\""), "第 28 列应为 AB");
        }
    }

    @Nested
    @DisplayName("fileName · 导出文件名")
    class FileName {

        @Test
        @DisplayName("形如 <编号>-<含义>-<yyyyMMdd>.xlsx")
        void normalCase() {
            String name = XlsxUtil.fileName("202615", "tracks");
            assertTrue(name.startsWith("202615-tracks-"), "实际：" + name);
            assertTrue(name.endsWith(".xlsx"), "实际：" + name);
            assertTrue(name.matches("202615-tracks-\\d{8}\\.xlsx"), "日期段应为 8 位数字，实际：" + name);
        }

        @Test
        @DisplayName("编号为空时回退为 typhoon")
        void blankCodeFallsBack() {
            assertTrue(XlsxUtil.fileName(null, "tracks").startsWith("typhoon-tracks-"));
            assertTrue(XlsxUtil.fileName("   ", "tracks").startsWith("typhoon-tracks-"));
        }
    }

    /** 统计子串出现次数（用于断言 row 数量）。 */
    private static int countOccurrences(String s, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = s.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
