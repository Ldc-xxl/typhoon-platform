package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.SystemSettingDao;
import com.zfz.typhoon.dao.WindCircleDao;
import com.zfz.typhoon.dao.impl.SystemSettingDaoImpl;
import com.zfz.typhoon.dao.impl.WindCircleDaoImpl;
import com.zfz.typhoon.model.WindCircle;
import com.zfz.typhoon.model.vo.WindCircleVO;
import com.zfz.typhoon.service.WindCircleService;
import com.zfz.typhoon.util.BusinessException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 风圈业务实现。
 *
 * <h3>最大展示级别为什么可配置</h3>
 * 12 级风圈半径通常只有几十公里，地图缩放到"看全省"时会小得看不见；
 * 而管理端若只想展示"影响范围"，7 级风圈就够了。
 * 因此最大级别从 {@code system_settings} 读取，便于按展示场景调整，
 * 而不是把 7/10/12 写死在代码里。
 *
 * @author 追风者项目组
 */
public class WindCircleServiceImpl implements WindCircleService {

    private final WindCircleDao windCircleDao;
    private final SystemSettingDao settingDao;

    /**
     * 生产用构造：按默认实现装配依赖。
     * <p>之所以把它拆成"无参 + 带参"两个构造，是为了给单元测试留出注入替身的缝隙：
     * 依赖全部在字段初始化里 new 出来的话，测试就必须连上真实 MySQL 才能跑，
     * 单元测试也就退化成了集成测试。
     */
    public WindCircleServiceImpl() {
        this(new WindCircleDaoImpl(), new SystemSettingDaoImpl());
    }

    /**
     * 测试用构造：注入替身依赖（Mockito 生成的 mock 或手写桩）。
     * <p>参数顺序与上方字段声明一致，均为本服务运行所需的协作者。
     */
    public WindCircleServiceImpl(WindCircleDao windCircleDao, SystemSettingDao settingDao) {
        this.windCircleDao = windCircleDao;
        this.settingDao = settingDao;
    }

    @Override
    public List<WindCircleVO> listByTyphoon(long typhoonId) {
        WindCircle latest = windCircleDao.findLatest(typhoonId);
        return toVos(latest);
    }

    @Override
    public List<WindCircleVO> listAt(long typhoonId, LocalDateTime obsTime) {
        WindCircle circle = windCircleDao.findByTyphoonAndTime(typhoonId, obsTime);
        return toVos(circle);
    }

    @Override
    public WindCircle latest(long typhoonId) {
        return windCircleDao.findLatest(typhoonId);
    }

    @Override
    public WindCircle at(long typhoonId, LocalDateTime obsTime) {
        if (obsTime == null) {
            return windCircleDao.findLatest(typhoonId);
        }
        WindCircle exact = windCircleDao.findByTyphoonAndTime(typhoonId, obsTime);
        if (exact != null) {
            return exact;
        }
        // 风圈表并非每个观测时次都有数据——多数历史台风整场只存了一组
        // （例如 typhoon_id=1 只有 2024-09-05 17:00 那一组，而它的轨迹有 3 个时次）。
        // 此时必须取「不晚于该时刻的最近一组」，而不能直接返回 null：
        // 返回 null 会让 RiskAssessmentServiceImpl#estimateWindForce 退化成
        // "按中心风力线性衰减"，于是「跟随最新实测」与「锁定最新那个时次」
        // 会算出两个不同的评分，而两者本该是同一件事——用户无法解释这个差异。
        // 该时刻之前确实还没有任何风圈数据时才返回 null，那属于数据事实。
        WindCircle floor = null;
        for (WindCircle c : windCircleDao.listByTyphoon(typhoonId)) {
            if (c == null || c.getObsTime() == null) {
                continue;
            }
            if (c.getObsTime().isAfter(obsTime)) {
                break;   // 升序排列，后面的只会更晚
            }
            floor = c;
        }
        return floor;
    }

    @Override
    public int averageRadius(long typhoonId, int level) {
        WindCircle circle = windCircleDao.findLatest(typhoonId);
        if (circle == null) {
            return 0;
        }
        // averageRadius 返回四象限平均半径（可能带小数），对外统一取整为 km
        return (int) Math.round(circle.averageRadius(level));
    }

    @Override
    public int maxDisplayLevel() {
        // 配置被改成非法值时回退 12，保证风圈一定能画出来
        int level = settingDao.getInt(SystemSettingDao.KEY_WIND_CIRCLE_MAX_LEVEL, 12);
        if (level != 7 && level != 10 && level != 12) {
            return 12;
        }
        return level;
    }

    @Override
    public int upsert(WindCircle circle) {
        if (circle.getTyphoonId() == null) {
            throw BusinessException.badRequest("缺少 typhoonId");
        }
        if (circle.getObsTime() == null) {
            throw BusinessException.badRequest("缺少观测时间 obsTime");
        }
        return windCircleDao.upsert(circle);
    }

    /* ------------------------------------------------------------------ */
    /*  内部实现                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * 把风圈实体转成"7 / 10 / 12"三档视图，并按配置裁剪最大级别。
     *
     * @param circle 风圈实体，可为 null
     * @return 视图列表（按级别升序）
     */
    private List<WindCircleVO> toVos(WindCircle circle) {
        List<WindCircleVO> result = new ArrayList<WindCircleVO>(3);
        if (circle == null) {
            return result;
        }
        int maxLevel = maxDisplayLevel();

        if (maxLevel >= WindCircleDao.LEVEL_7) {
            result.add(WindCircleVO.of(circle, WindCircleDao.LEVEL_7, LEVEL_COLOR_7));
        }
        if (maxLevel >= WindCircleDao.LEVEL_10) {
            result.add(WindCircleVO.of(circle, WindCircleDao.LEVEL_10, LEVEL_COLOR_10));
        }
        if (maxLevel >= WindCircleDao.LEVEL_12) {
            result.add(WindCircleVO.of(circle, WindCircleDao.LEVEL_12, LEVEL_COLOR_12));
        }
        return result;
    }
}
