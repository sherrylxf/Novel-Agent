<template>
  <div class="chapter-page">
    <section class="panel">
      <div class="panel-header">
        <div>
          <h1>章节管理</h1>
          <p>当前小说：{{ currentNovelLabel }}</p>
        </div>
        <div class="actions">
          <RouterLink class="link-btn" to="/workspace">切换小说</RouterLink>
          <button class="secondary" @click="reload">刷新章节</button>
        </div>
      </div>
      <p v-if="!currentNovelId" class="empty">请先在小说管理页选择一个当前小说。</p>
      <div v-if="currentNovelId" class="review-banner" :class="{ 'generating-banner': isGenerating }">
        <strong>{{ reviewBannerTitle }}</strong>
        <p>{{ reviewBannerText }}</p>
        <div class="actions">
          <button @click="openContinueModePicker" :disabled="continueDisabled">
            {{ continueButtonText }}
          </button>
        </div>
      </div>
    </section>

    <section v-if="currentNovelId" class="layout">
      <div class="panel">
        <div class="panel-header">
          <h2>章节列表</h2>
          <button class="secondary" @click="resetForm">新建章节</button>
        </div>
        <table class="chapter-table">
          <thead>
            <tr>
              <th>卷</th>
              <th>章</th>
              <th>标题</th>
              <th>字数</th>
              <th>更新时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="6">加载中...</td>
            </tr>
            <tr v-else-if="!chapters.length">
              <td colspan="6">暂无章节，请先创建。</td>
            </tr>
            <tr v-for="item in chapters" :key="item.chapterId" :class="{ active: item.chapterId === form.chapterId }">
              <td>{{ item.volumeNumber }}</td>
              <td>{{ item.chapterNumber }}</td>
              <td>{{ item.title || '-' }}</td>
              <td>{{ item.wordCount || 0 }}</td>
              <td>{{ formatDate(item.updateTime) }}</td>
              <td class="actions">
                <button @click="editChapter(item.chapterId)">编辑</button>
                <button class="danger" @click="removeChapter(item)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="panel">
        <div class="panel-header">
          <h2>{{ form.chapterId ? '编辑章节' : '新建章节' }}</h2>
          <button class="secondary" @click="resetForm">清空</button>
        </div>
        <div class="form-grid">
          <input v-model.number="form.volumeNumber" type="number" min="1" placeholder="卷号" />
          <input v-model.number="form.chapterNumber" type="number" min="1" placeholder="章节号" />
          <input v-model="form.title" type="text" placeholder="章节标题" class="full" />
          <textarea v-model="form.outline" rows="6" placeholder="章节梗概" class="full"></textarea>
          <textarea v-model="form.content" rows="14" placeholder="章节正文" class="full"></textarea>
        </div>
        <div class="actions">
          <button @click="saveChapter" :disabled="saving">{{ saving ? '保存中...' : '保存章节' }}</button>
        </div>
      </div>
    </section>

    <div v-if="showContinueModePicker" class="modal-overlay" @click.self="showContinueModePicker = false">
      <div class="mode-dialog">
        <div class="mode-header">
          <h3>选择继续创作方式</h3>
          <button class="close-btn" @click="showContinueModePicker = false">×</button>
        </div>
        <p class="mode-desc">你可以直接自动完成一章，也可以在 `章节管理` 页里按原来的步骤逐步确认。</p>
        <div class="mode-actions">
          <button class="mode-btn auto-btn" @click="startContinue('auto')">
            <strong>自动完成一章</strong>
            <span>系统自动跑到本章完成，再回到这里等你审阅。</span>
          </button>
          <button class="mode-btn step-btn" @click="startContinue('step')">
            <strong>逐步弹窗确认</strong>
            <span>在当前页面复用原来的逐步弹窗，一步一步确认继续。</span>
          </button>
        </div>
      </div>
    </div>

    <div v-if="waitingApproval" class="approval-modal" @click.self="handleApprovalCancel">
      <div class="approval-dialog">
        <div class="approval-header">
          <div class="header-content">
            <div class="header-icon">{{ getStageIcon(approvalStage) }}</div>
            <div>
              <h3>{{ getFriendlyTitle(approvalStage) }}</h3>
              <p class="header-subtitle">{{ getFriendlySubtitle(approvalStage) }}</p>
            </div>
          </div>
          <button @click="handleApprovalCancel" class="close-btn">×</button>
        </div>
        <div class="approval-content">
          <div class="progress-hint" v-if="approvalNextStage">
            <span class="hint-text">✨ 下一步将：{{ getStageLabel(approvalNextStage) }}</span>
          </div>
          <div class="node-name" v-if="approvalNodeName">当前节点：{{ approvalNodeName }}</div>

          <div class="content-preview" v-if="approvalData">
            <div v-if="getContentComponent(approvalData) === 'SeedContent'" class="seed-content">
              <div class="content-card">
                <div class="card-header"><h4>📖 小说标题</h4></div>
                <div class="card-body"><p class="novel-title">{{ approvalData.title || '未设置' }}</p></div>
              </div>
              <div class="content-card">
                <div class="card-header"><h4>🎭 题材类型</h4></div>
                <div class="card-body"><span class="genre-badge">{{ getGenreDisplay(approvalData.genre) || '未设置' }}</span></div>
              </div>
              <div class="content-card">
                <div class="card-header"><h4>⚡ 核心冲突</h4></div>
                <div class="card-body"><p class="text-content">{{ approvalData.coreConflict || '未设置' }}</p></div>
              </div>
              <div class="content-card" v-if="approvalData.worldSetting">
                <div class="card-header"><h4>🌍 世界观设定</h4></div>
                <div class="card-body"><p class="text-content">{{ approvalData.worldSetting }}</p></div>
              </div>
              <div class="content-card" v-if="approvalData.targetWordCount">
                <div class="card-header"><h4>📊 目标字数</h4></div>
                <div class="card-body"><span class="word-count">{{ formatWordCount(approvalData.targetWordCount) }}</span></div>
              </div>
            </div>

            <div v-else-if="getContentComponent(approvalData) === 'PlanContent'" class="plan-content">
              <div class="content-card" v-if="approvalData.overallOutline">
                <div class="card-header"><h4>📖 整体大纲</h4></div>
                <div class="card-body"><div class="outline-content">{{ approvalData.overallOutline }}</div></div>
              </div>
              <div class="content-card">
                <div class="card-header"><h4>📊 规划统计</h4></div>
                <div class="card-body">
                  <div class="plan-stats">
                    <div class="stat-item"><span class="stat-label">总卷数：</span><span class="stat-value">{{ approvalData.totalVolumes || 0 }} 卷</span></div>
                    <div class="stat-item"><span class="stat-label">每卷章节数：</span><span class="stat-value">{{ approvalData.chaptersPerVolume || 0 }} 章</span></div>
                  </div>
                </div>
              </div>
              <div class="content-card" v-if="approvalData.volumePlans && approvalData.volumePlans.length > 0">
                <div class="card-header"><h4>📚 分卷计划</h4></div>
                <div class="card-body">
                  <div class="volume-list">
                    <div v-for="(volume, index) in approvalData.volumePlans" :key="index" class="volume-item">
                      <div class="volume-header">
                        <span class="volume-number">第 {{ volume.volumeNumber || (index + 1) }} 卷</span>
                        <span class="volume-title" v-if="volume.volumeTitle">{{ volume.volumeTitle }}</span>
                      </div>
                      <div class="volume-info">
                        <span class="volume-theme" v-if="volume.volumeTheme">主题：{{ volume.volumeTheme }}</span>
                        <span class="chapter-count" v-if="volume.chapterCount">{{ volume.chapterCount }} 章</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div v-else-if="getContentComponent(approvalData) === 'SceneContent'" class="scene-content">
              <div class="content-card" v-if="approvalData.sceneTitle || approvalData.title">
                <div class="card-header"><h4>🎬 标题</h4></div>
                <div class="card-body"><p class="scene-title">{{ approvalData.sceneTitle || approvalData.title }}</p></div>
              </div>
              <div class="content-card" v-if="approvalData.outline">
                <div class="card-header"><h4>📝 梗概</h4></div>
                <div class="card-body"><div class="scene-text">{{ approvalData.outline }}</div></div>
              </div>
              <div class="content-card" v-if="approvalData.content">
                <div class="card-header"><h4>📄 内容</h4></div>
                <div class="card-body"><div class="scene-text">{{ approvalData.content }}</div></div>
              </div>
            </div>

            <div v-else class="generic-content">
              <div class="content-card">
                <div class="card-header"><h4>📋 生成内容</h4></div>
                <div class="card-body"><pre class="formatted-data">{{ formatApprovalData(approvalData) }}</pre></div>
              </div>
            </div>
          </div>
        </div>
        <div class="approval-actions">
          <button @click="handleApprove(true)" class="approve-btn approve-yes">
            <span>继续创作</span>
          </button>
          <button @click="handleApprove(false)" class="approve-btn approve-no">
            <span>暂停生成</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { deleteNovelChapter, getNovelChapter, listNovelChapters, saveNovelChapter, updateNovelChapter } from '@/services/novelApi'
import { useNovelWorkspaceStore } from '@/stores/novelWorkspace'
import { useNovelGenerationStore } from '@/stores/novelGeneration'

const workspaceStore = useNovelWorkspaceStore()
const generationStore = useNovelGenerationStore()
const chapters = ref([])
const loading = ref(false)
const saving = ref(false)
const continueLoading = ref(false)
const showContinueModePicker = ref(false)
const form = ref(createEmptyForm())

const currentNovelId = computed(() => workspaceStore.currentNovelId.value)
const isGenerating = computed(() => generationStore.isGenerating.value)
const waitingApproval = computed(() => generationStore.waitingApproval.value)
const approvalData = computed(() => generationStore.approvalData.value)
const approvalStage = computed(() => generationStore.approvalStage.value)
const approvalNodeName = computed(() => generationStore.approvalNodeName.value)
const approvalNextStage = computed(() => generationStore.approvalNextStage.value)
const awaitingChapterReview = computed(() => generationStore.awaitingChapterReview.value)
const canContinueAfterReview = computed(() => generationStore.canContinueAfterReview.value)
const continueDisabled = computed(() => continueLoading.value || isGenerating.value || (awaitingChapterReview.value && !canContinueAfterReview.value))
const continueButtonText = computed(() => {
  if (continueLoading.value) return '继续创作中...'
  if (isGenerating.value) return '正在生成中...'
  if (awaitingChapterReview.value) {
    return canContinueAfterReview.value ? '继续创作下一章' : '本轮创作已结束'
  }
  return '继续创作新章节'
})
const reviewBannerTitle = computed(() => {
  if (isGenerating.value) return '正在创作中。'
  if (waitingApproval.value) return '当前处于逐步确认模式。'
  if (awaitingChapterReview.value) return '本章已生成完成，请先检查并修改章节内容。'
  return '可以从这本已保存的小说继续创作。'
})
const reviewBannerText = computed(() => {
  if (isGenerating.value) return '你可以稍等本轮生成完成，或在出现逐步弹窗后继续按步骤确认。'
  if (waitingApproval.value) return '逐步弹窗已经在本页打开，确认后会继续执行下一步。'
  if (awaitingChapterReview.value) return '确认无误后，可以选择自动完成下一章，或在本页逐步确认生成过程。'
  return '如果后端昨晚关闭了，今天重新打开后也能从数据库恢复到下一章继续创作。'
})
const currentNovelLabel = computed(() => {
  const current = workspaceStore.currentNovel.value
  return current ? `${current.title || current.novelId}（${current.novelId}）` : '未选择'
})

const stageMap = {
  ROOT: '数据加载',
  SEED: '生成Seed',
  NOVEL_PLAN: '规划小说',
  VOLUME_PLAN: '规划卷/册',
  CHAPTER_OUTLINE: '生成章节梗概',
  SCENE_GENERATION: '生成场景',
  SCENE: '生成场景',
  VALIDATION: '校验',
  COMPLETE: '完成',
}

function createEmptyForm() {
  return {
    chapterId: '',
    volumeNumber: 1,
    chapterNumber: 1,
    title: '',
    outline: '',
    content: '',
  }
}

function formatDate(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN')
}

function getStageLabel(stage) {
  return stageMap[stage] || stage || '未知'
}

function getFriendlyTitle(stage) {
  const titleMap = {
    SEED: '✨ 小说创意已生成',
    NOVEL_PLAN: '📖 小说大纲已规划',
    VOLUME_PLAN: '📚 分卷计划已制定',
    CHAPTER_OUTLINE: '📝 章节梗概已创建',
    SCENE_GENERATION: '🎬 场景内容已生成',
    SCENE: '🎬 场景内容已生成',
    VALIDATION: '✅ 内容校验已完成',
  }
  return titleMap[stage] || '📋 内容已生成'
}

function getFriendlySubtitle(stage) {
  const subtitleMap = {
    SEED: '请查看 AI 为你创作的小说核心设定',
    NOVEL_PLAN: '请查看 AI 为你规划的小说结构',
    VOLUME_PLAN: '请查看 AI 为你制定的分卷安排',
    CHAPTER_OUTLINE: '请查看 AI 为你创作的章节概要',
    SCENE_GENERATION: '请查看 AI 为你生成的场景内容',
    SCENE: '请查看 AI 为你生成的场景内容',
    VALIDATION: '请查看 AI 为你校验的内容结果',
  }
  return subtitleMap[stage] || '请查看生成的内容'
}

function getStageIcon(stage) {
  const iconMap = {
    SEED: '🌱',
    NOVEL_PLAN: '📖',
    VOLUME_PLAN: '📚',
    CHAPTER_OUTLINE: '📝',
    SCENE_GENERATION: '🎬',
    SCENE: '🎬',
    VALIDATION: '✅',
  }
  return iconMap[stage] || '📋'
}

function getContentComponent(data) {
  if (!data) return 'EmptyContent'
  if (data.title && data.genre && data.coreConflict) return 'SeedContent'
  if ((data.volumePlans && Array.isArray(data.volumePlans)) || (data.totalVolumes && data.chaptersPerVolume)) return 'PlanContent'
  if (data.sceneTitle || data.content || data.outline) return 'SceneContent'
  return 'GenericContent'
}

function formatApprovalData(data) {
  if (!data) return '无数据'
  try {
    return JSON.stringify(data, null, 2)
  } catch {
    return String(data)
  }
}

function formatWordCount(count) {
  if (!count) return '未设置'
  if (count >= 10000) return `${(count / 10000).toFixed(1)} 万字`
  return `${count} 字`
}

function getGenreDisplay(genre) {
  if (!genre) return ''
  if (typeof genre === 'string') return genre
  if (typeof genre === 'object') return genre.name || genre.value || genre.toString()
  return String(genre)
}

async function reload() {
  if (!currentNovelId.value) return
  loading.value = true
  try {
    chapters.value = await listNovelChapters(currentNovelId.value)
    await workspaceStore.refreshCurrentNovel()
  } catch (error) {
    alert(error.message || '加载章节失败')
    chapters.value = []
  } finally {
    loading.value = false
  }
}

async function focusLatestChapterForReview() {
  if (!chapters.value.length) return
  const latestChapter = chapters.value[chapters.value.length - 1]
  if (latestChapter?.chapterId) {
    await editChapter(latestChapter.chapterId)
  }
}

function resetForm() {
  form.value = createEmptyForm()
}

async function editChapter(chapterId) {
  try {
    const detail = await getNovelChapter(chapterId)
    form.value = {
      chapterId: detail.chapterId,
      volumeNumber: detail.volumeNumber,
      chapterNumber: detail.chapterNumber,
      title: detail.title || '',
      outline: detail.outline || '',
      content: detail.content || '',
    }
  } catch (error) {
    alert(error.message || '加载章节详情失败')
  }
}

async function saveChapter() {
  if (!currentNovelId.value) {
    alert('请先选择小说')
    return
  }
  if (!form.value.title.trim()) {
    alert('请输入章节标题')
    return
  }
  saving.value = true
  try {
    const payload = {
      ...form.value,
      novelId: currentNovelId.value,
      status: 1,
    }
    if (form.value.chapterId) {
      await updateNovelChapter(payload)
    } else {
      await saveNovelChapter(payload)
    }
    resetForm()
    await reload()
  } catch (error) {
    alert(error.message || '保存章节失败')
  } finally {
    saving.value = false
  }
}

async function persistCurrentFormIfNeeded() {
  if (!currentNovelId.value || !form.value.title.trim()) return
  const payload = {
    ...form.value,
    novelId: currentNovelId.value,
    status: 1,
  }
  if (form.value.chapterId) {
    await updateNovelChapter(payload)
  } else {
    const saved = await saveNovelChapter(payload)
    if (saved?.chapterId) {
      form.value.chapterId = saved.chapterId
    }
  }
}

function openContinueModePicker() {
  if (continueDisabled.value) return
  showContinueModePicker.value = true
}

async function startContinue(continueMode) {
  if (awaitingChapterReview.value && !canContinueAfterReview.value) return
  continueLoading.value = true
  showContinueModePicker.value = false
  try {
    await persistCurrentFormIfNeeded()
    await reload()
    generationStore.continueNextChapter(currentNovelId.value, {
      continueMode,
      maxStep: continueMode === 'step' ? 999 : undefined,
    }).catch((error) => {
      alert(error.message || '继续创作失败')
    }).finally(() => {
      continueLoading.value = false
    })
  } catch (error) {
    continueLoading.value = false
    alert(error.message || '继续创作失败')
  }
}

async function handleApprove(approved) {
  try {
    await generationStore.respondToApproval(approved)
  } catch (error) {
    alert(error.message || '确认操作失败')
  }
}

function handleApprovalCancel() {
  handleApprove(false)
}

async function removeChapter(item) {
  if (!confirm(`确认删除章节「${item.title || item.chapterId}」吗？`)) return
  try {
    await deleteNovelChapter(item.chapterId)
    if (form.value.chapterId === item.chapterId) {
      resetForm()
    }
    await reload()
  } catch (error) {
    alert(error.message || '删除章节失败')
  }
}

onMounted(async () => {
  await workspaceStore.initWorkspace()
  await reload()
  if (awaitingChapterReview.value) {
    await focusLatestChapterForReview()
  }
})

watch(awaitingChapterReview, async (waiting) => {
  if (waiting) {
    continueLoading.value = false
  }
  if (!waiting) return
  await reload()
  await focusLatestChapterForReview()
})

watch(waitingApproval, (waiting) => {
  if (waiting) {
    continueLoading.value = false
  }
})

watch(currentNovelId, async (novelId, oldNovelId) => {
  if (novelId === oldNovelId) return
  resetForm()
  showContinueModePicker.value = false
  await reload()
})
</script>

<style scoped>
.chapter-page {
  min-height: 100vh;
  background: #f6f8fb;
  padding: 24px;
}

.layout {
  max-width: 1200px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 1fr 1fr;
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

.chapter-table {
  width: 100%;
  border-collapse: collapse;
}

.chapter-table th,
.chapter-table td {
  padding: 10px;
  border-bottom: 1px solid #e5e7eb;
  text-align: left;
}

.chapter-table tr.active {
  background: #eff6ff;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.form-grid .full {
  grid-column: 1 / -1;
}

input,
textarea,
button {
  font: inherit;
}

input,
textarea {
  width: 100%;
  border: 1px solid #d1d5db;
  border-radius: 10px;
  padding: 10px 12px;
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

button:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.secondary {
  background: #e5e7eb;
  color: #111827;
}

.danger,
.approve-no {
  background: #fee2e2;
  color: #b91c1c;
}

.empty {
  color: #6b7280;
}

.review-banner {
  margin-top: 16px;
  padding: 16px;
  border-radius: 12px;
  background: #eff6ff;
  color: #1e3a8a;
}

.generating-banner {
  background: #fef3c7;
  color: #92400e;
}

.review-banner p {
  margin: 8px 0 0;
}

.modal-overlay,
.approval-modal {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  z-index: 1000;
}

.mode-dialog,
.approval-dialog {
  width: min(960px, 100%);
  max-height: 90vh;
  overflow: auto;
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.2);
}

.mode-dialog {
  width: min(720px, 100%);
  padding: 24px;
}

.mode-header,
.approval-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.mode-desc {
  margin: 16px 0 20px;
  color: #4b5563;
}

.mode-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.mode-btn {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  min-height: 140px;
}

.mode-btn span {
  color: rgba(255, 255, 255, 0.9);
  text-align: left;
}

.auto-btn {
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
}

.step-btn {
  background: linear-gradient(135deg, #7c3aed, #5b21b6);
}

.approval-header {
  padding: 24px 24px 0;
}

.header-content {
  display: flex;
  gap: 16px;
  align-items: center;
}

.header-icon {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: #eff6ff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.header-subtitle {
  margin: 6px 0 0;
  color: #6b7280;
}

.close-btn {
  padding: 6px 12px;
  background: transparent;
  color: #6b7280;
  font-size: 24px;
}

.approval-content {
  padding: 20px 24px;
}

.progress-hint,
.node-name {
  margin-bottom: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #f8fafc;
  color: #334155;
}

.content-card {
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  overflow: hidden;
  margin-bottom: 16px;
}

.card-header {
  background: #f8fafc;
  padding: 14px 16px;
  border-bottom: 1px solid #e5e7eb;
}

.card-header h4 {
  margin: 0;
}

.card-body {
  padding: 16px;
}

.novel-title,
.scene-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #111827;
}

.text-content,
.outline-content,
.scene-text {
  white-space: pre-wrap;
  line-height: 1.75;
  color: #374151;
}

.genre-badge,
.word-count,
.volume-number,
.volume-title,
.volume-theme,
.chapter-count,
.stat-value {
  color: #1d4ed8;
}

.plan-stats,
.volume-list {
  display: grid;
  gap: 12px;
}

.volume-item {
  padding: 12px;
  border-radius: 12px;
  background: #f8fafc;
}

.volume-header,
.volume-info {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.formatted-data {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.approval-actions {
  padding: 0 24px 24px;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.approve-btn {
  min-width: 120px;
}

.approve-yes {
  background: #2563eb;
}

@media (max-width: 960px) {
  .chapter-page {
    padding: 16px;
  }

  .layout,
  .mode-actions {
    grid-template-columns: 1fr;
  }
}
</style>
