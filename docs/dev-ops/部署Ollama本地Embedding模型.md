# 部署Ollama本地Embedding模型

## 概述

Ollama是一个本地运行大语言模型的工具，可以免费运行embedding模型，无需API密钥和费用。

## 快速开始

### 步骤0：拉取Ollama镜像（可选）

如果网络较慢，可以预先拉取镜像：

```bash
docker pull registry.cn-hangzhou.aliyuncs.com/xfg-studio/ollama:0.5.10
```

**注意：** 使用阿里云镜像，国内访问速度更快。

### 步骤1：启动Ollama容器

如果其他容器已经运行，只需要启动Ollama服务：

```bash
cd "Novel Agent/docs/dev-ops"
docker compose -p novel-dev -f docker-compose-ollama.yml up -d
```

### 步骤2：确保网络存在

如果提示网络不存在，需要先创建网络（使用与主配置相同的网络名）：

```bash
docker network create novel-dev_novel-network
```

或者，如果主docker-compose已经运行，网络应该已经存在。

### 步骤3：拉取Embedding模型

首次启动后，需要拉取embedding模型（这可能需要几分钟，取决于网络速度）：

```bash
docker exec -it novel-dev-ollama ollama pull nomic-embed-text
```

**注意：** 模型大小约137MB，首次下载需要一些时间。

### 步骤4：验证模型

验证模型是否已成功拉取：

```bash
docker exec -it novel-dev-ollama ollama list
```

应该看到：
```
NAME                ID              SIZE    MODIFIED
nomic-embed-text    xxxxx           137 MB  xxxxx
```

### 步骤5：测试Ollama API

测试Ollama是否正常工作：

```bash
curl http://localhost:11434/api/tags
```

应该返回JSON格式的模型列表。

## 配置应用使用Ollama

### 方式1：使用Docker Compose环境变量（推荐）

如果应用通过docker-compose运行，在`docker-compose.yml`中已经配置了环境变量：

```yaml
environment:
  - EMBEDDING_API_KEY=ollama
  - EMBEDDING_BASE_URL=http://ollama:11434/v1
```

### 方式2：手动设置环境变量

如果应用不在docker-compose中运行，需要设置环境变量：

**Windows PowerShell:**
```powershell
$env:EMBEDDING_API_KEY="ollama"
$env:EMBEDDING_BASE_URL="http://localhost:11434/v1"  # 如果从主机访问
# 或
$env:EMBEDDING_BASE_URL="http://ollama:11434/v1"  # 如果在docker网络中
```

**Linux/Mac:**
```bash
export EMBEDDING_API_KEY="ollama"
export EMBEDDING_BASE_URL="http://localhost:11434/v1"
```

### 方式3：修改配置文件

修改 `application-docker.yml` 或 `application-dev.yml`：

```yaml
spring:
  ai:
    openai:
      embedding-api-key: ollama
      embedding-base-url: http://ollama:11434/v1  # Docker网络中使用服务名
      # 或
      embedding-base-url: http://localhost:11434/v1  # 从主机访问
      embedding:
        options:
          model: nomic-embed-text
```

## 验证配置

### 1. 检查Ollama容器状态

```bash
docker ps | grep ollama
```

应该看到 `novel-dev-ollama` 容器正在运行。

### 2. 检查应用日志

启动应用后，查看日志中是否有以下信息：

```
Embeddings配置来源检查:
  - 环境变量 EMBEDDING_API_KEY: 已设置
  - 环境变量 EMBEDDING_BASE_URL: 已设置
  - 最终使用的 Base URL来源: Embeddings专用配置
配置EmbeddingModel - baseUrl: http://ollama:11434/v1, embeddingsPath: embeddings, model: nomic-embed-text
Embeddings API完整URL: http://ollama:11434/v1/embeddings
```

### 3. 测试Embedding API

在应用运行时，尝试调用RAG功能，应该不再出现"余额不足"的错误。

## 常见问题

### 问题1：网络连接失败

**错误信息：** `Connection refused` 或 `Name or service not known`

**解决方案：**
1. 确认Ollama容器正在运行：`docker ps | grep ollama`
2. 确认网络名称正确：`docker network ls | grep novel`
3. 如果应用不在docker网络中，使用 `localhost:11434` 而不是 `ollama:11434`

### 问题2：模型未找到

**错误信息：** `model not found`

**解决方案：**
1. 确认模型已拉取：`docker exec -it novel-dev-ollama ollama list`
2. 如果模型不存在，重新拉取：`docker exec -it novel-dev-ollama ollama pull nomic-embed-text`

### 问题3：Ollama响应慢

**原因：** 首次运行模型时需要加载到内存，可能需要几秒钟。

**解决方案：**
1. 这是正常现象，后续请求会更快
2. 可以增加Ollama容器的内存限制（如果系统资源允许）

### 问题4：端口冲突

**错误信息：** `port is already allocated`

**解决方案：**
1. 检查端口11434是否被占用：`netstat -an | grep 11434`（Windows）或 `lsof -i :11434`（Linux/Mac）
2. 修改docker-compose-ollama.yml中的端口映射，例如改为 `"11435:11434"`

## 管理命令

### 查看Ollama日志

```bash
docker logs -f novel-dev-ollama
```

### 停止Ollama服务

```bash
docker compose -p novel-dev -f docker-compose-ollama.yml stop
```

### 重启Ollama服务

```bash
docker compose -p novel-dev -f docker-compose-ollama.yml restart
```

### 删除Ollama容器（保留数据）

```bash
docker compose -p novel-dev -f docker-compose-ollama.yml down
```

### 完全删除Ollama（包括模型数据）

```bash
docker compose -p novel-dev -f docker-compose-ollama.yml down -v
```

**注意：** 删除数据卷会删除所有已下载的模型，需要重新拉取。

## 性能说明

- **nomic-embed-text** 模型大小约137MB
- 首次加载到内存需要几秒钟
- 生成embedding的速度取决于CPU性能
- 对于大多数应用场景，性能足够使用

## 优势

✅ **免费** - 无需API密钥和费用  
✅ **本地运行** - 数据不出本地，隐私安全  
✅ **离线可用** - 不依赖外部服务  
✅ **易于部署** - Docker一键启动  

## 下一步

配置完成后：
1. 重启应用以加载新配置
2. 测试RAG功能是否正常工作
3. 查看应用日志确认没有错误
