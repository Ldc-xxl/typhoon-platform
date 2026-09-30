package com.zfz.typhoon.servlet;

import com.zfz.typhoon.model.FeedbackReport;
import com.zfz.typhoon.service.FeedbackService;
import com.zfz.typhoon.service.impl.FeedbackServiceImpl;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.JsonUtil;
import com.zfz.typhoon.util.ParamUtil;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户反馈接口。
 *
 * <pre>
 * POST /api/feedback                  提交反馈（支持匿名）
 * GET  /api/feedback?mine=1           我的反馈（需登录）
 * GET  /api/feedback?status=PENDING   反馈列表（管理员）
 * </pre>
 *
 * <h3>为什么反馈允许匿名提交</h3>
 * 很多有价值的反馈来自"懒得注册的路人"——他们刚发现某个城市的风圈画错了，
 * 如果必须先注册才能说，反馈率会断崖式下跌。
 * 因此 {@code user_id} 允许为空（数据库列本身就是可空的，
 * 外键还是 {@code ON DELETE SET NULL}，说明设计上就预期了匿名场景）。
 *
 * <h3>匿名提交的滥用防护</h3>
 * 匿名 + 可写 = 可能被刷。当前用两条轻量规则兜底：
 * <ol>
 *   <li>内容长度下限 5 字，挡住"test"这类无效提交；</li>
 *   <li>上限 1000 字，对齐数据库列宽，防止超长内容被截断后语义全变。</li>
 * </ol>
 * 真正的限流需要 IP 维度计数，属于部署层能力（见 {@code docs/issue-tracking.md}）。
 *
 * @author 追风者项目组
 */
@WebServlet(name = "feedbackServlet", urlPatterns = "/api/feedback")
public class FeedbackServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    /** 反馈请求体 */
    public static class FeedbackRequest {
        private String feedbackType;
        private String type;
        private String content;
        private String contactInfo;
        private String contact;

        public String getFeedbackType() {
            return feedbackType;
        }

        public void setFeedbackType(String feedbackType) {
            this.feedbackType = feedbackType;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getContactInfo() {
            return contactInfo;
        }

        public void setContactInfo(String contactInfo) {
            this.contactInfo = contactInfo;
        }

        public String getContact() {
            return contact;
        }

        public void setContact(String contact) {
            this.contact = contact;
        }

        /**
         * 转换为实体（未登录时 userId 为 null，允许匿名）。
         *
         * @param userId 会话中的用户ID，可为 null
         * @return 反馈实体
         */
        public FeedbackReport toEntity(Long userId) {
            FeedbackReport f = new FeedbackReport();
            f.setUserId(userId);
            f.setFeedbackType(ParamUtil.firstNonEmpty(feedbackType, type));
            f.setContent(content);
            f.setContactInfo(ParamUtil.firstNonEmpty(contactInfo, contact));
            return f;
        }
    }

    private final FeedbackService feedbackService = new FeedbackServiceImpl();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        boolean mine = ParamUtil.boolVal(req, "mine", false);
        if (mine) {
            long userId = requireUserId(req);
            ok(resp, feedbackService.listByUser(userId));
            return;
        }

        // 全量列表属于运营数据，仅管理员可见
        requireAdmin(req);
        String status = ParamUtil.str(req, "status");
        String type = ParamUtil.str(req, "type");
        int limit = ParamUtil.intVal(req, "limit", 50, 1, 200);
        List<FeedbackReport> rows = feedbackService.list(status, type, limit);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("items", rows);
        data.put("stats", feedbackService.stats());
        ok(resp, data);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        FeedbackRequest body = JsonUtil.readBody(req, FeedbackRequest.class);
        if (body == null) {
            throw BusinessException.badRequest("请求体不能为空");
        }
        // 未登录也能提交（匿名反馈），已登录则记录归属用户
        Long userId = currentUserId(req);
        long id = feedbackService.submit(body.toEntity(userId), userId);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("feedbackId", Long.valueOf(id));
        data.put("anonymous", Boolean.valueOf(userId == null));
        ok(resp, "感谢反馈，我们会尽快处理", data);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // 处理动作统一由 /api/admin/feedback/{id}/handle 承担，这里不重复提供入口
        requireAdmin(req);
        methodNotAllowed(req, resp);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        requireAdmin(req);
        methodNotAllowed(req, resp);
    }
}
