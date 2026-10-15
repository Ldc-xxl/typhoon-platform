-- ============================================================================
--  增量迁移：risk_assessments 增加 obs_time（评估基准观测时刻）
-- ============================================================================
--  适用对象：本机 3306 上 **已经存在** 的 `typhoon_monitor` 库。
--
--  为什么要单独给一个脚本：
--    主脚本 db/typhoon_monitor.sql 是 mysqldump 全量导出，含
--    `DROP TABLE IF EXISTS`，重跑会**清空全部业务数据**。
--    已有库想给表加一列，只能走增量方式，不能重跑主脚本。
--
--  执行方式（任选其一）：
--      mysql -u root -p typhoon_monitor < db/migration_risk_obs_time.sql
--    或在 Navicat / MySQL Workbench / IntelliJ IDEA 的 Database 工具里打开本文件执行。
--
--  幂等性：MySQL 8 没有 `ADD COLUMN IF NOT EXISTS`，因此这里先查
--    information_schema 判断列是否存在，再用预处理语句按需执行 ALTER。
--    重复执行不会报错、也不会丢数据。
--
--  这一列解决什么问题：
--    风险推算的起点（ETA 基准）是台风的**某个实测时刻**，而不是"点下评估按钮
--    的那一秒"。此前表里只有 assessment_time（评估动作发生的时刻），
--    于是"登陆前 24 小时平台给出的是什么结论"这类复盘问题无法回答——
--    因为看不出那一轮评估到底是拿哪个时次的中心位置与风圈算的。
--    风险页新增「评估基准」下拉后，这一步变成了常规操作，必须有地方落下来。
--
--  不执行会怎样：
--    **不影响任何现有功能**。RiskAssessmentDaoImpl 会探测该列是否存在，
--    缺列时自动回退到不含 obs_time 的 SQL（此时基准时刻只是不落库，
--    页面上的切换与重算仍完全可用）。
-- ============================================================================

SET @has_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'risk_assessments'
    AND COLUMN_NAME  = 'obs_time'
);

SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `risk_assessments`
     ADD COLUMN `obs_time` datetime DEFAULT NULL
     COMMENT ''评估基准观测时刻（台风实测时次），ETA 由此刻起算''
     AFTER `assessment_time`',
  'DO 0');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 校验：应返回一行，1 表示该列已存在
SELECT COUNT(*) AS obs_time_column_present
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME   = 'risk_assessments'
  AND COLUMN_NAME  = 'obs_time';
