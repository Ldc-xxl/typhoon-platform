package com.zfz.typhoon.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CsvUtil} 单元测试。
 *
 * <p>CSV 转义的错误表现很隐蔽：字段里带逗号或换行时若不加引号，
 * 导出的文件列会错位，但用 Excel 打开只表现为"数据串行"，不报错。
 * 因此这里重点覆盖 RFC 4180 的三种必须加引号的情形。
 */
@DisplayName("CsvUtil · CSV 生成与转义")
class CsvUtilTest {

    @org.junit.jupiter.api.Nested
    @DisplayName("escape · 单字段转义（RFC 4180）")
    class Escape {

        @Test
        @DisplayName("普通值原样返回")
        void plainValueUnchanged() {
            assertEquals("飞燕", CsvUtil.escape("飞燕"));
            assertEquals("202615", CsvUtil.escape("202615"));
        }

        @Test
        @DisplayName("null 转成空串，不输出字面量 null")
        void nullBecomesEmpty() {
            assertEquals("", CsvUtil.escape(null));
        }

        @Test
        @DisplayName("含逗号必须加引号，否则列会错位")
        void commaNeedsQuote() {
            assertEquals("\"厦门, 泉州\"", CsvUtil.escape("厦门, 泉州"));
        }

        @Test
        @DisplayName("含换行 / 回车必须加引号")
        void lineBreakNeedsQuote() {
            assertEquals("\"第一行\n第二行\"", CsvUtil.escape("第一行\n第二行"));
            assertEquals("\"第一行\r\n第二行\"", CsvUtil.escape("第一行\r\n第二行"));
        }

        @Test
        @DisplayName("含引号必须加引号并把内部引号翻倍")
        void quoteIsDoubled() {
            assertEquals("\"say \"\"hi\"\"\"", CsvUtil.escape("say \"hi\""));
        }

        @Test
        @DisplayName("空串保持空串（不需要引号）")
        void emptyStaysEmpty() {
            assertEquals("", CsvUtil.escape(""));
        }
    }

    @org.junit.jupiter.api.Nested
    @DisplayName("build · 整表生成")
    class Build {

        @Test
        @DisplayName("表头 + 数据行，每行以 CRLF 结尾")
        void headerAndRows() {
            String csv = CsvUtil.build(
                    Arrays.asList("时间", "风速"),
                    Arrays.asList(
                            Arrays.asList("2026-09-01 08:00:00", "38"),
                            Arrays.asList("2026-09-01 14:00:00", "42")));
            assertEquals("时间,风速\r\n2026-09-01 08:00:00,38\r\n2026-09-01 14:00:00,42\r\n", csv);
        }

        @Test
        @DisplayName("表头为 null 或空时不输出表头行")
        void noHeaderWhenAbsent() {
            assertEquals("1,2\r\n", CsvUtil.build(null, Collections.singletonList(Arrays.asList("1", "2"))));
            assertEquals("1,2\r\n",
                    CsvUtil.build(Collections.<String>emptyList(), Collections.singletonList(Arrays.asList("1", "2"))));
        }

        @Test
        @DisplayName("数据行为 null 时跳过，不影响其它行")
        void nullRowIsSkipped() {
            List<List<String>> rows = Arrays.asList(Arrays.asList("a"), null, Arrays.asList("b"));
            assertEquals("a\r\nb\r\n", CsvUtil.build(null, rows));
        }

        @Test
        @DisplayName("数据行内字段同样走转义")
        void rowsAreEscaped() {
            String csv = CsvUtil.build(Collections.singletonList("c"),
                    Collections.singletonList(Collections.singletonList("a,b")));
            assertEquals("c\r\n\"a,b\"\r\n", csv);
        }

        @Test
        @DisplayName("rows 为 null 时只输出表头")
        void nullRowsOnlyHeader() {
            assertEquals("c\r\n", CsvUtil.build(Collections.singletonList("c"), null));
        }

        @Test
        @DisplayName("全空输入返回空串")
        void allEmpty() {
            assertEquals("", CsvUtil.build(null, null));
        }

        @Test
        @DisplayName("不含 BOM（BOM 只在 write 时加，否则拼接会多出不可见字符）")
        void noBom() {
            String csv = CsvUtil.build(Collections.singletonList("c"), null);
            assertTrue(csv.charAt(0) == 'c', "首字符应是 c，而不是 BOM");
        }
    }

    @org.junit.jupiter.api.Nested
    @DisplayName("fileName · 导出文件名")
    class FileName {

        @Test
        @DisplayName("形如 <编号>-<含义>-<yyyyMMdd>.csv")
        void normalCase() {
            String name = CsvUtil.fileName("202615", "tracks");
            assertTrue(name.startsWith("202615-tracks-"), "实际：" + name);
            assertTrue(name.endsWith(".csv"), "实际：" + name);
            // 日期段应为 8 位数字
            assertTrue(name.matches("202615-tracks-\\d{8}\\.csv"), "实际：" + name);
        }

        @Test
        @DisplayName("编号为空时回退为 typhoon，避免生成「-tracks-….csv」")
        void blankCodeFallsBack() {
            assertTrue(CsvUtil.fileName(null, "tracks").startsWith("typhoon-tracks-"));
            assertTrue(CsvUtil.fileName("   ", "tracks").startsWith("typhoon-tracks-"));
        }
    }
}
