# 创建PostgreSQL向量存储表

## 问题

应用启动后出现错误：
```
ERROR: relation "public.novel_vector_store" does not exist
```

这是因为PostgreSQL中还没有创建向量存储表。

## 解决方案

### 方案1：手动执行SQL脚本（推荐）

#### 步骤1：连接到PostgreSQL

```bash
# 如果PostgreSQL在Docker中运行
docker exec -it novel-dev-postgresql psql -U postgres -d novel_vector

# 或者使用psql客户端直接连接
psql -h localhost -p 15432 -U postgres -d novel_vector
```

#### 步骤2：执行创建表脚本

```sql
-- 启用pgvector扩展（如果尚未启用）
CREATE EXTENSION IF NOT EXISTS vector;

-- 删除旧表（如果存在）
DROP TABLE IF EXISTS public.novel_vector_store;

-- 创建向量存储表
-- 注意：nomic-embed-text模型的向量维度是768
CREATE TABLE public.novel_vector_store (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT NOT NULL,
    metadata JSONB,
    embedding VECTOR(768)  -- nomic-embed-text的向量维度是768
);

-- 创建HNSW索引（用于快速相似度搜索）
CREATE INDEX novel_vector_store_embedding_idx 
ON public.novel_vector_store 
USING hnsw (embedding vector_cosine_ops);

-- 添加注释
COMMENT ON TABLE public.novel_vector_store IS 'Novel Agent向量存储表，用于RAG功能';
COMMENT ON COLUMN public.novel_vector_store.id IS '主键ID（UUID）';
COMMENT ON COLUMN public.novel_vector_store.content IS '文档内容';
COMMENT ON COLUMN public.novel_vector_store.metadata IS '元数据（JSON格式）';
COMMENT ON COLUMN public.novel_vector_store.embedding IS '向量嵌入（768维，nomic-embed-text模型）';
```

#### 步骤3：验证表创建成功

```sql
-- 查看表结构
\d public.novel_vector_store

-- 查看表是否存在
SELECT * FROM information_schema.tables 
WHERE table_schema = 'public' AND table_name = 'novel_vector_store';
```

### 方案2：使用SQL文件

#### 步骤1：复制SQL文件到容器

```bash
docker cp "Novel Agent/docs/dev-ops/pgvector/create-table.sql" novel-dev-postgresql:/tmp/create-table.sql
```

#### 步骤2：执行SQL文件

```bash
docker exec -it novel-dev-postgresql psql -U postgres -d novel_vector -f /tmp/create-table.sql
```

### 方案3：让Spring AI自动创建（如果支持）

Spring AI的PgVectorStore应该会自动创建表，但需要：
1. 能够成功调用embedding API获取维度
2. 配置了`createTableIfNotExists(true)`

**注意：** 代码已经更新，添加了`createTableIfNotExists(true)`配置，但首次运行时可能需要先手动创建表。

## 重要说明

### 向量维度

不同的embedding模型有不同的向量维度：

- **nomic-embed-text** (Ollama): **768维**
- **text-embedding-3-small** (OpenAI): 1536维
- **embedding-3** (智谱AI): 默认2048维（可配置256/512/1024/2048）
- **embedding-2** (智谱AI): 1024维
- **text-embedding-v2** (Qwen): 1536维

**当前配置使用nomic-embed-text，所以向量维度是768。**

### 如果切换embedding模型

如果以后切换到其他embedding模型，需要：
1. 删除旧表
2. 使用新模型的向量维度重新创建表

## 验证

创建表后，重启应用，应该不再出现表不存在的错误。

查看应用日志，应该看到：
```
Using the vector table name: novel_vector_store. Is empty: true/false
Initializing PGVectorStore schema for table: novel_vector_store
```

## 故障排查

### 问题1：权限不足

**错误信息：** `permission denied`

**解决方案：**
- 确保使用postgres用户连接
- 检查数据库用户权限

### 问题2：扩展未安装

**错误信息：** `extension "vector" does not exist`

**解决方案：**
```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

### 问题3：维度不匹配

**错误信息：** `dimensions mismatch`

**解决方案：**
- 确认使用的embedding模型
- 使用正确的向量维度创建表
- 如果维度不匹配，删除旧表并重新创建

## 完成！

创建表后，RAG功能应该可以正常工作了！
