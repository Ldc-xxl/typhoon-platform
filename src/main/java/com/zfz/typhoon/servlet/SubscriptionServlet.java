package com.zfz.typhoon.servlet;

import com.zfz.typhoon.model.UserSubscription;
import com.zfz.typhoon.service.SubscriptionService;
import com.zfz.typhoon.service.impl.SubscriptionServiceImpl;
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
 * 台风 / 城市订阅接口（全部需要登录）。
 *
 * <pre>
 * GET    /api/subscriptions               我的订阅列表
 * POST   /api/subscriptions               新增订阅  body: {typhoonId|cityId, notifyXxx...}
 * PUT    /api/subscriptions/{id}          修改通知偏好
 * PUT    /api/subscriptions/push?enabled=1 一键开关全部推送
 * DELETE /api/subscriptions/{id}          取消订阅
 * </pre>
 *
 * <h3>服务端为什么反复校验 userId</h3>
 * 订阅接口的路径里只有 {@code subscriptionId}，没有 userId——
 * 这是 REST 的常规写法，但也就意味着"改哪条订阅"完全由前端传的 ID 决定。
 * 因此每一次写操作都必须：
 * <ol>
 *   <li>把会话里的 userId 从<b>服务端</b>取出（不是从前端参数取）；</li>
 *   <li>在 SQL 的 WHERE 里带上它（{@code deleteByIdAndUser} 等）；</li>
 *   <li>改偏好前先查出记录并比对 {@code userId}。</li>
 * </ol>
 * 三条缺一不可。只做第 2 条时，越权修改会先"看起来成功"再被 0 行影响掩盖，
 * 用户得不到明确反馈；只做第 3 条时如果有别的写入口漏了检查就会出现缺口。
 *
 * @author 追风者项目组
 */
@WebServlet(name = "subscriptionServlet", urlPatterns = "/api/subscriptions/*")
public class SubscriptionServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    /** 订阅请求体 */
    public static class SubscriptionRequest {
        private Long typhoonId;
        private Long cityId;
        private Boolean notifyNewStorm;
        private Boolean notifyStrengthChange;
        private Boolean notifyPathChange;
        private Boolean pushEnabled;

        public Long getTyphoonId() {
            return typhoonId;
        }

        public void setTyphoonId(Long typhoonId) {
            this.typhoonId = typhoonId;
        }

        public Long getCityId() {
            return cityId;
        }

        public void setCityId(Long cityId) {
            this.cityId = cityId;
        }

        public Boolean getNotifyNewStorm() {
            return notifyNewStorm;
        }

        public void setNotifyNewStorm(Boolean notifyNewStorm) {
            this.notifyNewStorm = notifyNewStorm;
        }

        public Boolean getNotifyStrengthChange() {
            return notifyStrengthChange;
        }

        public void setNotifyStrengthChange(Boolean notifyStrengthChange) {
            this.notifyStrengthChange = notifyStrengthChange;
        }

        public Boolean getNotifyPathChange() {
            return notifyPathChange;
        }

        public void setNotifyPathChange(Boolean notifyPathChange) {
            this.notifyPathChange = notifyPathChange;
        }

        public Boolean getPushEnabled() {
            return pushEnabled;
        }

        public void setPushEnabled(Boolean pushEnabled) {
            this.pushEnabled = pushEnabled;
        }

        /**
         * 转换为实体（userId 由调用方注入，不接受前端传值）。
         *
         * @param userId 会话中的用户ID
         * @return 订阅实体
         */
        public UserSubscription toEntity(long userId) {
            UserSubscription s = new UserSubscription();
            s.setUserId(Long.valueOf(userId));
            s.setTyphoonId(typhoonId);
            s.setCityId(cityId);
            s.setNotifyNewStorm(notifyNewStorm);
            s.setNotifyStrengthChange(notifyStrengthChange);
            s.setNotifyPathChange(notifyPathChange);
            s.setPushEnabled(pushEnabled);
            return s;
        }
    }

    private final SubscriptionService subscriptionService = new SubscriptionServiceImpl();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long userId = requireUserId(req);
        List<UserSubscription> rows = subscriptionService.list(userId);

        long[] counters = countTypes(rows);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("items", rows);
        data.put("total", Integer.valueOf(rows.size()));
        data.put("typhoonCount", Long.valueOf(counters[0]));
        data.put("cityCount", Long.valueOf(counters[1]));
        data.put("pushEnabledCount", Long.valueOf(counters[2]));
        ok(resp, data);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long userId = requireUserId(req);
        SubscriptionRequest body = JsonUtil.readBody(req, SubscriptionRequest.class);
        if (body == null) {
            throw BusinessException.badRequest("请求体不能为空");
        }
        long id = subscriptionService.subscribe(body.toEntity(userId));
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("subscriptionId", Long.valueOf(id));
        ok(resp, "订阅已保存", data);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long userId = requireUserId(req);
        String[] seg = pathSegments(req);
        String first = segment(seg, 0);
        if (first == null) {
            notFound(resp, req.getRequestURI());
            return;
        }

        // PUT /api/subscriptions/push?enabled=1
        if ("push".equalsIgnoreCase(first)) {
            boolean enabled = ParamUtil.boolVal(req, "enabled", true);
            int rows = subscriptionService.setPushEnabled(userId, enabled);
            ok(resp, enabled ? "已开启全部推送" : "已关闭全部推送", Integer.valueOf(rows));
            return;
        }

        long subscriptionId = ParamUtil.requirePathId(first, "subscriptionId");
        SubscriptionRequest body = JsonUtil.readBody(req, SubscriptionRequest.class);
        if (body == null) {
            throw BusinessException.badRequest("请求体不能为空");
        }
        UserSubscription entity = body.toEntity(userId);
        entity.setSubscriptionId(Long.valueOf(subscriptionId));
        int rows = subscriptionService.updatePreferences(entity);
        ok(resp, "订阅偏好已更新", Integer.valueOf(rows));
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long userId = requireUserId(req);
        String[] seg = pathSegments(req);
        if (seg.length != 1) {
            notFound(resp, req.getRequestURI());
            return;
        }
        long subscriptionId = ParamUtil.requirePathId(seg[0], "subscriptionId");
        int rows = subscriptionService.unsubscribe(subscriptionId, userId);
        ok(resp, "已取消订阅", Integer.valueOf(rows));
    }

    /**
     * 统计订阅类型分布。
     *
     * @param rows 订阅列表
     * @return {@code [台风订阅数, 城市订阅数, 开启推送数]}
     */
    private long[] countTypes(List<UserSubscription> rows) {
        long typhoon = 0L;
        long city = 0L;
        long push = 0L;
        for (UserSubscription s : rows) {
            if (s.getTyphoonId() != null) {
                typhoon++;
            }
            if (s.getCityId() != null) {
                city++;
            }
            if (!Boolean.FALSE.equals(s.getPushEnabled())) {
                push++;
            }
        }
        return new long[]{typhoon, city, push};
    }
}
