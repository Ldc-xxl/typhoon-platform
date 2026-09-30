package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.FeedbackReportDao;
import com.zfz.typhoon.dao.impl.FeedbackReportDaoImpl;
import com.zfz.typhoon.model.FeedbackReport;
import com.zfz.typhoon.service.FeedbackService;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.DateUtil;
import com.zfz.typhoon.util.PasswordUtil;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户反馈业务实现。
 *
 * @author 追风者项目组
 * @see FeedbackService
 */
public class FeedbackServiceImpl implements FeedbackService {

    /** 内容长度下限：太短的反馈无法定位问题 */
    private static final int MIN_CONTENT = 5;

    /** 内容长度上限，对齐 {@code feedback_reports.content varchar(1000)} */
    private static final int MAX_CONTENT = 1000;

    /** 默认条数 */
    private static final int DEFAULT_LIMIT = 50;

    /** 条数上限 */
    private static final int MAX_LIMIT = 200;

    private final FeedbackReportDao feedbackDao;

    /**
     * 生产用构造：按默认实现装配依赖。
     * <p>之所以把它拆成"无参 + 带参"两个构造，是为了给单元测试留出注入替身的缝隙：
     * 依赖全部在字段初始化里 new 出来的话，测试就必须连上真实 MySQL 才能跑，
     * 单元测试也就退化成了集成测试。
     */
    public FeedbackServiceImpl() {
        this(new FeedbackReportDaoImpl());
    }

    /**
     * 测试用构造：注入替身依赖（Mockito 生成的 mock 或手写桩）。
     * <p>参数顺序与上方字段声明一致，均为本服务运行所需的协作者。
     */
    public FeedbackServiceImpl(FeedbackReportDao feedbackDao) {
        this.feedbackDao = feedbackDao;
    }

    @Override
    public long submit(FeedbackReport feedback, Long userId) {
        if (feedback == null) {
            throw BusinessException.badRequest("反馈内容不能为空");
        }
        String type = feedback.getFeedbackType();
        if (type == null || type.trim().isEmpty()) {
            throw BusinessException.badRequest("feedbackType 不能为空");
        }
        String t = type.trim().toUpperCase();
        if (!Arrays.asList(FeedbackReport.TYPE_DATA_ISSUE, FeedbackReport.TYPE_FEATURE,
                FeedbackReport.TYPE_PROBLEM).contains(t)) {
            throw BusinessException.badRequest(
                    "feedbackType 取值非法，应为 DATA_ISSUE / FEATURE_SUGGESTION / PROBLEM_REPORT 之一");
        }
        feedback.setFeedbackType(t);

        String content = feedback.getContent() == null ? "" : feedback.getContent().trim();
        if (content.length() < MIN_CONTENT) {
            throw BusinessException.badRequest("反馈内容至少 " + MIN_CONTENT + " 个字，请描述清楚问题现象");
        }
        if (content.length() > MAX_CONTENT) {
            throw BusinessException.badRequest("反馈内容不能超过 " + MAX_CONTENT + " 个字");
        }
        feedback.setContent(content);
        feedback.setContactInfo(PasswordUtil.maskContact(feedback.getContactInfo()));

        feedback.setUserId(userId);
        feedback.setStatus(FeedbackReport.STATUS_PENDING);
        feedback.setHandlerId(null);
        feedback.setHandleTime(null);
        feedback.setCreatedAt(DateUtil.now());
        return feedbackDao.insert(feedback);
    }

    @Override
    public List<FeedbackReport> list(String status, String type, int limit) {
        String s = normalizeStatus(status);
        String t = type == null || type.trim().isEmpty() ? null : type.trim().toUpperCase();
        int n = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return feedbackDao.list(s, t, n);
    }

    @Override
    public List<FeedbackReport> listByUser(long userId) {
        return feedbackDao.listByUser(userId);
    }

    @Override
    public int handle(long feedbackId, long handlerId) {
        FeedbackReport exists = feedbackDao.findById(feedbackId);
        if (exists == null) {
            throw BusinessException.notFound("反馈不存在：" + feedbackId);
        }
        if (FeedbackReport.STATUS_PROCESSED.equalsIgnoreCase(exists.getStatus())) {
            // 幂等：已处理过再点一次不算错误，返回 0 让前端提示"无需重复处理"
            return 0;
        }
        return feedbackDao.handle(feedbackId, handlerId, DateUtil.now());
    }

    @Override
    public int reopen(long feedbackId) {
        FeedbackReport exists = feedbackDao.findById(feedbackId);
        if (exists == null) {
            throw BusinessException.notFound("反馈不存在：" + feedbackId);
        }
        if (FeedbackReport.STATUS_PENDING.equalsIgnoreCase(exists.getStatus())) {
            return 0;
        }
        return feedbackDao.reopen(feedbackId);
    }

    @Override
    public Map<String, Integer> stats() {
        int pending = feedbackDao.countByStatus(FeedbackReport.STATUS_PENDING);
        int processed = feedbackDao.countByStatus(FeedbackReport.STATUS_PROCESSED);
        Map<String, Integer> m = new LinkedHashMap<String, Integer>();
        m.put("total", Integer.valueOf(pending + processed));
        m.put("pending", Integer.valueOf(pending));
        m.put("processed", Integer.valueOf(processed));
        return m;
    }

    /**
     * 规整状态参数。
     *
     * @param status 原始状态
     * @return 规范状态；空返回 null
     */
    private String normalizeStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return null;
        }
        String s = status.trim().toUpperCase();
        if (!FeedbackReport.STATUS_PENDING.equals(s) && !FeedbackReport.STATUS_PROCESSED.equals(s)) {
            throw BusinessException.badRequest("status 取值非法，应为 PENDING 或 PROCESSED");
        }
        return s;
    }
}
