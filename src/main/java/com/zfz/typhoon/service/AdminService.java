package com.zfz.typhoon.service;

import com.zfz.typhoon.model.DataSource;
import com.zfz.typhoon.model.vo.DataSourceVO;
import com.zfz.typhoon.model.vo.ModelConfigVO;

import java.util.List;
import java.util.Map;

/**
 * 管理后台汇总业务接口。
 *
 * <p>对应接口文档 {@code GET /api/admin/dashboard}。
 *
 * <h3>为什么要单独一个"汇总服务"</h3>
 * 后台首页要同时展示用户、台风、数据源、模型、反馈、待办六类数字。
 * 如果让 Servlet 自己去调六个 Service 再拼装，
 * 那么"哪些数字算待办""哪个阈值触发告警"这类业务判断就会写进控制层，
 * 无法被复用也无从测试。放到这里，后台页、汇报材料、将来的开放接口
 * 都能拿到同一份口径一致的数字。
 *
 * <h3>数字为主，文案为辅</h3>
 * 返回结构里以<b>数值</b>为主（{@code totalUsers} 而不是 {@code "注册用户 10"}），
 * 让前端决定怎么排版。原因很简单：同一份数据在卡片、表格、图表里的文案
 * 完全不同，把中文写死在接口里迟早要改。
 *
 * @author 追风者项目组
 */
public interface AdminService {

    /**
     * 后台运行概览。
     *
     * @return 汇总指标（用户 / 台风 / 数据源 / 模型 / 反馈 / 问卷 / 沙盘）
     */
    Map<String, Object> dashboard();

    /**
     * 大模型接入配置列表（后台表格）。
     *
     * @return 模型配置列表
     */
    List<ModelConfigVO> modelConfigs();

    /**
     * 待办与审核队列。
     *
     * <p>待办不落表，而是由系统当前状态实时推导：
     * 同步失败的数据源、待处理的反馈、待接入的模型、被停用的账号。
     * 这样待办永远不会与真实状态脱节——落表的待办最典型的问题就是
     * "问题已经解决了，待办还挂着"。
     *
     * @return 待办列表，每项含 {@code title / src / state / level / href}
     */
    List<Map<String, Object>> todos();

    /**
     * 数据源接入监控列表（后台表格）。
     *
     * @return 数据源视图列表
     */
    List<DataSourceVO> dataSources();

    /**
     * 后台统计图表数据（ECharts 用）。
     *
     * @return {@code {userGrowth:[{date,count}], dataDistribution:[{name,value}]}}
     */
    Map<String, Object> stats();
}
