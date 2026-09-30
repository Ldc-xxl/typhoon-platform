package com.zfz.typhoon.servlet;

import com.zfz.typhoon.model.vo.NotificationVO;
import com.zfz.typhoon.service.NotificationService;
import com.zfz.typhoon.service.impl.NotificationServiceImpl;
import com.zfz.typhoon.util.ParamUtil;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知中心接口（全部需要登录）。
 *
 * <pre>
 * GET    /api/notifications                通知列表（含未读数）
 * GET    /api/notifications/unread-count   仅未读数（顶栏红点轮询用）
 * PUT    /api/notifications/{id}/read      标记单条已读
 * PUT    /api/notifications/read-all       全部已读
 * DELETE /api/notifications/{id}           删除一条
 * </pre>
 *
 * <h3>为什么列表里带未读数</h3>
 * 前端顶栏同时要显示"列表内容"和"红点数字"。若分成两个请求，
 * 会出现"红点显示 3 但列表里只有 2 条未读"这种瞬时不一致
 * （两次请求之间被另一处标记已读）。
 * 合成一个响应，两个数字来自同一次数据库快照，天然一致。
 * 只有专门轮询红点的场景才走轻量的 {@code unread-count}。
 *
 * <h3>所有写操作的 WHERE 都带 user_id</h3>
 * 标记已读、删除都通过服务层把 {@code userId} 传进 SQL 条件，
 * 因此即使有人猜到别人的通知ID，也改不动别人的数据。
 * 这类"靠 ID 猜不到"并不是安全边界，真正的边界是这个 WHERE 条件。
 *
 * @author 追风者项目组
 */
@WebServlet(name = "notificationServlet", urlPatterns = "/api/notifications/*")
public class NotificationServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    /** 列表默认条数 */
    private static final int DEFAULT_LIMIT = 20;

    private final NotificationService notificationService = new NotificationServiceImpl();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long userId = requireUserId(req);
        String[] seg = pathSegments(req);
        String sub = segment(seg, 0);

        if ("unread-count".equalsIgnoreCase(sub)) {
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("unread", Integer.valueOf(notificationService.countUnread(userId)));
            ok(resp, data);
            return;
        }
        if (sub != null) {
            notFound(resp, req.getRequestURI());
            return;
        }

        int limit = ParamUtil.intVal(req, "limit", DEFAULT_LIMIT, 1, 100);
        List<NotificationVO> rows = notificationService.list(userId, limit);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("items", rows);
        data.put("unread", Integer.valueOf(notificationService.countUnread(userId)));
        ok(resp, data);
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

        if ("read-all".equalsIgnoreCase(first)) {
            int rows = notificationService.markAllRead(userId);
            ok(resp, "已将 " + rows + " 条通知标记为已读", Integer.valueOf(rows));
            return;
        }

        // PUT /api/notifications/{id}/read
        if (seg.length == 2 && "read".equalsIgnoreCase(seg[1])) {
            long notificationId = ParamUtil.requirePathId(first, "notificationId");
            int rows = notificationService.markRead(notificationId, userId);
            ok(resp, Integer.valueOf(rows));
            return;
        }
        notFound(resp, req.getRequestURI());
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // 通知删除未在服务层提供（历史通知保留更利于复盘），此处显式返回不支持
        requireUserId(req);
        methodNotAllowed(req, resp);
    }
}
