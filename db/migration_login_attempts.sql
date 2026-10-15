-- ============================================================================
--  增量迁移：新增 login_attempts 表（登录失败锁定状态持久化）
-- ============================================================================
--  适用对象：本机 3306 上 **已经存在** 的 `typhoon_monitor` 库。
--
--  为什么要单独给一个脚本：
--    主脚本 db/typhoon_monitor.sql 是 mysqldump 全量导出，含
--    `DROP TABLE IF EXISTS`，重跑会**清空全部业务数据**。
--    已有库想加这一张表，只能走增量方式，不能重跑主脚本。
--
--  执行方式（任选其一）：
--      mysql -u root -p typhoon_monitor < db/migration_login_attempts.sql
--    或在 Navicat / MySQL Workbench / IntelliJ IDEA 的 Database 工具里打开本文件执行。
--
--  幂等性：使用 `CREATE TABLE IF NOT EXISTS`，重复执行不会报错、也不会丢数据。
--
--  不执行会怎样：
--    **不影响任何功能**，登录仍完全可用。只是锁定状态退回改造前的
--    进程内计数（Tomcat 重启即清零）——即安全性略降，而非功能失效。
--    代码已对该情况做了降级：表不存在时自动回退内存计数，不会因缺表而无法登录。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `login_attempts` (
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录名（与 users.username 同口径）',
  `fail_count` int unsigned NOT NULL DEFAULT 0 COMMENT '连续失败次数（触发锁定后归零）',
  `last_fail_at` datetime DEFAULT NULL COMMENT '最近一次失败时间，用于判定是否属于「连续」爆破',
  `locked_until` datetime DEFAULT NULL COMMENT '锁定截止时间；NULL 表示未锁定',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`username`),
  KEY `idx_login_locked` (`locked_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='登录失败锁定状态表（持久化，Tomcat 重启不清零）';

-- 说明：这里**故意不加**指向 users 的外键。
-- 登录失败也要对“不存在的账号”计数（否则攻击者可用随机账号探测而不被锁定），
-- 因此 username 允许是库中不存在的名字，加了外键反而会写不进去。
