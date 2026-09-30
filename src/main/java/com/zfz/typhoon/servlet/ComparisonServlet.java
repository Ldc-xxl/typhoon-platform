package com.zfz.typhoon.servlet;

import com.zfz.typhoon.model.HistoricalComparison;
import com.zfz.typhoon.service.ComparisonService;
import com.zfz.typhoon.service.impl.ComparisonServiceImpl;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.JsonUtil;
import com.zfz.typhoon.util.ParamUtil;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 历史台风对比接口（全部需要登录，任务归属创建者）。
 *
 * <pre>
 * GET    /api/comparisons                 我的对比任务
 * GET    /api/comparisons/{id}            任务详情（含结论文档）
 * POST   /api/comparisons                 创建对比  body: {name?, primaryTyphoonId, compareTyphoonIds:[...]}
 * PUT    /api/comparisons/{id}            重命名    body: {name}
 * DELETE /api/comparisons/{id}            删除
 * </pre>
 *
 * <h3>创建即出结论</h3>
 * POST 会<b>同步</b>算完对比结论再返回，而不是建个"待计算"任务让前端轮询。
 * 理由是这个计算量对课程数据规模（单次最多 6 个台风、每条轨迹几十个点）
 * 只需毫秒级；引入异步任务队列需要额外的状态机、轮询接口与超时处理，
 * 复杂度远超收益。若将来台风轨迹点涨到上万个，再改成异步也不影响接口形态——
 * 届时 POST 返回的 {@code resultSummary} 先给空值、由轮询补齐即可。
 *
 * <h3>重命名用 PUT 而不是 PATCH</h3>
 * 虽然只改一个字段，但请求体里只有 {@code name}，
 * 服务端的语义是"把名称设为这个值"，属于幂等的全量替换，用 PUT 语义正确。
 *
 * @author 追风者项目组
 */
@WebServlet(name = "comparisonServlet", urlPatterns = "/api/comparisons/*")
public class ComparisonServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    /** 创建 / 重命名请求体 */
    public static class ComparisonRequest {
        private String name;
        private String comparisonName;
        private Long primaryTyphoonId;
        private List<Long> compareTyphoonIds;
        /** 兼容前端可能使用的单数形式 */
        private Long compareTyphoonId;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getComparisonName() {
            return comparisonName;
        }

        public void setComparisonName(String comparisonName) {
            this.comparisonName = comparisonName;
        }

        public Long getPrimaryTyphoonId() {
            return primaryTyphoonId;
        }

        public void setPrimaryTyphoonId(Long primaryTyphoonId) {
            this.primaryTyphoonId = primaryTyphoonId;
        }

        public List<Long> getCompareTyphoonIds() {
            return compareTyphoonIds;
        }

        public void setCompareTyphoonIds(List<Long> compareTyphoonIds) {
            this.compareTyphoonIds = compareTyphoonIds;
        }

        public Long getCompareTyphoonId() {
            return compareTyphoonId;
        }

        public void setCompareTyphoonId(Long compareTyphoonId) {
            this.compareTyphoonId = compareTyphoonId;
        }

        /**
         * 规整对比台风ID列表（同时接受数组与单值两种传法）。
         *
         * @return ID 列表（可能为空）
         */
        public List<Long> resolveCompareIds() {
            List<Long> ids = new ArrayList<Long>();
            if (compareTyphoonIds != null) {
                for (Long id : compareTyphoonIds) {
                    if (id != null) {
                        ids.add(id);
                    }
                }
            }
            if (ids.isEmpty() && compareTyphoonId != null) {
                ids.add(compareTyphoonId);
            }
            return ids;
        }

        /**
         * 取名称（两个字段名都兼容）。
         *
         * @return 名称；都未传返回 null
         */
        public String resolveName() {
            return ParamUtil.firstNonEmpty(name, comparisonName);
        }
    }

    private final ComparisonService comparisonService = new ComparisonServiceImpl();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long userId = requireUserId(req);
        String[] seg = pathSegments(req);
        if (seg.length == 0) {
            ok(resp, comparisonService.list(userId));
            return;
        }
        if (seg.length == 1) {
            long comparisonId = ParamUtil.requirePathId(seg[0], "comparisonId");
            ok(resp, comparisonService.get(comparisonId, userId));
            return;
        }
        notFound(resp, req.getRequestURI());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long userId = requireUserId(req);
        if (pathSegments(req).length != 0) {
            notFound(resp, req.getRequestURI());
            return;
        }
        ComparisonRequest body = JsonUtil.readBody(req, ComparisonRequest.class);
        if (body == null || body.getPrimaryTyphoonId() == null) {
            throw BusinessException.badRequest("请求体需要包含 primaryTyphoonId");
        }
        HistoricalComparison result = comparisonService.create(userId, body.resolveName(),
                body.getPrimaryTyphoonId().longValue(), body.resolveCompareIds());
        ok(resp, "对比已完成", result);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long userId = requireUserId(req);
        String[] seg = pathSegments(req);
        if (seg.length != 1) {
            notFound(resp, req.getRequestURI());
            return;
        }
        long comparisonId = ParamUtil.requirePathId(seg[0], "comparisonId");
        ComparisonRequest body = JsonUtil.readBody(req, ComparisonRequest.class);
        if (body == null) {
            throw BusinessException.badRequest("请求体不能为空");
        }
        int rows = comparisonService.rename(comparisonId, body.resolveName(), userId);
        ok(resp, "对比名称已更新", Integer.valueOf(rows));
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long userId = requireUserId(req);
        String[] seg = pathSegments(req);
        if (seg.length != 1) {
            notFound(resp, req.getRequestURI());
            return;
        }
        long comparisonId = ParamUtil.requirePathId(seg[0], "comparisonId");
        int rows = comparisonService.delete(comparisonId, userId);
        ok(resp, "对比任务已删除", Integer.valueOf(rows));
    }
}
