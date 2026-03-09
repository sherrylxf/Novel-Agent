# Novel Agent 前端

Novel Agent 的网页前端界面，基于 Vue 3 + Vite 构建。

## 功能特性

- 📝 小说生成表单（题材、核心冲突、世界观设定）
- 📊 实时进度显示（SSE流式输出）
- ✅ **用户可控流程**：每个Agent执行后暂停，等待用户审查和确认
- 📋 **内容预览**：显示每个节点生成的内容，支持JSON格式化展示
- 🎨 现代化UI设计
- 📱 响应式布局，支持移动端

## 技术栈

- Vue 3 (Composition API)
- Vite
- 原生 CSS（无UI框架依赖）

## 快速开始

### 安装依赖

```bash
npm install
```

### 开发模式

```bash
npm run dev
```

前端将在 `http://localhost:3000` 启动。

### 构建生产版本

```bash
npm run build
```

### 预览生产版本

```bash
npm run preview
```

## 配置

### API 地址配置

默认情况下，Vite 开发服务器已配置代理，将 `/api` 请求代理到 `http://localhost:8091`。

如果需要修改 API 地址，可以：

1. **使用环境变量**：创建 `.env` 文件（参考 `.env.example`）
   ```env
   VITE_API_BASE_URL=http://localhost:8091
   ```

2. **修改代理配置**：编辑 `vite.config.js` 中的 `server.proxy` 配置

## 使用说明

1. 填写左侧表单：
   - **题材类型**：选择小说题材（修仙、历史穿越、都市重生等）
   - **核心冲突/主题**：描述小说的核心冲突或主题
   - **世界观设定**：描述小说的世界观设定
   - **最大执行步数**：选择生成步数（5/10/20步）

2. 点击"开始生成"按钮

3. 右侧将实时显示生成进度：
   - 进度条显示整体进度
   - 日志显示每个阶段的执行情况
   - **每个节点执行完成后，会弹出确认对话框**
   - 在对话框中可以查看生成的内容
   - 点击"同意，继续执行"继续下一步，或点击"拒绝，停止执行"取消

4. **用户确认流程**：
   - 当节点执行完成后，会自动弹出确认对话框
   - 对话框中显示：
     - 当前节点名称
     - 当前阶段
     - 下一步阶段
     - 生成的内容（JSON格式，可滚动查看）
   - 用户可以选择：
     - ✅ **同意，继续执行**：继续执行下一个节点
     - ❌ **拒绝，停止执行**：停止生成流程

## API 接口

### 生成小说

- **接口**：`POST /api/v1/novel/generate`
- **请求格式**：JSON
- **响应格式**：SSE (Server-Sent Events)

**请求参数：**
```json
{
  "genre": "修仙",
  "coreConflict": "一个废材少年的逆袭之路",
  "worldSetting": "修真世界，分为炼气、筑基、金丹、元婴等境界",
  "sessionId": "session_1234567890_abc123",
  "maxStep": 5
}
```

**响应消息类型：**
- `progress`: 进度更新
- `waiting_for_approval`: **等待用户确认**（包含生成的内容数据）
- `complete`: 生成完成
- `error`: 错误信息

### 用户确认继续

- **接口**：`POST /api/v1/novel/approve`
- **请求参数**：
  - `sessionId`: 会话ID（必填）
  - `approved`: 是否同意继续（默认true）

**示例：**
```bash
POST /api/v1/novel/approve?sessionId=session_123&approved=true
```

## 项目结构

```
Frontend/
├── src/
│   ├── components/
│   │   └── NovelGenerator.vue    # 主组件
│   ├── services/
│   │   └── novelApi.js            # API 服务
│   ├── App.vue                     # 根组件
│   ├── main.js                     # 入口文件
│   └── assets/                     # 静态资源
├── public/                         # 公共资源
├── vite.config.js                  # Vite 配置
└── package.json                    # 项目配置
```

## 注意事项

1. 确保后端服务（Novel Agent）已启动并运行在 `http://localhost:8091`
2. 如果后端运行在不同端口，需要修改代理配置或环境变量
3. 浏览器需要支持 SSE（Server-Sent Events），现代浏览器均支持
4. **用户可控流程**：
   - 每个节点执行后会暂停，等待用户确认
   - 如果长时间不确认，连接可能会超时
   - 确认对话框会阻止继续执行，直到用户做出选择
   - 生成的内容以JSON格式显示，可以复制查看

## 开发建议

- 使用 Vue DevTools 进行调试
- 查看浏览器控制台了解 API 调用情况
- 检查网络面板查看 SSE 连接状态
