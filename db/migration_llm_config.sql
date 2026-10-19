-- ============================================================================
--  增量迁移：大模型接入配置表 + 测算耗时列
-- ============================================================================
--  适用对象：本机 3306 上 **已经存在** 的 `typhoon_monitor` 库。
--            主脚本 db/typhoon_monitor.sql 含 `DROP TABLE IF EXISTS`，
--            重跑会清空全部业务数据，已有库只能走增量。
--
--  执行方式：
--      mysql -u root -p typhoon_monitor < db/migration_llm_config.sql
--
--  幂等性：建表用 CREATE TABLE IF NOT EXISTS；初始配置行用
--          INSERT ... ON DUPLICATE KEY UPDATE；加列前先查 information_schema。
--          重复执行不会报错、不会丢数据、不会覆盖已填写的密钥。
--
-- ----------------------------------------------------------------------------
--  为什么需要这张表
-- ----------------------------------------------------------------------------
--  改造前「大模型接入配置」只有 GET /api/admin/models 一个只读接口：
--      status      = ai_prediction_records 里有没有该 model_name 的记录
--      keyConfigured = 同上（有记录就算"已配置密钥"）
--  而所有记录都来自 AiPredictionServiceImpl 的**本地多机构融合算法**，
--  model_name 只是写进去的标签。也就是说：后台从来没有真正接入过任何厂商，
--  也没地方存密钥 —— 点「接入」只会弹一条说明性 toast。
--
--  本表把"厂商凭据"与"业务数据"分开存放的理由：
--    1) 密钥必须可加密落库、且**绝不通过 /api/admin/settings 回传**；
--       system_settings 是 key-value 表且会被整表返回给前端，不能放密文；
--    2) 一家厂商一条记录，字段固定，比往 settings 里堆 llm.xxx.yyy 更好查；
--    3) 断开接入 = 清空密文，业务历史（ai_prediction_records）不受影响。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `llm_provider_config` (
  `config_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `model_key` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型标识，与 AdminService.SUPPORTED_MODELS 第二列一致（如 doubao-seed-1.6）',
  `model_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '展示名（如 豆包 Seed-1.6），写进 ai_prediction_records.model_name',
  `provider` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '厂商协议：doubao / qianfan / openai-compatible',
  `endpoint_id` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '推理接入点或模型ID（豆包形如 ep-xxxx / doubao-seed-1-6-250615）',
  `base_url` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '自定义网关地址；为空时用厂商默认域名',
  `api_key_cipher` text COLLATE utf8mb4_unicode_ci COMMENT 'API Key 的 AES-256-GCM 密文（绝不明文落库，也绝不回传前端）',
  `verified_at` datetime DEFAULT NULL COMMENT '最近一次连通性测试成功的时间；NULL 表示尚未验证通过',
  `last_error` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近一次失败原因（已剥离密钥，可直接展示）',
  `last_latency_ms` int unsigned DEFAULT NULL COMMENT '最近一次成功调用的往返耗时（毫秒）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`config_id`),
  UNIQUE KEY `uk_llm_model_key` (`model_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='大模型厂商接入配置（密钥密文存储）';

-- 预置平台支持的四家。只写标识与展示名，密钥留空 ——
-- 状态因此初始为「待接入」，与改造前的展示口径一致。
INSERT INTO `llm_provider_config` (`model_key`, `model_name`, `provider`) VALUES
  ('deepseek-v3',       'DeepSeek-V3',     'openai-compatible'),
  ('qwen-max',          '通义千问-max',     'openai-compatible'),
  ('doubao-seed-1.6',   '豆包 Seed-1.6',   'doubao'),
  ('ernie-4.0',         '文心一言 4.0',     'qianfan')
ON DUPLICATE KEY UPDATE `model_name` = VALUES(`model_name`),
                        `provider`   = VALUES(`provider`);

-- ----------------------------------------------------------------------------
--  ai_prediction_records 补 latency_ms
-- ----------------------------------------------------------------------------
--  MySQL 8 没有 ADD COLUMN IF NOT EXISTS，用 information_schema 判定后动态执行。
--  该列只对"真实调用厂商"的记录有意义；本地融合算法产生的历史行为 NULL。
-- ============================================================================
SET @has_latency := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME   = 'ai_prediction_records'
     AND COLUMN_NAME  = 'latency_ms'
);
SET @ddl := IF(@has_latency = 0,
  'ALTER TABLE `ai_prediction_records` ADD COLUMN `latency_ms` int unsigned DEFAULT NULL COMMENT ''单次调用往返耗时（毫秒）；本地融合算法记录为 NULL'' AFTER `deviation_vs_agencies`',
  'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
