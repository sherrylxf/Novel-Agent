-- PostgreSQL初始化脚本
-- 使用pgvector专用镜像，已内置pgvector扩展
-- 启用pgvector扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 创建向量存储表（Spring AI会自动创建，这里只是示例）
-- CREATE TABLE IF NOT EXISTS novel_vector_store (
--     id BIGSERIAL PRIMARY KEY,
--     content TEXT,
--     embedding vector(1536),
--     metadata JSONB
-- );

-- 创建索引（Spring AI会自动创建）
-- CREATE INDEX IF NOT EXISTS novel_vector_store_embedding_idx 
-- ON novel_vector_store 
-- USING hnsw (embedding vector_cosine_ops);
