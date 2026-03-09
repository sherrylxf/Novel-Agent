<template>
  <div class="config-page">
    <section class="panel">
      <div class="panel-header">
        <div>
          <h1>配置管理</h1>
          <p>当前小说：{{ currentNovelLabel }}</p>
        </div>
        <div class="actions">
          <RouterLink class="link-btn" to="/workspace">切换小说</RouterLink>
          <button class="secondary" @click="reload">刷新配置</button>
        </div>
      </div>
    </section>

    <section class="layout">
      <div class="panel">
        <div class="panel-header">
          <h2>新增或修改配置</h2>
          <button class="secondary" @click="resetForm">清空</button>
        </div>
        <div class="form-grid">
          <select v-model="form.scope">
            <option value="novel">当前小说配置</option>
            <option value="global">全局配置</option>
          </select>
          <input v-model="form.agentType" type="text" placeholder="Agent 类型，如 planner" />
          <input v-model="form.configKey" type="text" placeholder="配置键，如 temperature" />
          <input v-model="form.configId" type="text" placeholder="可选：配置ID（编辑时自动带出）" />
          <textarea v-model="form.configValue" rows="12" class="full" placeholder='配置值，建议写 JSON，如 {"temperature":0.7}'></textarea>
        </div>
        <div class="actions">
          <button @click="saveConfig" :disabled="saving">{{ saving ? '保存中...' : '保存配置' }}</button>
        </div>
      </div>

      <div class="panel">
        <h2>配置列表</h2>
        <h3>当前小说配置</h3>
        <div v-if="!novelConfigs.length" class="empty">暂无当前小说配置</div>
        <div v-for="item in novelConfigs" :key="item.configId" class="config-card">
          <div class="config-head">
            <strong>{{ item.agentType }} / {{ item.configKey }}</strong>
            <div class="actions">
              <button class="secondary" @click="fillForm(item, 'novel')">编辑</button>
              <button class="danger" @click="removeConfig(item.configId)">删除</button>
            </div>
          </div>
          <pre>{{ item.configValue }}</pre>
        </div>

        <h3>全局配置</h3>
        <div v-if="!globalConfigs.length" class="empty">暂无全局配置</div>
        <div v-for="item in globalConfigs" :key="item.configId" class="config-card">
          <div class="config-head">
            <strong>{{ item.agentType }} / {{ item.configKey }}</strong>
            <div class="actions">
              <button class="secondary" @click="fillForm(item, 'global')">编辑</button>
              <button class="danger" @click="removeConfig(item.configId)">删除</button>
            </div>
          </div>
          <pre>{{ item.configValue }}</pre>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { deleteNovelConfig, listNovelConfigs, saveNovelConfig } from '@/services/novelApi'
import { useNovelWorkspaceStore } from '@/stores/novelWorkspace'

const workspaceStore = useNovelWorkspaceStore()
const saving = ref(false)
const globalConfigs = ref([])
const novelConfigs = ref([])
const form = ref(createEmptyForm())

const currentNovelId = computed(() => workspaceStore.currentNovelId.value)
const currentNovelLabel = computed(() => {
  const current = workspaceStore.currentNovel.value
  return current ? `${current.title || current.novelId}（${current.novelId}）` : '未选择，可照常维护全局配置'
})

function createEmptyForm() {
  return {
    scope: 'novel',
    configId: '',
    agentType: '',
    configKey: '',
    configValue: '',
  }
}

function resetForm() {
  form.value = createEmptyForm()
}

function fillForm(item, scope) {
  form.value = {
    scope,
    configId: item.configId || '',
    agentType: item.agentType || '',
    configKey: item.configKey || '',
    configValue: item.configValue || '',
  }
}

async function reload() {
  try {
    const res = await listNovelConfigs(currentNovelId.value || '', true)
    globalConfigs.value = res.globalConfigs
    novelConfigs.value = res.novelConfigs
  } catch (error) {
    alert(error.message || '加载配置失败')
    globalConfigs.value = []
    novelConfigs.value = []
  }
}

async function saveConfig() {
  if (!form.value.agentType.trim() || !form.value.configKey.trim()) {
    alert('请填写 agentType 和 configKey')
    return
  }
  if (form.value.scope === 'novel' && !currentNovelId.value) {
    alert('当前没有选中的小说，无法保存小说级配置')
    return
  }
  saving.value = true
  try {
    await saveNovelConfig({
      configId: form.value.configId.trim() || undefined,
      novelId: form.value.scope === 'global' ? null : currentNovelId.value,
      agentType: form.value.agentType.trim(),
      configKey: form.value.configKey.trim(),
      configValue: form.value.configValue,
      status: 1,
    })
    resetForm()
    await reload()
  } catch (error) {
    alert(error.message || '保存配置失败')
  } finally {
    saving.value = false
  }
}

async function removeConfig(configId) {
  if (!confirm('确认删除该配置吗？')) return
  try {
    await deleteNovelConfig(configId)
    await reload()
  } catch (error) {
    alert(error.message || '删除配置失败')
  }
}

onMounted(async () => {
  await workspaceStore.initWorkspace()
  await reload()
})
</script>

<style scoped>
.config-page {
  min-height: 100vh;
  background: #f6f8fb;
  padding: 24px;
}
.layout {
  max-width: 1200px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 420px 1fr;
  gap: 20px;
}
.panel {
  max-width: 1200px;
  margin: 0 auto 20px;
  background: #fff;
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
}
.panel-header,
.actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.full {
  grid-column: 1 / -1;
}
input,
select,
textarea {
  width: 100%;
  border: 1px solid #d1d5db;
  border-radius: 10px;
  padding: 10px 12px;
  font: inherit;
}
button,
.link-btn {
  border: none;
  border-radius: 10px;
  padding: 10px 16px;
  cursor: pointer;
  background: #2563eb;
  color: #fff;
  text-decoration: none;
}
.secondary {
  background: #e5e7eb;
  color: #111827;
}
.danger {
  background: #fee2e2;
  color: #b91c1c;
}
.config-card {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 12px;
  margin-bottom: 12px;
}
.config-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.config-card pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  background: #f8fafc;
  border-radius: 8px;
  padding: 10px;
}
.empty {
  color: #6b7280;
  margin-bottom: 12px;
}
@media (max-width: 960px) {
  .config-page {
    padding: 16px;
  }
  .layout {
    grid-template-columns: 1fr;
  }
}
</style>
