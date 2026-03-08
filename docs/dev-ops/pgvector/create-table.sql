-- 创建向量存储表
-- 用于Novel Agent的RAG功能
-- 注意：nomic-embed-text模型的向量维度是768，不是1536

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
