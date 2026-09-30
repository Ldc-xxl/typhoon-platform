package com.zfz.typhoon.servlet;

import com.zfz.typhoon.model.SyncLog;
import com.zfz.typhoon.model.User;
import com.zfz.typhoon.model.vo.UserVO;
import com.zfz.typhoon.service.AdminService;
import com.zfz.typhoon.service.DataSourceService;
import com.zfz.typhoon.service.FeedbackService;
import com.zfz.typhoon.service.SystemSettingService;
import com.zfz.typhoon.service.UserService;
import com.zfz.typhoon.service.impl.AdminServiceImpl;
import com.zfz.typhoon.service.impl.DataSourceServiceImpl;
import com.zfz.typhoon.service.impl.FeedbackServiceImpl;
import com.zfz.typhoon.service.impl.SystemSettingServiceImpl;
import com.zfz.typhoon.service.impl.UserServiceImpl;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.JsonUtil;
import com.zfz.typhoon.util.ParamUtil;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台接口（全部需要管理员身份，由 {@code AuthFilter} 统一拦截）。
 *
 * <pre>
 * GET    /api/admin/dashboard                          后台运行概览
 * GET    /api/admin/stats                              后台统计图表（ECharts 用）
 * GET    /api/admin/todos                              待办与审核队列
 * GET    /api/admin/models                             大模型接入配置
 * GET    /api/admin/settings                           系统参数
 * PUT    /api/admin/settings                           批量保存参数  body: {key: value, ...}
 * GET    /api/admin/datasources                        数据源接入监控
 * PUT    /api/admin/datasources/{id}/status            启用/停用数据源  ?enabled=1
 * GET    /api/admin/logs/sync                          同步日志
 * POST   /api/admin/logs/sync                          登记一次同步结果（供采集任务回调）
 * GET    /api/admin/users                              用户列表
 * POST   /api/admin/users                              新建用户
 * PUT    /api/admin/users/{id}                         修改资料
 * PUT    /api/admin/users/{id}/status                  启用/停用  ?enabled=1
 * PUT    /api/admin/users/{id}/role                    调整角色  ?role=ADMIN
 * PUT    /api/admin/users/{id}/password                重置密码  body: {newPassword}
 * DELETE /api/admin/users/{id}                         删除用户
 * GET    /api/admin/feedback                           反馈列表
 * PUT    /api/admin/feedback/{id}/handle               标记已处理
 * PUT    /api/admin/feedback/{id}/reopen               退回待处理
 * </pre>
 *
 * <h3>为什么后台接口全在一个类里</h3>
 * 后台的功能边界是"管理员专用"，而这个边界已经由路径前缀
 * {@code /api/admin/**} 在过滤器里统一实施了。把它们拆成
 * {@code AdminUserServlet} / {@code AdminSettingServlet} 等多个类，
 * 意味着每个类都要重复一份 {@code pathSegments} 解析与分支分发逻辑，
 * 而它们的分发规则完全同构（{@code /{资源}/{id}/{动作}}）。
 * 集中一处反而更容易看全"管理员到底能干哪些事"——
 * 这对权限审查是有利的。
 *
 * <h3>请求体统一用 {@code Map} 收</h3>
 * 参数保存、重置密码这类接口的 body 结构随前端表单变化，
 * 为每个都定义一个 DTO 会产生大量只用一次的空壳类。
 * 这里用 {@code Map<String,Object>} 接收后再逐字段取用，
 * 类型转换的失败会变成明确的 400 提示，不会静默出错。
 *
 * @author 追风者项目组
 */
@WebServlet(name = "adminServlet", urlPatterns = "/api/admin/*")
public class AdminServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    private final AdminService adminService = new AdminServiceImpl();
    private final UserService userService = new UserServiceImpl();
    private final SystemSettingService settingService = new SystemSettingServiceImpl();
    private final DataSourceService dataSourceService = new DataSourceServiceImpl();
    private final FeedbackService feedbackService = new FeedbackServiceImpl();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        requireAdmin(req);
        String[] seg = pathSegments(req);
        String resource = segment(seg, 0);
        if (resource == null) {
            notFound(resp, req.getRequestURI());
            return;
        }

        if ("dashboard".equalsIgnoreCase(resource)) {
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("metrics", adminService.dashboard());
            data.put("todos", adminService.todos());
            ok(resp, data);
        } else if ("stats".equalsIgnoreCase(resource)) {
            // 后台统计图表数据（ECharts：用户增长趋势 + 数据分布饼图）
            ok(resp, adminService.stats());
        } else if ("todos".equalsIgnoreCase(resource)) {
            ok(resp, adminService.todos());
        } else if ("models".equalsIgnoreCase(resource)) {
            ok(resp, adminService.modelConfigs());
        } else if ("settings".equalsIgnoreCase(resource)) {
            ok(resp, settingService.listAll());
        } else if ("datasources".equalsIgnoreCase(resource)) {
            ok(resp, adminService.dataSources());
        } else if ("users".equalsIgnoreCase(resource)) {
            listUsers(req, resp, seg);
        } else if ("feedback".equalsIgnoreCase(resource)) {
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("items", feedbackService.list(ParamUtil.str(req, "status"),
                    ParamUtil.str(req, "type"), ParamUtil.intVal(req, "limit", 50, 1, 200)));
            data.put("stats", feedbackService.stats());
            ok(resp, data);
        } else if ("logs".equalsIgnoreCase(resource)) {
            logs(req, resp, seg);
        } else {
            notFound(resp, req.getRequestURI());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        requireAdmin(req);
        String[] seg = pathSegments(req);
        String resource = segment(seg, 0);

        if ("users".equalsIgnoreCase(resource)) {
            createUser(req, resp);
            return;
        }
        if ("logs".equalsIgnoreCase(resource) && seg.length == 2 && "sync".equalsIgnoreCase(seg[1])) {
            recordSync(req, resp);
            return;
        }
        notFound(resp, req.getRequestURI());
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        requireAdmin(req);
        String[] seg = pathSegments(req);
        String resource = segment(seg, 0);
        if (resource == null) {
            notFound(resp, req.getRequestURI());
            return;
        }

        if ("settings".equalsIgnoreCase(resource)) {
            saveSettings(req, resp);
            return;
        }
        if ("users".equalsIgnoreCase(resource)) {
            updateUser(req, resp, seg);
            return;
        }
        if ("datasources".equalsIgnoreCase(resource)) {
            updateDataSource(req, resp, seg);
            return;
        }
        if ("feedback".equalsIgnoreCase(resource)) {
            handleFeedback(req, resp, seg);
            return;
        }
        notFound(resp, req.getRequestURI());
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        requireAdmin(req);
        String[] seg = pathSegments(req);
        if (seg.length == 2 && "users".equalsIgnoreCase(seg[0])) {
            long userId = ParamUtil.requirePathId(seg[1], "userId");
            long operatorId = requireUserId(req);
            int rows = userService.delete(userId, operatorId);
            ok(resp, "用户已删除", Integer.valueOf(rows));
            return;
        }
        notFound(resp, req.getRequestURI());
    }

    /* ==================================================================== */
    /*  用户管理                                                            */
    /* ==================================================================== */

    /**
     * 用户列表（支持关键词 / 角色 / 状态过滤）。
     *
     * @param req  请求
     * @param resp 响应
     * @param seg  路径段
     */
    private void listUsers(HttpServletRequest req, HttpServletResponse resp, String[] seg) {
        if (seg.length == 2) {
            long userId = ParamUtil.requirePathId(seg[1], "userId");
            ok(resp, userService.getById(userId));
            return;
        }
        String keyword = ParamUtil.str(req, "keyword");
        if (keyword == null || keyword.trim().isEmpty()) {
            keyword = ParamUtil.str(req, "q");
        }
        String role = ParamUtil.str(req, "role");
        Boolean enabled = null;
        String enabledRaw = ParamUtil.str(req, "enabled");
        if (enabledRaw != null && !enabledRaw.trim().isEmpty()) {
            enabled = Boolean.valueOf("true".equalsIgnoreCase(enabledRaw.trim())
                    || "1".equals(enabledRaw.trim()));
        }
        List<UserVO> rows = userService.list(keyword, role, enabled);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("items", rows);
        data.put("stats", userService.stats());
        ok(resp, data);
    }

    /**
     * 新建用户。
     *
     * @param req  请求
     * @param resp 响应
     */
    private void createUser(HttpServletRequest req, HttpServletResponse resp) {
        Map<String, Object> body = readMap(req);
        User user = new User();
        user.setUsername(text(body, "username", "account"));
        user.setNickname(text(body, "nickname"));
        user.setEmail(text(body, "email"));
        user.setPhone(text(body, "phone"));
        user.setRole(text(body, "role", "roleCode"));
        String password = text(body, "password", "newPassword");
        if (password == null) {
            throw BusinessException.badRequest("缺少初始密码 password");
        }
        long id = userService.create(user, password);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("userId", Long.valueOf(id));
        ok(resp, "用户已创建", data);
    }

    /**
     * 用户资料修改 / 状态切换 / 角色调整 / 重置密码。
     *
     * <p>用路径最后一段作为动作名，因此
     * {@code PUT /users/3/status}、{@code /users/3/role} 走不同分支，
     * 而 {@code PUT /users/3} 视为资料修改。
     *
     * @param req  请求
     * @param resp 响应
     * @param seg  路径段
     */
    private void updateUser(HttpServletRequest req, HttpServletResponse resp, String[] seg) {
        if (seg.length == 1) {
            throw BusinessException.badRequest("缺少 userId");
        }
        long userId = ParamUtil.requirePathId(seg[1], "userId");
        long operatorId = requireUserId(req);
        String action = seg.length > 2 ? seg[2] : null;

        if ("status".equalsIgnoreCase(action)) {
            boolean enabled = ParamUtil.boolVal(req, "enabled", true);
            int rows = userService.setEnabled(userId, enabled, operatorId);
            ok(resp, enabled ? "账号已启用" : "账号已停用", Integer.valueOf(rows));
            return;
        }
        if ("role".equalsIgnoreCase(action)) {
            String role = ParamUtil.str(req, "role");
            if (role == null) {
                Map<String, Object> body = tryReadMap(req);
                role = body == null ? null : text(body, "role", "roleCode");
            }
            int rows = userService.setRole(userId, role, operatorId);
            ok(resp, "角色已更新", Integer.valueOf(rows));
            return;
        }
        if ("password".equalsIgnoreCase(action)) {
            Map<String, Object> body = readMap(req);
            String password = text(body, "newPassword", "password");
            if (password == null) {
                throw BusinessException.badRequest("缺少 newPassword");
            }
            int rows = userService.resetPassword(userId, password);
            ok(resp, "密码已重置", Integer.valueOf(rows));
            return;
        }
        if (action != null) {
            notFound(resp, req.getRequestURI());
            return;
        }

        Map<String, Object> body = readMap(req);
        User user = new User();
        user.setUserId(Long.valueOf(userId));
        user.setNickname(text(body, "nickname"));
        user.setEmail(text(body, "email"));
        user.setPhone(text(body, "phone"));
        int rows = userService.updateProfile(user);
        ok(resp, "资料已更新", Integer.valueOf(rows));
    }

    /* ==================================================================== */
    /*  系统参数                                                            */
    /* ==================================================================== */

    /**
     * 批量保存系统参数。
     *
     * <p>请求体既支持 {@code {"key":"value"}} 的扁平形式，
     * 也支持 {@code {"values":{"key":"value"}}} 的包装形式——
     * 后者在同时还要传备注等附加字段时更自然。
     *
     * @param req  请求
     * @param resp 响应
     */
    @SuppressWarnings("unchecked")
    private void saveSettings(HttpServletRequest req, HttpServletResponse resp) {
        Map<String, Object> body = readMap(req);
        Object nested = body.get("values");
        Map<String, Object> flat = nested instanceof Map ? (Map<String, Object>) nested : body;

        Map<String, String> values = new LinkedHashMap<String, String>();
        for (Map.Entry<String, Object> e : flat.entrySet()) {
            if ("values".equals(e.getKey())) {
                continue;
            }
            values.put(e.getKey(), e.getValue() == null ? null : String.valueOf(e.getValue()));
        }
        Long operatorId = currentUserId(req);
        int rows = settingService.updateBatch(values, operatorId);
        ok(resp, "已保存 " + rows + " 项参数", Integer.valueOf(rows));
    }

    /* ==================================================================== */
    /*  数据源与同步日志                                                     */
    /* ==================================================================== */

    /**
     * 启用 / 停用数据源。
     *
     * @param req  请求
     * @param resp 响应
     * @param seg  路径段
     */
    private void updateDataSource(HttpServletRequest req, HttpServletResponse resp, String[] seg) {
        if (seg.length != 3 || !"status".equalsIgnoreCase(seg[2])) {
            notFound(resp, req.getRequestURI());
            return;
        }
        long sourceId = ParamUtil.requirePathId(seg[1], "sourceId");
        boolean enabled = ParamUtil.boolVal(req, "enabled", true);
        int rows = dataSourceService.setEnabled(sourceId, enabled);
        ok(resp, enabled ? "数据源已启用" : "数据源已停用", Integer.valueOf(rows));
    }

    /**
     * 同步日志（{@code /api/admin/logs/sync}）。
     *
     * @param req  请求
     * @param resp 响应
     * @param seg  路径段
     */
    private void logs(HttpServletRequest req, HttpServletResponse resp, String[] seg) {
        if (seg.length != 2 || !"sync".equalsIgnoreCase(seg[1])) {
            notFound(resp, req.getRequestURI());
            return;
        }
        int limit = ParamUtil.intVal(req, "limit", 30, 1, 200);
        Long sourceId = optionalLong(req, "sourceId");

        List<SyncLog> rows;
        if (sourceId == null) {
            rows = dataSourceService.recentLogs(limit);
        } else {
            rows = filterBySource(dataSourceService.recentLogs(200), sourceId.longValue(), limit);
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("items", rows);
        data.put("summary", dataSourceService.monitorSummary());
        ok(resp, data);
    }

    /**
     * 登记一次同步结果（供外部采集任务回调）。
     *
     * @param req  请求
     * @param resp 响应
     */
    private void recordSync(HttpServletRequest req, HttpServletResponse resp) {
        Map<String, Object> body = readMap(req);
        SyncLog log = new SyncLog();
        Object sourceId = body.get("sourceId");
        if (sourceId instanceof Number) {
            log.setSourceId(Long.valueOf(((Number) sourceId).longValue()));
        } else if (sourceId != null) {
            log.setSourceId(Long.valueOf(Long.parseLong(String.valueOf(sourceId))));
        }
        log.setSyncStatus(text(body, "syncStatus", "status"));
        Object count = body.get("recordCount");
        if (count instanceof Number) {
            log.setRecordCount(Integer.valueOf(((Number) count).intValue()));
        }
        Object duration = body.get("durationSeconds");
        if (duration instanceof Number) {
            log.setDurationSeconds(Integer.valueOf(((Number) duration).intValue()));
        }
        log.setErrorMessage(text(body, "errorMessage", "message"));

        long id = dataSourceService.recordSync(log);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("logId", Long.valueOf(id));
        ok(resp, "同步结果已登记", data);
    }

    /* ==================================================================== */
    /*  反馈处理                                                            */
    /* ==================================================================== */

    /**
     * 反馈处理 / 退回。
     *
     * @param req  请求
     * @param resp 响应
     * @param seg  路径段
     */
    private void handleFeedback(HttpServletRequest req, HttpServletResponse resp, String[] seg) {
        if (seg.length != 3) {
            notFound(resp, req.getRequestURI());
            return;
        }
        long feedbackId = ParamUtil.requirePathId(seg[1], "feedbackId");
        String action = seg[2];
        if ("handle".equalsIgnoreCase(action)) {
            long handlerId = requireUserId(req);
            int rows = feedbackService.handle(feedbackId, handlerId);
            ok(resp, rows == 0 ? "该反馈已处理过" : "已标记为已处理", Integer.valueOf(rows));
        } else if ("reopen".equalsIgnoreCase(action)) {
            int rows = feedbackService.reopen(feedbackId);
            ok(resp, rows == 0 ? "该反馈仍处于待处理状态" : "已退回待处理", Integer.valueOf(rows));
        } else {
            notFound(resp, req.getRequestURI());
        }
    }

    /* ==================================================================== */
    /*  内部工具                                                            */
    /* ==================================================================== */

    /**
     * 读取请求体为 Map（空体时抛 400）。
     *
     * @param req 请求
     * @return 字段映射
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(HttpServletRequest req) {
        Map<String, Object> body = JsonUtil.readBody(req, Map.class);
        if (body == null) {
            throw BusinessException.badRequest("请求体不能为空");
        }
        return body;
    }

    /**
     * 尝试读取请求体为 Map（空体时返回 null，不抛异常）。
     *
     * @param req 请求
     * @return 字段映射；解析失败返回 null
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> tryReadMap(HttpServletRequest req) {
        try {
            return JsonUtil.readBody(req, Map.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 从 Map 中按多个候选键取字符串（取第一个非空）。
     *
     * @param body 字段映射
     * @param keys 候选键
     * @return 值；全部缺失返回 null
     */
    private String text(Map<String, Object> body, String... keys) {
        if (body == null) {
            return null;
        }
        for (String key : keys) {
            Object v = body.get(key);
            if (v != null && !String.valueOf(v).trim().isEmpty()) {
                return String.valueOf(v).trim();
            }
        }
        return null;
    }

    /**
     * 按数据源过滤日志并截断。
     *
     * <p>先取一批近期日志再在内存里筛，而不是为每种过滤条件各写一个 DAO 方法：
     * {@code SyncLogDao} 已有 {@code listBySource}，这里做的是"合并查询 + 截断"，
     * 逻辑属于展示层而非数据层。
     *
     * @param all      候选日志
     * @param sourceId 数据源ID
     * @param limit    条数上限
     * @return 过滤结果
     */
    private List<SyncLog> filterBySource(
            List<SyncLog> all, long sourceId, int limit) {
        List<SyncLog> result = new ArrayList<SyncLog>();
        for (SyncLog log : all) {
            if (log.getSourceId() != null && log.getSourceId().longValue() == sourceId) {
                result.add(log);
                if (result.size() >= limit) {
                    break;
                }
            }
        }
        return result;
    }
}
