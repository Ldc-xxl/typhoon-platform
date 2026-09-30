package com.zfz.typhoon.servlet;

import com.zfz.typhoon.dao.query.TyphoonQuery;
import com.zfz.typhoon.model.DisasterReport;
import com.zfz.typhoon.model.Typhoon;
import com.zfz.typhoon.model.vo.AgencyVO;
import com.zfz.typhoon.model.vo.CityVO;
import com.zfz.typhoon.model.vo.DisagreementVO;
import com.zfz.typhoon.model.vo.ForecastPathVO;
import com.zfz.typhoon.model.vo.ReplayVO;
import com.zfz.typhoon.model.vo.TrackVO;
import com.zfz.typhoon.model.vo.TyphoonVO;
import com.zfz.typhoon.model.vo.WindCircleVO;
import com.zfz.typhoon.service.CityService;
import com.zfz.typhoon.service.DisagreementService;
import com.zfz.typhoon.service.ForecastService;
import com.zfz.typhoon.service.TrackService;
import com.zfz.typhoon.service.TyphoonService;
import com.zfz.typhoon.service.WindCircleService;
import com.zfz.typhoon.service.impl.CityServiceImpl;
import com.zfz.typhoon.service.impl.DisagreementServiceImpl;
import com.zfz.typhoon.service.impl.ForecastServiceImpl;
import com.zfz.typhoon.service.impl.TrackServiceImpl;
import com.zfz.typhoon.service.impl.TyphoonServiceImpl;
import com.zfz.typhoon.service.impl.WindCircleServiceImpl;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.DateUtil;
import com.zfz.typhoon.util.JsonUtil;
import com.zfz.typhoon.util.ParamUtil;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 台风档案与实况接口（含各类子资源）。
 *
 * <pre>
 * GET    /api/typhoons                       列表（关键词 / 年份 / 海域 / 状态 / 排序 / 分页）
 * GET    /api/typhoons/{id}                  单台风详情
 * GET    /api/typhoons/{id}/tracks           实况轨迹点
 * GET    /api/typhoons/{id}/wind-circles     7/10/12 级风圈
 * GET    /api/typhoons/{id}/forecast-paths   多机构预报路径
 * GET    /api/typhoons/{id}/disagreement     多机构预报分歧解析
 * GET    /api/typhoons/{id}/replay           轨迹回放帧
 * GET    /api/typhoons/{id}/disaster         灾情报告
 * GET    /api/typhoons/meta                  筛选元数据（年份 / 海域 / 机构 / 城市）
 * POST   /api/typhoons                       新增台风（管理员）
 * PUT    /api/typhoons/{id}                  修改台风（管理员）
 * DELETE /api/typhoons/{id}                  删除台风（管理员）
 * </pre>
 *
 * <h3>为什么一个 Servlet 管这么多 URL</h3>
 * 这些接口共享<b>同一份路径解析逻辑</b>（都是 {@code /{id}/子资源}）。
 * 拆成 6 个类意味着同一段 {@code pathInfo} 解析代码抄 6 遍，
 * 而且 {@code /api/typhoons/*} 在 Servlet 里本就是同一个通配符映射，
 * 拆开反而会互相抢映射。因此按<b>资源族</b>而不是按<b>单个 URL</b>划分 Servlet。
 *
 * <h3>{@code meta} 与 {@code {id}} 的冲突处理</h3>
 * {@code /api/typhoons/meta} 与 {@code /api/typhoons/{id}} 在通配符映射下无法靠
 * 容器区分，两者都会落到本类。处理方式是：先判断首段是否为纯数字，
 * 不是数字就按具名子资源处理，是数字才解析为台风ID。
 * 这样"ID 必须为数字"这一约束同时也是路由规则，不需要额外维护映射表。
 *
 * <h3>分页总数为什么走响应头</h3>
 * 列表接口的 {@code data} 保持为<b>纯数组</b>（与前端 {@code data.js} 的 mock 形态一致，
 * 页面可直接 {@code map} 渲染）；总数放在 {@code X-Total-Count} 响应头里。
 * 这是 REST 里分页的常见做法，好处是列表结构不变、前端已有代码不用改；
 * 若把总数塞进 data 变成 {@code {items:[...], total:n}}，所有直接遍历 data 的地方都要改。
 *
 * @author 追风者项目组
 */
@WebServlet(name = "typhoonServlet", urlPatterns = "/api/typhoons/*")
public class TyphoonServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    /** 列表默认每页条数 */
    private static final int DEFAULT_LIMIT = 50;

    /** 每页最大条数，防止前端传个大数把整表拉出来 */
    private static final int MAX_LIMIT = 500;

    private final TyphoonService typhoonService = new TyphoonServiceImpl();
    private final TrackService trackService = new TrackServiceImpl();
    private final WindCircleService windCircleService = new WindCircleServiceImpl();
    private final ForecastService forecastService = new ForecastServiceImpl();
    private final DisagreementService disagreementService = new DisagreementServiceImpl();
    private final CityService cityService = new CityServiceImpl();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String[] seg = pathSegments(req);

        if (seg.length == 0) {
            list(req, resp);
            return;
        }

        String first = seg[0];
        if (!isNumeric(first)) {
            if ("meta".equalsIgnoreCase(first) && seg.length == 1) {
                meta(resp);
                return;
            }
            notFound(resp, req.getRequestURI());
            return;
        }

        long typhoonId = ParamUtil.requirePathId(first, "typhoonId");

        if (seg.length == 1) {
            ok(resp, typhoonService.findById(typhoonId));
            return;
        }
        if (seg.length > 2) {
            notFound(resp, req.getRequestURI());
            return;
        }

        String sub = seg[1];
        if ("tracks".equalsIgnoreCase(sub)) {
            List<TrackVO> rows = trackService.listByTyphoon(typhoonId);
            ok(resp, rows);
        } else if ("wind-circles".equalsIgnoreCase(sub)) {
            List<WindCircleVO> rows = windCircleService.listByTyphoon(typhoonId);
            ok(resp, rows);
        } else if ("forecast-paths".equalsIgnoreCase(sub)) {
            List<ForecastPathVO> rows = forecastService.listPaths(typhoonId);
            ok(resp, rows);
        } else if ("disagreement".equalsIgnoreCase(sub)) {
            disagreement(typhoonId, resp);
        } else if ("replay".equalsIgnoreCase(sub)) {
            int step = ParamUtil.intVal(req, "stepHours", 1, 1, 24);
            ReplayVO vo = trackService.replay(typhoonId, step);
            ok(resp, vo);
        } else if ("disaster".equalsIgnoreCase(sub)) {
            // 灾情报告是可选的，没有不是错误：回 null 让前端展示"暂无灾情记录"
            DisasterReport report = typhoonService.disaster(typhoonId);
            ok(resp, report);
        } else {
            notFound(resp, req.getRequestURI());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        requireAdmin(req);
        if (pathSegments(req).length != 0) {
            notFound(resp, req.getRequestURI());
            return;
        }
        Typhoon body = JsonUtil.readBody(req, Typhoon.class);
        if (body == null) {
            throw BusinessException.badRequest("请求体不能为空");
        }
        long id = typhoonService.create(body);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("typhoonId", Long.valueOf(id));
        ok(resp, "台风档案已创建", data);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        requireAdmin(req);
        String[] seg = pathSegments(req);
        if (seg.length != 1) {
            notFound(resp, req.getRequestURI());
            return;
        }
        long typhoonId = ParamUtil.requirePathId(seg[0], "typhoonId");
        Typhoon body = JsonUtil.readBody(req, Typhoon.class);
        if (body == null) {
            throw BusinessException.badRequest("请求体不能为空");
        }
        body.setTyphoonId(Long.valueOf(typhoonId));
        int rows = typhoonService.update(body);
        ok(resp, "已更新 " + rows + " 条记录", Integer.valueOf(rows));
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        requireAdmin(req);
        String[] seg = pathSegments(req);
        if (seg.length != 1) {
            notFound(resp, req.getRequestURI());
            return;
        }
        long typhoonId = ParamUtil.requirePathId(seg[0], "typhoonId");
        int rows = typhoonService.delete(typhoonId);
        ok(resp, "已删除台风档案", Integer.valueOf(rows));
    }

    /* ==================================================================== */
    /*  分支实现                                                            */
    /* ==================================================================== */

    /**
     * 台风列表（多条件筛选 + 分页）。
     *
     * @param req  请求
     * @param resp 响应
     */
    private void list(HttpServletRequest req, HttpServletResponse resp) {
        TyphoonQuery query = new TyphoonQuery();
        String keyword = ParamUtil.str(req, "keyword");
        if (keyword == null || keyword.trim().isEmpty()) {
            // 兼容档案页使用的 name 参数名
            keyword = ParamUtil.str(req, "name");
        }
        query.setKeyword(keyword);
        query.setBasin(ParamUtil.str(req, "basin"));
        query.setStatus(normalizeStatus(ParamUtil.str(req, "status")));
        query.setSort(ParamUtil.str(req, "sort"));
        query.setYear(parseOptionalInt(ParamUtil.str(req, "year"), "year"));
        query.setMinWindForce(parseOptionalInt(ParamUtil.str(req, "minWindForce"), "minWindForce"));

        String archived = ParamUtil.str(req, "archived");
        if (archived != null && !archived.trim().isEmpty()) {
            query.setArchived(Boolean.valueOf("true".equalsIgnoreCase(archived.trim())
                    || "1".equals(archived.trim())));
        }
        applyRange(req, "generateFrom", query, true);
        applyRange(req, "generateTo", query, false);

        int limit = ParamUtil.intVal(req, "limit", DEFAULT_LIMIT, 0, MAX_LIMIT);

        // 分页同时支持两种写法，避免调用方猜错参数名却"看起来成功"：
        //   ?page=2&limit=10   → offset = (page-1) * limit
        //   ?offset=10&limit=10 → 直接使用 offset
        // 只认 offset 时，前端若按常见的 page 语义传参，会每次都拿到第一页，
        // 而接口并不报错——这种"静默失效"比 400 更难发现。
        Integer page = parseOptionalInt(ParamUtil.str(req, "page"), "page");
        int offset;
        if (page != null && page.intValue() > 0) {
            offset = (page.intValue() - 1) * limit;
        } else {
            offset = ParamUtil.intVal(req, "offset", 0, 0, Integer.MAX_VALUE);
        }
        query.page(limit, offset);

        List<TyphoonVO> rows = typhoonService.list(query);
        // 总数放响应头，data 保持纯数组
        resp.setHeader("X-Total-Count", String.valueOf(typhoonService.count(query)));
        ok(resp, rows);
    }

    /**
     * 预报分歧解析（只算不落库的只读接口）。
     *
     * @param typhoonId 台风ID
     * @param resp      响应
     */
    private void disagreement(long typhoonId, HttpServletResponse resp) {
        DisagreementVO vo = disagreementService.analyze(typhoonId);
        if (vo == null) {
            throw BusinessException.notFound("该台风暂无可解析的多机构预报数据");
        }
        ok(resp, vo);
    }

    /**
     * 筛选元数据：年份 / 海域 / 机构 / 城市 / 状态码。
     *
     * <p>把下拉框的选项集中在一个接口里，前端筛选栏只发一次请求。
     * 拆成 4 个接口看起来更"RESTful"，但会让页面打开时的请求数翻 4 倍，
     * 而这些都是极小且极少变化的字典数据。
     *
     * @param resp 响应
     */
    private void meta(HttpServletResponse resp) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("years", typhoonService.listYears());
        data.put("basins", typhoonService.listBasins());
        List<AgencyVO> agencies = forecastService.listAgencies();
        data.put("agencies", agencies);
        List<CityVO> cities = cityService.listEnabled();
        data.put("cities", cities);
        data.put("statuses", Arrays.asList("ACTIVE", "ARCHIVED"));
        ok(resp, data);
    }

    /* ==================================================================== */
    /*  内部工具                                                            */
    /* ==================================================================== */

    /**
     * 判断字符串是否为纯数字（用于区分 {@code meta} 与 {@code {id}}）。
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

    /**
     * 状态参数规整：前端用 ACTIVE / ARCHIVED，数据库用 ACTIVE / FINISHED。
     *
     * <p>转换放在边界上，让数据库保持自己的命名（FINISHED 指"已结束"），
     * 前端保持自己的命名（ARCHIVED 指"已归档"），各说各话但接口通畅。
     *
     * @param status 原始状态
     * @return 数据库可识别的状态；{@code ALL} 或空返回 null（即不筛选）
     */
    private String normalizeStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return null;
        }
        String s = status.trim().toUpperCase();
        if ("ARCHIVED".equals(s)) {
            return "FINISHED";
        }
        if ("ALL".equals(s)) {
            return null;
        }
        if ("ACTIVE".equals(s) || "FINISHED".equals(s)) {
            return s;
        }
        throw BusinessException.badRequest("status 取值非法，应为 ACTIVE / ARCHIVED / FINISHED / ALL");
    }

    /**
     * 解析可选整数参数。
     *
     * @param raw  原始文本
     * @param name 参数名
     * @return 数值；未传返回 null
     */
    private Integer parseOptionalInt(String raw, String name) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            throw BusinessException.badRequest(name + " 必须为整数：" + raw);
        }
    }

    /**
     * 把时间区间参数写入查询条件。
     *
     * @param req   请求
     * @param name  参数名
     * @param query 查询条件
     * @param isFrom true=下界 / false=上界
     */
    private void applyRange(HttpServletRequest req, String name, TyphoonQuery query, boolean isFrom) {
        String raw = ParamUtil.str(req, name);
        if (raw == null || raw.trim().isEmpty()) {
            return;
        }
        LocalDateTime time;
        try {
            time = DateUtil.parseLenient(raw.trim());
        } catch (RuntimeException e) {
            time = null;
        }
        if (time == null) {
            throw BusinessException.badRequest(name + " 时间格式应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss");
        }
        if (isFrom) {
            query.setGenerateFrom(time);
        } else {
            query.setGenerateTo(time);
        }
    }
}
