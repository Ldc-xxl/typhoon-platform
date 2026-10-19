-- ============================================================================
--  追风者 · 台风实况监测与灾害辅助分析 Web 平台 —— 数据库初始化脚本
-- ============================================================================
--  本文件即本机 3306 实例上 `typhoon_monitor` 库的初始化脚本，
--  与 src/main/resources/db.properties 的 jdbc.url 库名完全一致。
--
--  为什么不改库名：`typhoon_monitor` 是原始备份 Dump20260916.sql 里就
--  存在的名字，也是现网/本机 3306 上真实存在的库。项目名（typhoon_platform）
--  与库名（typhoon_monitor）不一致是历史事实，强行改名只会制造
--  「代码和脚本各说各话」的低级故障 —— 应用启动后所有查询报
--  "Unknown database"。库名只保留一处口径：以本文件与 db.properties 为准。
--
--  使用方法（命令行）：
--      mysql -u root -p < db/typhoon_monitor.sql
--  或在 MySQL Workbench / Navicat / IntelliJ IDEA 的数据库工具里直接执行本脚本。
--
--  注意：脚本含 `CREATE DATABASE IF NOT EXISTS` 与 `DROP TABLE IF EXISTS`，
--  重复执行会清空业务数据（结构重建）。只想连库、不想动数据时不要跑本脚本。
--
--  内容结构（28 张表 + 演示数据）：
--    · 台风档案与实况：typhoons / typhoon_tracks / wind_circles
--    · 多机构预报：forecast_agencies / forecast_paths / forecast_disagree_records
--    · AI 智能测算：ai_prediction_records / similar_case_matches
--    · 风险评估与灾情：cities / risk_assessments / city_impact_records / disaster_reports
--    · 沙盘与对比：sandbox_scenarios / historical_comparisons
--    · 防灾问卷：user_behavior_surveys / survey_agg_results
--    · 用户与偏好：users / user_favorites / user_subscriptions / notifications
--    · 反馈与运维：feedback_reports / data_sources / sync_logs / export_records
--    · 系统配置：system_settings / typhoon_albums
--    · 大模型接入：llm_provider_config（厂商凭据以密文存储，密钥不出后端）
--    · 安全：login_attempts（登录失败锁定状态，持久化以免重启清零）
-- ============================================================================

CREATE DATABASE  IF NOT EXISTS `typhoon_monitor` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `typhoon_monitor`;
-- MySQL dump 10.13  Distrib 8.0.46, for Win64 (x86_64)
--
-- Host: localhost    Database: typhoon_monitor
-- ------------------------------------------------------
-- Server version	8.4.10

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `ai_prediction_records`
--

DROP TABLE IF EXISTS `ai_prediction_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_prediction_records` (
  `record_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `typhoon_id` bigint unsigned NOT NULL COMMENT '关联台风ID（当前活跃台风）',
  `model_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '大模型名称（如 DeepSeek-V3、通义千问-max、豆包Seed-1.6）',
  `prompt_version` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '提示词模板版本',
  `prediction_time` datetime NOT NULL COMMENT '测算时间',
  `predicted_path` json DEFAULT NULL COMMENT 'AI预测路径点JSON（时间/经纬度/强度）',
  `predicted_landfall_lat` decimal(6,3) DEFAULT NULL COMMENT 'AI预测登陆纬度',
  `predicted_landfall_lon` decimal(6,3) DEFAULT NULL COMMENT 'AI预测登陆经度',
  `confidence_score` decimal(5,2) NOT NULL COMMENT '模型置信度（0-100）',
  `deviation_vs_agencies` decimal(6,2) DEFAULT NULL COMMENT '与机构预报平均偏差（km）',
  `latency_ms` int unsigned DEFAULT NULL COMMENT '单次调用往返耗时（毫秒）；本地融合算法记录为 NULL',
  `summary_text` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'AI生成的解读文案',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING=待验证/VALIDATED=已验证/COMPARED=已与实况对比',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`record_id`),
  KEY `idx_ai_typhoon_time` (`typhoon_id`,`prediction_time`),
  KEY `idx_ai_model` (`model_name`),
  CONSTRAINT `fk_ai_pred_typhoon` FOREIGN KEY (`typhoon_id`) REFERENCES `typhoons` (`typhoon_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='大模型智能测算记录表（活跃台风路径智能测算结果）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_prediction_records`
--

LOCK TABLES `ai_prediction_records` WRITE;
/*!40000 ALTER TABLE `ai_prediction_records` DISABLE KEYS */;
INSERT INTO `ai_prediction_records` (`record_id`,`typhoon_id`,`model_name`,`prompt_version`,
  `prediction_time`,`predicted_path`,`predicted_landfall_lat`,`predicted_landfall_lon`,
  `confidence_score`,`deviation_vs_agencies`,`summary_text`,`status`,`created_at`) VALUES (1,13,'DeepSeek-V3','v1.0','2026-09-15 09:00:00','[{\"f\": 14, \"t\": \"2026-09-17 08:00\", \"lat\": 23.8, \"lon\": 119.2}, {\"f\": 12, \"t\": \"2026-09-18 08:00\", \"lat\": 25.6, \"lon\": 118.4}]',23.800,119.200,72.00,18.50,'综合三家机构预报与历史相似案例，AI 判断飞燕大概率在福建中南部沿海登陆，登陆窗口为 17 日夜间至 18 日凌晨。','PENDING','2026-09-16 11:08:06'),(2,13,'通义千问-max','v1.0','2026-09-15 09:00:00','[{\"f\": 13, \"t\": \"2026-09-17 08:00\", \"lat\": 24.3, \"lon\": 118.9}, {\"f\": 11, \"t\": \"2026-09-18 08:00\", \"lat\": 26.1, \"lon\": 117.7}]',24.300,118.900,68.00,22.00,'模型认为飞燕路径存在偏北分量，登陆点可能北移至泉州至莆田一带，需关注强降水影响。','PENDING','2026-09-16 11:08:06'),(3,13,'豆包Seed-1.6','v1.0','2026-09-15 09:00:00','[{\"f\": 14, \"t\": \"2026-09-17 08:00\", \"lat\": 23.5, \"lon\": 119.5}, {\"f\": 12, \"t\": \"2026-09-18 08:00\", \"lat\": 24.9, \"lon\": 118.9}]',23.500,119.500,75.00,15.00,'AI 测算登陆点在厦门至漳州之间，与 CMA 路径较为接近，移速偏慢需警惕暴雨叠加。','PENDING','2026-09-16 11:08:06'),(4,13,'DeepSeek-V3','v1.1','2026-09-16 08:00:00','[{\"f\": 14, \"t\": \"2026-09-17 08:00\", \"lat\": 23.9, \"lon\": 119.0}, {\"f\": 12, \"t\": \"2026-09-18 08:00\", \"lat\": 25.4, \"lon\": 118.3}]',23.900,119.000,78.00,12.00,'结合最新实况轨迹修正，AI 测算置信度提升，登陆点收敛于厦门至泉州沿海，建议关注风暴潮风险。','PENDING','2026-09-16 11:08:06'),(5,13,'通义千问-max','v1.1','2026-09-16 08:00:00','[{\"f\": 13, \"t\": \"2026-09-17 08:00\", \"lat\": 24.1, \"lon\": 118.7}, {\"f\": 11, \"t\": \"2026-09-18 08:00\", \"lat\": 25.8, \"lon\": 118.0}]',24.100,118.700,70.00,20.00,'模型仍保留偏北路径可能，但登陆点区间较昨日收窄，不确定性下降。','PENDING','2026-09-16 11:08:06'),(6,13,'豆包Seed-1.6','v1.1','2026-09-16 08:00:00','[{\"f\": 14, \"t\": \"2026-09-17 08:00\", \"lat\": 23.6, \"lon\": 119.3}, {\"f\": 12, \"t\": \"2026-09-18 08:00\", \"lat\": 25.1, \"lon\": 118.8}]',23.600,119.300,80.00,10.00,'AI 测算与 CMA 路径平均偏差 10km，多模型结果一致性较好，登陆概率最高的区间为厦门至漳州。','PENDING','2026-09-16 11:08:06'),(7,1,'DeepSeek-V3','v1.0','2024-09-05 08:00:00','[{\"f\": 17, \"t\": \"2024-09-06 08:00\", \"lat\": 19.8, \"lon\": 111.0}, {\"f\": 16, \"t\": \"2024-09-07 08:00\", \"lat\": 20.6, \"lon\": 110.8}]',19.800,111.000,71.00,25.00,'历史复盘：摩羯 AI 测算登陆点（海南文昌）与实况一致，强度预估略低 1 级。','COMPARED','2026-09-16 11:08:06'),(8,3,'文心一言4.0','v1.0','2023-07-26 12:00:00','[{\"f\": 16, \"t\": \"2023-07-27 08:00\", \"lat\": 24.4, \"lon\": 118.6}, {\"f\": 14, \"t\": \"2023-07-28 08:00\", \"lat\": 25.2, \"lon\": 118.2}]',24.800,118.600,65.00,30.00,'历史复盘：杜苏芮 AI 测算登陆点（福建晋江）与实况吻合，路径平均偏差约 30km。','COMPARED','2026-09-16 11:08:06'),(9,8,'通义千问-max','v1.0','2018-09-15 12:00:00','[{\"f\": 15, \"t\": \"2018-09-16 08:00\", \"lat\": 22.1, \"lon\": 113.0}, {\"f\": 14, \"t\": \"2018-09-17 08:00\", \"lat\": 21.9, \"lon\": 112.9}]',22.000,112.800,62.00,35.00,'历史复盘：山竹 AI 测算登陆点（广东台山）与实况一致，移速预估偏快。','COMPARED','2026-09-16 11:08:06'),(10,9,'豆包Seed-1.6','v1.0','2016-10-16 12:00:00','[{\"f\": 14, \"t\": \"2016-10-17 08:00\", \"lat\": 18.6, \"lon\": 110.2}, {\"f\": 13, \"t\": \"2016-10-18 08:00\", \"lat\": 18.9, \"lon\": 110.5}]',18.800,110.400,58.00,40.00,'历史复盘：莎莉嘉 AI 测算登陆点（海南万宁）与实况一致，路径平均偏差约 40km。','COMPARED','2026-09-16 11:08:06');
/*!40000 ALTER TABLE `ai_prediction_records` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `cities`
--

DROP TABLE IF EXISTS `cities`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cities` (
  `city_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '城市ID',
  `city_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '城市名称',
  `province` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属省份/直辖市',
  `latitude` decimal(7,4) NOT NULL COMMENT '纬度（北纬为正）',
  `longitude` decimal(8,4) NOT NULL COMMENT '经度（东经为正）',
  `population` int unsigned DEFAULT NULL COMMENT '常住人口（万人）',
  `is_coastal` tinyint NOT NULL DEFAULT '1' COMMENT '是否沿海：1=是，0=否',
  `base_risk_level` tinyint NOT NULL DEFAULT '2' COMMENT '台风基准风险等级：1=低，2=中，3=高，4=极高',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=启用，0=停用',
  PRIMARY KEY (`city_id`),
  UNIQUE KEY `uk_cities_name_province` (`city_name`,`province`),
  KEY `idx_cities_province` (`province`),
  KEY `idx_cities_risk_level` (`base_risk_level`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='城市信息表（主要城市基础信息）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `cities`
--

LOCK TABLES `cities` WRITE;
/*!40000 ALTER TABLE `cities` DISABLE KEYS */;
INSERT INTO `cities` VALUES (1,'深圳','广东',22.5431,114.0579,1768,1,3,1),(2,'广州','广东',23.1291,113.2644,1873,0,2,1),(3,'厦门','福建',24.4798,118.0894,530,1,3,1),(4,'福州','福建',26.0745,119.2965,842,1,3,1),(5,'温州','浙江',28.0023,120.7010,964,1,3,1),(6,'宁波','浙江',29.8683,121.5440,961,1,2,1),(7,'舟山','浙江',30.0197,122.2072,116,1,3,1),(8,'上海','上海',31.2304,121.4737,2489,1,2,1),(9,'海口','海南',20.0440,110.1999,287,1,3,1),(10,'三亚','海南',18.2528,109.5119,106,1,4,1),(11,'湛江','广东',21.2707,110.3594,698,1,3,1),(12,'汕头','广东',23.3535,116.6822,554,1,3,1),(13,'泉州','福建',24.8741,118.6757,887,1,3,1),(14,'杭州','浙江',30.2741,120.1551,1237,0,2,1);
/*!40000 ALTER TABLE `cities` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `city_impact_records`
--

DROP TABLE IF EXISTS `city_impact_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `city_impact_records` (
  `impact_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '影响记录ID',
  `typhoon_id` bigint unsigned NOT NULL COMMENT '台风ID',
  `city_id` bigint unsigned NOT NULL COMMENT '城市ID',
  `wind_circle_level` tinyint NOT NULL COMMENT '所在风圈层级（7/10/12）',
  `estimated_wind_force` tinyint NOT NULL COMMENT '预估风力等级（级）',
  `impact_start_time` datetime NOT NULL COMMENT '影响开始时间',
  `impact_end_time` datetime NOT NULL COMMENT '影响结束时间',
  `distance_km` decimal(8,1) NOT NULL COMMENT '城市距台风中心距离（km）',
  `generated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录生成时间',
  PRIMARY KEY (`impact_id`),
  UNIQUE KEY `uk_impact_typhoon_city` (`typhoon_id`,`city_id`,`wind_circle_level`,`generated_at`),
  KEY `idx_impact_city` (`city_id`),
  KEY `idx_impact_time` (`impact_start_time`,`impact_end_time`),
  CONSTRAINT `fk_impact_city` FOREIGN KEY (`city_id`) REFERENCES `cities` (`city_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_impact_typhoon` FOREIGN KEY (`typhoon_id`) REFERENCES `typhoons` (`typhoon_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='城市影响记录表（风圈分析自动生成的城市受影响记录）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `city_impact_records`
--

LOCK TABLES `city_impact_records` WRITE;
/*!40000 ALTER TABLE `city_impact_records` DISABLE KEYS */;
INSERT INTO `city_impact_records` VALUES (1,3,3,12,12,'2023-07-28 06:00:00','2023-07-28 14:00:00',30.5,'2023-07-28 05:00:00'),(2,3,13,10,10,'2023-07-28 04:00:00','2023-07-28 16:00:00',85.0,'2023-07-28 05:00:00'),(3,3,4,7,8,'2023-07-28 02:00:00','2023-07-28 18:00:00',160.0,'2023-07-28 05:00:00'),(4,8,2,12,12,'2018-09-16 12:00:00','2018-09-16 20:00:00',60.0,'2018-09-16 10:00:00'),(5,8,1,10,10,'2018-09-16 10:00:00','2018-09-16 22:00:00',90.0,'2018-09-16 10:00:00'),(6,8,11,7,8,'2018-09-16 14:00:00','2018-09-17 02:00:00',180.0,'2018-09-16 10:00:00'),(7,9,3,12,12,'2016-09-15 00:00:00','2016-09-15 08:00:00',20.0,'2016-09-14 22:00:00'),(8,9,13,10,10,'2016-09-15 01:00:00','2016-09-15 12:00:00',70.0,'2016-09-14 22:00:00'),(9,4,7,10,10,'2022-09-14 18:00:00','2022-09-15 02:00:00',40.0,'2022-09-14 16:00:00'),(10,4,6,7,8,'2022-09-14 20:00:00','2022-09-15 06:00:00',100.0,'2022-09-14 16:00:00'),(11,4,8,7,8,'2022-09-15 02:00:00','2022-09-15 12:00:00',150.0,'2022-09-14 16:00:00'),(12,13,3,7,8,'2026-09-17 00:00:00','2026-09-17 12:00:00',220.0,'2026-09-15 09:00:00');
/*!40000 ALTER TABLE `city_impact_records` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `data_sources`
--

DROP TABLE IF EXISTS `data_sources`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `data_sources` (
  `source_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '数据源ID',
  `source_code` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据源代码',
  `source_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据源名称',
  `api_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '接口地址',
  `update_frequency` int unsigned NOT NULL DEFAULT '30' COMMENT '更新频率（分钟）',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '接入状态：1=启用，0=停用',
  `last_sync_time` datetime DEFAULT NULL COMMENT '最后同步时间',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '数据源说明',
  PRIMARY KEY (`source_id`),
  UNIQUE KEY `uk_sources_code` (`source_code`),
  KEY `idx_sources_enabled` (`enabled`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据源表（气象数据接口维护）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `data_sources`
--

LOCK TABLES `data_sources` WRITE;
/*!40000 ALTER TABLE `data_sources` DISABLE KEYS */;
INSERT INTO `data_sources` VALUES (1,'CMA_TP_API','中央气象台台风网接口','https://typhoon.nmc.cn/weatherservice/typhoon/jsons/list_default',30,1,'2026-09-15 09:00:00','台风实况与路径数据'),(2,'JTWC_BEST','联合台风警报中心最佳路径','https://www.metoc.navy.mil/jtwc/products/best-tracks/',60,1,'2026-09-15 09:05:00','历史最佳路径数据'),(3,'JMA_RSMC','日本气象厅台风资料','https://www.jma.go.jp/en/typh/',30,1,'2026-09-15 09:10:00','西北太平洋台风实况与预报'),(4,'CMA_SAT_CLOUD','中央气象台卫星云图','https://img.nsmc.org.cn/CLOUDIMAGE/',10,1,'2026-09-15 09:15:00','卫星云图影像'),(5,'ECMWF_ENS','欧洲中心集合预报','https://apps.ecmwf.int/datasets/',360,1,'2026-09-15 07:00:00','集合预报分歧分析'),(6,'GFS_FORECAST','美国GFS全球预报模式','https://nomads.ncep.noaa.gov/',360,1,'2026-09-15 07:00:00','全球模式预报'),(7,'HKO_TP','香港天文台热带气旋资料','https://www.hko.gov.hk/en/informtc/tcMain.htm',60,1,'2026-09-15 06:30:00','华南沿海台风信息'),(8,'KMA_TP','韩国气象厅台风资料','https://www.weather.go.kr/w/typoon/typoon.do',60,1,'2026-09-15 06:00:00','西北太平洋台风信息'),(9,'CMA_RADAR','中央气象台雷达拼图','https://radar.nmc.cn/',10,1,'2026-09-15 09:20:00','全国雷达拼图'),(10,'STI_ARCHIVE','上海台风研究所历史档案','https://www.sti.org.cn/',1440,1,'2026-09-15 00:00:00','历史台风全量档案');
/*!40000 ALTER TABLE `data_sources` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `disaster_reports`
--

DROP TABLE IF EXISTS `disaster_reports`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `disaster_reports` (
  `report_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '灾情报告ID',
  `typhoon_id` bigint unsigned NOT NULL COMMENT '台风ID',
  `affected_provinces` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '影响省份',
  `main_city_id` bigint unsigned DEFAULT NULL COMMENT '主要影响城市ID（可为空）',
  `direct_economic_loss` decimal(10,2) DEFAULT NULL COMMENT '直接经济损失（亿元）',
  `casualties` int DEFAULT NULL COMMENT '死亡及失踪人数',
  `injured` int DEFAULT NULL COMMENT '受伤人数',
  `affected_population` int DEFAULT NULL COMMENT '受灾人口（万人）',
  `disaster_summary` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '灾情综述',
  `report_source` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '报告来源',
  `report_time` datetime NOT NULL COMMENT '报告时间',
  PRIMARY KEY (`report_id`),
  KEY `idx_dr_typhoon` (`typhoon_id`),
  KEY `idx_dr_city` (`main_city_id`),
  KEY `idx_dr_time` (`report_time`),
  CONSTRAINT `fk_dr_city` FOREIGN KEY (`main_city_id`) REFERENCES `cities` (`city_id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_dr_typhoon` FOREIGN KEY (`typhoon_id`) REFERENCES `typhoons` (`typhoon_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='灾情报告表（关联台风的公开灾情信息）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `disaster_reports`
--

LOCK TABLES `disaster_reports` WRITE;
/*!40000 ALTER TABLE `disaster_reports` DISABLE KEYS */;
INSERT INTO `disaster_reports` VALUES (1,1,'广东、海南、广西',2,210.50,0,15,4000,'摩羯给华南沿海带来强风暴雨，广东多地出现内涝与树木倒伏。','公开资料近似整理','2024-09-15 10:00:00'),(2,3,'福建、浙江',3,180.20,1,12,3500,'杜苏芮正面登陆福建，闽南地区受灾较重。','公开资料近似整理','2023-08-05 10:00:00'),(3,4,'浙江、上海',7,75.60,0,5,1500,'梅花三登我国，舟山、宁波等地出现大风暴雨。','公开资料近似整理','2022-09-20 10:00:00'),(4,7,'浙江、山东、辽宁',6,515.30,70,NULL,1402,'利奇马为2019年登陆我国最强台风，山东出现严重内涝。','应急管理部公开数据（整理）','2019-08-20 10:00:00'),(5,8,'广东、广西、海南',2,53.00,6,NULL,329,'山竹强度强、移速快，广东沿海城市受损明显。','应急管理部公开数据（整理）','2018-09-25 10:00:00'),(6,9,'福建、浙江',3,210.73,43,49,300,'莫兰蒂重创厦门，市区大面积停电。','应急管理部公开数据（整理）','2016-09-30 10:00:00'),(7,10,'广东、澳门、香港',2,62.50,12,0,250,'天鸽登陆珠海，风暴潮致海水倒灌。','公开资料近似整理','2017-08-30 10:00:00'),(8,11,'海南、广东、广西',9,68.90,18,0,300,'威马逊为建国以来登陆我国最强台风之一，文昌损失惨重。','公开资料近似整理','2014-07-30 10:00:00'),(9,12,'海南',9,45.20,3,0,80,'莎莉嘉登陆海南万宁，全岛普降暴雨。','公开资料近似整理','2016-10-25 10:00:00'),(10,13,'福建、广东（预估）',NULL,NULL,NULL,NULL,NULL,'飞燕为模拟测试用活跃台风，暂无灾情数据。','模拟数据','2026-09-15 09:00:00');
/*!40000 ALTER TABLE `disaster_reports` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `export_records`
--

DROP TABLE IF EXISTS `export_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `export_records` (
  `export_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '导出记录ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `typhoon_id` bigint unsigned DEFAULT NULL COMMENT '关联台风ID（可为空）',
  `export_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '导出类型：CSV/SCREENSHOT/SANDBOX_SNAPSHOT',
  `file_url` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件存储路径',
  `file_size` int unsigned DEFAULT NULL COMMENT '文件大小（KB）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '导出时间',
  PRIMARY KEY (`export_id`),
  KEY `idx_export_user` (`user_id`),
  KEY `idx_export_typhoon` (`typhoon_id`),
  KEY `idx_export_type` (`export_type`),
  CONSTRAINT `fk_export_typhoon` FOREIGN KEY (`typhoon_id`) REFERENCES `typhoons` (`typhoon_id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_export_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='导出记录表（CSV/截图/沙盘快照导出历史）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `export_records`
--

LOCK TABLES `export_records` WRITE;
/*!40000 ALTER TABLE `export_records` DISABLE KEYS */;
INSERT INTO `export_records` VALUES (1,3,1,'CSV','/exports/user03/20240906_track.csv',45,'2024-09-06 20:00:00'),(2,4,8,'CSV','/exports/user04/2018_track_mangkhut.csv',52,'2018-09-18 10:00:00'),(3,5,13,'CSV','/exports/user05/feiyan_track.csv',38,'2026-09-15 08:30:00'),(4,5,13,'SANDBOX_SNAPSHOT','/exports/user05/sandbox_scenario_A.png',420,'2026-09-15 09:00:00'),(5,6,9,'SCREENSHOT','/exports/user06/meranti_map.png',780,'2016-09-15 12:00:00'),(6,7,3,'SCREENSHOT','/exports/user07/doksuri_map.png',640,'2023-07-28 15:00:00'),(7,8,4,'CSV','/exports/user08/muifa_forecast.csv',29,'2022-09-16 09:00:00'),(8,3,10,'SCREENSHOT','/exports/user03/hato_windcircle.png',560,'2017-08-23 15:00:00'),(9,4,11,'CSV','/exports/user04/rammasun_track.csv',33,'2014-07-21 10:00:00'),(10,5,2,'SCREENSHOT','/exports/user05/haikui_map.png',510,'2023-09-06 08:00:00');
/*!40000 ALTER TABLE `export_records` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `feedback_reports`
--

DROP TABLE IF EXISTS `feedback_reports`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `feedback_reports` (
  `feedback_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '反馈ID',
  `user_id` bigint unsigned DEFAULT NULL COMMENT '反馈用户ID（可为空）',
  `feedback_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '反馈类型：DATA_ISSUE/FEATURE_SUGGESTION/PROBLEM_REPORT',
  `content` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '反馈内容',
  `contact_info` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系方式（可选）：AES-GCM 密文（enc1: 前缀 + Base64），非明文；展示时由应用层打码',
  `status` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '处理状态：PENDING/PROCESSED',
  `handler_id` bigint unsigned DEFAULT NULL COMMENT '处理人ID（可为空）',
  `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  PRIMARY KEY (`feedback_id`),
  KEY `idx_fb_user` (`user_id`),
  KEY `idx_fb_status` (`status`),
  KEY `idx_fb_handler` (`handler_id`),
  CONSTRAINT `fk_fb_handler` FOREIGN KEY (`handler_id`) REFERENCES `users` (`user_id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_fb_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户反馈表（数据异常、功能建议与问题反馈）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `feedback_reports`
--

LOCK TABLES `feedback_reports` WRITE;
/*!40000 ALTER TABLE `feedback_reports` DISABLE KEYS */;
INSERT INTO `feedback_reports` VALUES (1,3,'DATA_ISSUE','台风路径某轨迹点气压数据与官方发布不一致。','user01@example.com','PROCESSED',2,'2024-09-07 10:00:00','2024-09-06 21:00:00'),(2,4,'FEATURE_SUGGESTION','建议增加台风影响范围内自动短信提醒功能。',NULL,'PROCESSED',1,'2024-09-08 09:00:00','2024-09-06 22:00:00'),(3,5,'PROBLEM_REPORT','沙盘推演页面在平板浏览器上按钮错位。','13800000005','PROCESSED',1,'2024-09-09 09:00:00','2024-09-07 08:00:00'),(4,6,'DATA_ISSUE','历史台风1988年某条轨迹缺失。',NULL,'PROCESSED',2,'2024-09-10 10:00:00','2024-09-08 09:00:00'),(5,7,'FEATURE_SUGGESTION','希望支持对比两个城市的风险评估结果。',NULL,'PENDING',NULL,NULL,'2026-09-10 08:00:00'),(6,8,'DATA_ISSUE','飞燕12级风圈半径显示偏小，请核对。','user06@example.com','PENDING',NULL,NULL,'2026-09-14 15:00:00'),(7,3,'PROBLEM_REPORT','导出CSV文件中文乱码，建议加BOM。',NULL,'PROCESSED',2,'2026-09-14 16:00:00','2026-09-13 09:00:00'),(8,4,'FEATURE_SUGGESTION','希望增加台风强度时间序列折线图。',NULL,'PROCESSED',1,'2026-09-14 17:00:00','2026-09-13 10:00:00'),(9,5,'DATA_ISSUE','预报分歧面板中JMA数据更新时间滞后。',NULL,'PENDING',NULL,NULL,'2026-09-15 08:00:00'),(10,6,'FEATURE_SUGGESTION','建议问卷增加“是否准备应急包”选项。',NULL,'PENDING',NULL,NULL,'2026-09-15 09:00:00');
/*!40000 ALTER TABLE `feedback_reports` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `forecast_agencies`
--

DROP TABLE IF EXISTS `forecast_agencies`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forecast_agencies` (
  `agency_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '机构ID',
  `agency_code` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '机构代码（CMA/JMA/JTWC等）',
  `agency_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '机构全称',
  `region` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属国家/地区',
  `display_color` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '#1E90FF' COMMENT '地图路径显示颜色（十六进制）',
  `official_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '官方网站',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '机构简介',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=启用，0=停用',
  PRIMARY KEY (`agency_id`),
  UNIQUE KEY `uk_agencies_code` (`agency_code`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预报机构表（CMA、JMA、JTWC等）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `forecast_agencies`
--

LOCK TABLES `forecast_agencies` WRITE;
/*!40000 ALTER TABLE `forecast_agencies` DISABLE KEYS */;
INSERT INTO `forecast_agencies` VALUES (1,'CMA','中央气象台','中国','#E60012','https://typhoon.nmc.cn','中国国家级气象预报机构，发布台风路径与强度预报',1),(2,'JMA','日本气象厅','日本','#0066CC','https://www.jma.go.jp','东京区域专业气象中心，负责西北太平洋台风命名',1),(3,'JTWC','美国联合台风警报中心','美国','#FF9900','https://www.metoc.navy.mil/jtwc','美国海军联合台风警报中心',1),(4,'HKO','香港天文台','中国香港','#00A650','https://www.hko.gov.hk','香港地区热带气旋监测预报机构',1),(5,'KMA','韩国气象厅','韩国','#8A2BE2','https://www.kma.go.kr','韩国国家气象服务部门',1),(6,'PAGASA','菲律宾大气地球物理和天文服务管理局','菲律宾','#FF00FF','https://www.pagasa.dost.gov.ph','菲律宾台风监测与命名机构',1),(7,'ECMWF','欧洲中期天气预报中心','欧洲','#964B00','https://www.ecmwf.int','全球集合预报与确定性模式',1),(8,'NMC','中国国家气象中心','中国','#2E8B57','https://www.nmc.cn','国家级天气预报业务机构',1),(9,'STI','中国气象局上海台风研究所','中国','#4169E1','https://www.sti.org.cn','台风研究与历史档案机构',1),(10,'NCHMF','越南国家水文气象预报中心','越南','#FF6347','https://nchmf.gov.vn','越南台风监测预报机构',1);
/*!40000 ALTER TABLE `forecast_agencies` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `forecast_disagree_records`
--

DROP TABLE IF EXISTS `forecast_disagree_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forecast_disagree_records` (
  `record_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '分歧记录ID',
  `typhoon_id` bigint unsigned NOT NULL COMMENT '台风ID',
  `analysis_time` datetime NOT NULL COMMENT '分析时间',
  `landing_point_deviation_km` decimal(8,1) NOT NULL COMMENT '登陆点偏差（km）',
  `landing_time_deviation_hours` decimal(5,1) NOT NULL COMMENT '登陆时间偏差（小时）',
  `peak_intensity_deviation` decimal(5,1) NOT NULL COMMENT '峰值强度偏差（级）',
  `move_speed_deviation` decimal(5,1) NOT NULL COMMENT '移动速度偏差（km/h）',
  `variance_score` decimal(8,2) NOT NULL COMMENT '综合分歧方差值',
  `is_high_uncertainty` tinyint NOT NULL DEFAULT '0' COMMENT '高不确定性标记：1=是，0=否',
  `analysis_detail` json DEFAULT NULL COMMENT '分歧分析明细（JSON）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`record_id`),
  KEY `idx_fdr_typhoon_time` (`typhoon_id`,`analysis_time`),
  KEY `idx_fdr_uncertainty` (`is_high_uncertainty`),
  CONSTRAINT `fk_fdr_typhoon` FOREIGN KEY (`typhoon_id`) REFERENCES `typhoons` (`typhoon_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预报分歧分析记录表（多机构预报分歧量化结果）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `forecast_disagree_records`
--

LOCK TABLES `forecast_disagree_records` WRITE;
/*!40000 ALTER TABLE `forecast_disagree_records` DISABLE KEYS */;
INSERT INTO `forecast_disagree_records` VALUES (1,13,'2026-09-15 08:30:00',180.5,8.0,4.0,6.5,92.30,1,'{\"note\": \"登陆点分歧较大，可信度较低\", \"agencies\": [\"CMA\", \"JMA\", \"JTWC\"], \"landing_points\": {\"CMA\": [23.6, 119.0], \"JMA\": [24.8, 118.6], \"JTWC\": [22.2, 119.8]}}','2026-09-15 08:30:00'),(2,13,'2026-09-14 08:30:00',95.0,4.0,2.0,3.5,45.20,0,'{\"trend\": \"分歧收敛\", \"agencies\": [\"CMA\", \"JMA\", \"JTWC\"]}','2026-09-14 08:30:00'),(3,1,'2024-09-06 08:30:00',60.0,3.0,1.0,2.0,22.10,0,'{\"trend\": \"分歧收敛\", \"agencies\": [\"CMA\", \"JMA\", \"JTWC\"]}','2024-09-06 08:30:00'),(4,3,'2023-07-27 08:30:00',75.0,5.0,2.0,4.0,38.50,0,'{\"trend\": \"登陆点略有分歧\", \"agencies\": [\"CMA\", \"JMA\"]}','2023-07-27 08:30:00'),(5,5,'2021-09-10 08:00:00',150.0,12.0,3.0,5.0,78.60,1,'{\"note\": \"部分模式预报转向出海\", \"agencies\": [\"CMA\", \"JMA\", \"JTWC\"]}','2021-09-10 08:00:00'),(6,8,'2018-09-15 08:00:00',45.0,2.0,1.0,1.5,12.80,0,'{\"trend\": \"各家一致\", \"agencies\": [\"CMA\", \"JMA\", \"JTWC\"]}','2018-09-15 08:00:00'),(7,9,'2016-09-13 08:00:00',50.0,3.0,1.0,2.5,18.90,0,'{\"trend\": \"一致偏西\", \"agencies\": [\"CMA\", \"JTWC\"]}','2016-09-13 08:00:00'),(8,10,'2017-08-22 08:00:00',55.0,4.0,2.0,3.0,25.60,0,'{\"trend\": \"登陆点趋同\", \"agencies\": [\"CMA\", \"JMA\"]}','2017-08-22 08:00:00'),(9,11,'2014-07-17 08:00:00',40.0,2.0,1.0,2.0,15.20,0,'{\"trend\": \"各家一致\", \"agencies\": [\"CMA\", \"JMA\"]}','2014-07-17 08:00:00'),(10,2,'2023-08-30 08:00:00',110.0,6.0,2.0,4.5,52.40,1,'{\"note\": \"转向路径分歧明显\", \"agencies\": [\"CMA\", \"JMA\"]}','2023-08-30 08:00:00');
/*!40000 ALTER TABLE `forecast_disagree_records` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `forecast_paths`
--

DROP TABLE IF EXISTS `forecast_paths`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forecast_paths` (
  `forecast_point_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '预报点ID',
  `typhoon_id` bigint unsigned NOT NULL COMMENT '台风ID',
  `agency_id` bigint unsigned NOT NULL COMMENT '预报机构ID',
  `publish_time` datetime NOT NULL COMMENT '预报发布时间',
  `forecast_time` datetime NOT NULL COMMENT '预报目标时刻',
  `latitude` decimal(7,4) NOT NULL COMMENT '预报纬度',
  `longitude` decimal(8,4) NOT NULL COMMENT '预报经度',
  `forecast_wind_force` tinyint DEFAULT NULL COMMENT '预报中心最大风力（级）',
  `forecast_wind_speed` int DEFAULT NULL COMMENT '预报中心最大风速（m/s）',
  `forecast_pressure` int DEFAULT NULL COMMENT '预报中心气压（hPa）',
  PRIMARY KEY (`forecast_point_id`),
  KEY `idx_fp_typhoon` (`typhoon_id`),
  KEY `idx_fp_agency` (`agency_id`),
  KEY `idx_fp_publish_time` (`publish_time`),
  KEY `idx_fp_typhoon_agency` (`typhoon_id`,`agency_id`,`publish_time`),
  CONSTRAINT `fk_fp_agency` FOREIGN KEY (`agency_id`) REFERENCES `forecast_agencies` (`agency_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_fp_typhoon` FOREIGN KEY (`typhoon_id`) REFERENCES `typhoons` (`typhoon_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预报路径表（各机构发布的预报路径点）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `forecast_paths`
--

LOCK TABLES `forecast_paths` WRITE;
/*!40000 ALTER TABLE `forecast_paths` DISABLE KEYS */;
INSERT INTO `forecast_paths` VALUES (1,13,1,'2026-09-15 08:00:00','2026-09-15 20:00:00',20.5000,122.0000,12,38,965),(2,13,1,'2026-09-15 08:00:00','2026-09-16 08:00:00',21.8000,120.5000,13,40,955),(3,13,1,'2026-09-15 08:00:00','2026-09-17 08:00:00',23.6000,119.0000,14,42,950),(4,13,2,'2026-09-15 08:00:00','2026-09-15 20:00:00',20.8000,121.8000,13,40,960),(5,13,2,'2026-09-15 08:00:00','2026-09-16 08:00:00',22.5000,120.2000,14,43,950),(6,13,2,'2026-09-15 08:00:00','2026-09-17 08:00:00',24.8000,118.6000,15,48,940),(7,13,3,'2026-09-15 08:00:00','2026-09-15 20:00:00',20.2000,122.4000,12,38,965),(8,13,3,'2026-09-15 08:00:00','2026-09-16 08:00:00',21.0000,121.0000,13,40,960),(9,13,3,'2026-09-15 08:00:00','2026-09-17 08:00:00',22.2000,119.8000,14,42,955),(10,1,1,'2024-09-06 08:00:00','2024-09-06 20:00:00',21.0000,114.5000,15,48,940),(11,1,2,'2024-09-06 08:00:00','2024-09-06 20:00:00',20.5000,115.0000,14,45,945),(12,1,3,'2024-09-06 08:00:00','2024-09-06 20:00:00',22.0000,113.8000,15,48,940);
/*!40000 ALTER TABLE `forecast_paths` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `historical_comparisons`
--

DROP TABLE IF EXISTS `historical_comparisons`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `historical_comparisons` (
  `comparison_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '对比任务ID',
  `user_id` bigint unsigned NOT NULL COMMENT '创建用户ID',
  `comparison_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '对比名称',
  `primary_typhoon_id` bigint unsigned NOT NULL COMMENT '主对比台风ID',
  `compare_typhoon_ids` json NOT NULL COMMENT '对比台风ID数组，如[8,9]',
  `result_summary` json DEFAULT NULL COMMENT '对比结果摘要（JSON）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`comparison_id`),
  KEY `idx_hc_user` (`user_id`),
  KEY `idx_hc_primary_typhoon` (`primary_typhoon_id`),
  CONSTRAINT `fk_hc_primary_typhoon` FOREIGN KEY (`primary_typhoon_id`) REFERENCES `typhoons` (`typhoon_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_hc_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='历史对比任务表（多台风路径对比配置与结果）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `historical_comparisons`
--

LOCK TABLES `historical_comparisons` WRITE;
/*!40000 ALTER TABLE `historical_comparisons` DISABLE KEYS */;
INSERT INTO `historical_comparisons` VALUES (1,3,'摩羯vs山竹路径对比',1,'[8]','{\"conclusion\": \"路径高度相似\", \"path_diff_km\": 85, \"intensity_diff\": 2}','2024-09-07 10:00:00'),(2,3,'摩羯vs莫兰蒂对比',1,'[9]','{\"conclusion\": \"强度相当\", \"path_diff_km\": 120, \"intensity_diff\": 1}','2024-09-07 11:00:00'),(3,4,'杜苏芮vs天鸽对比',3,'[10]','{\"conclusion\": \"登陆点略有差异\", \"path_diff_km\": 150, \"intensity_diff\": 1}','2023-07-28 20:00:00'),(4,5,'梅花vs烟花路径对比',4,'[6]','{\"conclusion\": \"华东路径相似\", \"path_diff_km\": 60, \"intensity_diff\": 1}','2022-09-15 09:00:00'),(5,6,'山竹vs威马逊强度对比',8,'[11]','{\"conclusion\": \"同为极强台风\", \"path_diff_km\": 200, \"intensity_diff\": 0}','2018-09-18 10:00:00'),(6,7,'利奇马vs烟花对比',7,'[6]','{\"conclusion\": \"利奇马更强\", \"path_diff_km\": 90, \"intensity_diff\": 3}','2019-08-14 09:00:00'),(7,8,'灿都vs莫兰蒂对比',5,'[9]','{\"conclusion\": \"强度接近\", \"path_diff_km\": 100, \"intensity_diff\": 0}','2021-09-18 10:00:00'),(8,3,'飞燕vs摩羯对比',13,'[1]','{\"conclusion\": \"飞燕弱于摩羯\", \"path_diff_km\": 80, \"intensity_diff\": 5}','2026-09-15 09:30:00'),(9,4,'飞燕vs杜苏芮对比',13,'[3]','{\"conclusion\": \"飞燕弱于杜苏芮\", \"path_diff_km\": 95, \"intensity_diff\": 4}','2026-09-15 09:35:00'),(10,5,'海葵vs利奇马对比',2,'[7]','{\"conclusion\": \"利奇马更强\", \"path_diff_km\": 140, \"intensity_diff\": 2}','2023-09-06 10:00:00');
/*!40000 ALTER TABLE `historical_comparisons` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `llm_provider_config`
--

DROP TABLE IF EXISTS `llm_provider_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `llm_provider_config` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='大模型厂商接入配置（密钥密文存储）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `llm_provider_config`
--

LOCK TABLES `llm_provider_config` WRITE;
/*!40000 ALTER TABLE `llm_provider_config` DISABLE KEYS */;
INSERT INTO `llm_provider_config` VALUES (1,'deepseek-v3','DeepSeek-V3','openai-compatible',NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-18 21:10:00','2026-10-18 21:10:00'),(2,'qwen-max','通义千问-max','openai-compatible',NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-18 21:10:00','2026-10-18 21:10:00'),(3,'doubao-seed-1.6','豆包 Seed-1.6','doubao',NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-18 21:10:00','2026-10-18 21:10:00'),(4,'ernie-4.0','文心一言 4.0','qianfan',NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-18 21:10:00','2026-10-18 21:10:00');
/*!40000 ALTER TABLE `llm_provider_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `login_attempts`
--

DROP TABLE IF EXISTS `login_attempts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `login_attempts` (
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录名（与 users.username 同口径）',
  `fail_count` int unsigned NOT NULL DEFAULT 0 COMMENT '连续失败次数（触发锁定后归零）',
  `last_fail_at` datetime DEFAULT NULL COMMENT '最近一次失败时间，用于判定是否属于「连续」爆破',
  `locked_until` datetime DEFAULT NULL COMMENT '锁定截止时间；NULL 表示未锁定',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`username`),
  KEY `idx_login_locked` (`locked_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录失败锁定状态表（持久化，Tomcat 重启不清零；不建外键是因为不存在的账号也要能记失败）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `login_attempts`
--

LOCK TABLES `login_attempts` WRITE;
/*!40000 ALTER TABLE `login_attempts` DISABLE KEYS */;
/*!40000 ALTER TABLE `login_attempts` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `notifications`
--

DROP TABLE IF EXISTS `notifications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notifications` (
  `notification_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '通知ID',
  `user_id` bigint unsigned NOT NULL COMMENT '接收用户ID',
  `typhoon_id` bigint unsigned DEFAULT NULL COMMENT '关联台风ID（可为空）',
  `city_id` bigint unsigned DEFAULT NULL COMMENT '关联城市ID（可为空）',
  `notify_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知类型：NEW_STORM/STRENGTH_CHANGE/PATH_CHANGE/SYSTEM',
  `title` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知标题',
  `content` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知内容',
  `is_read` tinyint NOT NULL DEFAULT '0' COMMENT '是否已读：1=已读，0=未读',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  PRIMARY KEY (`notification_id`),
  KEY `idx_notif_user_read` (`user_id`,`is_read`),
  KEY `idx_notif_typhoon` (`typhoon_id`),
  KEY `idx_notif_city` (`city_id`),
  KEY `idx_notif_type` (`notify_type`),
  CONSTRAINT `fk_notif_city` FOREIGN KEY (`city_id`) REFERENCES `cities` (`city_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_notif_typhoon` FOREIGN KEY (`typhoon_id`) REFERENCES `typhoons` (`typhoon_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_notif_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知消息表（系统推送的台风预警、强度变化、路径调整等通知）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `notifications`
--

LOCK TABLES `notifications` WRITE;
/*!40000 ALTER TABLE `notifications` DISABLE KEYS */;
INSERT INTO `notifications` VALUES (1,3,13,NULL,'NEW_STORM','新台风生成','台风“飞燕”已生成，请关注最新动态。',0,'2026-09-08 08:30:00'),(2,3,13,NULL,'PATH_CHANGE','飞燕路径调整','飞燕预报路径向西调整，请留意影响范围变化。',0,'2026-09-14 09:00:00'),(3,4,13,NULL,'STRENGTH_CHANGE','飞燕强度加强','飞燕已加强为台风级（12级），请做好防范。',0,'2026-09-15 08:00:00'),(4,5,NULL,3,'SYSTEM','台风科普提示','台风临近，请提前储备物资并关注撤离信息。',1,'2026-09-15 09:00:00'),(5,6,1,NULL,'SYSTEM','台风回顾','摩羯台风档案已归档，可查看历史复盘。',1,'2024-09-10 10:00:00'),(6,7,3,NULL,'STRENGTH_CHANGE','杜苏芮强度变化','杜苏芮已加强为强台风级。',1,'2023-07-26 08:00:00'),(7,8,8,NULL,'NEW_STORM','山竹生成提醒','超强台风山竹已生成，华南沿海需警惕。',1,'2018-09-07 09:00:00'),(8,3,4,NULL,'PATH_CHANGE','梅花路径调整','梅花将在浙江沿海登陆。',1,'2022-09-14 08:00:00'),(9,4,9,NULL,'STRENGTH_CHANGE','莫兰蒂超强','莫兰蒂达到超强台风级（17级）。',1,'2016-09-13 12:00:00'),(10,5,10,NULL,'NEW_STORM','天鸽生成','台风天鸽已生成，趋向广东沿海。',1,'2017-08-20 09:00:00'),(11,6,11,NULL,'STRENGTH_CHANGE','威马逊极强','威马逊强度极高，海南严阵以待。',1,'2014-07-18 10:00:00'),(12,7,13,NULL,'SYSTEM','问卷邀请','欢迎参与防灾行为问卷，帮助优化科普内容。',0,'2026-09-15 10:00:00');
/*!40000 ALTER TABLE `notifications` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `risk_assessments`
--

DROP TABLE IF EXISTS `risk_assessments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `risk_assessments` (
  `assessment_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '评估ID',
  `typhoon_id` bigint unsigned NOT NULL COMMENT '台风ID',
  `city_id` bigint unsigned NOT NULL COMMENT '城市ID',
  `user_id` bigint unsigned NOT NULL COMMENT '评估用户ID',
  `assessment_time` datetime NOT NULL COMMENT '评估时间',
  `obs_time` datetime DEFAULT NULL COMMENT '评估基准观测时刻（台风实测时次），ETA 由此刻起算',
  `distance_km` decimal(8,1) NOT NULL COMMENT '台风中心距城市距离（km）',
  `eta_time` datetime DEFAULT NULL COMMENT '预计到达时间',
  `affected_start_time` datetime DEFAULT NULL COMMENT '受影响开始时段',
  `affected_end_time` datetime DEFAULT NULL COMMENT '受影响结束时段',
  `risk_level` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '风险等级：LOW/MEDIUM/HIGH/EXTREME',
  `risk_score` decimal(5,2) NOT NULL COMMENT '风险评分（0-100）',
  `suggestion` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '风险提示建议',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`assessment_id`),
  KEY `idx_ra_typhoon` (`typhoon_id`),
  KEY `idx_ra_city` (`city_id`),
  KEY `idx_ra_user` (`user_id`),
  KEY `idx_ra_time` (`assessment_time`),
  CONSTRAINT `fk_ra_city` FOREIGN KEY (`city_id`) REFERENCES `cities` (`city_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_ra_typhoon` FOREIGN KEY (`typhoon_id`) REFERENCES `typhoons` (`typhoon_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_ra_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='风险评估表（用户城市风险评估结果）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `risk_assessments`
--

LOCK TABLES `risk_assessments` WRITE;
/*!40000 ALTER TABLE `risk_assessments` DISABLE KEYS */;
INSERT INTO `risk_assessments` (`assessment_id`,`typhoon_id`,`city_id`,`user_id`,
  `assessment_time`,`distance_km`,`eta_time`,`affected_start_time`,`affected_end_time`,
  `risk_level`,`risk_score`,`suggestion`,`created_at`) VALUES (1,3,3,3,'2023-07-27 20:00:00',35.0,'2023-07-28 06:00:00','2023-07-28 06:00:00','2023-07-28 14:00:00','HIGH',78.50,'预计24小时内正面影响厦门，请做好防风加固与物资储备。','2026-09-16 11:08:06'),(2,8,2,4,'2018-09-16 08:00:00',65.0,'2018-09-16 12:00:00','2018-09-16 12:00:00','2018-09-16 20:00:00','HIGH',75.00,'台风即将登陆，广州风雨显著增强，避免外出。','2026-09-16 11:08:06'),(3,8,1,5,'2018-09-16 08:00:00',95.0,'2018-09-16 14:00:00','2018-09-16 14:00:00','2018-09-17 02:00:00','MEDIUM',62.00,'深圳有12级阵风风险，远离海边与危房。','2026-09-16 11:08:06'),(4,9,3,6,'2016-09-14 12:00:00',25.0,'2016-09-15 03:00:00','2016-09-15 03:00:00','2016-09-15 08:00:00','EXTREME',92.00,'超强台风正面登陆厦门，风险极高，务必遵从官方转移指令。','2026-09-16 11:08:06'),(5,4,7,3,'2022-09-14 12:00:00',45.0,'2022-09-14 20:00:00','2022-09-14 20:00:00','2022-09-15 02:00:00','HIGH',72.00,'舟山处于10级风圈内，船只务必回港避风。','2026-09-16 11:08:06'),(6,4,6,7,'2022-09-14 12:00:00',105.0,'2022-09-15 02:00:00','2022-09-15 02:00:00','2022-09-15 08:00:00','MEDIUM',58.00,'宁波风雨较大，注意低洼区域积水。','2026-09-16 11:08:06'),(7,13,3,5,'2026-09-15 09:00:00',220.0,'2026-09-17 00:00:00','2026-09-17 00:00:00','2026-09-17 12:00:00','MEDIUM',55.00,'模拟推演：飞燕路径存在分歧，请持续关注官方预报。','2026-09-16 11:08:06'),(8,13,9,6,'2026-09-15 09:00:00',520.0,'2026-09-18 08:00:00','2026-09-18 08:00:00','2026-09-18 20:00:00','LOW',30.00,'当前距离较远，建议保持关注。','2026-09-16 11:08:06'),(9,13,4,8,'2026-09-15 09:00:00',260.0,'2026-09-17 04:00:00','2026-09-17 04:00:00','2026-09-17 16:00:00','MEDIUM',52.00,'福州需防范强风与城市内涝。','2026-09-16 11:08:06'),(10,1,2,3,'2024-09-06 08:00:00',150.0,'2024-09-06 18:00:00','2024-09-06 18:00:00','2024-09-07 02:00:00','MEDIUM',60.00,'广州受台风外围环流影响，有强降雨。','2026-09-16 11:08:06'),(11,10,1,4,'2017-08-23 06:00:00',50.0,'2017-08-23 09:00:00','2017-08-23 09:00:00','2017-08-23 18:00:00','HIGH',76.00,'天鸽带来狂风暴雨，深圳全市停课停工。','2026-09-16 11:08:06'),(12,11,9,5,'2014-07-18 12:00:00',80.0,'2014-07-18 19:00:00','2014-07-18 19:00:00','2014-07-19 06:00:00','HIGH',80.00,'威马逊强度极强，海口须防风暴潮。','2026-09-16 11:08:06');
/*!40000 ALTER TABLE `risk_assessments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sandbox_scenarios`
--

DROP TABLE IF EXISTS `sandbox_scenarios`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sandbox_scenarios` (
  `scenario_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '情景ID',
  `user_id` bigint unsigned NOT NULL COMMENT '创建用户ID',
  `base_typhoon_id` bigint unsigned NOT NULL COMMENT '基底台风ID',
  `scenario_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '情景名称',
  `scenario_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '情景类型：BASELINE=基准/SCENARIO_A/SCENARIO_B',
  `params_json` json NOT NULL COMMENT '修改参数（登陆点偏移、移速、强度、风圈）',
  `result_json` json DEFAULT NULL COMMENT '推演结果快照（受影响城市、风险等级）',
  `is_public` tinyint NOT NULL DEFAULT '0' COMMENT '是否公开分享：1=是，0=否',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`scenario_id`),
  KEY `idx_ss_user` (`user_id`),
  KEY `idx_ss_base_typhoon` (`base_typhoon_id`),
  KEY `idx_ss_type` (`scenario_type`),
  CONSTRAINT `fk_ss_base_typhoon` FOREIGN KEY (`base_typhoon_id`) REFERENCES `typhoons` (`typhoon_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_ss_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='沙盘模拟情景表（多套推演情景与结果快照）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sandbox_scenarios`
--

LOCK TABLES `sandbox_scenarios` WRITE;
/*!40000 ALTER TABLE `sandbox_scenarios` DISABLE KEYS */;
INSERT INTO `sandbox_scenarios` VALUES (1,5,13,'基准情景（官方预报）','BASELINE','{\"circle_scale\": 1.0, \"speed_factor\": 1.0, \"intensity_offset\": 0, \"landing_offset_km\": 0}','{\"risk_level\": \"MEDIUM\", \"max_wind_city\": \"厦门\", \"affected_cities\": [\"厦门\", \"福州\", \"泉州\"]}',1,'2026-09-15 08:00:00','2026-09-15 08:00:00'),(2,5,13,'情景A（偏西登陆）','SCENARIO_A','{\"circle_scale\": 1.1, \"speed_factor\": 1.0, \"intensity_offset\": 1, \"landing_offset_km\": -80}','{\"note\": \"登陆点偏西，粤东影响加大\", \"risk_level\": \"HIGH\", \"max_wind_city\": \"汕头\", \"affected_cities\": [\"汕头\", \"厦门\", \"泉州\"]}',1,'2026-09-15 08:10:00','2026-09-15 08:10:00'),(3,5,13,'情景B（快速转向出海）','SCENARIO_B','{\"circle_scale\": 0.9, \"speed_factor\": 1.4, \"intensity_offset\": -1, \"landing_offset_km\": 150}','{\"note\": \"转向出海，大陆影响减弱\", \"risk_level\": \"LOW\", \"max_wind_city\": \"舟山\", \"affected_cities\": [\"舟山\", \"宁波\"]}',1,'2026-09-15 08:20:00','2026-09-15 08:20:00'),(4,6,1,'摩羯基准情景','BASELINE','{\"circle_scale\": 1.0, \"speed_factor\": 1.0, \"intensity_offset\": 0, \"landing_offset_km\": 0}','{\"risk_level\": \"HIGH\", \"affected_cities\": [\"广州\", \"深圳\", \"湛江\"]}',0,'2024-09-05 10:00:00','2024-09-05 10:00:00'),(5,6,1,'摩羯情景A（北抬）','SCENARIO_A','{\"circle_scale\": 1.0, \"speed_factor\": 0.9, \"intensity_offset\": 0, \"landing_offset_km\": 120}','{\"risk_level\": \"MEDIUM\", \"affected_cities\": [\"汕头\", \"厦门\", \"福州\"]}',0,'2024-09-05 10:10:00','2024-09-05 10:10:00'),(6,7,3,'杜苏芮情景A（闽南登陆）','SCENARIO_A','{\"circle_scale\": 1.2, \"speed_factor\": 1.1, \"intensity_offset\": 1, \"landing_offset_km\": 0}','{\"note\": \"强度上调，闽南风险极高\", \"risk_level\": \"EXTREME\", \"affected_cities\": [\"厦门\", \"泉州\", \"福州\"]}',0,'2023-07-27 15:00:00','2023-07-27 15:00:00'),(7,7,3,'杜苏芮情景B（减弱北上）','SCENARIO_B','{\"circle_scale\": 0.8, \"speed_factor\": 1.2, \"intensity_offset\": -2, \"landing_offset_km\": 60}','{\"risk_level\": \"LOW\", \"affected_cities\": [\"福州\", \"温州\"]}',0,'2023-07-27 15:10:00','2023-07-27 15:10:00'),(8,8,4,'梅花基准情景','BASELINE','{\"circle_scale\": 1.0, \"speed_factor\": 1.0, \"intensity_offset\": 0, \"landing_offset_km\": 0}','{\"risk_level\": \"MEDIUM\", \"affected_cities\": [\"舟山\", \"宁波\", \"上海\"]}',1,'2022-09-13 10:00:00','2022-09-13 10:00:00'),(9,8,8,'山竹情景A（偏西）','SCENARIO_A','{\"circle_scale\": 1.1, \"speed_factor\": 1.0, \"intensity_offset\": 0, \"landing_offset_km\": -60}','{\"risk_level\": \"HIGH\", \"affected_cities\": [\"湛江\", \"海口\"]}',0,'2018-09-15 10:00:00','2018-09-15 10:00:00'),(10,3,13,'飞燕情景A（增强西行）','SCENARIO_A','{\"circle_scale\": 1.3, \"speed_factor\": 0.8, \"intensity_offset\": 2, \"landing_offset_km\": -40}','{\"note\": \"移速减慢暴雨累积量增大\", \"risk_level\": \"HIGH\", \"affected_cities\": [\"厦门\", \"泉州\", \"汕头\"]}',1,'2026-09-15 09:00:00','2026-09-15 09:00:00');
/*!40000 ALTER TABLE `sandbox_scenarios` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `similar_case_matches`
--

DROP TABLE IF EXISTS `similar_case_matches`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `similar_case_matches` (
  `match_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '匹配记录ID',
  `current_typhoon_id` bigint unsigned NOT NULL COMMENT '当前台风ID',
  `history_typhoon_id` bigint unsigned NOT NULL COMMENT '历史相似台风ID',
  `similarity_score` decimal(5,4) NOT NULL COMMENT '综合相似度（0-1）',
  `path_similarity` decimal(5,4) NOT NULL COMMENT '路径形态相似度',
  `peak_intensity_similarity` decimal(5,4) NOT NULL COMMENT '峰值强度相似度',
  `season_similarity` decimal(5,4) NOT NULL COMMENT '登陆季节相似度',
  `move_speed_similarity` decimal(5,4) NOT NULL COMMENT '移速相似度',
  `wind_circle_similarity` decimal(5,4) NOT NULL COMMENT '风圈规模相似度',
  `match_result` json DEFAULT NULL COMMENT '匹配结果与提示（JSON）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`match_id`),
  UNIQUE KEY `uk_scm_pair` (`current_typhoon_id`,`history_typhoon_id`),
  KEY `idx_scm_history` (`history_typhoon_id`),
  KEY `idx_scm_score` (`similarity_score`),
  CONSTRAINT `fk_scm_current` FOREIGN KEY (`current_typhoon_id`) REFERENCES `typhoons` (`typhoon_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_scm_history` FOREIGN KEY (`history_typhoon_id`) REFERENCES `typhoons` (`typhoon_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='相似案例匹配记录表（当前台风与历史台风相似度打分）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `similar_case_matches`
--

LOCK TABLES `similar_case_matches` WRITE;
/*!40000 ALTER TABLE `similar_case_matches` DISABLE KEYS */;
INSERT INTO `similar_case_matches` VALUES (1,13,1,0.8600,0.8800,0.9000,0.9200,0.8000,0.8200,'{\"matched\": true, \"highlight\": \"路径与强度接近摩羯，需关注登陆点分歧\"}','2026-09-15 09:00:00'),(2,13,3,0.8100,0.8400,0.8500,0.9000,0.7500,0.7000,'{\"matched\": true, \"highlight\": \"与杜苏芮路径相似，警惕闽南沿海\"}','2026-09-15 09:00:00'),(3,13,8,0.7800,0.8000,0.8800,0.8500,0.7000,0.6600,'{\"matched\": true, \"highlight\": \"强度接近山竹\"}','2026-09-15 09:00:00'),(4,13,9,0.7500,0.8200,0.8500,0.8000,0.6500,0.6200,'{\"matched\": true, \"highlight\": \"形态接近莫兰蒂\"}','2026-09-15 09:00:00'),(5,13,11,0.7200,0.7500,0.8800,0.7000,0.6000,0.6600,'{\"matched\": true, \"highlight\": \"极端强度类似威马逊\"}','2026-09-15 09:00:00'),(6,1,8,0.8800,0.9000,0.9200,0.9500,0.8200,0.8400,'{\"matched\": true, \"highlight\": \"摩羯与山竹高度相似\"}','2024-09-06 09:00:00'),(7,1,9,0.8200,0.8500,0.8800,0.9000,0.7600,0.7200,'{\"matched\": true}','2024-09-06 09:00:00'),(8,3,10,0.7900,0.8200,0.8600,0.8500,0.7400,0.7000,'{\"matched\": true}','2023-07-27 09:00:00'),(9,4,6,0.8300,0.8600,0.8800,0.9200,0.7800,0.7400,'{\"matched\": true, \"highlight\": \"路径与烟花相似，华东沿海\"}','2022-09-13 09:00:00'),(10,7,6,0.8000,0.8400,0.8600,0.8800,0.7200,0.7000,'{\"matched\": true}','2019-08-08 09:00:00'),(11,5,9,0.7700,0.8000,0.8500,0.8200,0.7000,0.6800,'{\"matched\": true}','2021-09-12 09:00:00'),(12,2,7,0.7100,0.7400,0.8000,0.8500,0.6200,0.5500,'{\"matched\": true}','2023-09-01 09:00:00');
/*!40000 ALTER TABLE `similar_case_matches` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `survey_agg_results`
--

DROP TABLE IF EXISTS `survey_agg_results`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `survey_agg_results` (
  `agg_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '聚合记录ID',
  `typhoon_id` bigint unsigned NOT NULL COMMENT '台风ID',
  `city_id` bigint unsigned NOT NULL COMMENT '城市ID',
  `total_count` int NOT NULL COMMENT '有效样本量',
  `supply_prep_rate` decimal(5,2) NOT NULL COMMENT '物资储备率（%）',
  `evacuation_attention_rate` decimal(5,2) NOT NULL COMMENT '撤离信息关注率（%）',
  `heavy_rain_worry_rate` decimal(5,2) NOT NULL COMMENT '担心暴雨内涝比例（%）',
  `storm_surge_worry_rate` decimal(5,2) NOT NULL COMMENT '担心风暴潮比例（%）',
  `high_trust_rate` decimal(5,2) NOT NULL COMMENT '高可信度比例（%）',
  `risk_overestimation_rate` decimal(5,2) NOT NULL COMMENT '高估风险比例（%）',
  `risk_underestimation_rate` decimal(5,2) NOT NULL COMMENT '低估风险比例（%）',
  `stat_time` datetime NOT NULL COMMENT '统计时间',
  PRIMARY KEY (`agg_id`),
  UNIQUE KEY `uk_sar_typhoon_city` (`typhoon_id`,`city_id`),
  KEY `idx_sar_city` (`city_id`),
  KEY `idx_sar_stat_time` (`stat_time`),
  CONSTRAINT `fk_sar_city` FOREIGN KEY (`city_id`) REFERENCES `cities` (`city_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_sar_typhoon` FOREIGN KEY (`typhoon_id`) REFERENCES `typhoons` (`typhoon_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='调研聚合统计表（按城市、台风聚合的群体防灾行为统计）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `survey_agg_results`
--

LOCK TABLES `survey_agg_results` WRITE;
/*!40000 ALTER TABLE `survey_agg_results` DISABLE KEYS */;
INSERT INTO `survey_agg_results` VALUES (1,13,3,120,66.50,85.00,45.00,30.00,70.00,15.00,10.00,'2026-09-15 10:00:00'),(2,13,4,80,62.50,80.00,55.00,20.00,65.00,12.00,15.00,'2026-09-15 10:00:00'),(3,13,1,95,70.00,88.00,35.00,42.00,72.00,10.00,8.00,'2026-09-15 10:00:00'),(4,13,9,60,58.00,75.00,30.00,50.00,60.00,18.00,12.00,'2026-09-15 10:00:00'),(5,1,2,150,72.00,90.00,48.00,25.00,75.00,8.00,9.00,'2024-09-07 10:00:00'),(6,1,11,70,60.00,78.00,52.00,22.00,62.00,14.00,13.00,'2024-09-07 10:00:00'),(7,3,3,200,68.00,86.00,42.00,35.00,71.00,11.00,10.00,'2023-07-29 10:00:00'),(8,8,2,180,65.00,82.00,40.00,38.00,69.00,13.00,12.00,'2018-09-18 10:00:00'),(9,9,3,90,70.00,89.00,44.00,32.00,73.00,9.00,8.00,'2016-09-17 10:00:00'),(10,10,1,110,64.00,84.00,38.00,36.00,68.00,12.00,11.00,'2017-08-25 10:00:00');
/*!40000 ALTER TABLE `survey_agg_results` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sync_logs`
--

DROP TABLE IF EXISTS `sync_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sync_logs` (
  `log_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `source_id` bigint unsigned NOT NULL COMMENT '数据源ID',
  `sync_time` datetime NOT NULL COMMENT '同步时间',
  `record_count` int NOT NULL DEFAULT '0' COMMENT '同步记录条数',
  `sync_status` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '同步状态：SUCCESS/FAILED',
  `error_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '异常信息',
  `duration_seconds` int DEFAULT NULL COMMENT '同步耗时（秒）',
  PRIMARY KEY (`log_id`),
  KEY `idx_sync_source_time` (`source_id`,`sync_time`),
  KEY `idx_sync_status` (`sync_status`),
  CONSTRAINT `fk_sync_source` FOREIGN KEY (`source_id`) REFERENCES `data_sources` (`source_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据同步日志表（同步时间、条数、状态与异常信息）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sync_logs`
--

LOCK TABLES `sync_logs` WRITE;
/*!40000 ALTER TABLE `sync_logs` DISABLE KEYS */;
INSERT INTO `sync_logs` VALUES (1,1,'2026-09-15 08:00:00',128,'SUCCESS',NULL,12),(2,2,'2026-09-15 08:05:00',64,'SUCCESS',NULL,8),(3,3,'2026-09-15 08:10:00',96,'SUCCESS',NULL,15),(4,4,'2026-09-15 08:15:00',32,'SUCCESS',NULL,5),(5,5,'2026-09-15 07:00:00',48,'SUCCESS',NULL,30),(6,6,'2026-09-15 07:00:00',0,'FAILED','接口响应超时，重试3次仍失败',60),(7,7,'2026-09-15 06:30:00',24,'SUCCESS',NULL,9),(8,8,'2026-09-15 06:00:00',16,'SUCCESS',NULL,6),(9,9,'2026-09-15 08:20:00',8,'SUCCESS',NULL,4),(10,10,'2026-09-15 00:00:00',2,'SUCCESS',NULL,3),(11,1,'2026-09-15 09:00:00',130,'SUCCESS',NULL,11),(12,2,'2026-09-15 09:05:00',66,'SUCCESS',NULL,9);
/*!40000 ALTER TABLE `sync_logs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `system_settings`
--

DROP TABLE IF EXISTS `system_settings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_settings` (
  `setting_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `setting_key` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置键',
  `setting_value` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置值',
  `setting_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'STRING' COMMENT '配置值类型：INT/DECIMAL/STRING/JSON',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '配置说明',
  `updated_by` bigint unsigned DEFAULT NULL COMMENT '最后修改人（可为空）',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`setting_id`),
  UNIQUE KEY `uk_settings_key` (`setting_key`),
  KEY `fk_settings_updater` (`updated_by`),
  CONSTRAINT `fk_settings_updater` FOREIGN KEY (`updated_by`) REFERENCES `users` (`user_id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表（全局参数配置）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `system_settings`
--

LOCK TABLES `system_settings` WRITE;
/*!40000 ALTER TABLE `system_settings` DISABLE KEYS */;
INSERT INTO `system_settings` VALUES (1,'data_refresh_interval','30','INT','数据自动刷新间隔（分钟）',1,'2026-09-01 09:00:00'),(2,'default_base_map','NORMAL','STRING','默认底图：NORMAL/SATELLITE/TERRAIN',1,'2026-09-01 09:00:00'),(3,'wind_circle_opacity','0.45','DECIMAL','风圈图层透明度',1,'2026-09-01 09:00:00'),(4,'wind_circle_max_level','12','INT','最大展示风圈等级',1,'2026-09-01 09:00:00'),(5,'data_retention_days','365','INT','历史数据保留天数',2,'2026-09-01 09:00:00'),(6,'max_export_rows','10000','INT','单次导出最大行数',1,'2026-09-01 09:00:00'),(7,'sandbox_param_range','{\"landing_offset_max_km\":120,\"speed_factor\":[0.5,1.5],\"intensity_offset_max\":3,\"circle_scale\":[0.5,2.0]}','JSON','沙盘参数可调范围',1,'2026-09-01 09:00:00'),(8,'survey_enabled','1','INT','问卷采集开关',1,'2026-09-01 09:00:00'),(9,'push_enabled','1','INT','浏览器消息推送总开关',1,'2026-09-01 09:00:00'),(10,'high_uncertainty_threshold','0.7','DECIMAL','高不确定性分歧阈值',1,'2026-09-01 09:00:00');
/*!40000 ALTER TABLE `system_settings` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `typhoon_albums`
--

DROP TABLE IF EXISTS `typhoon_albums`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `typhoon_albums` (
  `album_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '影像ID',
  `typhoon_id` bigint unsigned NOT NULL COMMENT '台风ID',
  `image_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '影像类型：SATELLITE=卫星云图，RADAR=雷达图',
  `image_url` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '影像URL',
  `shot_time` datetime NOT NULL COMMENT '拍摄时间',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '影像说明',
  PRIMARY KEY (`album_id`),
  KEY `idx_album_typhoon` (`typhoon_id`),
  KEY `idx_album_type` (`image_type`),
  KEY `idx_album_shot_time` (`shot_time`),
  CONSTRAINT `fk_album_typhoon` FOREIGN KEY (`typhoon_id`) REFERENCES `typhoons` (`typhoon_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='台风云图表（卫星云图、雷达图URL与拍摄时间）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `typhoon_albums`
--

LOCK TABLES `typhoon_albums` WRITE;
/*!40000 ALTER TABLE `typhoon_albums` DISABLE KEYS */;
INSERT INTO `typhoon_albums` VALUES (1,1,'SATELLITE','https://example.com/sat/yagi_20240905.png','2024-09-05 12:00:00','摩羯台风卫星云图'),(2,1,'RADAR','https://example.com/radar/yagi_20240906.png','2024-09-06 18:00:00','摩羯登陆前雷达图'),(3,3,'SATELLITE','https://example.com/sat/doksuri_20230727.png','2023-07-27 06:00:00','杜苏芮逼近福建云图'),(4,8,'SATELLITE','https://example.com/sat/mangkhut_20180915.png','2018-09-15 08:00:00','山竹台风卫星云图'),(5,9,'SATELLITE','https://example.com/sat/meranti_20160914.png','2016-09-14 06:00:00','莫兰蒂台风卫星云图'),(6,9,'RADAR','https://example.com/radar/meranti_20160915.png','2016-09-15 02:00:00','莫兰蒂登陆厦门雷达图'),(7,10,'SATELLITE','https://example.com/sat/hato_20170823.png','2017-08-23 06:00:00','天鸽台风卫星云图'),(8,11,'SATELLITE','https://example.com/sat/rammasun_20140718.png','2014-07-18 12:00:00','威马逊台风卫星云图'),(9,13,'SATELLITE','https://example.com/sat/feiyan_20260914.png','2026-09-14 06:00:00','飞燕台风卫星云图（模拟）'),(10,13,'RADAR','https://example.com/radar/feiyan_20260915.png','2026-09-15 08:00:00','飞燕台风雷达拼图（模拟）');
/*!40000 ALTER TABLE `typhoon_albums` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `typhoon_tracks`
--

DROP TABLE IF EXISTS `typhoon_tracks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `typhoon_tracks` (
  `track_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '轨迹点ID',
  `typhoon_id` bigint unsigned NOT NULL COMMENT '台风ID',
  `obs_time` datetime NOT NULL COMMENT '观测时间',
  `latitude` decimal(7,4) NOT NULL COMMENT '纬度',
  `longitude` decimal(8,4) NOT NULL COMMENT '经度',
  `wind_force` tinyint NOT NULL COMMENT '中心最大风力（级）',
  `wind_speed` int NOT NULL COMMENT '中心最大风速（m/s）',
  `pressure` int NOT NULL COMMENT '中心气压（hPa）',
  `move_direction` decimal(5,1) DEFAULT NULL COMMENT '移动方向（方位角，度）',
  `move_speed` decimal(5,1) DEFAULT NULL COMMENT '移动速度（km/h）',
  PRIMARY KEY (`track_id`),
  UNIQUE KEY `uk_tracks_typhoon_time` (`typhoon_id`,`obs_time`),
  KEY `idx_tracks_obs_time` (`obs_time`),
  KEY `idx_tracks_lat_lon` (`latitude`,`longitude`),
  CONSTRAINT `fk_tracks_typhoon` FOREIGN KEY (`typhoon_id`) REFERENCES `typhoons` (`typhoon_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='台风实况轨迹表（各时刻经纬度、风力、风速、气压、移向移速）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `typhoon_tracks`
--

LOCK TABLES `typhoon_tracks` WRITE;
/*!40000 ALTER TABLE `typhoon_tracks` DISABLE KEYS */;
INSERT INTO `typhoon_tracks` VALUES (1,1,'2024-09-03 17:00:00',14.8000,125.3000,12,33,980,285.0,20.0),(2,1,'2024-09-05 17:00:00',19.0000,119.5000,17,62,915,300.0,22.0),(3,1,'2024-09-06 20:00:00',22.3000,112.9000,14,45,940,315.0,25.0),(4,3,'2023-07-25 08:00:00',18.5000,124.8000,13,38,965,295.0,18.0),(5,3,'2023-07-27 08:00:00',22.8000,119.0000,14,42,955,305.0,20.0),(6,3,'2023-07-28 09:00:00',24.5000,118.5000,14,40,960,310.0,22.0),(7,4,'2022-09-12 08:00:00',25.1000,122.6000,13,38,960,330.0,15.0),(8,4,'2022-09-14 20:00:00',30.4000,121.7000,12,35,970,350.0,28.0),(9,6,'2021-07-25 12:00:00',29.8000,122.3000,11,30,980,350.0,8.0),(10,6,'2021-07-26 09:00:00',30.6000,121.8000,10,28,985,0.0,5.0),(11,7,'2019-08-08 10:00:00',23.4000,123.9000,14,45,950,315.0,18.0),(12,7,'2019-08-10 01:00:00',28.7000,121.4000,14,42,950,345.0,30.0),(13,8,'2018-09-15 09:00:00',18.7000,114.7000,15,50,935,295.0,28.0),(14,8,'2018-09-16 17:00:00',22.5000,111.9000,12,38,960,300.0,32.0),(15,9,'2016-09-14 10:00:00',21.8000,123.6000,16,58,915,300.0,20.0),(16,9,'2016-09-15 03:00:00',24.4000,118.3000,15,48,940,310.0,22.0),(17,10,'2017-08-23 04:00:00',21.5000,114.5000,14,48,945,310.0,25.0),(18,10,'2017-08-23 12:00:00',22.2000,113.3000,14,42,950,320.0,28.0),(19,11,'2014-07-18 15:00:00',20.3000,112.3000,15,50,935,300.0,18.0),(20,11,'2014-07-18 19:30:00',20.3000,111.0000,14,46,940,290.0,20.0),(21,13,'2026-09-13 08:00:00',16.5000,128.5000,9,23,990,290.0,15.0),(22,13,'2026-09-14 08:00:00',18.0000,126.0000,10,28,985,295.0,17.0),(23,13,'2026-09-15 08:00:00',19.5000,123.8000,12,38,965,300.0,20.0);
/*!40000 ALTER TABLE `typhoon_tracks` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `typhoons`
--

DROP TABLE IF EXISTS `typhoons`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `typhoons` (
  `typhoon_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '台风ID',
  `international_id` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '国际编号（如202305）',
  `chinese_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '中文名称',
  `english_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '英文名称',
  `basin` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '西北太平洋' COMMENT '所属海域',
  `generate_time` datetime NOT NULL COMMENT '生成时间',
  `dissipate_time` datetime DEFAULT NULL COMMENT '消亡时间',
  `status` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '生命周期状态：ACTIVE=活跃，FINISHED=已结束',
  `max_wind_force` tinyint DEFAULT NULL COMMENT '历史最大风力（级）',
  `max_wind_speed` int DEFAULT NULL COMMENT '历史最大风速（m/s）',
  `min_pressure` int DEFAULT NULL COMMENT '中心最低气压（hPa）',
  `is_historical_archive` tinyint NOT NULL DEFAULT '0' COMMENT '历史档案标记：1=已归档，0=未归档',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`typhoon_id`),
  UNIQUE KEY `uk_typhoons_intl_id` (`international_id`),
  KEY `idx_typhoons_status` (`status`),
  KEY `idx_typhoons_basin` (`basin`),
  KEY `idx_typhoons_generate_time` (`generate_time`),
  KEY `idx_typhoons_archive` (`is_historical_archive`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='台风主表（基本信息与历史档案标记）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `typhoons`
--

LOCK TABLES `typhoons` WRITE;
/*!40000 ALTER TABLE `typhoons` DISABLE KEYS */;
INSERT INTO `typhoons` VALUES (1,'202411','摩羯','YAGI','西北太平洋','2024-09-01 20:00:00','2024-09-09 12:00:00','FINISHED',17,62,915,1,'2024-09-01 20:00:00','2024-09-10 00:00:00'),(2,'202311','海葵','HAIKUI','西北太平洋','2023-08-27 08:00:00','2023-09-05 12:00:00','FINISHED',14,45,945,1,'2023-08-27 08:00:00','2023-09-06 00:00:00'),(3,'202305','杜苏芮','DOKSURI','西北太平洋','2023-07-21 08:00:00','2023-07-29 20:00:00','FINISHED',16,52,925,1,'2023-07-21 08:00:00','2023-07-30 00:00:00'),(4,'202212','梅花','MUIFA','西北太平洋','2022-09-08 08:00:00','2022-09-16 08:00:00','FINISHED',14,45,950,1,'2022-09-08 08:00:00','2022-09-17 00:00:00'),(5,'202114','灿都','CHANTHU','西北太平洋','2021-09-06 08:00:00','2021-09-17 20:00:00','FINISHED',17,60,920,1,'2021-09-06 08:00:00','2021-09-18 00:00:00'),(6,'202106','烟花','IN-FA','西北太平洋','2021-07-17 08:00:00','2021-07-30 12:00:00','FINISHED',13,40,955,1,'2021-07-17 08:00:00','2021-07-31 00:00:00'),(7,'201909','利奇马','LEKIMA','西北太平洋','2019-08-04 08:00:00','2019-08-13 08:00:00','FINISHED',16,52,930,1,'2019-08-04 08:00:00','2019-08-14 00:00:00'),(8,'201822','山竹','MANGKHUT','西北太平洋','2018-09-07 08:00:00','2018-09-17 12:00:00','FINISHED',17,65,905,1,'2018-09-07 08:00:00','2018-09-18 00:00:00'),(9,'201614','莫兰蒂','MERANTI','西北太平洋','2016-09-10 08:00:00','2016-09-16 20:00:00','FINISHED',17,70,890,1,'2016-09-10 08:00:00','2016-09-17 00:00:00'),(10,'201713','天鸽','HATO','南海','2017-08-20 08:00:00','2017-08-24 20:00:00','FINISHED',15,48,940,1,'2017-08-20 08:00:00','2017-08-25 00:00:00'),(11,'201409','威马逊','RAMMASUN','西北太平洋','2014-07-10 08:00:00','2014-07-20 20:00:00','FINISHED',17,72,880,1,'2014-07-10 08:00:00','2014-07-21 00:00:00'),(12,'201621','莎莉嘉','SARIKA','南海','2016-10-13 08:00:00','2016-10-19 20:00:00','FINISHED',14,45,950,1,'2016-10-13 08:00:00','2016-10-20 00:00:00'),(13,'202615','飞燕','FEIYAN','西北太平洋','2026-09-08 08:00:00',NULL,'ACTIVE',12,38,965,0,'2026-09-08 08:00:00','2026-09-15 08:00:00');
/*!40000 ALTER TABLE `typhoons` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_behavior_surveys`
--

DROP TABLE IF EXISTS `user_behavior_surveys`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_behavior_surveys` (
  `survey_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '问卷记录ID',
  `typhoon_id` bigint unsigned NOT NULL COMMENT '关联台风ID',
  `city_id` bigint unsigned NOT NULL COMMENT '所在城市ID',
  `question_set_version` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'V1.0' COMMENT '问卷版本',
  `prepared_supplies` tinyint NOT NULL COMMENT '是否储备物资：1=是，0=否',
  `pay_attention_evacuation` tinyint NOT NULL COMMENT '是否关注撤离信息：1=是，0=否',
  `worried_disaster_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '最担心灾害类型：WIND/RAIN/STORM_SURGE/OTHER',
  `trust_level` tinyint NOT NULL COMMENT '对预报可信度主观判断（1-5）',
  `submit_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `contact_info` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系方式（可选）：AES-GCM 密文（enc1: 前缀 + Base64），非明文；展示时由应用层打码',
  PRIMARY KEY (`survey_id`),
  KEY `idx_ubs_typhoon` (`typhoon_id`),
  KEY `idx_ubs_city` (`city_id`),
  KEY `idx_ubs_submit_time` (`submit_time`),
  CONSTRAINT `fk_ubs_city` FOREIGN KEY (`city_id`) REFERENCES `cities` (`city_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_ubs_typhoon` FOREIGN KEY (`typhoon_id`) REFERENCES `typhoons` (`typhoon_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户防灾行为调研表（普通问卷原始记录，含联系方式等基本信息）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_behavior_surveys`
--

LOCK TABLES `user_behavior_surveys` WRITE;
/*!40000 ALTER TABLE `user_behavior_surveys` DISABLE KEYS */;
INSERT INTO `user_behavior_surveys` VALUES (1,13,3,'V1.0',1,1,'RAIN',4,'2026-09-15 09:10:00','138****0001'),(2,13,3,'V1.0',1,1,'STORM_SURGE',3,'2026-09-15 09:15:00',NULL),(3,13,3,'V1.0',0,1,'WIND',5,'2026-09-15 09:20:00','139****0002'),(4,13,4,'V1.0',1,0,'RAIN',4,'2026-09-15 09:30:00',NULL),(5,13,4,'V1.0',0,1,'RAIN',2,'2026-09-15 09:35:00','137****0003'),(6,13,1,'V1.0',1,1,'STORM_SURGE',4,'2026-09-15 09:40:00',NULL),(7,13,9,'V1.0',1,1,'STORM_SURGE',3,'2026-09-15 09:45:00','136****0004'),(8,13,9,'V1.0',0,1,'WIND',2,'2026-09-15 09:50:00',NULL),(9,1,2,'V1.0',1,1,'RAIN',4,'2024-09-06 10:00:00','135****0005'),(10,1,2,'V1.0',1,1,'WIND',3,'2024-09-06 10:05:00',NULL),(11,1,11,'V1.0',0,0,'RAIN',3,'2024-09-06 10:10:00','138****0006'),(12,3,3,'V1.0',1,1,'STORM_SURGE',4,'2023-07-27 12:00:00',NULL);
/*!40000 ALTER TABLE `user_behavior_surveys` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_favorites`
--

DROP TABLE IF EXISTS `user_favorites`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_favorites` (
  `favorite_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `typhoon_id` bigint unsigned DEFAULT NULL COMMENT '收藏台风ID（可为空）',
  `city_id` bigint unsigned DEFAULT NULL COMMENT '收藏城市ID（可为空）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`favorite_id`),
  UNIQUE KEY `uk_fav_user_typhoon_city` (`user_id`,`typhoon_id`,`city_id`),
  KEY `idx_fav_typhoon` (`typhoon_id`),
  KEY `idx_fav_city` (`city_id`),
  CONSTRAINT `fk_fav_city` FOREIGN KEY (`city_id`) REFERENCES `cities` (`city_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_fav_typhoon` FOREIGN KEY (`typhoon_id`) REFERENCES `typhoons` (`typhoon_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_fav_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏表（台风档案与常用城市快捷入口）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_favorites`
--

LOCK TABLES `user_favorites` WRITE;
/*!40000 ALTER TABLE `user_favorites` DISABLE KEYS */;
INSERT INTO `user_favorites` VALUES (1,3,1,NULL,'2024-09-07 10:00:00'),(2,3,3,NULL,'2023-07-28 20:00:00'),(3,4,8,NULL,'2018-09-18 10:00:00'),(4,5,13,NULL,'2026-09-10 09:00:00'),(5,6,9,NULL,'2016-09-16 10:00:00'),(6,7,NULL,3,'2024-05-01 10:00:00'),(7,8,NULL,1,'2024-05-02 10:00:00'),(8,3,NULL,9,'2023-07-20 10:00:00'),(9,4,11,NULL,'2014-07-21 10:00:00'),(10,5,10,NULL,'2017-08-24 10:00:00'),(11,6,4,NULL,'2022-09-16 10:00:00'),(12,7,NULL,5,'2024-06-01 10:00:00');
/*!40000 ALTER TABLE `user_favorites` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_subscriptions`
--

DROP TABLE IF EXISTS `user_subscriptions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_subscriptions` (
  `subscription_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '订阅ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `typhoon_id` bigint unsigned DEFAULT NULL COMMENT '订阅台风ID（可为空）',
  `city_id` bigint unsigned DEFAULT NULL COMMENT '关注城市ID（可为空）',
  `notify_new_storm` tinyint NOT NULL DEFAULT '1' COMMENT '台风新增提醒：1=开，0=关',
  `notify_strength_change` tinyint NOT NULL DEFAULT '1' COMMENT '强度突变提醒：1=开，0=关',
  `notify_path_change` tinyint NOT NULL DEFAULT '1' COMMENT '路径调整提醒：1=开，0=关',
  `push_enabled` tinyint NOT NULL DEFAULT '1' COMMENT '浏览器推送开关：1=开，0=关',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`subscription_id`),
  UNIQUE KEY `uk_sub_user_typhoon_city` (`user_id`,`typhoon_id`,`city_id`),
  KEY `idx_sub_typhoon` (`typhoon_id`),
  KEY `idx_sub_city` (`city_id`),
  CONSTRAINT `fk_sub_city` FOREIGN KEY (`city_id`) REFERENCES `cities` (`city_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_sub_typhoon` FOREIGN KEY (`typhoon_id`) REFERENCES `typhoons` (`typhoon_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_sub_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户订阅表（台风/城市订阅与通知偏好）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_subscriptions`
--

LOCK TABLES `user_subscriptions` WRITE;
/*!40000 ALTER TABLE `user_subscriptions` DISABLE KEYS */;
INSERT INTO `user_subscriptions` VALUES (1,3,13,NULL,1,1,1,1,'2026-09-08 09:00:00'),(2,4,13,NULL,1,1,1,1,'2026-09-08 09:10:00'),(3,5,NULL,3,1,0,0,1,'2026-09-08 09:20:00'),(4,6,NULL,1,0,1,1,1,'2026-09-08 09:30:00'),(5,7,1,NULL,0,1,1,1,'2024-09-01 10:00:00'),(6,8,3,NULL,1,1,0,1,'2023-07-21 10:00:00'),(7,3,NULL,9,1,0,0,1,'2023-07-20 10:00:00'),(8,4,NULL,11,1,0,0,0,'2023-07-20 10:00:00'),(9,5,8,NULL,0,0,1,1,'2018-09-07 10:00:00'),(10,6,13,NULL,1,1,1,1,'2026-09-09 09:00:00'),(11,7,NULL,5,0,1,0,1,'2024-05-01 10:00:00'),(12,8,4,NULL,1,1,1,1,'2022-09-08 10:00:00');
/*!40000 ALTER TABLE `user_subscriptions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `user_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录用户名',
  `password_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码哈希值',
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `nickname` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '昵称',
  `role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN=平台总管理员，USER=普通用户',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=正常，0=禁用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `last_login` datetime DEFAULT NULL COMMENT '最后登录时间；仅登录成功时更新，从未登录为 NULL',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_users_username` (`username`),
  KEY `idx_users_role` (`role`),
  KEY `idx_users_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台用户表（普通用户、平台总管理员）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES (1,'admin01','sha256$5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8','admin01@typhoon.cn','13800000001','总管理员','ADMIN',1,'2024-01-01 09:00:00','2026-09-01 09:00:00',NULL),(2,'dataadmin01','sha256$8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92','dataadmin01@typhoon.cn','13800000002','系统管理员','ADMIN',1,'2024-01-01 09:00:00','2026-09-01 09:00:00',NULL),(3,'user01','sha256$4766c48d8761ec5d9de86d5609b32349744c73c4762389a6295a65e8447b0281','user01@example.com','13800000003','追风少年','USER',1,'2024-02-01 10:00:00','2026-09-01 09:00:00',NULL),(4,'user02','sha256$4766c48d8761ec5d9de86d5609b32349744c73c4762389a6295a65e8447b0281','user02@example.com','13800000004','厦门市民','USER',1,'2024-03-02 10:00:00','2026-09-01 09:00:00',NULL),(5,'user03','sha256$4766c48d8761ec5d9de86d5609b32349744c73c4762389a6295a65e8447b0281','user03@example.com','13800000005','渔港人家','USER',1,'2024-04-03 10:00:00','2026-09-01 09:00:00',NULL),(6,'user04','sha256$4766c48d8761ec5d9de86d5609b32349744c73c4762389a6295a65e8447b0281','user04@example.com','13800000006','台风观察员','USER',1,'2024-05-04 10:00:00','2026-09-01 09:00:00',NULL),(7,'user05','sha256$4766c48d8761ec5d9de86d5609b32349744c73c4762389a6295a65e8447b0281','user05@example.com','13800000007','海边的卡夫卡','USER',1,'2024-06-05 10:00:00','2026-09-01 09:00:00',NULL),(8,'user06','sha256$4766c48d8761ec5d9de86d5609b32349744c73c4762389a6295a65e8447b0281','user06@example.com','13800000008','台风科普君','USER',1,'2024-07-06 10:00:00','2026-09-01 09:00:00',NULL),(9,'user07','sha256$4766c48d8761ec5d9de86d5609b32349744c73c4762389a6295a65e8447b0281','user07@example.com','13800000009','风暴猎人','USER',0,'2024-08-07 10:00:00','2026-08-01 09:00:00',NULL),(10,'user08','sha256$4766c48d8761ec5d9de86d5609b32349744c73c4762389a6295a65e8447b0281','user08@example.com','13800000010','海岛旅人','USER',1,'2024-09-08 10:00:00','2026-09-01 09:00:00',NULL);
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `wind_circles`
--

DROP TABLE IF EXISTS `wind_circles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wind_circles` (
  `circle_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '风圈记录ID',
  `typhoon_id` bigint unsigned NOT NULL COMMENT '台风ID',
  `obs_time` datetime NOT NULL COMMENT '观测时间',
  `level7_ne` int unsigned NOT NULL COMMENT '7级风圈东北象限半径（km）',
  `level7_nw` int unsigned NOT NULL COMMENT '7级风圈西北象限半径（km）',
  `level7_se` int unsigned NOT NULL COMMENT '7级风圈东南象限半径（km）',
  `level7_sw` int unsigned NOT NULL COMMENT '7级风圈西南象限半径（km）',
  `level10_ne` int unsigned NOT NULL COMMENT '10级风圈东北象限半径（km）',
  `level10_nw` int unsigned NOT NULL COMMENT '10级风圈西北象限半径（km）',
  `level10_se` int unsigned NOT NULL COMMENT '10级风圈东南象限半径（km）',
  `level10_sw` int unsigned NOT NULL COMMENT '10级风圈西南象限半径（km）',
  `level12_ne` int unsigned NOT NULL COMMENT '12级风圈东北象限半径（km）',
  `level12_nw` int unsigned NOT NULL COMMENT '12级风圈西北象限半径（km）',
  `level12_se` int unsigned NOT NULL COMMENT '12级风圈东南象限半径（km）',
  `level12_sw` int unsigned NOT NULL COMMENT '12级风圈西南象限半径（km）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`circle_id`),
  UNIQUE KEY `uk_circles_typhoon_time` (`typhoon_id`,`obs_time`),
  CONSTRAINT `fk_circles_typhoon` FOREIGN KEY (`typhoon_id`) REFERENCES `typhoons` (`typhoon_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='风圈半径表（7/10/12级风圈四象限半径）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `wind_circles`
--

LOCK TABLES `wind_circles` WRITE;
/*!40000 ALTER TABLE `wind_circles` DISABLE KEYS */;
INSERT INTO `wind_circles` VALUES (1,1,'2024-09-05 17:00:00',350,320,300,380,150,140,130,160,80,70,60,85,'2026-09-16 11:08:06'),(2,3,'2023-07-27 08:00:00',300,280,260,320,120,110,100,130,50,45,40,55,'2026-09-16 11:08:06'),(3,4,'2022-09-12 08:00:00',250,230,220,260,100,90,85,110,40,35,30,45,'2026-09-16 11:08:06'),(4,5,'2021-09-10 08:00:00',330,300,290,350,140,130,120,150,70,65,60,75,'2026-09-16 11:08:06'),(5,6,'2021-07-25 12:00:00',280,260,250,300,110,100,95,120,45,40,35,50,'2026-09-16 11:08:06'),(6,7,'2019-08-08 10:00:00',300,280,270,320,120,110,105,130,50,45,40,55,'2026-09-16 11:08:06'),(7,8,'2018-09-15 09:00:00',340,320,310,360,150,140,135,160,80,75,70,85,'2026-09-16 11:08:06'),(8,9,'2016-09-14 10:00:00',320,300,290,340,140,130,120,150,75,70,65,80,'2026-09-16 11:08:06'),(9,10,'2017-08-23 04:00:00',280,260,250,300,110,100,95,120,45,40,35,50,'2026-09-16 11:08:06'),(10,11,'2014-07-18 15:00:00',310,290,280,330,130,120,110,140,70,65,60,75,'2026-09-16 11:08:06'),(11,12,'2016-10-17 08:00:00',260,240,230,270,100,90,85,110,40,35,30,45,'2026-09-16 11:08:06'),(12,13,'2026-09-15 08:00:00',200,180,170,220,80,70,65,90,30,25,20,35,'2026-09-16 11:08:06');
/*!40000 ALTER TABLE `wind_circles` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-16 11:08:45
