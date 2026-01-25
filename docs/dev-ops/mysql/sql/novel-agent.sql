# ************************************************************
# Novel Agent 数据库表设计
# 版本号： 1.0
# 生成时间: 2025-01-15
# ************************************************************

CREATE database if NOT EXISTS `novel_agent` default character set utf8mb4 collate utf8mb4_0900_ai_ci;
use `novel_agent`;

# 转储表 novel
# ------------------------------------------------------------

DROP TABLE IF EXISTS `novel`;

CREATE TABLE `novel` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `novel_id` varchar(64) NOT NULL COMMENT '小说ID',
  `title` varchar(255) NOT NULL COMMENT '小说标题',
  `genre` varchar(50) DEFAULT NULL COMMENT '题材（修仙/历史穿越/都市重生/女频言情）',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_novel_id` (`novel_id`),
  KEY `idx_genre` (`genre`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='小说表';

# 转储表 novel_seed
# ------------------------------------------------------------

DROP TABLE IF EXISTS `novel_seed`;

CREATE TABLE `novel_seed` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `seed_id` varchar(64) NOT NULL COMMENT '种子ID',
  `novel_id` varchar(64) NOT NULL COMMENT '小说ID',
  `title` varchar(255) NOT NULL COMMENT '小说标题',
  `genre` varchar(50) DEFAULT NULL COMMENT '题材',
  `core_conflict` text COMMENT '核心冲突/主题',
  `world_setting` text COMMENT '世界观设定',
  `protagonist_setting` text COMMENT '主角设定',
  `target_word_count` int DEFAULT '1000000' COMMENT '目标字数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_seed_id` (`seed_id`),
  KEY `idx_novel_id` (`novel_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='小说种子表';

# 转储表 novel_plan
# ------------------------------------------------------------

DROP TABLE IF EXISTS `novel_plan`;

CREATE TABLE `novel_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `plan_id` varchar(64) NOT NULL COMMENT '规划ID',
  `novel_id` varchar(64) NOT NULL COMMENT '小说ID',
  `total_volumes` int DEFAULT '0' COMMENT '总卷数',
  `chapters_per_volume` int DEFAULT '20' COMMENT '每卷章节数',
  `overall_outline` text COMMENT '整体大纲',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_id` (`plan_id`),
  KEY `idx_novel_id` (`novel_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='小说规划表';

# 转储表 volume_plan
# ------------------------------------------------------------

DROP TABLE IF EXISTS `volume_plan`;

CREATE TABLE `volume_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `volume_id` varchar(64) NOT NULL COMMENT '卷ID',
  `novel_id` varchar(64) NOT NULL COMMENT '小说ID',
  `volume_number` int NOT NULL COMMENT '卷序号',
  `volume_title` varchar(255) DEFAULT NULL COMMENT '卷标题',
  `volume_theme` text COMMENT '卷主题',
  `chapter_count` int DEFAULT '20' COMMENT '章节数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_volume_id` (`volume_id`),
  KEY `idx_novel_id` (`novel_id`),
  KEY `idx_volume_number` (`novel_id`, `volume_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卷/册规划表';

# 转储表 chapter
# ------------------------------------------------------------

DROP TABLE IF EXISTS `chapter`;

CREATE TABLE `chapter` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `chapter_id` varchar(64) NOT NULL COMMENT '章节ID',
  `novel_id` varchar(64) NOT NULL COMMENT '小说ID',
  `volume_number` int NOT NULL COMMENT '卷序号',
  `chapter_number` int NOT NULL COMMENT '章节序号',
  `title` varchar(255) DEFAULT NULL COMMENT '章节标题',
  `outline` text COMMENT '章节梗概',
  `content` longtext COMMENT '章节正文',
  `word_count` int DEFAULT '0' COMMENT '字数',
  `status` tinyint(1) DEFAULT '0' COMMENT '状态(0:草稿,1:已完成)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chapter_id` (`chapter_id`),
  KEY `idx_novel_id` (`novel_id`),
  KEY `idx_volume_chapter` (`novel_id`, `volume_number`, `chapter_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='章节表';

# 转储表 scene
# ------------------------------------------------------------

DROP TABLE IF EXISTS `scene`;

CREATE TABLE `scene` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `scene_id` varchar(64) NOT NULL COMMENT '场景ID',
  `chapter_id` varchar(64) NOT NULL COMMENT '章节ID',
  `scene_number` int NOT NULL COMMENT '场景序号',
  `scene_title` varchar(255) DEFAULT NULL COMMENT '场景标题',
  `scene_type` varchar(50) DEFAULT NULL COMMENT '场景类型（对话/战斗/日常/情感等）',
  `content` longtext COMMENT '场景正文',
  `word_count` int DEFAULT '0' COMMENT '字数',
  `characters` varchar(500) DEFAULT NULL COMMENT '参与人物（JSON数组）',
  `location` varchar(255) DEFAULT NULL COMMENT '地点',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scene_id` (`scene_id`),
  KEY `idx_chapter_id` (`chapter_id`),
  KEY `idx_scene_number` (`chapter_id`, `scene_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='场景表';

# 转储表 novel_agent_config
# ------------------------------------------------------------

DROP TABLE IF EXISTS `novel_agent_config`;

CREATE TABLE `novel_agent_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_id` varchar(64) NOT NULL COMMENT '配置ID',
  `novel_id` varchar(64) DEFAULT NULL COMMENT '小说ID（NULL表示全局配置）',
  `agent_type` varchar(50) NOT NULL COMMENT 'Agent类型',
  `config_key` varchar(100) NOT NULL COMMENT '配置键',
  `config_value` text COMMENT '配置值（JSON格式）',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_id` (`config_id`),
  KEY `idx_novel_agent` (`novel_id`, `agent_type`),
  KEY `idx_agent_type` (`agent_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Novel Agent配置表';
