package com.zfz.typhoon.service;

import com.zfz.typhoon.model.User;
import com.zfz.typhoon.model.vo.UserVO;

import java.util.List;
import java.util.Map;

/**
 * 用户管理业务接口（管理员专属）。
 *
 * <p>对应接口文档 {@code /api/admin/*}：
 * {@code GET /api/admin/users}、{@code PUT /api/admin/users/{id}/status} 等。
 *
 * <h3>两条不可绕过的业务规则</h3>
 * <ol>
 *   <li><b>不能停用或降级"最后一个管理员"</b>。否则系统会永久失去管理能力，
 *       只能改数据库救回来。这类"自锁"缺陷在权限系统里非常常见，必须在服务层拦。</li>
 *   <li><b>不能停用/删除自己</b>。同样是为了避免管理员把自己锁在门外。</li>
 * </ol>
 * 两条规则都放在这一层而不是 Servlet：任何调用方（后台接口、将来的批处理）
 * 都不可能绕过。
 *
 * @author 追风者项目组
 */
public interface UserService {

    /**
     * 条件查询用户列表。
     *
     * @param keyword 账号/昵称/邮箱模糊关键词，可为 null
     * @param role    角色过滤（ADMIN / USER），可为 null
     * @param enabled 状态过滤（true=正常 / false=停用），可为 null
     * @return 用户视图列表
     */
    List<UserVO> list(String keyword, String role, Boolean enabled);

    /**
     * 按ID查询用户视图。
     *
     * @param userId 用户ID
     * @return 用户视图；不存在抛 404
     */
    UserVO getById(long userId);

    /**
     * 新建用户（管理员操作）。
     *
     * @param user        用户实体（用户名、角色、昵称等）
     * @param rawPassword 明文初始密码
     * @return 新用户ID
     */
    long create(User user, String rawPassword);

    /**
     * 修改用户资料（不含密码）。
     *
     * @param user 用户实体（须含 userId）
     * @return 受影响行数
     */
    int updateProfile(User user);

    /**
     * 启用 / 停用账号。
     *
     * @param userId    目标用户ID
     * @param enabled   目标状态
     * @param operatorId 操作者ID（用于"不能停用自己"与"最后一个管理员"校验）
     * @return 受影响行数
     */
    int setEnabled(long userId, boolean enabled, long operatorId);

    /**
     * 调整角色。
     *
     * @param userId     目标用户ID
     * @param role       目标角色（ADMIN / USER）
     * @param operatorId 操作者ID
     * @return 受影响行数
     */
    int setRole(long userId, String role, long operatorId);

    /**
     * 管理员重置他人密码（无需原密码）。
     *
     * @param userId      目标用户ID
     * @param newPassword 明文新密码
     * @return 受影响行数
     */
    int resetPassword(long userId, String newPassword);

    /**
     * 删除用户。
     *
     * @param userId     目标用户ID
     * @param operatorId 操作者ID
     * @return 受影响行数
     */
    int delete(long userId, long operatorId);

    /**
     * 后台首页统计卡片的数据。
     *
     * @return 键为 {@code total / admin / normal / disabled} 的计数
     */
    Map<String, Integer> stats();

    /**
     * 用户增长趋势（按注册日期分组的每日新增）。
     *
     * @return [{date: "2026-09-01", count: 3}, ...]，升序，无数据返回空列表
     */
    List<Map<String, Object>> growthTrend();

    /**
     * 校验账号是否可用（注册前查重）。
     *
     * @param username 登录名
     * @return 已存在返回 true
     */
    boolean existsUsername(String username);
}
