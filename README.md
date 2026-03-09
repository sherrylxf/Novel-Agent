# Novel Agent 后端

AI 小说创作 Agent 后端服务：支持小说规划、章节生成、知识图谱与 RAG 检索。

## 项目功能

| 模块 | 功能说明 |
|------|----------|
| **小说工作台** | 小说列表、创建/保存/归档小说，切换当前创作上下文；Agent 配置管理（全局/按小说）；章节的增删改查与编辑。 |
| **小说生成** | 从创意（题材、核心冲突、世界观）生成 Seed；自动规划整体大纲与分卷计划；按步骤生成章节梗概与场景内容，支持人工确认后继续。 |
| **大纲与计划** | 大纲/分卷计划的保存、按小说查询、更新；支持前端对接「继续创作」「自动完成一章」等流程。 |
| **知识图谱** | 按小说维护人物、伏笔等节点与关系；提供图谱数据接口（`/api/kg/graph`），支持人物/伏笔的增删改查，供前端可视化。 |
| **RAG 文档库** | 按小说/章节、记忆类型等筛选文档；语义检索（可带人物、地点、剧情线等过滤）；检索结果与 explain 分数；文档删除等。 |

**技术栈：** Spring Boot、Spring AI（OpenAI 兼容）、MySQL、PostgreSQL（pgvector）、Neo4j；LLM 支持 DeepSeek / OpenAI / 通义千问等。

---

## 环境要求

- **JDK** 17+
- **Maven** 3.6+
- **MySQL** 8.0+（业务库）
- **PostgreSQL** 14+（可选，pgvector 向量库）
- **Neo4j** 5.0+（可选，知识图谱）
- **LLM API**：DeepSeek / OpenAI / 通义千问等（兼容 OpenAI API 格式）

---

## 一、克隆项目

```bash
git clone <仓库地址> novel-agent
cd novel-agent
```

---

## 二、配置

### 1. 密钥与敏感信息（必读）

**请勿将任何 API Key、密码等敏感信息提交到 Git。**

- 所有密钥、密码**仅通过环境变量或本地配置文件**设置。
- 项目已通过 `.gitignore` 忽略：
  - `.env`、`.env.*`（保留 `.env.example` 模板）
  - `**/application-local.yml`、`**/application-secret.yml` 等
- **正确做法**：复制 `.env.example` 为 `.env`，在本地填写真实值，且不要提交 `.env`。
- 若曾误将 Key 提交到仓库：请到对应平台（如 DeepSeek）**撤销旧 Key 并重新生成**，再在本地用新 Key 配置。

### 2. 使用 .env 配置（推荐）

```bash
# 复制模板（仓库内只有 .env.example，无真实密钥）
cp .env.example .env

# 编辑 .env，填写你自己的配置，例如 DeepSeek：
# OPENAI_API_KEY=sk-xxxxxxxx   # 你的 DeepSeek/OpenAI Key
# OPENAI_BASE_URL=https://api.deepseek.com
```

**.env.example 中仅为占位符，不含任何真实 Key。**

### 3. 主要配置项说明

| 变量名 | 说明 | 示例 |
|--------|------|------|
| `OPENAI_API_KEY` | LLM API Key（DeepSeek/OpenAI/通义等） | 在对应平台申请 |
| `OPENAI_BASE_URL` | API 地址 | DeepSeek: `https://api.deepseek.com` |
| `EMBEDDING_API_KEY` / `EMBEDDING_BASE_URL` | 向量模型（RAG） | 本地 Ollama 可用 `ollama` + `http://localhost:11434/v1` |

数据库等更多配置见 `ai-agent-station-study-app/src/main/resources/application-dev.yml`（MySQL/PostgreSQL/Neo4j 地址、账号等）。生产环境建议用环境变量覆盖，勿在配置文件中写真实密码。

**详情见 docs/dev-ops**



