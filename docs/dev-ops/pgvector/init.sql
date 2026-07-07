-- =============================================================================
-- PostgreSQL 初始化脚本（Novel Agent）
-- =============================================================================
-- 内容：启用 pgvector 扩展 + 小说业务表 + RAG 向量表。
-- 约定：建议在专用库（如 novel_vector）中执行，与 application 中数据源配置一致。
-- 幂等：使用 IF NOT EXISTS，可重复执行。
-- 向量维度：768，与 nomic-embed-text 等常见嵌入模型对齐；若换模型需同步改 VECTOR(n)。
-- 索引：向量 HNSW 不在本脚本创建，见文末说明及 docs/dev-ops/pgvector/create-table.sql。
-- =============================================================================

-- 向量相似检索能力（余弦/L2 等由查询侧指定算子）
CREATE EXTENSION IF NOT EXISTS vector;


-- =============================================================================
-- 业务表：从「项目 → 种子 → 总纲 → 卷 → 章 → 场景」分层，外加 Agent 配置
-- =============================================================================

-- 小说项目根表：一条业务上的「书」/工程。
CREATE TABLE IF NOT EXISTS novel (
    id              BIGSERIAL PRIMARY KEY,           -- 自增主键（内部）
    novel_id        VARCHAR(64)  NOT NULL,          -- 业务唯一 ID（对外主键）
    title           VARCHAR(255) NOT NULL,          -- 书名
    genre           VARCHAR(50),                    -- 题材/类型
    status          SMALLINT DEFAULT 1,              -- 业务状态（如草稿/进行中/完结，含义由应用定义）
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_novel_id UNIQUE (novel_id)
);
CREATE INDEX IF NOT EXISTS idx_novel_genre ON novel (genre);
CREATE INDEX IF NOT EXISTS idx_novel_status ON novel (status);

-- 创作种子：世界观、主角、核心矛盾与目标字数等，从 novel 派生规划前的输入。
CREATE TABLE IF NOT EXISTS novel_seed (
    id                   BIGSERIAL PRIMARY KEY,
    seed_id              VARCHAR(64) NOT NULL,     -- 种子业务 ID
    novel_id             VARCHAR(64) NOT NULL,     -- 所属小说
    title                VARCHAR(255) NOT NULL,
    genre                VARCHAR(50),
    core_conflict        TEXT,                       -- 核心冲突
    world_setting        TEXT,                       -- 世界观设定
    protagonist_setting  TEXT,                       -- 主角设定
    target_word_count    INTEGER DEFAULT 1000000,  -- 目标总字数
    create_time          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_novel_seed_seed_id UNIQUE (seed_id)
);
CREATE INDEX IF NOT EXISTS idx_novel_seed_novel_id ON novel_seed (novel_id);

-- 全书总纲：卷数、每卷章数、整体大纲等。
CREATE TABLE IF NOT EXISTS novel_plan (
    id                   BIGSERIAL PRIMARY KEY,
    plan_id              VARCHAR(64) NOT NULL,
    novel_id             VARCHAR(64) NOT NULL,
    total_volumes        INTEGER DEFAULT 0,
    chapters_per_volume  INTEGER DEFAULT 20,
    overall_outline      TEXT,
    create_time          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_novel_plan_plan_id UNIQUE (plan_id)
);
CREATE INDEX IF NOT EXISTS idx_novel_plan_novel_id ON novel_plan (novel_id);

-- 分卷计划：卷序号、主题、本卷预计章数。
CREATE TABLE IF NOT EXISTS volume_plan (
    id             BIGSERIAL PRIMARY KEY,
    volume_id      VARCHAR(64) NOT NULL,
    novel_id       VARCHAR(64) NOT NULL,
    volume_number  INTEGER NOT NULL,
    volume_title   VARCHAR(255),
    volume_theme   TEXT,
    chapter_count  INTEGER DEFAULT 20,
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_volume_plan_volume_id UNIQUE (volume_id)
);
CREATE INDEX IF NOT EXISTS idx_volume_plan_novel_id ON volume_plan (novel_id); -- 按某本小说查所有卷
CREATE INDEX IF NOT EXISTS idx_volume_plan_novel_vol ON volume_plan (novel_id, volume_number); -- 在某本小说按卷排序

-- 章节：正文、大纲、字数与生成状态等。
CREATE TABLE IF NOT EXISTS chapter (
    id              BIGSERIAL PRIMARY KEY,
    chapter_id      VARCHAR(64) NOT NULL,
    novel_id        VARCHAR(64) NOT NULL,
    volume_number   INTEGER NOT NULL,
    chapter_number  INTEGER NOT NULL,
    title           VARCHAR(255),
    outline         TEXT,                            -- 章纲
    content         TEXT,                            -- 正文
    word_count      INTEGER DEFAULT 0,
    status          SMALLINT DEFAULT 0,              -- 生成/校验状态（应用枚举）
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_chapter_chapter_id UNIQUE (chapter_id)
);
CREATE INDEX IF NOT EXISTS idx_chapter_novel_id ON chapter (novel_id); -- 按某本小说查所有章
CREATE INDEX IF NOT EXISTS idx_chapter_novel_vol_ch ON chapter (novel_id, volume_number, chapter_number); -- 在某本小说按卷和章排序

-- 场景：章内细分单元（一幕/一段），Agent 可逐场景生成正文，检索也可按场景粒度切片。
CREATE TABLE IF NOT EXISTS scene (
    id            BIGSERIAL PRIMARY KEY,              -- 库内自增主键
    scene_id      VARCHAR(64) NOT NULL,             -- 业务唯一 ID（对外主键）
    chapter_id    VARCHAR(64) NOT NULL,              -- 所属章节（关联 chapter.chapter_id）
    scene_number  INTEGER NOT NULL,                  -- 章内顺序号，通常从 1 递增
    scene_title   VARCHAR(255),                      -- 场景标题/小节名
    scene_type    VARCHAR(50),                       -- 如对话/动作/描写（应用约定）
    content       TEXT,                              -- 本场景正文
    word_count    INTEGER DEFAULT 0,                 -- 本场景字数统计
    characters    VARCHAR(500),                      -- 出场人物摘要或列表（文本存储）
    location      VARCHAR(255),                      -- 场景发生地点
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_scene_scene_id UNIQUE (scene_id)   -- 保证 scene_id 全局不重复
);
CREATE INDEX IF NOT EXISTS idx_scene_chapter_id ON scene (chapter_id); -- 按章节拉取该章下全部场景
CREATE INDEX IF NOT EXISTS idx_scene_chapter_no ON scene (chapter_id, scene_number); -- 按章内序号定位或排序

-- Agent 运行参数：键值对配置；同一 (novel_id, agent_type, config_key) 由应用层约定是否唯一。
-- novel_id 为空表示全局默认，有值则只对该书生效，便于多项目隔离与覆盖策略。
CREATE TABLE IF NOT EXISTS novel_agent_config (
    id           BIGSERIAL PRIMARY KEY,              -- 库内自增主键
    config_id    VARCHAR(64) NOT NULL,              -- 本条配置记录的业务唯一 ID
    novel_id     VARCHAR(64),                       -- 所属小说；NULL = 全局默认
    agent_type   VARCHAR(50) NOT NULL,              -- Agent 类型标识（与代码中枚举/字符串一致）
    config_key   VARCHAR(100) NOT NULL,             -- 参数名；LLM 覆盖项：temperature、max_tokens/maxTokens、model
    config_value TEXT,                              -- 参数值（字符串）；agent_type 须与编排注册名一致（如 SceneGenerationAgent）
    status       SMALLINT DEFAULT 1,                -- 是否启用等（应用枚举，1 常表示有效）
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_novel_agent_config_id UNIQUE (config_id)   -- config_id 全局不重复
);
CREATE INDEX IF NOT EXISTS idx_nac_novel_agent ON novel_agent_config (novel_id, agent_type); -- 按书 + Agent 类型筛配置
CREATE INDEX IF NOT EXISTS idx_nac_agent_type ON novel_agent_config (agent_type);             -- 按 Agent 类型列出所有相关配置

-- Pipeline 编排检查点：末次业务阶段 + 生命周期状态（与章节业务表推导续写可并存；需 novel.pipeline.checkpoint.enabled=true）
CREATE TABLE IF NOT EXISTS novel_pipeline_checkpoint (
    id                       BIGSERIAL PRIMARY KEY,
    novel_id                 VARCHAR(64)  NOT NULL,
    session_id               VARCHAR(128),
    current_stage            VARCHAR(64),
    pipeline_execution_state VARCHAR(32),
    last_failure_message     TEXT,
    update_time              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_novel_pipeline_checkpoint_novel UNIQUE (novel_id)
);
CREATE INDEX IF NOT EXISTS idx_npc_state ON novel_pipeline_checkpoint (pipeline_execution_state);


-- =============================================================================
-- 向量表（RAG）：文档块 + 元数据 + 768 维嵌入
-- =============================================================================

CREATE TABLE IF NOT EXISTS public.novel_vector_store (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content   TEXT NOT NULL,                         -- 入库文本块
    metadata  JSONB,                                  -- novel_id、chapter_id 等过滤字段建议放此处
    embedding VECTOR(768)                            -- 与嵌入模型维度一致
);

-- 首次初始化不创建 HNSW/IVFFlat：部分 PG/pgvector 版本在空表或事务内建索引会失败，导致整脚本回滚。
-- 有数据后请执行：docs/dev-ops/pgvector/create-table.sql 中的索引段落，或按需手工 CREATE INDEX。

COMMENT ON TABLE public.novel_vector_store IS 'Novel Agent 向量存储表（RAG）';
