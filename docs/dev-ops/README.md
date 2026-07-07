# Novel Agent Docker 容器组配置指南

## 📋 概述

本项目使用 Docker Compose 管理所有服务，项目名称为 `novel-dev`。

### Windows / Docker Desktop 注意事项

- **路径含空格**（如 `D:\destop\Novel Agent\...`）：在终端执行时**必须给 compose 文件加引号**，例如  
  `docker compose -p novel-dev -f "D:\destop\Novel Agent\docs\dev-ops\docker-compose-environment.yml" up -d`  
  或在 IDE 里将工作目录设为**无空格**的短路径，避免 `cd: can't cd to ...`。
- **若仍出现 `novel-dev-mysql`，说明在用旧版 Compose**：请拉取最新代码或确认打开的 `docker-compose-*.yml` 中**已无** `mysql` 服务。
- **PostgreSQL 报 unhealthy**：若曾初始化失败，旧数据卷可能处于坏状态，可执行  
  `docker compose -p novel-dev -f <你的 compose 文件> down -v`（会删卷）后重新 `up`；并查看  
  `docker logs novel-dev-postgresql` 中的报错。
- **端口 15432 已被占用**（`bind: Only one usage of each socket address`）：
  1. 看是否已有容器占用：`docker ps -a` 找到仍绑定 `15432` 的容器并 `docker stop <容器名>` / `docker rm`。
  2. Windows 查占用进程：`netstat -ano | findstr :15432`，最后一列为 PID，再用 `tasklist | findstr <PID>` 或任务管理器结束；常见为**本机已装的 PostgreSQL** 或其它 Docker 项目。
  3. 若需保留本机 15432：在 compose 里把 `15432:5432` 改成 **`15433:5432`**（或其它未占用端口），并同步修改 `application-dev.yml` 里 JDBC：`jdbc:postgresql://127.0.0.1:15433/novel_vector`。

**业务与向量数据统一在 PostgreSQL**（库名 `novel_vector`）：MyBatis 业务表与 `novel_vector_store` 向量表同库，由 `pgvector/init.sql` 初始化。

**服务端口映射：**
- PostgreSQL: `localhost:15432`（业务 + pgvector）
- Neo4j HTTP: `http://localhost:17474`
- Neo4j Bolt: `localhost:17687`
- Redis: `localhost:6379`（RAG 热点缓存、Redisson 限流与分布式锁）
- pgAdmin（仅 `docker-compose-environment-aliyun.yml`）: `http://localhost:5050`
- 应用（如启动）: `localhost:8091`

## 🚀 快速启动（只启动数据库服务）

**推荐方式：** 只启动数据库和相关服务，不启动应用服务。

### 方式1：使用标准镜像（Docker Hub）

```bash
# 进入dev-ops目录
cd docs/dev-ops

# 启动服务（PostgreSQL、Neo4j、Redis、Ollama）
docker compose -p novel-dev -f docker-compose-environment.yml up -d

# 查看服务状态
docker compose -p novel-dev ps

# 查看日志
docker compose -p novel-dev logs -f
```

### 方式2：使用阿里云镜像（推荐，国内访问更快）

```bash
# 进入dev-ops目录
cd docs/dev-ops

# 启动服务（含 pgAdmin）
docker compose -p novel-dev -f docker-compose-environment-aliyun.yml up -d

# 查看服务状态
docker compose -p novel-dev ps

# 查看日志
docker compose -p novel-dev logs -f
```

## 📊 服务管理命令

### 查看服务状态

```bash
# 查看所有服务
docker compose -p novel-dev ps

# 查看特定服务日志
docker compose -p novel-dev logs -f postgresql
docker compose -p novel-dev logs -f neo4j
docker compose -p novel-dev logs -f redis
```

### 停止服务

```bash
# 停止所有服务
docker compose -p novel-dev down

# 停止并删除数据卷（⚠️ 会删除所有数据）
docker compose -p novel-dev down -v
```

### 重启服务

```bash
# 重启所有服务
docker compose -p novel-dev restart

# 重启特定服务
docker compose -p novel-dev restart postgresql
```

### 进入容器

```bash
# 进入 PostgreSQL 容器
docker exec -it novel-dev-postgresql bash
psql -U postgres -d novel_vector

# 进入 Neo4j 容器
docker exec -it novel-dev-neo4j bash
```

## 🔍 验证服务

### 1. 检查 PostgreSQL

```bash
# 使用命令行
psql -h 127.0.0.1 -p 15432 -U postgres -d novel_vector
# 密码：postgres

# 阿里云 compose 可使用 pgAdmin
# 浏览器访问：http://localhost:5050
# 邮箱：admin@novel.com
# 密码：admin
```

### 2. 检查 Neo4j

```bash
# 浏览器访问：http://localhost:17474
# 用户名：neo4j
# 密码：password
```

### 3. 检查 Redis

```bash
docker exec -it novel-dev-redis redis-cli ping
# 期望输出：PONG
```

## 📝 数据库初始化

### PostgreSQL 初始化

容器首次启动会执行 `pgvector/init.sql`：

- 启用 `vector` 扩展  
- 创建业务表（`novel`、`novel_seed`、`novel_plan` 等，原 MySQL 结构已迁入）  
- 创建向量表 `novel_vector_store`（含 HNSW 索引）

### Neo4j 初始化

Neo4j 会自动创建默认数据库，无需手动初始化。

## 🔧 配置说明

### 环境变量

数据库服务的配置见 `docker-compose-environment.yml` 或 `docker-compose-environment-aliyun.yml`：

**PostgreSQL:**
- 用户名: `postgres`
- 密码: `postgres`
- 数据库: `novel_vector`（业务表 + 向量表）
- 端口: `15432:5432`

**Neo4j:**
- 用户名: `neo4j`
- 密码: `password`
- HTTP端口: `17474:7474`
- Bolt端口: `17687:7687`

**Redis:**
- 端口: `6379:6379`
- 持久化: AOF，数据卷 `redis_data`
- 应用（`SPRING_PROFILES_ACTIVE=docker`）默认 `SPRING_DATA_REDIS_HOST=redis`

### 数据持久化

所有数据都保存在 Docker 卷中：

- `postgresql_data` - PostgreSQL（业务 + 向量）
- `neo4j_data` - Neo4j 数据
- `neo4j_logs` - Neo4j 日志
- `redis_data` - Redis（AOF）数据
- `ollama_data` - Ollama 模型（若启用）

即使删除容器，数据也不会丢失（除非使用 `down -v`）。

## 🐛 常见问题

### 1. 端口被占用

修改 compose 文件中对应 `ports` 映射即可。

### 2. 容器启动失败

```bash
docker compose -p novel-dev logs postgresql
docker ps -a | grep novel-dev
```

### 3. 数据库连接失败

确保容器已启动并健康：`docker compose -p novel-dev ps`

### 4. 初始化 SQL 未执行

仅在新数据卷首次启动时执行 `init.sql`。若需重建：

```bash
docker compose -p novel-dev down -v
docker compose -p novel-dev -f docker-compose-environment.yml up -d
```

## 📚 下一步

1. **配置应用连接**：`application-dev.yml` / `application-docker.yml` 中 `spring.datasource.postgresql.*` 指向 `localhost:15432` 或 Compose 服务名 `postgresql`。  
2. **启动应用**：本地运行 `Application.java` 或使用 `docker-compose.yml` 中的应用服务。  
3. **验证**：连接 `novel_vector` 库，确认业务表与 `novel_vector_store` 存在。

## 🔗 相关文档

- `docker-compose.yml` - 完整配置（含应用服务）
- `docker-compose-environment.yml` - 标准镜像
- `docker-compose-environment-aliyun.yml` - 阿里云镜像 + pgAdmin
- 历史 MySQL 脚本（仅供参考，已不随 Compose 启动）：`mysql/sql/novel-agent.sql`，结构以 `pgvector/init.sql` 为准
