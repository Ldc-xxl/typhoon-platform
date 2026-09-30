package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.FeedbackReportDao;
import com.zfz.typhoon.model.FeedbackReport;
import com.zfz.typhoon.util.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FeedbackServiceImpl} 单元测试。
 *
 * <p>反馈是"管理端闭环"的入口，垃圾数据会直接污染后台待办，因此本类重点验证：
 * <ol>
 *   <li><b>提交校验</b>——类型白名单 + 内容长度上下限；</li>
 *   <li><b>联系方式脱敏</b>——手机号 / 邮箱不落明文；</li>
 *   <li><b>幂等处理</b>——重复处理 / 重复打回返回 0，不算错误。</li>
 * </ol>
 *
 * @author 追风者项目组
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FeedbackServiceImpl · 用户反馈")
class FeedbackServiceImplTest {

    @Mock
    private FeedbackReportDao feedbackDao;

    private FeedbackServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FeedbackServiceImpl(feedbackDao);
    }

    /* ------------------------------------------------------------------ */

    private FeedbackReport report(String type, String content) {
        FeedbackReport f = new FeedbackReport();
        f.setFeedbackType(type);
        f.setContent(content);
        return f;
    }

    private FeedbackReport existing(String status) {
        FeedbackReport f = new FeedbackReport();
        f.setFeedbackId(Long.valueOf(7L));
        f.setStatus(status);
        return f;
    }

    private static void assertBusinessError(int code, String part, Runnable action) {
        BusinessException e = assertThrows(BusinessException.class, action::run);
        assertEquals(code, e.getCode(), "业务码不符：" + e.getMessage());
        assertTrue(e.getMessage() != null && e.getMessage().contains(part),
                "提示应包含「" + part + "」，实际：" + e.getMessage());
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    /* ==================================================================== */
    /*  submit                                                              */
    /* ==================================================================== */

    @Nested
    @DisplayName("submit · 提交反馈")
    class Submit {

        @Test
        @DisplayName("反馈对象为 null 抛 400")
        void nullReport() {
            assertBusinessError(400, "反馈内容不能为空", () -> service.submit(null, Long.valueOf(1L)));
        }

        @Test
        @DisplayName("类型为 null / 空串 / 纯空白抛 400")
        void blankType() {
            assertBusinessError(400, "feedbackType 不能为空",
                    () -> service.submit(report(null, "问题描述很长"), Long.valueOf(1L)));
            assertBusinessError(400, "feedbackType 不能为空",
                    () -> service.submit(report("   ", "问题描述很长"), Long.valueOf(1L)));
        }

        @Test
        @DisplayName("类型不在白名单内抛 400")
        void invalidType() {
            assertBusinessError(400, "取值非法",
                    () -> service.submit(report("UNKNOWN", "问题描述很长"), Long.valueOf(1L)));
        }

        @Test
        @DisplayName("内容为 null / 不足 5 个字抛 400")
        void contentTooShort() {
            assertBusinessError(400, "至少",
                    () -> service.submit(report(FeedbackReport.TYPE_DATA_ISSUE, null), Long.valueOf(1L)));
            assertBusinessError(400, "至少",
                    () -> service.submit(report(FeedbackReport.TYPE_DATA_ISSUE, "abcd"), Long.valueOf(1L)));
        }

        @Test
        @DisplayName("内容超过 1000 字抛 400")
        void contentTooLong() {
            assertBusinessError(400, "不能超过",
                    () -> service.submit(report(FeedbackReport.TYPE_PROBLEM, repeat('a', 1001)),
                            Long.valueOf(1L)));
        }

        @Test
        @DisplayName("刚好 1000 字可以通过（上边界）")
        void contentAtMaxAccepted() {
            Mockito.when(feedbackDao.insert(Mockito.any(FeedbackReport.class))).thenReturn(1L);

            assertEquals(1L, service.submit(report(FeedbackReport.TYPE_PROBLEM, repeat('a', 1000)),
                    Long.valueOf(1L)));
        }

        @Test
        @DisplayName("刚好 5 个字可以通过（下边界）")
        void contentAtMinAccepted() {
            Mockito.when(feedbackDao.insert(Mockito.any(FeedbackReport.class))).thenReturn(2L);

            assertEquals(2L, service.submit(report(FeedbackReport.TYPE_PROBLEM, "abcde"),
                    Long.valueOf(1L)));
        }

        @Test
        @DisplayName("类型会转成大写并写入，内容会裁剪首尾空白")
        void normalizesTypeAndContent() {
            Mockito.when(feedbackDao.insert(Mockito.any(FeedbackReport.class))).thenReturn(3L);

            service.submit(report("data_issue", "  问题描述很长  "), Long.valueOf(1L));

            ArgumentCaptor<FeedbackReport> captor = ArgumentCaptor.forClass(FeedbackReport.class);
            Mockito.verify(feedbackDao).insert(captor.capture());
            FeedbackReport saved = captor.getValue();
            assertEquals(FeedbackReport.TYPE_DATA_ISSUE, saved.getFeedbackType());
            assertEquals("问题描述很长", saved.getContent());
            assertEquals(FeedbackReport.STATUS_PENDING, saved.getStatus());
            assertEquals(Long.valueOf(1L), saved.getUserId());
            assertNull(saved.getHandlerId());
            assertNull(saved.getHandleTime());
            assertNotNull(saved.getCreatedAt());
        }

        @Test
        @DisplayName("手机号联系方式会被脱敏")
        void masksMobile() {
            Mockito.when(feedbackDao.insert(Mockito.any(FeedbackReport.class))).thenReturn(4L);
            FeedbackReport f = report(FeedbackReport.TYPE_FEATURE, "功能建议内容");
            f.setContactInfo("13800138000");

            service.submit(f, Long.valueOf(1L));

            assertEquals("138****8000", f.getContactInfo());
        }

        @Test
        @DisplayName("邮箱联系方式会被脱敏")
        void masksEmail() {
            Mockito.when(feedbackDao.insert(Mockito.any(FeedbackReport.class))).thenReturn(4L);
            FeedbackReport f = report(FeedbackReport.TYPE_FEATURE, "功能建议内容");
            f.setContactInfo("user@example.com");

            service.submit(f, Long.valueOf(1L));

            assertEquals("u***@example.com", f.getContactInfo());
        }

        @Test
        @DisplayName("未填联系方式时保持为 null")
        void noContact() {
            Mockito.when(feedbackDao.insert(Mockito.any(FeedbackReport.class))).thenReturn(4L);

            service.submit(report(FeedbackReport.TYPE_FEATURE, "功能建议内容"), Long.valueOf(1L));

            ArgumentCaptor<FeedbackReport> captor = ArgumentCaptor.forClass(FeedbackReport.class);
            Mockito.verify(feedbackDao).insert(captor.capture());
            assertNull(captor.getValue().getContactInfo());
        }

        @Test
        @DisplayName("允许匿名：userId 为 null 时照常写入")
        void anonymousAllowed() {
            Mockito.when(feedbackDao.insert(Mockito.any(FeedbackReport.class))).thenReturn(5L);

            assertEquals(5L, service.submit(report(FeedbackReport.TYPE_DATA_ISSUE, "数据有问题"), null));
        }
    }

    /* ==================================================================== */
    /*  list                                                                */
    /* ==================================================================== */

    @Nested
    @DisplayName("list · 管理端列表")
    class ListQuery {

        @Test
        @DisplayName("状态与类型为空表示查全部，条数按默认值透传")
        void allByDefault() {
            Mockito.when(feedbackDao.list(null, null, 50))
                    .thenReturn(Collections.singletonList(new FeedbackReport()));

            assertEquals(1, service.list(null, null, 0).size());
            assertEquals(1, service.list("  ", "", -5).size());
        }

        @Test
        @DisplayName("状态与类型会被规范化（大写）后透传")
        void normalizes() {
            Mockito.when(feedbackDao.list(FeedbackReport.STATUS_PENDING, FeedbackReport.TYPE_DATA_ISSUE, 10))
                    .thenReturn(new ArrayList<FeedbackReport>());

            assertTrue(service.list(" pending ", "data_issue", 10).isEmpty());
        }

        @Test
        @DisplayName("状态非法抛 400，且不查库")
        void invalidStatus() {
            assertBusinessError(400, "status 取值非法", () -> service.list("DONE", null, 10));
            Mockito.verifyNoInteractions(feedbackDao);
        }

        @Test
        @DisplayName("条数超过 200 时被截断")
        void capsLimit() {
            Mockito.when(feedbackDao.list(null, null, 200)).thenReturn(new ArrayList<FeedbackReport>());

            assertTrue(service.list(null, null, 9999).isEmpty());
        }
    }

    @Nested
    @DisplayName("listByUser · 我的反馈")
    class ListByUser {

        @Test
        @DisplayName("直接委托 DAO")
        void delegates() {
            Mockito.when(feedbackDao.listByUser(1L))
                    .thenReturn(Collections.singletonList(new FeedbackReport()));

            assertEquals(1, service.listByUser(1L).size());
        }
    }

    /* ==================================================================== */
    /*  handle / reopen                                                     */
    /* ==================================================================== */

    @Nested
    @DisplayName("handle · 标记已处理")
    class Handle {

        @Test
        @DisplayName("反馈不存在抛 404")
        void notFound() {
            Mockito.when(feedbackDao.findById(7L)).thenReturn(null);
            assertBusinessError(404, "反馈不存在", () -> service.handle(7L, 1L));
        }

        @Test
        @DisplayName("已处理过的再处理返回 0（幂等）")
        void alreadyProcessed() {
            Mockito.when(feedbackDao.findById(7L)).thenReturn(existing(FeedbackReport.STATUS_PROCESSED));

            assertEquals(0, service.handle(7L, 1L));
            Mockito.verify(feedbackDao, Mockito.never())
                    .handle(Mockito.anyLong(), Mockito.anyLong(), Mockito.any(LocalDateTime.class));
        }

        @Test
        @DisplayName("状态大小写不敏感：小写 processed 也视为已处理")
        void caseInsensitive() {
            Mockito.when(feedbackDao.findById(7L)).thenReturn(existing("processed"));

            assertEquals(0, service.handle(7L, 1L));
        }

        @Test
        @DisplayName("待处理的反馈写入处理人与处理时间")
        void handled() {
            Mockito.when(feedbackDao.findById(7L)).thenReturn(existing(FeedbackReport.STATUS_PENDING));
            Mockito.when(feedbackDao.handle(Mockito.eq(7L), Mockito.eq(1L), Mockito.any(LocalDateTime.class)))
                    .thenReturn(1);

            assertEquals(1, service.handle(7L, 1L));
        }
    }

    @Nested
    @DisplayName("reopen · 打回待处理")
    class Reopen {

        @Test
        @DisplayName("反馈不存在抛 404")
        void notFound() {
            Mockito.when(feedbackDao.findById(7L)).thenReturn(null);
            assertBusinessError(404, "反馈不存在", () -> service.reopen(7L));
        }

        @Test
        @DisplayName("仍是待处理状态时返回 0（幂等）")
        void alreadyPending() {
            Mockito.when(feedbackDao.findById(7L)).thenReturn(existing(FeedbackReport.STATUS_PENDING));

            assertEquals(0, service.reopen(7L));
            Mockito.verify(feedbackDao, Mockito.never()).reopen(7L);
        }

        @Test
        @DisplayName("已处理的反馈被打回")
        void reopened() {
            Mockito.when(feedbackDao.findById(7L)).thenReturn(existing(FeedbackReport.STATUS_PROCESSED));
            Mockito.when(feedbackDao.reopen(7L)).thenReturn(1);

            assertEquals(1, service.reopen(7L));
        }
    }

    /* ==================================================================== */
    /*  stats                                                               */
    /* ==================================================================== */

    @Nested
    @DisplayName("stats · 待办统计")
    class Stats {

        @Test
        @DisplayName("total 为待处理 + 已处理之和，且顺序稳定")
        void computes() {
            Mockito.when(feedbackDao.countByStatus(FeedbackReport.STATUS_PENDING)).thenReturn(3);
            Mockito.when(feedbackDao.countByStatus(FeedbackReport.STATUS_PROCESSED)).thenReturn(7);

            Map<String, Integer> m = service.stats();

            assertEquals(Integer.valueOf(10), m.get("total"));
            assertEquals(Integer.valueOf(3), m.get("pending"));
            assertEquals(Integer.valueOf(7), m.get("processed"));
            List<String> keys = new ArrayList<String>(m.keySet());
            assertEquals(Arrays.asList("total", "pending", "processed"), keys);
        }
    }
}
