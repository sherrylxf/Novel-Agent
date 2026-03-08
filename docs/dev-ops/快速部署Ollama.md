# 快速部署Ollama本地Embedding模型

## 前提条件

- Docker和Docker Compose已安装
- 其他容器（MySQL、PostgreSQL等）已经运行

## 一键部署步骤

### 1. 进入配置目录

```bash
cd "Novel Agent/docs/dev-ops"
```

### 2. 拉取Ollama镜像（可选，docker compose会自动拉取）

```bash
docker pull registry.cn-hangzhou.aliyuncs.com/xfg-studio/ollama:0.5.10
```

### 3. 启动Ollama服务

```bash
docker compose -p novel-dev -f docker-compose-ollama.yml up -d
```

### 3. 拉取Embedding模型

```bash
docker exec -it novel-dev-ollama ollama pull nomic-embed-text
```

**等待模型下载完成**（约137MB，取决于网络速度）

### 4. 验证部署

```bash
# 检查容器状态
docker ps | grep ollama

# 检查模型列表
docker exec -it novel-dev-ollama ollama list

# 测试API
curl http://localhost:11434/api/tags
```

## 配置应用使用Ollama

### 如果应用在Docker中运行

应用配置已经更新，会自动使用Ollama（如果环境变量未设置其他值）。

重启应用容器：

```bash
docker restart novel-dev-app
```

### 如果应用在本地运行

设置环境变量：

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

然后重启应用。

## 验证配置是否生效

查看应用启动日志，应该看到：

```
Embeddings配置来源检查:
  - 最终使用的 Base URL来源: Embeddings专用配置
配置EmbeddingModel - baseUrl: http://ollama:11434/v1, model: nomic-embed-text
```

## 完成！

现在您的应用已经配置为使用本地Ollama Embedding模型，不再需要智谱AI的API密钥和余额。

## 故障排查

如果遇到问题，请查看：
- [详细部署文档](./部署Ollama本地Embedding模型.md)
- Ollama容器日志：`docker logs novel-dev-ollama`
- 应用日志中的错误信息
