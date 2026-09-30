package com.zfz.typhoon.service;

import com.zfz.typhoon.model.UserBehaviorSurvey;
import com.zfz.typhoon.model.vo.SurveyAggResultVO;

/**
 * 防灾行为问卷业务接口。
 *
 * <p>对应接口文档：{@code POST /api/surveys}（提交问卷）、
 * {@code GET /api/surveys/agg}（查询聚合成统计）。
 *
 * <h3>隐私红线（本模块的最高优先级约束）</h3>
 * 原始问卷表 {@code user_behavior_surveys} 的<b>任何字段都不对外暴露</b>。
 * 对外只有两种形态：
 * <ol>
 *   <li>提交接口只回一个「收到」的结果（含样本计数），不回显填写内容；</li>
 *   <li>查询接口只回 {@link SurveyAggResultVO} —— 按城市聚合后的群体统计。</li>
 * </ol>
 * 联系方式入库前经 {@code PasswordUtil#maskContact} 脱敏（见实现类），
 * 且永远不会出现在聚合结果里。
 *
 * <h3>为什么要有"样本门槛"</h3>
 * 3 个人填的问卷也能算出"67% 用户储备了物资"，但这个数字毫无统计意义，
 * 还会误导防灾决策。因此本服务设置最小样本量门槛，
 * 未达门槛时仍返回数据但把 {@code enoughSamples} 标为 false，
 * 由前端决定是否加"样本不足，仅供参考"的提示。
 *
 * @author 追风者项目组
 */
public interface SurveyService {

    /**
     * 提交一份问卷。
     *
     * <p>入库前会做三件事：补默认问卷版本、补提交时间、脱敏联系方式。
     *
     * @param survey 问卷（typhoonId / cityId / 三个必答题必须已有值）
     * @return 新记录ID
     */
    long submit(UserBehaviorSurvey survey);

    /**
     * 查询聚合统计。
     *
     * @param typhoonId 台风ID
     * @param cityId    城市ID；为 null 表示查询该台风下全部城市
     * @return 聚合视图（含分城市明细与反向科普提示）
     */
    SurveyAggResultVO agg(long typhoonId, Long cityId);

    /**
     * 重新聚合并写入 {@code survey_agg_results}（供离线任务或管理端触发）。
     *
     * <p>之所以要落库而不是每次实时算：聚合是 7 个维度的全表扫描，
     * 而看板刷新频率远高于问卷提交频率，把结果物化下来可避免重复计算。
     *
     * @param typhoonId 台风ID
     * @return 写入的城市条数
     */
    int rebuild(long typhoonId);

    /**
     * 统计某台风下的有效样本总量。
     *
     * @param typhoonId 台风ID
     * @return 样本量
     */
    int countSamples(long typhoonId);
}
