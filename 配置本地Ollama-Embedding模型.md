# 配置本地Ollama Embedding模型

## 前提条件

✅ Ollama容器已启动  
✅ Embedding模型已拉取（nomic-embed-text）  
✅ 其他容器（MySQL、PostgreSQL等）正常运行  

## 配置步骤

### 方式1：使用环境变量（推荐）

#### 如果应用在本地运行（不在Docker中）

**Windows PowerShell:**
```powershell
$env:EMBEDDING_API_KEY="ollama"
$env:EMBEDDING_BASE_URL="http://localhost:11434/v1"
```

**Linux/Mac:**
```bash
export EMBEDDING_API_KEY="ollama"
export EMBEDDING_BASE_URL="http://localhost:11434/v1"
```

#### 如果应用在Docker中运行

应用已经通过`docker-compose.yml`配置了环境变量，会自动使用Ollama：
- `EMBEDDING_API_KEY=ollama`
- `EMBEDDING_BASE_URL=http://ollama:11434/v1`

### 方式2：修改配置文件

#### 开发环境（application-dev.yml）

配置文件已更新，默认使用Ollama：

```yaml
spring:
  ai:
    openai:
      embedding:
        options:
          model: nomic-embed-text  # Ollama的embedding模型
      embedding-api-key: ollama
      embedding-base-url: http://localhost:11434/v1  # 本地运行
```

**注意：** 
- 如果应用在Docker中运行，改为 `http://ollama:11434/v1`
- 如果应用在本地运行，使用 `http://localhost:11434/v1`

#### Docker环境（application-docker.yml）

配置文件已配置为使用Docker服务名：

```yaml
spring:
  ai:
    openai:
      embedding:
        options:
          model: nomic-embed-text
      embedding-api-key: ollama
      embedding-base-url: http://ollama:11434/v1  # Docker网络中使用服务名
```

## 验证配置

### 1. 检查Ollama服务

```bash
# 检查容器状态
docker ps | grep ollama

# 检查模型列表
docker exec -it novel-dev-ollama ollama list

# 测试API
curl http://localhost:11434/api/tags
```

### 2. 启动应用并查看日志

启动应用后，查看启动日志，应该看到：

```
Embeddings配置来源检查:
  - 环境变量 EMBEDDING_API_KEY: 已设置/未设置
  - 环境变量 EMBEDDING_BASE_URL: 已设置/未设置
  - 配置文件 embedding-api-key: 已设置
  - 配置文件 embedding-base-url: 已设置
  - 最终使用的 API Key来源: Embeddings专用配置
  - 最终使用的 Base URL来源: Embeddings专用配置
检测到Ollama配置，自动调整embeddings路径
配置EmbeddingModel - baseUrl: http://localhost:11434/v1/, embeddingsPath: embeddings, model: nomic-embed-text
Embeddings API完整URL: http://localhost:11434/v1/embeddings
```

### 3. 测试RAG功能

调用RAG功能，应该不再出现"余额不足"的错误，而是正常返回向量搜索结果。

## 配置说明

### URL格式说明

- **本地运行：** `http://localhost:11434/v1`
- **Docker运行：** `http://ollama:11434/v1`（使用Docker服务名）
- **外部访问：** `http://your-server-ip:11434/v1`

### 模型名称

- **nomic-embed-text** - Ollama的embedding模型（推荐）
- 模型大小约137MB
- 支持多种语言

### 优先级

配置优先级（从高到低）：
1. **环境变量** - `EMBEDDING_API_KEY` 和 `EMBEDDING_BASE_URL`
2. **配置文件** - `application-dev.yml` 或 `application-docker.yml`
3. **默认值** - 使用Chat API的配置（不推荐）

## 常见问题

### 问题1：连接失败

**错误信息：** `Connection refused` 或 `Name or service not known`

**解决方案：**
- 确认Ollama容器正在运行：`docker ps | grep ollama`
- 确认URL格式正确：
  - 本地运行：`http://localhost:11434/v1`
  - Docker运行：`http://ollama:11434/v1`
- 检查网络连接：`curl http://localhost:11434/api/tags`

### 问题2：模型未找到

**错误信息：** `model not found`

**解决方案：**
- 确认模型已拉取：`docker exec -it novel-dev-ollama ollama list`
- 如果模型不存在，重新拉取：`docker exec -it novel-dev-ollama ollama pull nomic-embed-text`

### 问题3：仍然使用智谱AI

**原因：** 环境变量或配置文件优先级问题

**解决方案：**
1. 检查环境变量：`echo $env:EMBEDDING_BASE_URL`（Windows）或 `echo $EMBEDDING_BASE_URL`（Linux/Mac）
2. 如果环境变量指向智谱AI，删除或修改环境变量
3. 重启应用以加载新配置

### 问题4：Ollama响应慢

**原因：** 首次运行模型时需要加载到内存

**解决方案：**
- 这是正常现象，后续请求会更快
- 可以增加Ollama容器的内存限制（如果系统资源允许）

## 切换回智谱AI（如果需要）

如果需要切换回智谱AI，可以：

### 方式1：设置环境变量

```powershell
$env:EMBEDDING_API_KEY="your-zhipu-api-key"
$env:EMBEDDING_BASE_URL="https://open.bigmodel.cn/api/paas/v4"
```

### 方式2：修改配置文件

在`application-dev.yml`中：
1. 注释掉Ollama配置
2. 取消注释智谱AI配置
3. 设置正确的API密钥

## 完成！

配置完成后：
1. ✅ 重启应用
2. ✅ 查看启动日志确认配置生效
3. ✅ 测试RAG功能
4. ✅ 确认不再出现"余额不足"错误

现在您的应用已经配置为使用本地Ollama Embedding模型，完全免费且无需API密钥！
