package com.zfz.typhoon.service;

import com.zfz.typhoon.model.vo.RiskAssessmentVO;

import java.util.List;

/**
 * 城市风险预评估服务。
 *
 * <p>对应前端 P4（风险评估页）与接口文档 3.9 节。
 *
 * <p>评估口径是<b>规则推算</b>而非数值模式：以台风当前实测位置为中心，
 * 结合最新风圈的<b>四象限</b>半径判断城市是否落入 7/10/12 级风圈，
 * 再叠加城市自身的基础风险等级与人口规模，得到一个 0—100 的风险评分。
 *
 * <p>之所以坚持用风圈象限半径而不是"按直线距离套一个衰减公式"：
 * 台风的危险半圆是客观存在的（北半球移向右侧风雨更强），
 * 数据库 {@code wind_circles} 表既然已经存了四象限半径，就应该用起来，
 * 否则这张表等于白建。
 *
 * <p><b>合规要求</b>：所有结果必须随 {@code disclaimer} 一起展示，
 * 见 {@link com.zfz.typhoon.model.RiskAssessment#DISCLAIMER}。
 *
 * @author 追风者项目组
 */
public interface RiskAssessmentService {

    /** 系统配置键：参与评估的最大距离半径（km），超出则不评估 */
    String KEY_RANGE_KM = "risk.assess.rangeKm";
    /** 系统配置键：未登录时落库所用的默认用户ID */
    String KEY_DEFAULT_USER_ID = "risk.assess.defaultUserId";

    /** 默认评估半径（km） */
    double DEFAULT_RANGE_KM = 800D;

    /**
     * 取指定台风已落库的最新一轮城市风险评估。
     *
     * @param typhoonId 台风ID
     * @return 风险评估列表，按风险评分降序；库中无数据时返回空列表
     */
    List<RiskAssessmentVO> listLatest(long typhoonId);

    /**
     * 实时推算指定台风的城市风险评估（<b>只算不落库</b>）。
     *
     * <p>供 GET 接口使用：读接口不应产生副作用。同时它也是
     * {@link #assessAndSave(long, long)} 的计算内核。
     *
     * @param typhoonId 台风ID
     * @return 评估列表，按风险评分降序
     * @throws com.zfz.typhoon.util.BusinessException 台风不存在或无实测轨迹时抛出
     */
    List<RiskAssessmentVO> assess(long typhoonId);

    /**
     * 推算并落库，返回落库后的一轮评估。
     *
     * @param typhoonId 台风ID
     * @param userId    发起评估的用户ID（{@code risk_assessments.user_id} 为非空列）
     * @return 落库后的评估列表，按风险评分降序
     */
    List<RiskAssessmentVO> assessAndSave(long typhoonId, long userId);

    /**
     * 推算单个城市的风险评估。
     *
     * @param typhoonId 台风ID
     * @param cityId    城市ID
     * @return 该城市的评估结果；城市不存在时抛出 404
     */
    RiskAssessmentVO assessCity(long typhoonId, long cityId);

    /**
     * 清除指定台风的历史评估记录（重新评估前调用，避免旧一轮结果与新一轮混杂）。
     *
     * @param typhoonId 台风ID
     * @return 受影响行数
     */
    int clear(long typhoonId);
}
