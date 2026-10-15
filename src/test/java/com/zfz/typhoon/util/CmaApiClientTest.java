package com.zfz.typhoon.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link CmaApiClient} 单元测试。
 *
 * <p>这个客户端把"外部接口的形状"翻译成"库里的形状"，中间全是可判定的纯函数：
 * JSONP 怎么剥、编号怎么归位、风速怎么变风级、方位名怎么变角度、UTC 怎么变北京时间。
 * 这几步任何一步错了，页面上的数字都会静默地偏 —— 不会报错，只是不对。
 * 所以这里对每一步分别立边界断言，尤其卡住<b>档位边界值</b>
 * （例如 17.2 m/s 到底是 8 级还是 9 级）。
 *
 * <p><b>为什么不测 HTTP 本身</b>：那需要真的连 typhoon.nmc.cn，
 * 会让单元测试依赖外网可用性。网络部分留给部署后的端到端验证。
 */
@DisplayName("CmaApiClient · 中央气象台接口解析")
class CmaApiClientTest {

    @Nested
    @DisplayName("JSONP 剥离")
    class UnwrapJsonp {

        @Test
        @DisplayName("列表接口的双层括号能剥干净")
        void stripsDoubleParens() {
            String raw = "typhoon_jsons_list_default(({\"typhoonList\":[[3346033,\"KOGUMA\"]]}))";
            assertEquals("{\"typhoonList\":[[3346033,\"KOGUMA\"]]}", CmaApiClient.unwrapJsonp(raw));
        }

        @Test
        @DisplayName("详情接口的单层括号能剥干净")
        void stripsSingleParens() {
            String raw = "typhoon_jsons_view_3346033({\"typhoon\":[3346033]})";
            assertEquals("{\"typhoon\":[3346033]}", CmaApiClient.unwrapJsonp(raw));
        }

        @Test
        @DisplayName("响应里没有 JSON 主体时抛采集异常")
        void rejectsNonJson() {
            assertThrows(CmaApiClient.CmaApiException.class,
                    () -> CmaApiClient.unwrapJsonp("<html><body>404 Not Found</body></html>"));
        }

        @Test
        @DisplayName("空响应抛采集异常")
        void rejectsNull() {
            assertThrows(CmaApiClient.CmaApiException.class,
                    () -> CmaApiClient.unwrapJsonp(null));
        }
    }

    @Nested
    @DisplayName("编号归一化")
    class NormalizeIntlId {

        @Test
        @DisplayName("四位编号补上两位世纪前缀")
        void padsFourDigit() {
            assertEquals("202629", CmaApiClient.normalizeIntlId("2629"));
            assertEquals("202601", CmaApiClient.normalizeIntlId("2601"));
        }

        @Test
        @DisplayName("八位热带低压编号原样保留")
        void keepsEightDigit() {
            // 截断成四位会与同序号的正规台风撞号，因此必须原样带下去
            assertEquals("20260027", CmaApiClient.normalizeIntlId("20260027"));
        }

        @Test
        @DisplayName("已是六位的编号不被二次加前缀")
        void keepsSixDigit() {
            assertEquals("202411", CmaApiClient.normalizeIntlId("202411"));
        }

        @Test
        @DisplayName("首尾空白被去掉")
        void trims() {
            assertEquals("202629", CmaApiClient.normalizeIntlId(" 2629 "));
        }

        @Test
        @DisplayName("null 原样返回")
        void handlesNull() {
            assertNull(CmaApiClient.normalizeIntlId(null));
        }
    }

    @Nested
    @DisplayName("风速换算蒲福风级")
    class Beaufort {

        @Test
        @DisplayName("每个档位的下界都落在本级")
        void lowerBoundsMapToTheirLevel() {
            // 下界值本身属于本级，这是最容易写错成上一级的地方
            assertEquals(8, CmaApiClient.beaufort(18));   // 17.2 起 8 级
            assertEquals(9, CmaApiClient.beaufort(21));   // 20.8 起 9 级
            assertEquals(10, CmaApiClient.beaufort(25));  // 24.5 起 10 级
            assertEquals(11, CmaApiClient.beaufort(29));  // 28.5 起 11 级
            assertEquals(12, CmaApiClient.beaufort(33));  // 32.7 起 12 级
            assertEquals(13, CmaApiClient.beaufort(37));  // 37.0 起 13 级
            assertEquals(14, CmaApiClient.beaufort(42));  // 41.5 起 14 级
            assertEquals(15, CmaApiClient.beaufort(47));  // 46.2 起 15 级
            assertEquals(16, CmaApiClient.beaufort(51));  // 51.0 起 16 级
            assertEquals(17, CmaApiClient.beaufort(57));  // 56.1 起 17 级
            assertEquals(18, CmaApiClient.beaufort(62));  // 61.3 起 18 级
        }

        @Test
        @DisplayName("某一级上界处的风速仍归该级")
        void justBelowLowerBound() {
            // 这几个值是某一级的风速上界，必须仍归该级 —— 最容易误判成上一级
            assertEquals(8, CmaApiClient.beaufort(17));   // 未达 17.2，按 8 级下限处理
            assertEquals(8, CmaApiClient.beaufort(20));   // 20.7 是 8 级的上界
            assertEquals(10, CmaApiClient.beaufort(28));  // 28.4 是 10 级的上界
            assertEquals(16, CmaApiClient.beaufort(56));  // 56.0 是 16 级的上界
        }

        @Test
        @DisplayName("低于 8 级按 8 级处理")
        void floorsAtEight() {
            // 本平台只展示热带风暴及以上，更弱的热带低压不在业务范围内
            assertEquals(8, CmaApiClient.beaufort(0));
            assertEquals(8, CmaApiClient.beaufort(10));
        }

        @Test
        @DisplayName("超过 18 级的上限封顶在 18")
        void capsAtEighteen() {
            assertEquals(18, CmaApiClient.beaufort(80));
        }
    }

    @Nested
    @DisplayName("方位名转方位角")
    class Compass {

        @Test
        @DisplayName("四个主方位")
        void cardinalPoints() {
            assertEquals(Double.valueOf(0D), CmaApiClient.compassToDegree("N"));
            assertEquals(Double.valueOf(90D), CmaApiClient.compassToDegree("E"));
            assertEquals(Double.valueOf(180D), CmaApiClient.compassToDegree("S"));
            assertEquals(Double.valueOf(270D), CmaApiClient.compassToDegree("W"));
        }

        @Test
        @DisplayName("十六方位的每格是 22.5 度")
        void intermediatePoints() {
            assertEquals(Double.valueOf(22.5D), CmaApiClient.compassToDegree("NNE"));
            assertEquals(Double.valueOf(202.5D), CmaApiClient.compassToDegree("SSW"));
            assertEquals(Double.valueOf(337.5D), CmaApiClient.compassToDegree("NNW"));
        }

        @Test
        @DisplayName("大小写不敏感")
        void caseInsensitive() {
            assertEquals(Double.valueOf(337.5D), CmaApiClient.compassToDegree("nnw"));
        }

        @Test
        @DisplayName("无法识别的方位返回 null 而不是硬猜一个角度")
        void unknownReturnsNull() {
            assertNull(CmaApiClient.compassToDegree("XYZ"));
            assertNull(CmaApiClient.compassToDegree(null));
        }
    }

    @Nested
    @DisplayName("接口时间转北京时间")
    class ApiTime {

        @Test
        @DisplayName("接口给的是 UTC，入库前统一加 8 小时")
        void addsEightHours() {
            assertEquals(LocalDateTime.of(2026, 10, 13, 17, 0),
                    CmaApiClient.parseApiTime("202610130900"));
            assertEquals(LocalDateTime.of(2026, 10, 5, 8, 0),
                    CmaApiClient.parseApiTime("202610050000"));
        }

        @Test
        @DisplayName("跨日边界不会算错")
        void crossesDayBoundary() {
            // 16:00 UTC 应当是次日 00:00（北京）
            assertEquals(LocalDateTime.of(2026, 10, 6, 0, 0),
                    CmaApiClient.parseApiTime("202610051600"));
        }

        @Test
        @DisplayName("长度不足或非数字返回 null")
        void rejectsMalformed() {
            assertNull(CmaApiClient.parseApiTime(null));
            assertNull(CmaApiClient.parseApiTime("2026"));
            assertNull(CmaApiClient.parseApiTime("abcd050000"));
        }
    }
}
