# 关于pgvector和Embedding模型的说明

## 问题：使用pgvector可以不使用智谱AI吗？

**简短回答：** pgvector只是向量存储，**仍然需要embedding模型**来生成向量。但您可以选择其他embedding服务提供商或本地模型。

## 详细说明

### pgvector的作用

- **pgvector** 是PostgreSQL的向量扩展插件
- 用于**存储和检索**向量数据
- **不能生成向量**，只能存储和查询

### Embedding模型的作用

- **Embedding模型** 负责将文本转换为向量（embeddings）
- 这是RAG（检索增强生成）功能的核心
- 每次搜索都需要调用embedding API将查询文本转换为向量

### 工作流程

```
文本查询 → Embedding模型（智谱AI/OpenAI等） → 向量 → pgvector存储/检索 → 相似文档
```

## 解决方案

### 方案1：使用其他Embedding服务提供商（推荐）

#### 选项A：使用Qwen（通义千问）- 支持embeddings

修改 `application-dev.yml`：

```yaml
spring:
  ai:
    openai:
      # Qwen Embeddings配置
      embedding-api-key: ${EMBEDDING_API_KEY:your-qwen-api-key}
      embedding-base-url: ${EMBEDDING_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}
      embedding:
        options:
          model: text-embedding-v2  # Qwen的embeddings模型
```

#### 选项B：使用OpenAI（如果可用）

```yaml
spring:
  ai:
    openai:
      embedding-api-key: ${EMBEDDING_API_KEY:your-openai-api-key}
      embedding-base-url: ${EMBEDDING_BASE_URL:https://api.openai.com/v1}
      embedding:
        options:
          model: text-embedding-3-small  # 或 text-embedding-ada-002
```

### 方案2：使用本地Embedding模型（免费）

#### 使用Ollama运行本地模型

1. **安装Ollama**
   - 访问 https://ollama.ai 下载并安装

2. **下载embedding模型**
   ```bash
   ollama pull nomic-embed-text
   ```

3. **修改配置**
   ```yaml
   spring:
     ai:
       openai:
         embedding-api-key: ${EMBEDDING_API_KEY:ollama}  # Ollama不需要真实的key
         embedding-base-url: ${EMBEDDING_BASE_URL:http://localhost:11434/v1}
         embedding:
           options:
             model: nomic-embed-text  # Ollama的embeddings模型
   ```

4. **启动Ollama服务**
   ```bash
   ollama serve
   ```

### 方案3：暂时禁用RAG功能（最简单）

如果暂时不需要RAG功能，可以禁用向量存储：

#### 方法1：注释掉VectorStoreConfig

在 `Application.java` 中已经排除了 `PgVectorStoreAutoConfiguration`，如果不想使用RAG：

1. **确保VectorStoreConfig不会被加载**
   - 检查 `VectorStoreConfig.java` 的 `@ConditionalOnBean(name = "pgVectorJdbcTemplate")`
   - 如果没有PostgreSQL连接，VectorStoreConfig不会创建

2. **应用会自动使用占位RAG服务**
   - `NovelAgentConfig` 会自动检测并使用占位实现
   - 应用可以正常运行，只是不使用向量检索

#### 方法2：修改配置文件，不配置embedding API

如果不想使用embedding API，可以：

1. **不设置环境变量** `EMBEDDING_API_KEY`
2. **配置文件中的默认值会被使用**，但如果API不可用，应用会优雅降级
3. **VectorRAGService会返回空结果**，但不会导致应用崩溃

### 方案4：检查并修复智谱AI配置

如果仍想使用智谱AI，请检查：

1. **确认环境变量已设置并生效**
   ```powershell
   # 在新的PowerShell窗口中验证
   echo $env:EMBEDDING_API_KEY
   echo $env:EMBEDDING_BASE_URL
   ```

2. **查看应用启动日志**
   应该看到：
   ```
   Embeddings配置来源检查:
     - 环境变量 EMBEDDING_API_KEY: 已设置
     - 最终使用的 API Key来源: Embeddings专用配置
   ```

3. **确认智谱AI账户余额**
   - 登录智谱AI控制台
   - 检查账户余额和API密钥状态

4. **确认API密钥格式正确**
   - 没有多余的空格
   - 没有换行符
   - 完整复制API密钥

## 推荐方案

根据您的需求选择：

1. **需要RAG功能 + 有预算** → 使用Qwen或OpenAI的embeddings API
2. **需要RAG功能 + 无预算** → 使用Ollama本地模型
3. **暂时不需要RAG功能** → 禁用RAG，应用仍可正常运行（只是不使用向量检索）

## 当前状态

根据错误日志，应用已经能够优雅处理余额不足的错误：
- ✅ 不会导致应用崩溃
- ✅ 会返回空结果，应用继续运行
- ⚠️ 但会不断重试，产生大量错误日志

**改进建议：** 已更新 `VectorRAGService` 的错误处理，现在会更好地处理429错误并给出明确的提示。

## 下一步操作

1. **如果选择使用其他API提供商**：修改配置文件，设置新的API密钥
2. **如果选择使用Ollama**：安装Ollama并配置本地模型
3. **如果选择禁用RAG**：确保不配置embedding API，应用会自动使用占位实现
4. **如果继续使用智谱AI**：检查环境变量、账户余额和API密钥
