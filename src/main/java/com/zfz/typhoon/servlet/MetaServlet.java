package com.zfz.typhoon.servlet;

import com.zfz.typhoon.model.City;
import com.zfz.typhoon.model.vo.AgencyVO;
import com.zfz.typhoon.model.vo.CityVO;
import com.zfz.typhoon.service.CityService;
import com.zfz.typhoon.service.ForecastService;
import com.zfz.typhoon.service.impl.CityServiceImpl;
import com.zfz.typhoon.service.impl.ForecastServiceImpl;
import com.zfz.typhoon.util.ParamUtil;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 基础字典接口：城市与预报机构。
 *
 * <pre>
 * GET /api/cities      城市清单（?keyword=厦 模糊搜索；?province=福建 按省过滤）
 * GET /api/agencies    预报机构清单（含配色，供路径图例使用）
 * </pre>
 *
 * <h3>为什么这两个接口放在同一个类里</h3>
 * 它们是同一类东西：<b>全站共用的、几乎不变的字典数据</b>。
 * 都被地图打点、筛选下拉、图例渲染依赖，调用特征完全一致，
 * 合并后共用同一份缓存策略与同一套参数处理。若将来城市需要支持增删改，
 * 再拆出独立的城市管理接口也不迟——那时它就从"字典"变成"业务实体"了。
 *
 * <h3>机构配色为什么必须由后端给</h3>
 * 前端路径图上 CMA / JMA / JTWC 各是一条不同颜色的线。
 * 如果颜色写在前端，后端新增一个机构（比如 KMA）时前端就会漏画一条线；
 * 颜色来自 {@code forecast_agencies.display_color}，
 * 新增机构只需在数据库里补一行，前端自动就能画出来。
 *
 * @author 追风者项目组
 */
@WebServlet(name = "metaServlet", urlPatterns = {"/api/cities", "/api/agencies"})
public class MetaServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    private final CityService cityService = new CityServiceImpl();
    private final ForecastService forecastService = new ForecastServiceImpl();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getServletPath();
        if ("/api/agencies".equals(path)) {
            List<AgencyVO> rows = forecastService.listAgencies();
            ok(resp, rows);
            return;
        }

        // 城市清单
        String keyword = ParamUtil.str(req, "keyword");
        String province = ParamUtil.str(req, "province");
        boolean onlyEnabled = ParamUtil.boolVal(req, "enabled", true);

        List<CityVO> rows;
        if (keyword != null && !keyword.trim().isEmpty()) {
            rows = cityService.search(keyword.trim());
        } else if (province != null && !province.trim().isEmpty()) {
            List<City> cities = cityService.listByProvince(province.trim());
            rows = new ArrayList<CityVO>(cities.size());
            for (City c : cities) {
                rows.add(CityVO.of(c));
            }
        } else {
            rows = onlyEnabled ? cityService.listEnabled() : cityService.listAll();
        }
        ok(resp, rows);
    }
}
