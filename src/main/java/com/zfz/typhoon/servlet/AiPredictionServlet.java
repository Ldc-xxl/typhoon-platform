package com.zfz.typhoon.servlet;

import com.zfz.typhoon.model.vo.AiPredictionVO;
import com.zfz.typhoon.service.AiPredictionService;
import com.zfz.typhoon.service.impl.AiPredictionServiceImpl;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.JsonUtil;
import com.zfz.typhoon.util.ParamUtil;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * AI 路径智能测算接口。
 *
 * <pre>
 * GET  /api/ai/predictions?typhoonId=13   查询融合测算结果（含各模型明细）
 * GET  /api/ai/predictions?typhoonId=13&models=1   只取模型明细清单
 * POST /api/ai/predictions                触发一次测算并落库  body: {typhoonId, model}
 * DELETE /api/ai/predictions?typhoonId=13 清除测算记录（管理员）
 * </pre>
 *
 * <h3>为什么"查询"与"触发"是同一路径的不同方法</h3>
 * 它们操作的是同一个资源集合（测算结果），语义上就是"读"与"写"的关系，
 * REST 里用方法区分是自然的。前端 {@code api.js} 也正是这样定义的
 * （{@code getAiPredictions} 与 {@code triggerAiPrediction}）。
 *
 * <h3>查询是只读的</h3>
 * GET 只计算不落库，因此刷新页面不会在 {@code ai_prediction_records}
 * 里堆出一串重复记录。需要留痕时走 POST。
 * 这个区分很重要：如果 GET 也写库，一个手贱的 F5 就能让历史记录失去意义。
 *
 * @author 追风者项目组
 */
@WebServlet(name = "aiPredictionServlet", urlPatterns = "/api/ai/predictions")
public class AiPredictionServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    /** 触发测算的请求体 */
    public static class TriggerRequest {
        private Long typhoonId;
        private String model;
        /** 兼容前端可能使用的字段名 */
        private String modelName;

        public Long getTyphoonId() {
            return typhoonId;
        }

        public void setTyphoonId(Long typhoonId) {
            this.typhoonId = typhoonId;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        /**
         * 取模型名（两个字段名取到哪个用哪个）。
         *
         * @return 模型名；都未传返回 null
         */
        public String resolveModel() {
            return ParamUtil.firstNonEmpty(model, modelName);
        }
    }

    private final AiPredictionService aiPredictionService = new AiPredictionServiceImpl();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long typhoonId = ParamUtil.longVal(req, "typhoonId", 0L);
        if (typhoonId <= 0L) {
            // 前端 api.js 在某些调用点用 id 传参，这里一并兼容
            typhoonId = ParamUtil.longVal(req, "id", 0L);
        }
        if (typhoonId <= 0L) {
            throw BusinessException.badRequest("缺少 typhoonId");
        }

        if (ParamUtil.boolVal(req, "models", false)) {
            ok(resp, aiPredictionService.listModels(typhoonId));
            return;
        }

        AiPredictionVO vo = aiPredictionService.latest(typhoonId);
        if (vo == null) {
            throw BusinessException.notFound("该台风暂无 AI 测算结果");
        }
        ok(resp, vo);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        TriggerRequest body = JsonUtil.readBody(req, TriggerRequest.class);
        if (body == null || body.getTyphoonId() == null) {
            throw BusinessException.badRequest("请求体需要包含 typhoonId");
        }
        AiPredictionVO vo = aiPredictionService.trigger(
                body.getTyphoonId().longValue(), body.resolveModel());
        ok(resp, "测算完成", vo);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        requireAdmin(req);
        long typhoonId = ParamUtil.longVal(req, "typhoonId", 0L);
        if (typhoonId <= 0L) {
            throw BusinessException.badRequest("缺少 typhoonId");
        }
        int rows = aiPredictionService.clear(typhoonId);
        ok(resp, "已清除 " + rows + " 条测算记录", Integer.valueOf(rows));
    }
}
