-- ============================================================================
--  增量迁移：联系方式列宽 100 → 255（配合「AES-GCM 加密入库」改造）
-- ============================================================================
--  适用对象：本机 3306 上 **已经存在** 的 `typhoon_monitor` 库。
--
--  为什么要加宽：
--    db.properties 的密码/联系方式加密流程把手机号、邮箱加密成
--    `enc1:<Base64(IV 12B + 密文 + 认证标签 16B)>`。以 11 位手机号为例，
--    密文约 39 字节 → Base64 约 52 字符 → 加前缀共约 57 字符，varchar(100) 够用；
--    但更长的联系方式就不够了。长度换算（明文 N 字符 → 入库串）：
--        5 + 4*ceil((N + 28) / 3)       // 前缀 + Base64(IV 12B + 明文 N + 标签 16B)
--    实测边界：N = 41 → 97 字符（放得下）；N = 42 → 101 字符；N = 64 → 129 字符。
--    而前端问卷的联系方式输入框是 maxlength="64"，后端**没有长度校验**，
--    也就是说 N 可达 64，必然超出 100。
--
--    超长时的表现取决于 sql_mode：
--      · 开启 STRICT_TRANS_TABLES（MySQL 5.7+ 默认，本机实测即如此）
--        → **直接报错 1406 Data too long**，提交失败（接口返回 500），问题会暴露；
--      · 非严格模式 → **静默截断**成 100 字符，密文截断后**永远解不回来**，
--        且不报错，属于"数据悄悄坏掉"的典型形态。
--    无论哪一种都是缺陷，因此统一放宽到 255，留足余量（64 字符明文 → 129，余量 126）。
--
--  为什么必须单独给一个脚本：
--    主脚本 db/typhoon_monitor.sql 是 mysqldump 全量导出，含
--    `DROP TABLE IF EXISTS`，重跑会**清空全部业务数据**。已有库只能走增量。
--
--  执行方式（任选其一）：
--      mysql -u root -p typhoon_monitor < db/migration_contact_encryption.sql
--    或在 Navicat / MySQL Workbench / IntelliJ IDEA 的 Database 工具里执行。
--     注意：Studio 类工具执行前请先 `USE typhoon_monitor;`
--
--  幂等性：`MODIFY COLUMN` 可重复执行（重复改同一宽度是空操作）。
--
--  不执行会怎样：
--    11 位手机号与短邮箱（明文 ≤ 41 字符）不受影响；
--    更长的联系方式（问卷输入框允许到 64）会**提交失败或密文被截断**
--    （取决于 sql_mode，见上）。
--
--  历史数据说明：
--    本迁移**不改写**已有值。老库里可能是明文（未加密时代写入），
--    ContactUtil.decrypt 对不带 `enc1:` 前缀的值原样返回，因此仍能正常显示；
--    若希望历史明文一并加密，需要在应用侧写一次性的回填脚本（本项目未提供）。
-- ============================================================================

ALTER TABLE `feedback_reports`
  MODIFY COLUMN `contact_info` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL
  COMMENT '联系方式（可选）：AES-GCM 密文（enc1: 前缀 + Base64），非明文；展示时由应用层打码';

ALTER TABLE `user_behavior_surveys`
  MODIFY COLUMN `contact_info` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL
  COMMENT '联系方式（可选）：AES-GCM 密文（enc1: 前缀 + Base64），非明文；展示时由应用层打码';
