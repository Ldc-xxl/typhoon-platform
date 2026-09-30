package com.zfz.typhoon.service;

import com.zfz.typhoon.model.WindCircle;
import com.zfz.typhoon.model.vo.WindCircleVO;

import java.util.List;

/**
 * 风圈业务接口。
 *
 * <p>对应接口文档：{@code GET /api/typhoons/{id}/wind-circles}。
 * 前端 P1 总览页的 {@code scaleWind} 与 P2 详情页的风圈图层都消费本接口。
 *
 * <h3>为什么风圈要提供"整组"而不是单个级别</h3>
 * 风圈的绘制语义是<b>同心的三层</b>（7 级在外、10 级居中、12 级在内），
 * 三者必须在同一时刻、同一中心点上才能叠出正确图形。
 * 因此接口一次返回当前时刻的全部有效级别，避免前端分别请求后
 * 出现"7 级用 08:00 的中心、12 级用 14:00 的中心"这类错位。
 *
 * <h3>颜色归属</h3>
 * 风圈配色属于<b>视觉契约</b>（与设计稿一致，且需在全站统一），
 * 由服务端随数据下发，前端不硬编码。映射见 {@link #LEVEL_COLOR_7} 等常量。
 *
 * @author 追风者项目组
 */
public interface WindCircleService {

    /** 7 级风圈色（外圈 · 暖黄） */
    String LEVEL_COLOR_7 = "#FFD166";

    /** 10 级风圈色（中圈 · 橙） */
    String LEVEL_COLOR_10 = "#FF9F43";

    /** 12 级风圈色（内圈 · 红，与"危险"语义一致） */
    String LEVEL_COLOR_12 = "#E8453C";

    /**
     * 查询台风当前时刻的风圈（转换为前端视图，含配色）。
     *
     * @param typhoonId 台风ID
     * @return 风圈视图列表（按级别升序：7 → 10 → 12）；无数据返回空列表
     */
    List<WindCircleVO> listByTyphoon(long typhoonId);

    /**
     * 查询台风某观测时刻的风圈。
     *
     * @param typhoonId 台风ID
     * @param obsTime   观测时刻
     * @return 风圈视图列表
     */
    List<WindCircleVO> listAt(long typhoonId, java.time.LocalDateTime obsTime);

    /**
     * 取台风最新一组风圈实体（供影响城市分析复用，避免重复查库）。
     *
     * @param typhoonId 台风ID
     * @return 风圈实体；无数据返回 null
     */
    WindCircle latest(long typhoonId);

    /**
     * 取某级别的平均半径（km），用于影响范围快速估算。
     *
     * @param typhoonId 台风ID
     * @param level     级别：7 / 10 / 12
     * @return 平均半径；无数据返回 0
     */
    int averageRadius(long typhoonId, int level);

    /**
     * 取所有台风的最大展示风圈级别（来自系统配置 {@code wind_circle_max_level}）。
     *
     * @return 最大级别，默认 12
     */
    int maxDisplayLevel();

    /**
     * 幂等写入一组风圈（重复同步同一时刻会更新而非重复插入）。
     *
     * @param circle 风圈
     * @return 受影响行数
     */
    int upsert(WindCircle circle);
}
