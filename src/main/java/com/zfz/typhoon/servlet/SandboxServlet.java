package com.zfz.typhoon.servlet;

import com.zfz.typhoon.model.dto.SandboxRequest;
import com.zfz.typhoon.model.vo.SandboxScenarioVO;
import com.zfz.typhoon.service.SandboxService;
import com.zfz.typhoon.service.impl.SandboxServiceImpl;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.JsonUtil;
import com.zfz.typhoon.util.ParamUtil;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 情景推演沙盘接口。
 *
 * <pre>
 * GET    /api/sandbox/scenarios              情景库列表
 * GET    /api/sandbox/scenarios/params       四项参数的可调范围
 * GET    /api/sandbox/scenarios/{id}         单个情景详情
 * POST   /api/sandbox/scenarios              推演（不落库）
 * POST   /api/sandbox/scenarios?save=1       推演并保存
 * PUT    /api/sandbox/scenarios/{id}/share   公开 / 取消公开（管理员）
 * DELETE /api/sandbox/scenarios/{id}         删除
 * </pre>
 *
 * <h3>为什么"推演"与"保存"是同一个 POST 的两个模式</h3>
 * 沙盘的交互是"拖参数 → 看结果 → 满意了再保存"。
 * 拖动过程中会产生大量中间态，如果每次都落库，情景库里会塞满几十条
 * 只差一个小数点的垃圾记录，真正有价值的方案反而找不到。
 * 因此用 {@code save} 参数显式区分：
 * <ul>
 *   <li>不传或 {@code save=0}：只算结果，前端实时渲染；</li>
 *   <li>{@code save=1}：算完再写一条 {@code sandbox_scenarios}。</li>
 * </ul>
 * 这样"浏览器里随便拖"和"服务端留下方案"两件事就不会互相污染。
 *
 * <h3>{@code params} 与 {@code {id}} 的路由</h3>
 * 与台风接口同一套办法：首段非数字即视为具名子资源。
 * {@code paramRange()} 的可调范围来自 {@code system_settings.sandbox_param_range}，
 * 因此管理端改配置后前端滑块范围会立刻跟着变，而不需要改前端代码。
 *
 * @author 追风者项目组
 */
@WebServlet(name = "sandboxServlet", urlPatterns = "/api/sandbox/scenarios/*")
public class SandboxServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    private final SandboxService sandboxService = new SandboxServiceImpl();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String[] seg = pathSegments(req);

        if (seg.length == 0) {
            // 情景库列表：默认只列公开情景；带 mine=1 时列自己的（需登录）
            boolean mine = ParamUtil.boolVal(req, "mine", false);
            Long userId = mine ? Long.valueOf(requireUserId(req)) : optionalLong(req, "userId");
            Long baseTyphoonId = optionalLong(req, "baseTyphoonId");
            int limit = ParamUtil.intVal(req, "limit", 20, 1, 100);
            List<SandboxScenarioVO> rows = sandboxService.list(userId, baseTyphoonId, limit);
            ok(resp, rows);
            return;
        }

        if (!isNumeric(seg[0])) {
            if ("params".equalsIgnoreCase(seg[0]) && seg.length == 1) {
                Map<String, Object> range = sandboxService.paramRange();
                ok(resp, range);
                return;
            }
            notFound(resp, req.getRequestURI());
            return;
        }

        long scenarioId = ParamUtil.requirePathId(seg[0], "scenarioId");
        if (seg.length != 1) {
            notFound(resp, req.getRequestURI());
            return;
        }
        ok(resp, sandboxService.getById(scenarioId));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        SandboxRequest body = JsonUtil.readBody(req, SandboxRequest.class);
        if (body == null) {
            throw BusinessException.badRequest("请求体不能为空，至少需要 typhoonId");
        }
        boolean save = ParamUtil.boolVal(req, "save", false);
        if (save) {
            long userId = requireUserId(req);
            ok(resp, "情景已保存", sandboxService.save(userId, body));
        } else {
            ok(resp, sandboxService.simulate(body));
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        requireAdmin(req);
        String[] seg = pathSegments(req);
        if (seg.length != 2 || !"share".equalsIgnoreCase(seg[1])) {
            notFound(resp, req.getRequestURI());
            return;
        }
        long scenarioId = ParamUtil.requirePathId(seg[0], "scenarioId");
        boolean shared = ParamUtil.boolVal(req, "shared", false);
        int rows = sandboxService.setShared(scenarioId, shared);
        ok(resp, shared ? "情景已公开" : "情景已取消公开", Integer.valueOf(rows));
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String[] seg = pathSegments(req);
        if (seg.length != 1) {
            notFound(resp, req.getRequestURI());
            return;
        }
        long scenarioId = ParamUtil.requirePathId(seg[0], "scenarioId");
        long userId = requireUserId(req);
        int rows = sandboxService.delete(scenarioId, userId);
        ok(resp, "已删除情景", Integer.valueOf(rows));
    }

    /**
     * 判断字符串是否为纯数字。
     *
     * @param s 字符串
     * @return 纯数字返回 true
     */
    private boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
