<template>
  <div class="workspace-page">
    <section class="hero">
      <div>
        <h1>小说管理</h1>
        <p>管理多个小说项目，并在刷新后恢复到最近选择的小说上下文。</p>
      </div>
      <div class="hero-actions">
        <RouterLink class="link-btn" to="/">去创作</RouterLink>
        <RouterLink class="link-btn secondary" to="/chapters">管理章节</RouterLink>
      </div>
    </section>

    <section class="panel">
      <h2>新建或录入小说</h2>
      <div class="form-grid">
        <input v-model="form.title" type="text" placeholder="小说标题" />
        <input v-model="form.genre" type="text" placeholder="题材，如修仙/科幻" />
        <input v-model="form.novelId" type="text" placeholder="可选：手动指定 novelId" />
        <button @click="handleSaveNovel" :disabled="saving">{{ saving ? '保存中...' : '保存小说' }}</button>
      </div>
      <p class="hint">如果不填 `novelId`，后端会自动生成；保存后会自动切换为当前小说。</p>
    </section>

    <section v-if="currentNovel" class="panel current-panel">
      <div class="panel-header">
        <h2>当前小说</h2>
        <span class="badge">{{ currentNovel.title || currentNovel.novelId }}</span>
      </div>
      <div class="current-grid">
        <div class="meta-card">
          <strong>小说ID</strong>
          <span>{{ currentNovel.novelId }}</span>
        </div>
        <div class="meta-card">
          <strong>题材</strong>
          <span>{{ currentNovel.genre || '-' }}</span>
        </div>
        <div class="meta-card">
          <strong>大纲</strong>
          <span>{{ currentNovel.hasPlan ? '已存在' : '未保存' }}</span>
        </div>
        <div class="meta-card">
          <strong>章节数</strong>
          <span>{{ currentNovel.chapterCount || 0 }}</span>
        </div>
      </div>
      <p v-if="currentNovel.coreConflict" class="summary">{{ currentNovel.coreConflict }}</p>
      <div class="hero-actions">
        <RouterLink class="link-btn" to="/">继续创作</RouterLink>
        <RouterLink class="link-btn secondary" to="/chapters">章节管理</RouterLink>
        <RouterLink class="link-btn secondary" to="/configs">配置管理</RouterLink>
      </div>
    </section>

    <section class="panel">
      <div class="panel-header">
        <h2>小说列表</h2>
        <button class="refresh-btn" @click="reload">刷新</button>
      </div>
      <table class="novel-table">
        <thead>
          <tr>
            <th>标题</th>
            <th>题材</th>
            <th>章节</th>
            <th>大纲</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="6">加载中...</td>
          </tr>
          <tr v-else-if="!novels.length">
            <td colspan="6">暂无小说，请先创建或开始创作。</td>
          </tr>
          <tr v-for="item in novels" :key="item.novelId" :class="{ active: item.novelId === currentNovelId }">
            <td>
              <strong>{{ item.title || item.novelId }}</strong>
              <div class="sub">{{ item.novelId }}</div>
            </td>
            <td>{{ item.genre || '-' }}</td>
            <td>{{ item.chapterCount || 0 }}</td>
            <td>{{ item.hasPlan ? '已保存' : '未保存' }}</td>
            <td>{{ formatDate(item.updateTime) }}</td>
            <td class="actions">
              <button @click="handleSelectNovel(item.novelId)">切换</button>
              <button class="danger" @click="handleArchive(item)">归档</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useNovelWorkspaceStore } from '@/stores/novelWorkspace'

const workspaceStore = useNovelWorkspaceStore()
const form = ref({
  novelId: '',
  title: '',
  genre: '',
})
const saving = ref(false)

const novels = computed(() => workspaceStore.novels.value)
const currentNovel = computed(() => workspaceStore.currentNovel.value)
const currentNovelId = computed(() => workspaceStore.currentNovelId.value)
const loading = computed(() => workspaceStore.loading.value)

function formatDate(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN')
}

async function reload() {
  await workspaceStore.loadNovels()
  await workspaceStore.refreshCurrentNovel()
}

async function handleSaveNovel() {
  if (!form.value.title.trim()) {
    alert('请输入小说标题')
    return
  }
  saving.value = true
  try {
    await workspaceStore.saveNovel({
      novelId: form.value.novelId.trim() || undefined,
      title: form.value.title.trim(),
      genre: form.value.genre.trim(),
    })
    form.value = { novelId: '', title: '', genre: '' }
  } catch (error) {
    alert(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleSelectNovel(novelId) {
  try {
    await workspaceStore.selectNovel(novelId)
  } catch (error) {
    alert(error.message || '切换失败')
  }
}

async function handleArchive(item) {
  if (!confirm(`确认归档小说「${item.title || item.novelId}」吗？`)) return
  try {
    await workspaceStore.archiveNovel(item.novelId)
  } catch (error) {
    alert(error.message || '归档失败')
  }
}

onMounted(async () => {
  await workspaceStore.initWorkspace()
})
</script>

<style scoped>
.workspace-page {
  min-height: 100vh;
  background: #f6f8fb;
  padding: 24px;
  color: #1f2937;
}
.hero,
.panel {
  max-width: 1200px;
  margin: 0 auto 20px;
  background: #fff;
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
}
.hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
.hero h1,
.panel h2 {
  margin: 0 0 8px;
}
.hero p,
.hint,
.summary,
.sub {
  color: #6b7280;
}
.hero-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.link-btn,
button {
  border: none;
  border-radius: 10px;
  padding: 10px 16px;
  font-size: 14px;
  cursor: pointer;
}
.link-btn {
  background: #2563eb;
  color: #fff;
  text-decoration: none;
}
.link-btn.secondary,
.refresh-btn {
  background: #e5e7eb;
  color: #111827;
}
.form-grid,
.current-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}
.form-grid input {
  border: 1px solid #d1d5db;
  border-radius: 10px;
  padding: 10px 12px;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.badge {
  background: #dbeafe;
  color: #1d4ed8;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 13px;
}
.meta-card {
  background: #f8fafc;
  border-radius: 12px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.novel-table {
  width: 100%;
  border-collapse: collapse;
}
.novel-table th,
.novel-table td {
  padding: 12px;
  border-bottom: 1px solid #e5e7eb;
  text-align: left;
}
.novel-table tr.active {
  background: #eff6ff;
}
.actions {
  display: flex;
  gap: 8px;
}
.danger {
  background: #fee2e2;
  color: #b91c1c;
}
@media (max-width: 768px) {
  .workspace-page {
    padding: 16px;
  }
  .hero {
    flex-direction: column;
    align-items: flex-start;
  }
  .actions {
    flex-direction: column;
  }
}
</style>
