package com.zfz.typhoon.servlet;

import com.zfz.typhoon.model.vo.CityVO;
import com.zfz.typhoon.model.vo.TyphoonVO;
import com.zfz.typhoon.service.FavoriteService;
import com.zfz.typhoon.service.SubscriptionService;
import com.zfz.typhoon.service.impl.FavoriteServiceImpl;
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
 * 收藏接口（全部需要登录）。
 *
 * <pre>
 * GET    /api/favorites                     收藏总览（台风 + 城市 + 总数）
 * GET    /api/favorites/check?typhoonId=13  判断是否已收藏（详情页心形状态）
 * POST   /api/favorites                     新增收藏  body: {typhoonId} 或 {cityId}
 * DELETE /api/favorites/typhoon/{id}        取消收藏台风
 * DELETE /api/favorites/city/{id}           取消收藏城市
 * </pre>
 *
 * <h3>新增收藏同时写订阅</h3>
 * 收藏一个台风意味着"我关注它"，而关注的自然延伸就是"它有变化时告诉我"。
 * 因此 {@link #addFavorite} 会顺带建立一条订阅（保持订阅偏好默认全开）。
 * 这不是隐式副作用，而是产品语义：前端只有一个"关注"按钮，
 * 用户不需要理解"收藏"与"订阅"在数据库里是两张表。
 *
 * <p>反过来，取消收藏<b>不</b>自动取消订阅——用户可能只是清理收藏列表，
 * 但依然希望收到预警。这两件事的"撤销"方向必须不对称，否则会误关预警。
 *
 * @author 追风者项目组
 */
@WebServlet(name = "favoriteServlet", urlPatterns = "/api/favorites/*")
public class FavoriteServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    /** 收藏请求体 */
    public static class FavoriteRequest {
        private Long typhoonId;
        private Long cityId;
        /** 是否同时建立订阅，默认 true */
        private Boolean subscribe;

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

        public Boolean getSubscribe() {
            return subscribe;
        }

        public void setSubscribe(Boolean subscribe) {
            this.subscribe = subscribe;
        }
    }

    private final FavoriteService favoriteService = new FavoriteServiceImpl();
    private final SubscriptionService subscriptionService = new SubscriptionServiceImpl();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long userId = requireUserId(req);
        String[] seg = pathSegments(req);
        String sub = segment(seg, 0);

        if ("check".equalsIgnoreCase(sub)) {
            check(req, resp, userId);
            return;
        }
        if (sub != null) {
            notFound(resp, req.getRequestURI());
            return;
        }

        List<TyphoonVO> typhoons = favoriteService.listTyphoons(userId);
        List<CityVO> cities = favoriteService.listCities(userId);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("typhoons", typhoons);
        data.put("cities", cities);
        data.put("total", Integer.valueOf(favoriteService.count(userId)));
        ok(resp, data);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long userId = requireUserId(req);
        FavoriteRequest body = JsonUtil.readBody(req, FavoriteRequest.class);
        if (body == null || (body.getTyphoonId() == null && body.getCityId() == null)) {
            throw BusinessException.badRequest("请求体需要提供 typhoonId 或 cityId");
        }

        long favoriteId;
        if (body.getTyphoonId() != null) {
            long typhoonId = body.getTyphoonId().longValue();
            favoriteId = favoriteService.favoriteTyphoon(userId, typhoonId);
            if (!Boolean.FALSE.equals(body.getSubscribe())) {
                subscriptionService.toggleTyphoon(userId, typhoonId, true);
            }
        } else {
            favoriteId = favoriteService.favoriteCity(userId, body.getCityId().longValue());
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("favoriteId", Long.valueOf(favoriteId));
        data.put("total", Integer.valueOf(favoriteService.count(userId)));
        ok(resp, "已加入收藏", data);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long userId = requireUserId(req);
        String[] seg = pathSegments(req);
        if (seg.length != 2) {
            notFound(resp, req.getRequestURI());
            return;
        }
        long id = ParamUtil.requirePathId(seg[1], "id");

        int rows;
        if ("typhoon".equalsIgnoreCase(seg[0])) {
            rows = favoriteService.unfavoriteTyphoon(userId, id);
        } else if ("city".equalsIgnoreCase(seg[0])) {
            rows = favoriteService.unfavoriteCity(userId, id);
        } else {
            notFound(resp, req.getRequestURI());
            return;
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("removed", Integer.valueOf(rows));
        data.put("total", Integer.valueOf(favoriteService.count(userId)));
        // 说明：取消收藏不会连带取消订阅，避免误关台风预警
        ok(resp, "已取消收藏", data);
    }

    /**
     * 查询收藏状态。
     *
     * @param req    请求
     * @param resp   响应
     * @param userId 用户ID
     */
    private void check(HttpServletRequest req, HttpServletResponse resp, long userId) {
        Long typhoonId = optionalLong(req, "typhoonId");
        Long cityId = optionalLong(req, "cityId");
        if (typhoonId == null && cityId == null) {
            throw BusinessException.badRequest("需要提供 typhoonId 或 cityId");
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        if (typhoonId != null) {
            data.put("favorited", Boolean.valueOf(favoriteService.isTyphoonFavorited(userId, typhoonId.longValue())));
        }
        if (cityId != null) {
            data.put("cityFavorited", Boolean.valueOf(favoriteService.isCityFavorited(userId, cityId.longValue())));
        }
        ok(resp, data);
    }
}
