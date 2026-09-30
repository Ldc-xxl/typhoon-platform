package com.zfz.typhoon.servlet;

import com.zfz.typhoon.model.UserBehaviorSurvey;
import com.zfz.typhoon.model.vo.SurveyAggResultVO;
import com.zfz.typhoon.service.SurveyService;
import com.zfz.typhoon.service.impl.SurveyServiceImpl;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.JsonUtil;
import com.zfz.typhoon.util.ParamUtil;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 防灾行为问卷接口。
 *
 * <pre>
 * POST /api/surveys        提交问卷   body: {typhoonId, cityId, q1..q4, contactInfo?}
 * GET  /api/surveys/agg    聚合统计   ?typhoonId=13[&cityId=5]
 * </pre>
 *
 * <h3>问卷题目与字段的对应</h3>
 * 前端问卷是"人话"（「是否储备了应急物资」），数据库是"列名"
 * （{@code prepared_supplies}）。本 Servlet 负责在两者之间做映射，
 * 并同时接受<b>两套命名</b>：
 * <ul>
 *   <li>前端表单常用 {@code q1 / q2 / q3 / q4}（题目顺序稳定、改名不影响逻辑）；</li>
 *   <li>接口设计文档用语义名（{@code preparedSupplies} 等），便于外部调用者理解。</li>
 * </ul>
 * 两套都收，前端不必为了字段名争论。
 *
 * <h3>隐私</h3>
 * 提交接口<b>只回一个计数</b>，不回显填写内容；查询接口只回群体聚合。
 * 联系方式在服务层脱敏后存储，且永远不出现在聚合结果里。
 *
 * @author 追风者项目组
 */
@WebServlet(name = "surveyServlet", urlPatterns = "/api/surveys/*")
public class SurveyServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    /** 提交请求体：同时接受语义名与 q1..q4 两套字段 */
    public static class SurveyRequest {
        private Long typhoonId;
        private Long cityId;

        private Boolean preparedSupplies;
        private Boolean payAttentionEvacuation;
        private String worriedDisasterType;
        private Integer trustLevel;
        private String contactInfo;

        private Boolean q1;
        private Boolean q2;
        private String q3;
        private Integer q4;

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

        public Boolean getPreparedSupplies() {
            return preparedSupplies;
        }

        public void setPreparedSupplies(Boolean preparedSupplies) {
            this.preparedSupplies = preparedSupplies;
        }

        public Boolean getPayAttentionEvacuation() {
            return payAttentionEvacuation;
        }

        public void setPayAttentionEvacuation(Boolean payAttentionEvacuation) {
            this.payAttentionEvacuation = payAttentionEvacuation;
        }

        public String getWorriedDisasterType() {
            return worriedDisasterType;
        }

        public void setWorriedDisasterType(String worriedDisasterType) {
            this.worriedDisasterType = worriedDisasterType;
        }

        public Integer getTrustLevel() {
            return trustLevel;
        }

        public void setTrustLevel(Integer trustLevel) {
            this.trustLevel = trustLevel;
        }

        public String getContactInfo() {
            return contactInfo;
        }

        public void setContactInfo(String contactInfo) {
            this.contactInfo = contactInfo;
        }

        public Boolean getQ1() {
            return q1;
        }

        public void setQ1(Boolean q1) {
            this.q1 = q1;
        }

        public Boolean getQ2() {
            return q2;
        }

        public void setQ2(Boolean q2) {
            this.q2 = q2;
        }

        public String getQ3() {
            return q3;
        }

        public void setQ3(String q3) {
            this.q3 = q3;
        }

        public Integer getQ4() {
            return q4;
        }

        public void setQ4(Integer q4) {
            this.q4 = q4;
        }

        /**
         * 转换为实体，两套字段名按"语义名优先"合并。
         *
         * @return 问卷实体
         */
        public UserBehaviorSurvey toEntity() {
            UserBehaviorSurvey s = new UserBehaviorSurvey();
            s.setTyphoonId(typhoonId);
            s.setCityId(cityId);
            s.setPreparedSupplies(preparedSupplies != null ? preparedSupplies : q1);
            s.setPayAttentionEvacuation(payAttentionEvacuation != null ? payAttentionEvacuation : q2);
            s.setWorriedDisasterType(resolveConcern(worriedDisasterType != null ? worriedDisasterType : q3));
            s.setTrustLevel(trustLevel != null ? trustLevel : q4);
            s.setContactInfo(contactInfo);
            return s;
        }

        /**
         * 规整灾害类型取值：前端下拉传的是中文，数据库存的是英文枚举。
         *
         * @param raw 原始值
         * @return 枚举值；无法识别时原样返回，由 Service 层统一报 400
         */
        private String resolveConcern(String raw) {
            if (raw == null) {
                return null;
            }
            String v = raw.trim();
            if ("大风".equals(v) || "WIND".equalsIgnoreCase(v)) {
                return UserBehaviorSurvey.CONCERN_WIND;
            }
            if ("暴雨".equals(v) || "暴雨内涝".equals(v) || "RAIN".equalsIgnoreCase(v)) {
                return UserBehaviorSurvey.CONCERN_RAIN;
            }
            if ("风暴潮".equals(v) || "STORM_SURGE".equalsIgnoreCase(v)) {
                return UserBehaviorSurvey.CONCERN_STORM_SURGE;
            }
            if ("其他".equals(v) || "OTHER".equalsIgnoreCase(v)) {
                return UserBehaviorSurvey.CONCERN_OTHER;
            }
            return v;
        }
    }

    private final SurveyService surveyService = new SurveyServiceImpl();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String[] seg = pathSegments(req);
        String sub = segment(seg, 0);
        if (sub != null && !"agg".equalsIgnoreCase(sub)) {
            notFound(resp, req.getRequestURI());
            return;
        }

        long typhoonId = ParamUtil.longVal(req, "typhoonId", 0L);
        if (typhoonId <= 0L) {
            throw BusinessException.badRequest("缺少 typhoonId");
        }
        Long cityId = optionalLong(req, "cityId");
        SurveyAggResultVO vo = surveyService.agg(typhoonId, cityId);
        ok(resp, vo);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        SurveyRequest body = JsonUtil.readBody(req, SurveyRequest.class);
        if (body == null) {
            throw BusinessException.badRequest("请求体不能为空");
        }
        long id = surveyService.submit(body.toEntity());

        // 只回计数，不回显填写内容——避免把个人答案回传到前端
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("surveyId", Long.valueOf(id));
        data.put("accepted", Boolean.TRUE);
        data.put("totalSamples", Integer.valueOf(
                surveyService.countSamples(body.getTyphoonId() == null ? 0L : body.getTyphoonId().longValue())));
        ok(resp, "感谢参与，你的回答已计入群体统计", data);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // 问卷数据涉及公众隐私，不提供接口删除；如需清理请在服务端按台风归档时一并处理
        requireAdmin(req);
        methodNotAllowed(req, resp);
    }
}
