<template>
  <div class="kg-page">
    <div class="top">
      <h1>知识图谱</h1>
      <div class="filter">
        <label>小说ID</label>
        <input v-model="novelId" type="text" placeholder="可选，留空查全部" />
        <button type="button" class="btn-secondary" @click="useCurrentNovel">使用当前小说</button>
        <button @click="loadGraph">加载图谱</button>
        <span class="sep">|</span>
        <span class="filter-label">筛选</span>
        <label class="group-label">节点类型：</label>
        <div class="chk-group">
          <label v-for="t in availableNodeTypes" :key="'n-' + t" class="chk">
            <input v-model="filterNodeTypes" type="checkbox" :value="t" @change="applyTypeFilters" />
            <span :class="'type-' + t">{{ nodeTypeLabel(t) }}</span>
          </label>
          <span v-if="availableNodeTypes.length === 0 && graphData.nodes.length" class="placeholder">无类型</span>
          <span v-else-if="graphData.nodes.length === 0" class="placeholder">加载后显示</span>
        </div>
        <label class="group-label">关系类型：</label>
        <div class="chk-group">
          <label v-for="t in availableEdgeTypes" :key="'e-' + t" class="chk">
            <input v-model="filterEdgeTypes" type="checkbox" :value="t" @change="applyTypeFilters" />
            <span>{{ t || '(无类型)' }}</span>
          </label>
          <span v-if="availableEdgeTypes.length === 0 && graphData.edges.length" class="placeholder">无类型</span>
          <span v-else-if="graphData.edges.length === 0" class="placeholder">加载后显示</span>
        </div>
        <button type="button" class="btn-reset" @click="resetFilter">重置筛选</button>
        <span class="sep">|</span>
        <label class="layout-label">布局</label>
        <select v-model="layoutMode" @change="renderCy()">
          <option value="concentric">同心圆（参考图样式）</option>
          <option value="cose">力导向</option>
        </select>
        <label class="theme-label">主题</label>
        <select v-model="theme" @change="applyTheme(); renderCy()">
          <option value="light">浅色（参考图）</option>
          <option value="dark">深色</option>
        </select>
        <span class="current-tip">{{ currentNovelTip }}</span>
      </div>
    </div>
    <div class="layout">
      <div ref="cyRef" class="cy-container"></div>
      <div class="side">
        <h3>节点列表 · 点击节点看详情</h3>
        <p v-if="visibleNodes.length" class="stats">共 {{ visibleNodes.length }} 节点，{{ visibleEdges.length }} 条连线</p>
        <ul v-if="visibleNodes.length" class="node-list">
          <li
            v-for="n in visibleNodes"
            :key="n.id"
            :class="{ active: selectedNodeId === n.id }"
            @click="selectedNodeId = n.id; showDetail(n.id)"
          >
            <span class="type">[{{ n.type || '' }}]</span> {{ n.label || n.id }}
          </li>
        </ul>
        <p v-else-if="graphData.nodes.length === 0" class="empty">{{ novelId ? '该小说暂无知识图谱数据。可留空小说ID查看全部节点；或先生成章节（伏笔会写入 KG）。' : '暂无节点，请先输入小说ID加载或留空查看全部' }}</p>
        <p v-else class="empty">当前筛选无节点，请勾选节点类型或点击「重置筛选」</p>
        <div v-if="selectedNode" class="detail">
          <pre>{{ JSON.stringify(selectedNode.properties || {}, null, 2) }}</pre>
          <button v-if="canDeleteNode(selectedNode)" class="btn-del" @click="deleteNode(selectedNode)">删除</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import cytoscape from 'cytoscape'
import { getKgGraph, deleteKgCharacter, deleteKgForeshadowing } from '@/services/novelApi'
import { useNovelWorkspaceStore } from '@/stores/novelWorkspace'

const cyRef = ref(null)
const novelId = ref('')
const graphData = ref({ nodes: [], edges: [] })
const selectedNodeId = ref(null)
const layoutMode = ref('concentric')
const theme = ref('light')
const filterNodeTypes = ref([])
const filterEdgeTypes = ref([])
const workspaceStore = useNovelWorkspaceStore()
let cy = null
const currentNovelTip = computed(() => {
  const current = workspaceStore.currentNovel.value
  return current ? `当前小说：${current.title || current.novelId}` : '当前未选择小说，可留空查看全部'
})

const availableNodeTypes = computed(() => {
  const set = new Set()
  ;(graphData.value.nodes || []).forEach((n) => n.type && set.add(n.type))
  return [...set].sort()
})
const availableEdgeTypes = computed(() => {
  const set = new Set()
  ;(graphData.value.edges || []).forEach((e) => set.add(e.type != null ? e.type : ''))
  return [...set].sort()
})
const visibleNodes = computed(() => {
  const nodes = graphData.value.nodes || []
  if (!filterNodeTypes.value.length) return nodes
  return nodes.filter((n) => filterNodeTypes.value.includes(n.type))
})
const visibleEdges = computed(() => {
  const edges = graphData.value.edges || []
  const nodeIds = new Set(visibleNodes.value.map((n) => n.id))
  return edges.filter((e) => {
    if (!nodeIds.has(e.source) || !nodeIds.has(e.target)) return false
    if (!filterEdgeTypes.value.length) return true
    const et = e.type != null ? e.type : ''
    return filterEdgeTypes.value.includes(et)
  })
})
function nodeTypeLabel(t) {
  const map = { Character: '人物', Foreshadowing: '伏笔', Event: '事件', PlotThread: '剧情线', StoryHub: '故事', Location: '地点', Faction: '势力', Artifact: '物品', Technique: '技法' }
  return map[t] || t
}
function applyTypeFilters() {
  renderCy()
}
function resetFilter() {
  filterNodeTypes.value = [...availableNodeTypes.value]
  filterEdgeTypes.value = [...availableEdgeTypes.value]
  renderCy()
}

const selectedNode = ref(null)
function showDetail(id) {
  selectedNode.value = graphData.value.nodes.find((n) => n.id === id) || null
}

async function loadGraph() {
  try {
    const res = await getKgGraph(novelId.value.trim())
    graphData.value = res
    const nodes = res?.nodes || []
    const edges = res?.edges || []
    filterNodeTypes.value = [...new Set(nodes.map((n) => n.type).filter(Boolean))]
    filterEdgeTypes.value = [...new Set(edges.map((e) => e.type != null ? e.type : ''))]
    renderCy()
  } catch (e) {
    console.error(e)
    alert('加载失败: ' + (e.message || e))
  }
}

function useCurrentNovel() {
  novelId.value = workspaceStore.currentNovelId.value || ''
  loadGraph()
}

const NODE_COLORS_DARK = {
  StoryHub: '#414868',
  Character: '#7aa2f7',
  Foreshadowing: '#bb9af7',
  PlotThread: '#e0af68',
  Event: '#9ece6a',
  Location: '#73daca',
  Faction: '#ff9e64',
  Artifact: '#f7768e',
  Technique: '#ad8ee6',
}
const NODE_COLORS_LIGHT = {
  StoryHub: '#94a3b8',
  Character: '#3b82f6',
  Foreshadowing: '#8b5cf6',
  PlotThread: '#d97706',
  Event: '#22c55e',
  Location: '#14b8a6',
  Faction: '#f97316',
  Artifact: '#ef4444',
  Technique: '#a855f7',
}
function nodeColor(type) {
  const colors = theme.value === 'light' ? NODE_COLORS_LIGHT : NODE_COLORS_DARK
  return colors[type] || colors.Character
}
function nodeSize(type) {
  if (type === 'StoryHub') return 48
  if (type === 'Character') return 36
  return 28
}
function applyTheme() {
  const page = document.querySelector('.kg-page')
  if (!page) return
  if (theme.value === 'dark') {
    page.classList.add('theme-dark')
  } else {
    page.classList.remove('theme-dark')
  }
}

function renderCy() {
  if (!cyRef.value) return
  applyTheme()
  const nodes = visibleNodes.value
  const edges = visibleEdges.value
  const elements = []
  nodes.forEach((n) => {
    elements.push({ data: { id: n.id, label: n.label || n.id, type: n.type || '', level: n.type === 'StoryHub' ? 0 : n.type === 'Character' ? 1 : 2 } })
  })
  edges.forEach((e) => {
    elements.push({ data: { id: e.id, source: e.source, target: e.target, label: e.type || '', type: e.type || '' } })
  })
  if (cy) cy.destroy()
  const isLight = theme.value === 'light'
  const textColor = isLight ? '#334155' : '#c0caf5'
  const edgeColor = isLight ? '#64748b' : '#565f89'
  const concentricStyle = layoutMode.value === 'concentric'
  cy = cytoscape({
    container: cyRef.value,
    elements,
    style: [
      {
        selector: 'node',
        style: {
          label: 'data(label)',
          'text-valign': 'bottom',
          'text-margin-y': 4,
          'font-size': (ele) => (ele.data('type') === 'StoryHub' ? 12 : 10),
          color: textColor,
          'background-color': (ele) => nodeColor(ele.data('type')),
          'border-width': isLight ? 1 : 0,
          'border-color': isLight ? '#94a3b8' : '#363b54',
          width: (ele) => nodeSize(ele.data('type')),
          height: (ele) => nodeSize(ele.data('type')),
        },
      },
      {
        selector: 'edge',
        style: {
          width: 1.5,
          'line-color': edgeColor,
          'target-arrow-color': concentricStyle ? 'transparent' : edgeColor,
          'curve-style': 'bezier',
          'target-arrow-shape': concentricStyle ? 'none' : 'triangle',
          label: concentricStyle ? '' : 'data(label)',
          'font-size': '8px',
          color: isLight ? '#64748b' : '#a9b1d6',
          'text-rotation': 'autorotate',
          'text-margin-y': -8,
        },
      },
    ],
    layout: layoutMode.value === 'concentric'
      ? { name: 'concentric', concentric: (node) => node.data('level'), levelWidth: () => 1, animate: false }
      : { name: 'cose', animate: false },
  })
  cy.on('tap', 'node', (evt) => {
    selectedNodeId.value = evt.target.id()
    showDetail(selectedNodeId.value)
  })
}

function canDeleteNode(node) {
  return node && node.type !== 'StoryHub' && (node.type === 'Character' || node.type === 'Foreshadowing')
}

async function deleteNode(node) {
  if (!canDeleteNode(node) || !confirm('确定删除该节点？')) return
  try {
    if (node.type === 'Character') await deleteKgCharacter(node.id)
    else await deleteKgForeshadowing(node.id)
    await loadGraph()
    selectedNodeId.value = null
    selectedNode.value = null
  } catch (e) {
    alert('删除失败: ' + (e.message || e))
  }
}

watch(selectedNodeId, (id) => showDetail(id))

onMounted(() => {
  workspaceStore.initWorkspace().then(() => {
    if (workspaceStore.currentNovelId.value) {
      novelId.value = workspaceStore.currentNovelId.value
    }
    applyTheme()
    loadGraph()
  })
})

watch(workspaceStore.currentNovelId, (id) => {
  if (id && !novelId.value) {
    novelId.value = id
    loadGraph()
  }
})
</script>

<style scoped>
.kg-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--page-bg);
  color: var(--text-primary);
}
.kg-page.theme-dark {
  background: #1a1b26;
  color: #c0caf5;
}
.kg-page.theme-dark .top {
  background: #16161e;
  border-color: #363b54;
}
.kg-page.theme-dark .side {
  background: #16161e;
  border-color: #363b54;
}
.kg-page.theme-dark .filter input,
.kg-page.theme-dark .node-list li,
.kg-page.theme-dark .detail {
  background: #24283b;
  color: #c0caf5;
  border-color: #363b54;
}
.top {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--panel-bg);
  border-bottom: 1px solid var(--border);
  box-shadow: var(--panel-shadow);
}
.top h1 {
  margin: 0;
  font-size: 1.25rem;
}
.filter {
  display: flex;
  align-items: center;
  gap: 8px 12px;
  flex-wrap: wrap;
}
.filter input {
  padding: 6px 10px;
  border: 1px solid var(--input-border);
  border-radius: 10px;
  background: var(--panel-bg);
  color: var(--text-primary);
  width: 200px;
}
.filter button {
  padding: 8px 14px;
  border: none;
  border-radius: 10px;
  background: var(--primary);
  color: #fff;
  cursor: pointer;
  font-weight: 500;
}
.filter .btn-secondary {
  background: var(--secondary-bg);
  color: var(--secondary-text);
}
.current-tip {
  color: var(--text-muted);
  font-size: 0.85rem;
}
.filter button:hover {
  opacity: 0.9;
}
.filter .btn-secondary:hover {
  background: #d1d5db;
}
.filter .sep {
  color: var(--border);
  margin: 0 2px;
}
.filter .filter-label {
  font-weight: 600;
  color: var(--primary);
  font-size: 0.9rem;
}
.filter .group-label {
  color: var(--text-muted);
  font-size: 0.85rem;
}
.filter .chk-group {
  display: inline-flex;
  align-items: center;
  gap: 6px 10px;
  flex-wrap: wrap;
}
.filter .chk {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: var(--text-primary);
  font-size: 0.85rem;
}
.filter .chk input {
  width: auto;
  margin: 0;
  accent-color: var(--primary);
}
.filter .placeholder {
  color: var(--text-muted);
  font-size: 0.8rem;
}
.filter .btn-reset {
  padding: 6px 12px;
  font-size: 0.85rem;
  border: 1px solid var(--primary);
  border-radius: 10px;
  background: transparent;
  color: var(--primary);
  font-weight: 500;
}
.filter .btn-reset:hover {
  background: var(--badge-bg);
}
.filter .type-Character { color: #3b82f6; }
.filter .type-Foreshadowing { color: #8b5cf6; }
.filter .type-Event { color: #22c55e; }
.filter .type-PlotThread { color: #d97706; }
.filter .type-StoryHub { color: #64748b; }
.filter .type-Location { color: #14b8a6; }
.filter .type-Faction { color: #f97316; }
.filter .type-Artifact { color: #ef4444; }
.filter .type-Technique { color: #a855f7; }
.layout {
  display: flex;
  flex: 1;
  min-height: 0;
}
.cy-container {
  flex: 1;
  min-width: 0;
}
.side {
  width: 320px;
  background: var(--panel-bg);
  border-left: 1px solid var(--border);
  overflow: auto;
  padding: 12px;
  box-shadow: -2px 0 8px rgba(15, 23, 42, 0.06);
}
.side h3 {
  margin: 0 0 8px 0;
  font-size: 0.95rem;
  color: var(--primary);
}
.stats {
  margin: 0 0 8px 0;
  font-size: 0.8rem;
  color: var(--text-muted);
}
.node-list {
  list-style: none;
  padding: 0;
  margin: 0 0 16px 0;
}
.node-list li {
  padding: 8px 10px;
  border-radius: 10px;
  margin-bottom: 4px;
  background: var(--meta-bg);
  font-size: 0.85rem;
  cursor: pointer;
  border: 1px solid transparent;
}
.node-list li:hover,
.node-list li.active {
  background: var(--active-row-bg);
  border-color: var(--border);
}
.node-list .type {
  color: var(--badge-text);
  margin-right: 6px;
}
.detail {
  font-size: 0.8rem;
  color: var(--text-primary);
  margin-top: 8px;
  padding: 10px;
  background: var(--meta-bg);
  border-radius: 10px;
  border: 1px solid var(--border);
  white-space: pre-wrap;
  word-break: break-all;
}
.detail pre {
  margin: 0 0 8px 0;
}
.btn-del {
  padding: 6px 12px;
  font-size: 0.82rem;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  background: var(--danger-bg);
  color: var(--danger-text);
}
.empty {
  color: var(--text-muted);
  font-size: 0.9rem;
}
</style>
