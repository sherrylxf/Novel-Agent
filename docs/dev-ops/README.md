# Novel Agent Docker 容器组配置指南

## 📋 概述

本项目使用 Docker Compose 管理所有服务，项目名称为 `novel-dev`。

**服务端口映射：**
- MySQL: `localhost:13306`
- PostgreSQL: `localhost:15432`
- Neo4j HTTP: `http://localhost:17474`
- Neo4j Bolt: `localhost:17687`
- phpMyAdmin: `http://localhost:8899`
- pgAdmin: `http://localhost:5050`
- 应用（如启动）: `localhost:8091`

## 🚀 快速启动（只启动数据库服务）

**推荐方式：** 只启动数据库和管理工具，不启动应用服务。

### 方式1：使用标准镜像（Docker Hub）

```bash
# 进入dev-ops目录
cd docs/dev-ops

# 启动数据库服务（MySQL、PostgreSQL、Neo4j + 管理工具）
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

# 启动数据库服务（使用阿里云镜像）
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
docker compose -p novel-dev logs -f mysql
docker compose -p novel-dev logs -f postgresql
docker compose -p novel-dev logs -f neo4j
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
docker compose -p novel-dev restart mysql
```

### 进入容器

```bash
# 进入MySQL容器
docker exec -it novel-dev-mysql bash
mysql -u root -p123456

# 进入PostgreSQL容器
docker exec -it novel-dev-postgresql bash
psql -U postgres -d novel_vector

# 进入Neo4j容器
docker exec -it novel-dev-neo4j bash
```

## 🔍 验证服务

### 1. 检查MySQL

```bash
# 使用命令行
mysql -h 127.0.0.1 -P 13306 -u root -p123456

# 或使用phpMyAdmin
# 浏览器访问：http://localhost:8899
# 用户名：root
# 密码：123456
```

### 2. 检查PostgreSQL

```bash
# 使用命令行
psql -h 127.0.0.1 -p 15432 -U postgres -d novel_vector
# 密码：postgres

# 或使用pgAdmin
# 浏览器访问：http://localhost:5050
# 邮箱：admin@novel.com
# 密码：admin
```

### 3. 检查Neo4j

```bash
# 浏览器访问：http://localhost:17474
# 用户名：neo4j
# 密码：password
```

## 📝 数据库初始化

### MySQL初始化

MySQL容器启动时会自动执行 `mysql/sql/` 目录下的SQL脚本：
- `novel-agent.sql` - 创建数据库和表结构

### PostgreSQL初始化

PostgreSQL容器启动时会自动执行 `pgvector/init.sql`：
- 创建 `vector` 扩展

### Neo4j初始化

Neo4j会自动创建默认数据库，无需手动初始化。

## 🔧 配置说明

### 环境变量

数据库服务的配置都在 `docker-compose-environment.yml` 或 `docker-compose-environment-aliyun.yml` 中：

**MySQL:**
- Root密码: `123456`
- 端口: `13306:3306`

**PostgreSQL:**
- 用户名: `postgres`
- 密码: `postgres`
- 数据库: `novel_vector`
- 端口: `15432:5432`

**Neo4j:**
- 用户名: `neo4j`
- 密码: `password`
- HTTP端口: `17474:7474`
- Bolt端口: `17687:7687`

### 数据持久化

所有数据都保存在Docker卷中：
- `mysql_data` - MySQL数据
- `postgresql_data` - PostgreSQL数据
- `neo4j_data` - Neo4j数据
- `neo4j_logs` - Neo4j日志

即使删除容器，数据也不会丢失（除非使用 `down -v`）。

## 🐛 常见问题

### 1. 端口被占用

如果端口被占用，可以修改 `docker-compose-environment.yml` 或 `docker-compose-environment-aliyun.yml` 中的端口映射：

```yaml
ports:
  - "13307:3306"  # 修改为其他端口
```

### 2. 容器启动失败

```bash
# 查看详细日志
docker compose -p novel-dev logs mysql
docker compose -p novel-dev logs postgresql

# 检查容器状态
docker ps -a | grep novel-dev
```

### 3. 数据库连接失败

确保容器已启动并健康：

```bash
# 检查健康状态
docker compose -p novel-dev ps

# 等待健康检查通过（可能需要30-60秒）
```

### 4. 初始化SQL未执行

如果MySQL初始化SQL未执行，可以手动执行：

```bash
# 进入MySQL容器
docker exec -it novel-dev-mysql bash

# 执行SQL脚本
mysql -u root -p123456 < /docker-entrypoint-initdb.d/novel-agent.sql
```

## 📚 下一步

数据库服务启动后，可以：

1. **配置应用连接**
   - 修改 `application.yml` 中的数据库连接信息
   - 使用服务名（如 `mysql`、`postgresql`）或 `localhost` + 映射端口

2. **启动应用**
   - 本地运行：在IDE中运行 `Application.java`
   - Docker运行：构建镜像后使用完整配置启动

3. **测试连接**
   - 使用管理工具验证数据库连接
   - 检查表结构是否正确创建

## 🔗 相关文档

- `配置和运行指南.md` - 完整的配置和运行指南
- `docker-compose.yml` - 完整配置（包含应用服务）
- `docker-compose-environment.yml` - 数据库服务配置（标准镜像）
- `docker-compose-environment-aliyun.yml` - 数据库服务配置（阿里云镜像，推荐）
- `docker-compose-db-only.yml` - 旧版配置（已废弃，保留兼容性）
