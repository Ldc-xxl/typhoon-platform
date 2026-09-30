package com.zfz.typhoon.servlet;

import com.zfz.typhoon.model.vo.SimilarCaseVO;
import com.zfz.typhoon.service.SimilarCaseService;
import com.zfz.typhoon.service.impl.SimilarCaseServiceImpl;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.JsonUtil;
import com.zfz.typhoon.util.ParamUtil;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * 历史相似台风案例接口。
 *
 * <pre>
 * GET  /api/similar-cases?typhoonId=13            查询 Top-N 相似案例（只算不落库）
 * GET  /api/similar-cases?typhoonId=13&topN=3     指定条数
 * GET  /api/similar-cases?currentId=13&historyId=8 两个台风之间的五维打分
 * POST /api/similar-cases                         计算并保存匹配结果  body: {typhoonId}
 * </pre>
 *
 * <h3>查询不落库的理由</h3>
 * 相似度打分的输入是"当前台风的轨迹 + 全部历史台风的轨迹"，
 * 而当前台风的轨迹<b>每隔几小时就会新增一个点</b>，分数因此一直在变。
 * 如果 GET 顺手把结果写进 {@code similar_case_matches}，
 * 半小时后表里就同时存在"上一版分数"和"这一版分数"，
 * 前端读哪一条都说不清。因此落库由 POST 显式触发，
 * 语义是"以此刻的数据为准，存档一份用于报告引用"。
 *
 * @author 追风者项目组
 */
@WebServlet(name = "similarCaseServlet", urlPatterns = "/api/similar-cases")
public class SimilarCaseServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    /** 单对打分的请求体 */
    public static class ScoreRequest {
        private Long currentId;
        private Long historyId;
        private Long typhoonId;

        public Long getCurrentId() {
            return currentId;
        }

        public void setCurrentId(Long currentId) {
            this.currentId = currentId;
        }

        public Long getHistoryId() {
            return historyId;
        }

        public void setHistoryId(Long historyId) {
            this.historyId = historyId;
        }

        public Long getTyphoonId() {
            return typhoonId;
        }

        public void setTyphoonId(Long typhoonId) {
            this.typhoonId = typhoonId;
        }
    }

    /** 默认返回条数 */
    private static final int DEFAULT_TOP_N = 5;

    private final SimilarCaseService similarCaseService = new SimilarCaseServiceImpl();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Long currentId = optionalLong(req, "currentId");
        Long historyId = optionalLong(req, "historyId");
        if (currentId != null && historyId != null) {
            ok(resp, similarCaseService.score(currentId.longValue(), historyId.longValue()));
            return;
        }

        long typhoonId = resolveTyphoonId(req);
        int topN = ParamUtil.intVal(req, "topN", DEFAULT_TOP_N, 1, 20);
        List<SimilarCaseVO> rows = similarCaseService.findSimilar(typhoonId, topN);
        ok(resp, rows);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ScoreRequest body = JsonUtil.readBody(req, ScoreRequest.class);
        long typhoonId;
        if (body != null && body.getTyphoonId() != null) {
            typhoonId = body.getTyphoonId().longValue();
        } else {
            typhoonId = resolveTyphoonId(req);
        }
        int rows = similarCaseService.saveMatches(typhoonId);
        ok(resp, "已保存 " + rows + " 条相似案例匹配结果", Integer.valueOf(rows));
    }

    /**
     * 取台风ID（依次尝试 typhoonId、id）。
     *
     * @param req 请求
     * @return 台风ID
     */
    private long resolveTyphoonId(HttpServletRequest req) {
        long id = ParamUtil.longVal(req, "typhoonId", 0L);
        if (id <= 0L) {
            id = ParamUtil.longVal(req, "id", 0L);
        }
        if (id <= 0L) {
            throw BusinessException.badRequest("缺少 typhoonId");
        }
        return id;
    }
}
