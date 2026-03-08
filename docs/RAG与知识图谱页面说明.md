# RAG 文档库与知识图谱页面说明

## 1. 知识图谱（Neo4j）

### 后端接口（`/api/kg/*`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/kg/graph?novelId=xxx` | 返回该小说下的节点与边，格式 `{ nodes, edges }`，供前端图可视化 |
| GET | `/api/kg/characters?novelId=xxx` | 按小说列出人物 |
| GET | `/api/kg/foreshadowing?novelId=xxx` | 按小说列出伏笔 |
| POST | `/api/kg/character` | 创建人物（Body: characterId, name, type, personality, background, abilities, novelId） |
| PUT | `/api/kg/character` | 更新人物 |
| DELETE | `/api/kg/character?characterId=xxx` | 删除人物 |
| POST | `/api/kg/foreshadowing` | 创建伏笔（Body: foreshadowingId, content, novelId?） |
| PUT | `/api/kg/foreshadowing` | 更新伏笔 |
| DELETE | `/api/kg/foreshadowing?foreshadowingId=xxx` | 删除伏笔 |

- `novelId` 为空时，图与列表返回「全部」数据（人物为无 novelId 或空 novelId 的节点；伏笔为全部）。
- 人物节点已支持 `novelId` 字段（`Character` 值对象与 Neo4j 写入/更新均已包含），便于按小说筛选。

### 前端页面

- 访问：启动应用后打开 **http://localhost:8080/kg.html**
- 功能：输入小说 ID 后点击「加载图谱」渲染图（Cytoscape.js）；侧边栏为节点列表，点击节点可看详情并删除。

---

## 2. RAG 文档库（PgVector）

### 后端接口（`/api/rag/*`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/rag/documents?novelId=xxx&chapterId=xxx&page=1&size=20` | 分页查询文档列表（按 novelId、可选 chapterId 筛选） |
| GET | `/api/rag/search?q=xxx&novelId=xxx&topK=10` | 语义检索，可选按 novelId 过滤结果 |
| DELETE | `/api/rag/document?id=xxx` | 按文档 ID 删除一条 |
| DELETE | `/api/rag/documents?novelId=xxx` | 按小说 ID 删除该小说下全部 RAG 文档 |

- 文档列表项包含：id、contentSummary（内容摘要）、metadata（含 novelId、卷/章、场景等）。

### 前端页面

- 访问：**http://localhost:8080/rag.html**
- 功能：表格展示文档列表（小说/卷/章、场景、内容摘要）；可按小说ID、章节ID 筛选、分页；搜索框调用检索接口，下方展示命中文档与相似度。

---

## 3. 入口与静态资源

### Vue 前端（推荐）

- 启动前端：在 `Frontend` 目录执行 `npm run dev`，访问 **http://localhost:3000/**
- 顶部导航：**小说生成**（首页）、**知识图谱**（/kg）、**RAG 文档库**（/rag）
- 知识图谱页：http://localhost:3000/kg  
- RAG 文档库页：http://localhost:3000/rag  
- 前端通过 `VITE_API_BASE_URL`（默认 `http://localhost:8091`）或 Vite 代理（/api → 8091）访问后端，请确保 Novel Agent 后端已启动（如 8091 端口）。

### 后端静态页（可选）

- 若直接访问后端：**http://localhost:8091/** 或 **http://localhost:8091/kg.html**、**http://localhost:8091/rag.html**（端口以实际为准，如 8091）。
- 静态页面位于：`ai-agent-station-study-app/src/main/resources/static/`（kg.html、rag.html、index.html）。

---

## 4. 技术说明

- **知识图谱**：`IKnowledgeGraphService` 提供 `getGraph`、`listCharacters`、`listForeshadowing` 及人物/伏笔的增删改；Neo4j 实现中人物/伏笔/关系均支持按 novelId 查询。**自动写入**（避免图谱为空）：
  - **Seed 生成后**：从主角设定（`protagonistSetting`）解析主角名并创建「主角」节点，背景为设定全文。
  - **章节梗概生成后**：将本章 `keyCharacters` 同步为人物节点（类型默认「配角」），同一章内多个人物两两建立 `RELATED_TO` 关系边；伏笔继续写入 `Foreshadowing` 节点。
  - **场景生成后**：将场景中的 `characters` 同步为人物节点（不存在则创建）。
  - 人物 ID 规则：`novelId + "_c_" + 规范化名称`，便于按小说去重与筛选。若图谱仍为空，请确认 Neo4j 已启动且已执行过至少一次 Seed/章节/场景生成。
- **RAG**：`IRAGService` 提供 `listDocuments`、`deleteById`、`deleteByNovelId`；`search(query, language, topK, novelId)` 支持按小说 ID 过滤，**场景生成时仅检索本小说**，避免跨小说干扰。写入向量库时 metadata 包含 `novelId`、章节/场景信息及 **characters**、**location**，便于后续按人物/地点筛选或展示。
