package com.zfz.typhoon.servlet;

import com.zfz.typhoon.model.vo.RiskAssessmentVO;
import com.zfz.typhoon.service.RiskAssessmentService;
import com.zfz.typhoon.service.impl.RiskAssessmentServiceImpl;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.DateUtil;
import com.zfz.typhoon.util.ParamUtil;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 城市风险预评估接口。
 *
 * <pre>
 * GET  /api/risk/assessments?typhoonId=13            查询风险评估（只算不落库）
 * GET  /api/risk/assessments?typhoonId=13&cityId=5   单城市评估
 * GET  /api/risk/assessments?typhoonId=13&cities=1   只列受影响城市清单
 * GET  /api/risk/assessments?typhoonId=13&obsTime=2026-09-15 08:00:00
 *                                                    以指定实测时次为基准重演
 * POST /api/risk/assessments                         评估并落库  body: {typhoonId, obsTime?}
 * </pre>
 *
 * <h3>obsTime 是什么</h3>
 * 台风的中心位置与风圈都是<b>按观测时刻</b>存的，因此"换一个基准时刻"= 换一对
 * （中心点, 风圈）重新推一遍，所有输入仍是真实观测值。前端「评估基准」下拉的
 * 每一项就是一个 obsTime。不传即取最后实测时刻（与改造前行为完全一致）。
 *
 * <h3>GET 为什么不落库</h3>
 * {@code risk_assessments.user_id} 是非空外键，落库就必然绑定到某个用户。
 * 而风险评估的查询是公开的（未登录也能看），若把 GET 做成"顺手写一条"，
 * 会出现两个问题：一是未登录时无从填写 user_id；二是每次刷新页面都新增一条，
 * 表会迅速膨胀且历史记录失去分析价值。因此落库只发生在显式的 POST。
 *
 * <h3>为什么需要 POST 版本</h3>
 * 因为 {@code risk_assessments} 表存在的意义就是"留档可追溯"：
 * 防灾复盘时要能回答"登陆前 24 小时平台给出的是什么结论"。
 * 这个留档动作应当由用户主动触发（前端按钮），而不是由浏览行为产生。
 *
 * @author 追风者项目组
 */
@WebServlet(name = "riskAssessmentServlet", urlPatterns = "/api/risk/assessments")
public class RiskAssessmentServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    /** 落库请求体 */
    public static class AssessRequest {
        private Long typhoonId;
        private Long cityId;
        /** 基准观测时刻，形如 {@code 2026-09-15 08:00:00}；不传则取最后实测时刻 */
        private String obsTime;

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

        public String getObsTime() {
            return obsTime;
        }

        public void setObsTime(String obsTime) {
            this.obsTime = obsTime;
        }
    }

    private final RiskAssessmentService riskService = new RiskAssessmentServiceImpl();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long typhoonId = resolveTyphoonId(req);
        Long cityId = optionalLong(req, "cityId");
        LocalDateTime obsTime = resolveObsTime(req);

        if (cityId != null) {
            ok(resp, riskService.assessCity(typhoonId, cityId.longValue(), obsTime));
            return;
        }

        List<RiskAssessmentVO> rows = riskService.assess(typhoonId, obsTime);
        ok(resp, rows);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // 评估结果要绑定到具体用户，因此必须登录
        long userId = requireUserId(req);
        AssessRequest body = com.zfz.typhoon.util.JsonUtil.readBody(req, AssessRequest.class);
        long typhoonId;
        if (body != null && body.getTyphoonId() != null) {
            typhoonId = body.getTyphoonId().longValue();
        } else {
            typhoonId = resolveTyphoonId(req);
        }
        // 基准时刻优先取请求体，其次取查询参数——与 typhoonId 的回退顺序保持一致
        String obsText = body != null && body.getObsTime() != null
                ? body.getObsTime() : ParamUtil.str(req, "obsTime");
        LocalDateTime obsTime = obsText == null ? null : DateUtil.parseLenient(obsText);
        List<RiskAssessmentVO> rows = riskService.assessAndSave(typhoonId, userId, obsTime);
        ok(resp, "评估结果已存档", rows);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        requireAdmin(req);
        long typhoonId = resolveTyphoonId(req);
        int rows = riskService.clear(typhoonId);
        ok(resp, "已清除 " + rows + " 条评估记录", Integer.valueOf(rows));
    }

    /**
     * 取台风ID（依次尝试 typhoonId、id，均无则报 400）。
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

    /**
     * 取可选的基准观测时刻 {@code obsTime}。
     *
     * <p>用 {@link DateUtil#parseLenient} 而不是严格版：前端下拉的 value 直接来自
     * 轨迹接口的 {@code time} 字段（{@code yyyy-MM-dd HH:mm:ss}），但手工调试时
     * 少写秒数（{@code yyyy-MM-dd HH:mm}）是很自然的事，为这个报 400 不划算。
     *
     * @param req 请求
     * @return 解析后的时刻；未传返回 null（表示以最后实测时刻为基准）
     */
    private LocalDateTime resolveObsTime(HttpServletRequest req) {
        String text = ParamUtil.str(req, "obsTime");
        return text == null ? null : DateUtil.parseLenient(text);
    }
}
