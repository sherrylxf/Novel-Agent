<template>
  <div class="novel-generator">
    <div class="container">
      <header class="header">
        <h1>📚 Novel Agent - AI小说生成器</h1>
        <p class="subtitle">基于AI智能体的小说自动生成系统</p>
      </header>

      <div class="workspace-banner">
        <div>
          <strong>{{ currentNovelSummary }}</strong>
          <p>刷新后会恢复到当前小说，并从后端重新加载已保存的大纲、章节、RAG 和知识图谱。</p>
        </div>
      </div>

      <div class="content">
        <!-- 左侧：表单区域 -->
        <div class="form-section">
          <div class="card">
            <h2>小说设定</h2>
            <form @submit.prevent="handleSubmit">
              <div class="form-group">
                <label for="genre">题材类型 *</label>
                <select id="genre" v-model="form.genre" required>
                  <option value="">请选择题材</option>
                  <option value="修仙">修仙</option>
                  <option value="历史穿越">历史穿越</option>
                  <option value="都市重生">都市重生</option>
                  <option value="女频言情">女频言情</option>
                  <option value="科幻">科幻</option>
                  <option value="悬疑">悬疑</option>
                </select>
              </div>

              <div class="form-group">
                <label for="coreConflict">核心冲突/主题 *</label>
                <textarea
                  id="coreConflict"
                  v-model="form.coreConflict"
                  rows="3"
                  placeholder="例如：一个废材少年的逆袭之路"
                  required
                ></textarea>
              </div>

              <div class="form-group">
                <label for="worldSetting">世界观设定 *</label>
                <textarea
                  id="worldSetting"
                  v-model="form.worldSetting"
                  rows="4"
                  placeholder="例如：修真世界，分为炼气、筑基、金丹、元婴等境界"
                  required
                ></textarea>
              </div>

              <div class="form-group">
                <label for="targetWordCount">目标总字数</label>
                <div class="word-count-input">
                  <select id="targetWordCount" v-model="form.targetWordCountPreset" @change="onTargetWordCountPresetChange">
                    <option value="">默认 100 万字</option>
                    <option value="50000">5 万字（短篇）</option>
                    <option value="100000">10 万字</option>
                    <option value="300000">30 万字</option>
                    <option value="500000">50 万字</option>
                    <option value="1000000">100 万字（长篇）</option>
                    <option value="2000000">200 万字</option>
                    <option value="5000000">500 万字</option>
                    <option value="custom">自定义</option>
                  </select>
                  <input
                    v-if="form.targetWordCountPreset === 'custom'"
                    type="number"
                    v-model.number="form.targetWordCount"
                    placeholder="输入字数"
                    min="10000"
                    step="10000"
                    class="custom-word-count"
                  />
                </div>
                <p class="form-hint">留空则使用默认 100 万字，用于规划章节结构</p>
              </div>

              <div class="form-group">
                <label for="maxStep">最大执行步数</label>
                <select id="maxStep" v-model="form.maxStep">
                  <option :value="5">5步（快速）</option>
                  <option :value="10">10步（标准）</option>
                  <option :value="20">20步（完整）</option>
                </select>
              </div>

              <button
                type="submit"
                :disabled="isGenerating"
                class="submit-btn"
                :class="{ loading: isGenerating }"
              >
                <span v-if="!isGenerating">🚀 开始生成</span>
                <span v-else>生成中...</span>
              </button>
            </form>
            
            <!-- 查看大纲按钮 -->
            <div class="action-buttons" v-if="novelId">
              <button @click="showPlanModal = true" class="view-plan-btn">
                <span>📖 查看大纲</span>
              </button>
            </div>
          </div>
        </div>

        <!-- 右侧：进度和结果区域 -->
        <div class="result-section">
          <div class="card">
            <h2>生成进度</h2>

            <!-- 进度条 -->
            <div class="progress-bar-container" v-if="isGenerating || progress.length > 0">
              <div class="progress-bar">
                <div
                  class="progress-fill"
                  :style="{ width: `${progressPercentage}%` }"
                ></div>
              </div>
              <div class="progress-text">{{ currentStageText }}</div>
            </div>

            <!-- 进度日志 -->
            <div class="progress-log" v-if="progress.length > 0">
              <div
                v-for="(item, index) in progress"
                :key="index"
                class="log-item"
                :class="item.type"
              >
                <span class="log-time">{{ formatTime(item.timestamp) }}</span>
                <span class="log-stage">{{ getStageLabel(item.stage) }}</span>
                <span class="log-content">{{ item.content }}</span>
              </div>
            </div>

            <!-- 空状态 -->
            <div class="empty-state" v-if="!isGenerating && progress.length === 0">
              <div class="empty-icon">📝</div>
              <p>填写左侧表单，开始生成你的小说</p>
            </div>

            <!-- 完成状态 -->
            <div class="complete-state" v-if="completed && !isGenerating">
              <div class="complete-icon">✅</div>
              <h3>生成完成！</h3>
              <p v-if="novelId">小说ID: <code>{{ novelId }}</code></p>
              <button @click="reset" class="reset-btn">重新生成</button>
            </div>

            <!-- 错误状态 -->
            <div class="error-state" v-if="error">
              <div class="error-icon">❌</div>
              <h3>生成失败</h3>
              <p>{{ error }}</p>
              <button @click="reset" class="reset-btn">重试</button>
            </div>
          </div>
        </div>
      </div>

      <!-- 用户确认对话框 -->
      <div class="approval-modal" v-if="waitingApproval" @click.self="handleApprovalCancel">
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
            <!-- 进度提示 -->
            <div class="progress-hint" v-if="approvalNextStage">
              <span class="hint-text">✨ 下一步将：{{ getStageLabel(approvalNextStage) }}</span>
            </div>
            
            <!-- 美化后的内容展示 -->
            <div class="content-preview" v-if="approvalData">
              <!-- Seed内容展示 -->
              <div v-if="getContentComponent(approvalData) === 'SeedContent'" class="seed-content">
                <div class="content-card">
                  <div class="card-header">
                    <h4>📖 小说标题</h4>
                  </div>
                  <div class="card-body">
                    <p class="novel-title">{{ approvalData.title || '未设置' }}</p>
                  </div>
                </div>
                
                <div class="content-card">
                  <div class="card-header">
                    <h4>🎭 题材类型</h4>
                  </div>
                  <div class="card-body">
                    <span class="genre-badge">{{ getGenreDisplay(approvalData.genre) || '未设置' }}</span>
                  </div>
                </div>
                
                <div class="content-card">
                  <div class="card-header">
                    <h4>⚡ 核心冲突</h4>
                  </div>
                  <div class="card-body">
                    <p class="text-content">{{ approvalData.coreConflict || '未设置' }}</p>
                  </div>
                </div>
                
                <div class="content-card" v-if="approvalData.protagonistSetting">
                  <div class="card-header">
                    <h4>👤 主角设定</h4>
                  </div>
                  <div class="card-body">
                    <p class="text-content">{{ approvalData.protagonistSetting }}</p>
                  </div>
                </div>
                
                <div class="content-card" v-if="approvalData.worldSetting">
                  <div class="card-header">
                    <h4>🌍 世界观设定</h4>
                  </div>
                  <div class="card-body">
                    <p class="text-content">{{ approvalData.worldSetting }}</p>
                  </div>
                </div>
                
                <div class="content-card" v-if="approvalData.targetWordCount">
                  <div class="card-header">
                    <h4>📊 目标字数</h4>
                  </div>
                  <div class="card-body">
                    <span class="word-count">{{ formatWordCount(approvalData.targetWordCount) }}</span>
                  </div>
                </div>
              </div>
              
              <!-- Plan内容展示 -->
              <div v-else-if="getContentComponent(approvalData) === 'PlanContent'" class="plan-content">
                <!-- 整体大纲 -->
                <div class="content-card" v-if="approvalData.overallOutline">
                  <div class="card-header">
                    <h4>📖 整体大纲</h4>
                  </div>
                  <div class="card-body">
                    <div class="outline-content">{{ approvalData.overallOutline }}</div>
                  </div>
                </div>
                
                <!-- 规划统计 -->
                <div class="content-card">
                  <div class="card-header">
                    <h4>📊 规划统计</h4>
                  </div>
                  <div class="card-body">
                    <div class="plan-stats">
                      <div class="stat-item">
                        <span class="stat-label">总卷数：</span>
                        <span class="stat-value">{{ approvalData.totalVolumes || 0 }} 卷</span>
                      </div>
                      <div class="stat-item">
                        <span class="stat-label">每卷章节数：</span>
                        <span class="stat-value">{{ approvalData.chaptersPerVolume || 0 }} 章</span>
                      </div>
                      <div class="stat-item" v-if="approvalData.totalVolumes && approvalData.chaptersPerVolume">
                        <span class="stat-label">预计总章节：</span>
                        <span class="stat-value">{{ approvalData.totalVolumes * approvalData.chaptersPerVolume }} 章</span>
                      </div>
                    </div>
                  </div>
                </div>
                
                <!-- 分卷计划 -->
                <div class="content-card" v-if="approvalData.volumePlans && approvalData.volumePlans.length > 0">
                  <div class="card-header">
                    <h4>📚 分卷计划</h4>
                    <span class="badge">{{ approvalData.volumePlans.length }} 卷</span>
                  </div>
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
                
                <!-- 保存提示 -->
                <div class="save-hint" v-if="approvalStage === 'NOVEL_PLAN'">
                  <span class="hint-icon">💾</span>
                  <span class="hint-text">生成的大纲可以保存到数据库，方便后续查看和编辑</span>
                </div>
              </div>
              
              <!-- Scene内容展示 -->
              <div v-else-if="getContentComponent(approvalData) === 'SceneContent'" class="scene-content">
                <div class="content-card" v-if="approvalData.sceneTitle">
                  <div class="card-header">
                    <h4>🎬 场景标题</h4>
                  </div>
                  <div class="card-body">
                    <p class="scene-title">{{ approvalData.sceneTitle }}</p>
                  </div>
                </div>
                
                <div class="content-card" v-if="approvalData.content">
                  <div class="card-header">
                    <h4>📝 场景内容</h4>
                  </div>
                  <div class="card-body">
                    <div class="scene-text">{{ approvalData.content }}</div>
                  </div>
                </div>
              </div>
              
              <!-- 通用内容展示 -->
              <div v-else class="generic-content">
                <div class="content-card">
                  <div class="card-header">
                    <h4>📋 生成内容</h4>
                  </div>
                  <div class="card-body">
                    <pre class="formatted-data">{{ formatApprovalData(approvalData) }}</pre>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div class="approval-actions">
            <button 
              v-if="approvalStage === 'NOVEL_PLAN' && approvalData" 
              @click="handleSavePlan" 
              class="approve-btn approve-save"
              :disabled="savingPlan"
            >
              <span class="btn-icon">💾</span>
              <span>{{ savingPlan ? '保存中...' : '保存大纲' }}</span>
            </button>
            <button @click="handleApprove(true)" class="approve-btn approve-yes">
              <span class="btn-icon">✨</span>
              <span>继续创作</span>
            </button>
            <button @click="handleApprove(false)" class="approve-btn approve-no">
              <span class="btn-icon">⏸️</span>
              <span>暂停生成</span>
            </button>
          </div>
        </div>
      </div>
      
      <!-- 大纲查看/编辑模态框 -->
      <div class="plan-modal" v-if="showPlanModal" @click.self="showPlanModal = false">
        <div class="plan-modal-dialog">
          <div class="plan-modal-header">
            <h3>📖 小说大纲</h3>
            <button @click="showPlanModal = false" class="close-btn">×</button>
          </div>
          <div class="plan-modal-content">
            <div v-if="loadingPlan" class="loading-state">
              <div class="loading-spinner"></div>
              <p>加载中...</p>
            </div>
            <div v-else-if="planError" class="error-state">
              <p>{{ planError }}</p>
              <button @click="loadPlan" class="retry-btn">重试</button>
            </div>
            <div v-else-if="currentPlan" class="plan-view">
              <!-- 编辑模式 -->
              <div v-if="editingPlan" class="plan-edit">
                <div class="form-group">
                  <label>规划ID</label>
                  <input type="text" v-model="editPlanData.planId" disabled />
                </div>
                <div class="form-group">
                  <label>小说ID</label>
                  <input type="text" v-model="editPlanData.novelId" disabled />
                </div>
                <div class="form-row">
                  <div class="form-group">
                    <label>总卷数</label>
                    <input type="number" v-model.number="editPlanData.totalVolumes" />
                  </div>
                  <div class="form-group">
                    <label>每卷章节数</label>
                    <input type="number" v-model.number="editPlanData.chaptersPerVolume" />
                  </div>
                </div>
                <div class="form-group">
                  <label>整体大纲</label>
                  <textarea v-model="editPlanData.overallOutline" rows="10"></textarea>
                </div>
                <div class="form-actions">
                  <button @click="savePlanChanges" class="save-btn" :disabled="savingPlan">
                    {{ savingPlan ? '保存中...' : '保存修改' }}
                  </button>
                  <button @click="cancelEdit" class="cancel-btn">取消</button>
                </div>
              </div>
              
              <!-- 查看模式 -->
              <div v-else class="plan-display">
                <div class="plan-info">
                  <div class="info-item">
                    <span class="info-label">规划ID：</span>
                    <span class="info-value">{{ currentPlan.planId }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">小说ID：</span>
                    <span class="info-value">{{ currentPlan.novelId }}</span>
                  </div>
                  <div class="info-row">
                    <div class="info-item">
                      <span class="info-label">总卷数：</span>
                      <span class="info-value">{{ currentPlan.totalVolumes }} 卷</span>
                    </div>
                    <div class="info-item">
                      <span class="info-label">每卷章节数：</span>
                      <span class="info-value">{{ currentPlan.chaptersPerVolume }} 章</span>
                    </div>
                  </div>
                </div>
                
                <div class="outline-section">
                  <h4>整体大纲</h4>
                  <div class="outline-display">{{ currentPlan.overallOutline }}</div>
                </div>
                
                <div class="volumes-section" v-if="currentPlan.volumePlans && currentPlan.volumePlans.length > 0">
                  <h4>分卷计划（{{ currentPlan.volumePlans.length }} 卷）</h4>
                  <div class="volumes-list">
                    <div v-for="(volume, index) in currentPlan.volumePlans" :key="index" class="volume-card">
                      <div class="volume-card-header">
                        <span class="volume-num">第 {{ volume.volumeNumber }} 卷</span>
                        <span class="volume-title">{{ volume.volumeTitle }}</span>
                      </div>
                      <div class="volume-card-body">
                        <p class="volume-theme-text" v-if="volume.volumeTheme">主题：{{ volume.volumeTheme }}</p>
                        <p class="volume-chapters">章节数：{{ volume.chapterCount }} 章</p>
                      </div>
                    </div>
                  </div>
                </div>
                
                <div class="plan-actions">
                  <button @click="startEdit" class="edit-btn">✏️ 编辑大纲</button>
                </div>
              </div>
            </div>
            <div v-else class="empty-plan">
              <p>暂无大纲数据</p>
              <p class="hint">请先生成小说大纲</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue';
import { saveNovelPlan, getNovelPlanByNovelId, updateNovelPlan } from '../services/novelApi.js';
import { useNovelWorkspaceStore } from '@/stores/novelWorkspace'
import { useNovelGenerationStore } from '@/stores/novelGeneration'

const form = ref({
  genre: '',
  coreConflict: '',
  worldSetting: '',
  targetWordCountPreset: '', // 预设：'' | '50000' | '100000' | ... | 'custom'
  targetWordCount: null,     // 自定义时的具体字数
  maxStep: 5,
});

const savingPlan = ref(false);
const savedPlanId = ref('');
const showPlanModal = ref(false);
const currentPlan = ref(null);
const loadingPlan = ref(false);
const planError = ref(null);
const editingPlan = ref(false);
const editPlanData = ref(null);
const workspaceStore = useNovelWorkspaceStore()
const generationStore = useNovelGenerationStore()
const isGenerating = computed(() => generationStore.isGenerating.value)
const progress = computed(() => generationStore.progress.value)
const completed = computed(() => generationStore.completed.value)
const novelId = computed(() => generationStore.novelId.value)
const error = computed(() => generationStore.error.value)
const waitingApproval = computed(() => generationStore.waitingApproval.value)
const approvalData = computed(() => generationStore.approvalData.value)
const approvalStage = computed(() => generationStore.approvalStage.value)
const approvalNodeName = computed(() => generationStore.approvalNodeName.value)
const approvalNextStage = computed(() => generationStore.approvalNextStage.value)
const currentNovelSummary = computed(() => {
  const current = workspaceStore.currentNovel.value
  if (!current) return '当前未选择小说，新生成内容会自动创建小说项目'
  return `当前小说：${current.title || current.novelId}（${current.novelId}）`
})

// 阶段映射
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
  // 兼容旧格式
  seed: '生成Seed',
  plan: '规划小说',
  volume: '规划卷/册',
  chapter: '生成章节',
  scene: '生成场景',
  validation: '校验',
};

// 计算进度百分比
const progressPercentage = computed(() => {
  if (!isGenerating.value && completed.value) return 100;
  const stageOrder = ['ROOT', 'SEED', 'NOVEL_PLAN', 'VOLUME_PLAN', 'CHAPTER_OUTLINE', 'SCENE_GENERATION', 'VALIDATION'];
  const lastProgress = progress.value[progress.value.length - 1];
  if (!lastProgress) return 0;
  const currentIndex = stageOrder.findIndex(
    (stage) => stage === lastProgress.stage
  );
  if (currentIndex === -1) {
    // 兼容旧格式
    const oldStageOrder = ['seed', 'plan', 'volume', 'chapter', 'scene', 'validation'];
    const oldIndex = oldStageOrder.findIndex((stage) => stage === lastProgress.stage);
    return oldIndex >= 0 ? ((oldIndex + 1) / oldStageOrder.length) * 100 : 0;
  }
  return ((currentIndex + 1) / stageOrder.length) * 100;
});

// 当前阶段文本
const currentStageText = computed(() => {
  if (completed.value) return '已完成';
  if (error.value) return '生成失败';
  if (progress.value.length === 0) return '等待开始';
  const lastProgress = progress.value[progress.value.length - 1];
  return stageMap[lastProgress.stage] || '处理中...';
});

// 获取阶段标签
const getStageLabel = (stage) => {
  return stageMap[stage] || stage || '未知';
};

// 格式化时间
const formatTime = (timestamp) => {
  if (!timestamp) return '';
  const date = new Date(timestamp);
  return date.toLocaleTimeString('zh-CN');
};

// 提交表单
const handleSubmit = async () => {
  if (isGenerating.value) return;
  const targetNovelId = novelId.value || workspaceStore.currentNovelId.value || '';

  try {
    await generationStore.startGeneration({
      genre: form.value.genre,
      coreConflict: form.value.coreConflict,
      worldSetting: form.value.worldSetting,
      novelId: targetNovelId || undefined,
      maxStep: form.value.maxStep,
      targetWordCount: getTargetWordCountForApi(),
    });
  } catch (err) {
    generationStore.state.error = err.message || '生成失败';
    generationStore.state.isGenerating = false;
  }
};

// 处理用户确认
const handleApprove = async (approved) => {
  if (!generationStore.sessionId.value) {
    console.error('SessionId不存在');
    return;
  }

  try {
    await generationStore.respondToApproval(approved);
  } catch (err) {
    console.error('确认操作失败:', err);
    generationStore.state.error = err.message || '确认操作失败';
  }
};

// 取消确认对话框
const handleApprovalCancel = () => {
  // 取消时默认拒绝
  handleApprove(false);
};

// 获取友好的标题
const getFriendlyTitle = (stage) => {
  const titleMap = {
    SEED: '✨ 小说创意已生成',
    NOVEL_PLAN: '📖 小说大纲已规划',
    VOLUME_PLAN: '📚 分卷计划已制定',
    CHAPTER_OUTLINE: '📝 章节梗概已创建',
    SCENE_GENERATION: '🎬 场景内容已生成',
    SCENE: '🎬 场景内容已生成',
    VALIDATION: '✅ 内容校验已完成',
  };
  return titleMap[stage] || '📋 内容已生成';
};

// 获取友好的副标题
const getFriendlySubtitle = (stage) => {
  const subtitleMap = {
    SEED: '请查看AI为您创作的小说核心设定',
    NOVEL_PLAN: '请查看AI为您规划的小说结构',
    VOLUME_PLAN: '请查看AI为您制定的分卷安排',
    CHAPTER_OUTLINE: '请查看AI为您创作的章节概要',
    SCENE_GENERATION: '请查看AI为您生成的场景内容',
    SCENE: '请查看AI为您生成的场景内容',
    VALIDATION: '请查看AI为您校验的内容结果',
  };
  return subtitleMap[stage] || '请查看生成的内容';
};

// 获取阶段图标
const getStageIcon = (stage) => {
  const iconMap = {
    SEED: '🌱',
    NOVEL_PLAN: '📖',
    VOLUME_PLAN: '📚',
    CHAPTER_OUTLINE: '📝',
    SCENE_GENERATION: '🎬',
    SCENE: '🎬',
    VALIDATION: '✅',
  };
  return iconMap[stage] || '📋';
};

// 根据数据类型获取展示组件
const getContentComponent = (data) => {
  if (!data) return 'EmptyContent';
  
  // 判断数据类型
  if (data.title && data.genre && data.coreConflict) {
    return 'SeedContent'; // Seed数据
  } else if ((data.volumePlans && Array.isArray(data.volumePlans)) || 
             (data.volumes && Array.isArray(data.volumes)) ||
             (data.totalVolumes && data.chaptersPerVolume)) {
    return 'PlanContent'; // Plan数据
  } else if (data.sceneTitle || data.content) {
    return 'SceneContent'; // Scene数据
  } else {
    return 'GenericContent'; // 通用数据
  }
};

// 格式化确认数据用于显示（保留用于通用展示）
const formatApprovalData = (data) => {
  if (!data) return '无数据';
  try {
    return JSON.stringify(data, null, 2);
  } catch (e) {
    return String(data);
  }
};

// 根据预设和自定义值计算实际 targetWordCount（用于 API）
const getTargetWordCountForApi = () => {
  if (form.value.targetWordCountPreset === 'custom') {
    const val = form.value.targetWordCount;
    return val && val >= 10000 ? val : null;
  }
  if (form.value.targetWordCountPreset && form.value.targetWordCountPreset !== 'custom') {
    return parseInt(form.value.targetWordCountPreset, 10);
  }
  return null; // 使用后端默认
};

// 预设变更时清空自定义输入
const onTargetWordCountPresetChange = () => {
  if (form.value.targetWordCountPreset !== 'custom') {
    form.value.targetWordCount = null;
  }
};

// 格式化字数
const formatWordCount = (count) => {
  if (!count) return '未设置';
  if (count >= 10000) {
    return `${(count / 10000).toFixed(1)} 万字`;
  }
  return `${count} 字`;
};

// 获取题材显示文本（处理枚举值）
const getGenreDisplay = (genre) => {
  if (!genre) return '';
  // 如果是字符串，直接返回
  if (typeof genre === 'string') return genre;
  // 如果是对象（枚举），尝试获取name或value属性
  if (typeof genre === 'object') {
    return genre.name || genre.value || genre.toString();
  }
  return String(genre);
};

// 保存小说规划
const handleSavePlan = async () => {
  if (!approvalData.value || savingPlan.value) return;
  
  try {
    savingPlan.value = true;
    
    // 准备保存数据
    const planData = {
      planId: approvalData.value.planId || `plan_${Date.now()}_${Math.random().toString(36).substring(2, 11)}`,
      novelId: approvalData.value.novelId || novelId.value || `novel_${Date.now()}`,
      totalVolumes: approvalData.value.totalVolumes,
      chaptersPerVolume: approvalData.value.chaptersPerVolume,
      overallOutline: approvalData.value.overallOutline,
      volumePlans: (approvalData.value.volumePlans || []).map(volume => ({
        volumeId: volume.volumeId,
        volumeNumber: volume.volumeNumber,
        volumeTitle: volume.volumeTitle,
        volumeTheme: volume.volumeTheme,
        chapterCount: volume.chapterCount,
      })),
    };
    
    const result = await saveNovelPlan(planData);
    
    if (result.success) {
      savedPlanId.value = result.planId;
      if (planData.novelId) {
        generationStore.syncNovelId(planData.novelId);
        workspaceStore.selectNovel(planData.novelId).catch(() => {})
      }
      // 显示成功提示
      alert('大纲保存成功！');
    } else {
      throw new Error(result.message || '保存失败');
    }
  } catch (error) {
    console.error('保存大纲失败:', error);
    alert('保存大纲失败：' + (error.message || '未知错误'));
  } finally {
    savingPlan.value = false;
  }
};

// 加载大纲
const loadPlan = async () => {
  if (!novelId.value) {
    planError.value = '小说ID不存在';
    return;
  }
  
  try {
    loadingPlan.value = true;
    planError.value = null;
    const result = await getNovelPlanByNovelId(novelId.value);
    
    if (result.success && result.data) {
      currentPlan.value = result.data;
    } else {
      currentPlan.value = null;
      planError.value = result.message || '未找到大纲数据';
    }
  } catch (error) {
    console.error('加载大纲失败:', error);
    planError.value = error.message || '加载失败';
    currentPlan.value = null;
  } finally {
    loadingPlan.value = false;
  }
};

// 开始编辑
const startEdit = () => {
  if (!currentPlan.value) return;
  editPlanData.value = {
    planId: currentPlan.value.planId,
    novelId: currentPlan.value.novelId,
    totalVolumes: currentPlan.value.totalVolumes,
    chaptersPerVolume: currentPlan.value.chaptersPerVolume,
    overallOutline: currentPlan.value.overallOutline,
    volumePlans: currentPlan.value.volumePlans || [],
  };
  editingPlan.value = true;
};

// 取消编辑
const cancelEdit = () => {
  editingPlan.value = false;
  editPlanData.value = null;
};

// 保存修改
const savePlanChanges = async () => {
  if (!editPlanData.value || savingPlan.value) return;
  
  try {
    savingPlan.value = true;
    const result = await updateNovelPlan(editPlanData.value);
    
    if (result.success) {
      // 重新加载大纲
      await loadPlan();
      if (editPlanData.value?.novelId) {
        workspaceStore.selectNovel(editPlanData.value.novelId).catch(() => {})
      }
      editingPlan.value = false;
      alert('大纲更新成功！');
    } else {
      throw new Error(result.message || '更新失败');
    }
  } catch (error) {
    console.error('更新大纲失败:', error);
    alert('更新大纲失败：' + (error.message || '未知错误'));
  } finally {
    savingPlan.value = false;
  }
};

// 监听模态框显示，自动加载大纲
watch(showPlanModal, (newVal) => {
  if (newVal && novelId.value) {
    loadPlan();
  }
});

watch(workspaceStore.currentNovelId, (id) => {
  generationStore.syncNovelId(id || '')
  currentPlan.value = null
})

onMounted(async () => {
  await workspaceStore.initWorkspace()
  if (workspaceStore.currentNovelId.value) {
    generationStore.syncNovelId(workspaceStore.currentNovelId.value)
  }
})

// 重置
const reset = () => {
  generationStore.resetState(workspaceStore.currentNovelId.value || '');
  showPlanModal.value = false;
  currentPlan.value = null;
  editingPlan.value = false;
  editPlanData.value = null;
  form.value.targetWordCountPreset = '';
  form.value.targetWordCount = null;
};
</script>

<style scoped>
.novel-generator {
  min-height: 100vh;
  background: var(--page-bg);
  padding: 2rem;
}

.container {
  max-width: 1400px;
  margin: 0 auto;
}

.header {
  text-align: center;
  color: var(--text-primary);
  margin-bottom: 2rem;
}

.header h1 {
  font-size: 2.5rem;
  margin: 0 0 0.5rem 0;
}

.subtitle {
  font-size: 1.1rem;
  color: var(--text-muted);
  margin: 0;
}

.content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2rem;
}

.workspace-banner {
  max-width: 1400px;
  margin: 0 auto 1.5rem;
  background: var(--panel-bg);
  border-radius: var(--panel-radius);
  padding: 1rem 1.25rem;
  box-shadow: var(--panel-shadow);
}

.workspace-banner p {
  margin: 0.35rem 0 0;
  color: var(--text-muted);
}

.card {
  background: var(--panel-bg);
  border-radius: var(--panel-radius);
  padding: 2rem;
  box-shadow: var(--panel-shadow);
}

.card h2 {
  margin: 0 0 1.5rem 0;
  color: var(--text-primary);
  font-size: 1.5rem;
  border-bottom: 2px solid var(--primary);
  padding-bottom: 0.5rem;
}

.form-group {
  margin-bottom: 1.5rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.5rem;
  color: var(--text-muted);
  font-weight: 500;
}

.form-group select,
.form-group textarea {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid var(--input-border);
  border-radius: 10px;
  font-size: 1rem;
  font-family: inherit;
  transition: border-color 0.2s;
}

.form-group select:focus,
.form-group textarea:focus {
  outline: none;
  border-color: var(--primary);
}

.form-group textarea {
  resize: vertical;
}

.word-count-input {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}

.word-count-input select {
  flex: 1;
  min-width: 0;
}

.word-count-input .custom-word-count {
  width: 140px;
  padding: 0.75rem;
  border: 1px solid var(--input-border);
  border-radius: 10px;
  font-size: 1rem;
}

.word-count-input .custom-word-count:focus {
  outline: none;
  border-color: var(--primary);
}

.form-hint {
  margin: 0.5rem 0 0;
  color: var(--text-muted);
  font-size: 0.85rem;
}

.submit-btn {
  width: 100%;
  padding: 1rem;
  background: var(--primary);
  color: #fff;
  border: none;
  border-radius: 10px;
  font-size: 1.1rem;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.35);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.submit-btn.loading {
  background: var(--secondary-bg);
  color: var(--secondary-text);
}

.progress-bar-container {
  margin-bottom: 2rem;
}

.progress-bar {
  width: 100%;
  height: 12px;
  background: var(--secondary-bg);
  border-radius: 6px;
  overflow: hidden;
  margin-bottom: 0.5rem;
}

.progress-fill {
  height: 100%;
  background: var(--primary);
  transition: width 0.3s ease;
  border-radius: 6px;
}

.progress-text {
  text-align: center;
  color: var(--primary);
  font-weight: 600;
  font-size: 0.9rem;
}

.progress-log {
  max-height: 400px;
  overflow-y: auto;
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 1rem;
  background: var(--meta-bg);
}

.log-item {
  display: flex;
  gap: 1rem;
  padding: 0.75rem;
  margin-bottom: 0.5rem;
  border-left: 3px solid var(--primary);
  background: var(--panel-bg);
  border-radius: 8px;
  font-size: 0.9rem;
}

.log-item.progress {
  border-left-color: var(--primary);
}

.log-item.complete {
  border-left-color: #22c55e;
}

.log-item.error {
  border-left-color: var(--danger-text);
}

.log-time {
  color: var(--text-muted);
  font-size: 0.85rem;
  min-width: 80px;
}

.log-stage {
  color: var(--primary);
  font-weight: 600;
  min-width: 100px;
}

.log-content {
  color: var(--text-primary);
  flex: 1;
}

.empty-state,
.complete-state,
.error-state {
  text-align: center;
  padding: 3rem 1rem;
}

.empty-icon,
.complete-icon,
.error-icon {
  font-size: 4rem;
  margin-bottom: 1rem;
}

.complete-state h3,
.error-state h3 {
  color: var(--text-primary);
  margin: 0 0 1rem 0;
}

.complete-state code {
  background: var(--meta-bg);
  padding: 0.25rem 0.5rem;
  border-radius: 6px;
  font-family: monospace;
}

.reset-btn {
  margin-top: 1rem;
  padding: 0.75rem 2rem;
  background: var(--primary);
  color: #fff;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  font-size: 1rem;
  transition: background 0.2s;
}

.reset-btn:hover {
  background: var(--primary-hover);
}

.error-state {
  color: var(--danger-text);
}

/* 确认对话框样式 */
.approval-modal {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 1rem;
}

.approval-dialog {
  background: white;
  border-radius: 12px;
  max-width: 800px;
  width: 100%;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.approval-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 2rem;
  border-bottom: 1px solid var(--border);
  background: var(--meta-bg);
}

.header-content {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  flex: 1;
}

.header-icon {
  font-size: 2.5rem;
  line-height: 1;
}

.approval-header h3 {
  margin: 0 0 0.5rem 0;
  color: var(--text-primary);
  font-size: 1.5rem;
  font-weight: 600;
}

.header-subtitle {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.95rem;
  font-weight: 400;
}

.close-btn {
  background: none;
  border: none;
  font-size: 2rem;
  color: var(--text-muted);
  cursor: pointer;
  line-height: 1;
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  transition: background 0.2s;
}

.close-btn:hover {
  background: var(--secondary-bg);
}

.approval-content {
  padding: 2rem;
  overflow-y: auto;
  flex: 1;
  background: var(--page-bg);
}

.progress-hint {
  margin-bottom: 1.5rem;
  padding: 1rem 1.5rem;
  background: var(--badge-bg);
  border-radius: 10px;
  border-left: 4px solid var(--primary);
}

.hint-text {
  color: var(--text-muted);
  font-size: 0.95rem;
  font-weight: 500;
}

.content-preview {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.content-card {
  background: white;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transition: transform 0.2s, box-shadow 0.2s;
}

.content-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.5rem;
  background: var(--meta-bg);
  border-bottom: 1px solid var(--border);
}

.card-header h4 {
  margin: 0;
  color: var(--text-primary);
  font-size: 1rem;
  font-weight: 600;
}

.badge {
  background: var(--badge-bg);
  color: var(--badge-text);
  padding: 0.25rem 0.75rem;
  border-radius: 12px;
  font-size: 0.85rem;
  font-weight: 600;
}

.card-body {
  padding: 1.5rem;
}

.novel-title {
  font-size: 1.3rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
  line-height: 1.5;
}

.genre-badge {
  display: inline-block;
  background: var(--badge-bg);
  color: var(--badge-text);
  padding: 0.5rem 1rem;
  border-radius: 20px;
  font-weight: 500;
  font-size: 0.95rem;
}

.text-content {
  color: var(--text-muted);
  line-height: 1.8;
  margin: 0;
  font-size: 0.95rem;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.word-count {
  display: inline-block;
  background: var(--badge-bg);
  color: var(--badge-text);
  padding: 0.5rem 1rem;
  border-radius: 10px;
  font-weight: 600;
  font-size: 1rem;
}

.volume-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.volume-item {
  padding: 1rem;
  background: var(--meta-bg);
  border-radius: 10px;
  border-left: 3px solid var(--primary);
  transition: background 0.2s;
}

.volume-item:hover {
  background: var(--active-row-bg);
}

.volume-header {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 0.5rem;
}

.volume-number {
  color: var(--badge-text);
  font-weight: 600;
  font-size: 0.95rem;
}

.volume-title {
  color: var(--text-primary);
  font-weight: 500;
  flex: 1;
}

.chapter-count {
  color: var(--text-muted);
  font-size: 0.85rem;
}

.scene-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
  line-height: 1.5;
}

.scene-text {
  color: var(--text-muted);
  line-height: 2;
  margin: 0;
  font-size: 0.95rem;
  white-space: pre-wrap;
  word-wrap: break-word;
  text-align: justify;
}

.formatted-data {
  background: var(--meta-bg);
  color: var(--text-primary);
  padding: 1rem;
  border-radius: 10px;
  overflow-x: auto;
  font-size: 0.85rem;
  line-height: 1.6;
  max-height: 400px;
  overflow-y: auto;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  border: 1px solid var(--border);
}

.outline-content {
  color: var(--text-muted);
  line-height: 2;
  margin: 0;
  font-size: 0.95rem;
  white-space: pre-wrap;
  word-wrap: break-word;
  text-align: justify;
  max-height: 300px;
  overflow-y: auto;
  padding: 0.5rem;
  background: var(--meta-bg);
  border-radius: 8px;
}

.plan-stats {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.stat-label {
  color: var(--text-muted);
  font-size: 0.9rem;
}

.stat-value {
  color: var(--badge-text);
  font-weight: 600;
  font-size: 0.95rem;
}

.volume-theme {
  color: var(--text-muted);
  font-size: 0.85rem;
  margin-right: 1rem;
}

.save-hint {
  margin-top: 1rem;
  padding: 1rem 1.5rem;
  background: #fef3c7;
  border-radius: 10px;
  border-left: 4px solid #d97706;
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.hint-icon {
  font-size: 1.2rem;
}

.save-hint .hint-text {
  color: #92400e;
  font-size: 0.9rem;
}

.approve-save {
  background: #d97706;
  color: #fff;
}

.approve-save:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(217, 119, 6, 0.35);
}

.approve-save:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.approval-actions {
  display: flex;
  gap: 1rem;
  padding: 1.5rem;
  border-top: 1px solid var(--border);
}

.approve-btn {
  flex: 1;
  padding: 1rem 1.5rem;
  border: none;
  border-radius: 10px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
}

.btn-icon {
  font-size: 1.2rem;
}

.approve-yes {
  background: #22c55e;
  color: #fff;
}

.approve-yes:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(34, 197, 94, 0.35);
}

.approve-no {
  background: var(--danger-text);
  color: #fff;
}

.approve-no:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(185, 28, 28, 0.35);
}

@media (max-width: 1024px) {
  .content {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .novel-generator {
    padding: 1rem;
  }

  .header h1 {
    font-size: 2rem;
  }

  .card {
    padding: 1.5rem;
  }

  .approval-dialog {
    max-width: 95%;
  }

  .approval-actions {
    flex-direction: column;
  }
}

/* 查看大纲按钮 */
.action-buttons {
  margin-top: 1.5rem;
  padding-top: 1.5rem;
  border-top: 1px solid var(--border);
}

.view-plan-btn {
  width: 100%;
  padding: 0.75rem;
  background: var(--primary);
  color: #fff;
  border: none;
  border-radius: 10px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.view-plan-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.35);
}

/* 大纲模态框 */
.plan-modal {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  padding: 1rem;
}

.plan-modal-dialog {
  background: var(--panel-bg);
  border-radius: var(--panel-radius);
  max-width: 900px;
  width: 100%;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  box-shadow: var(--panel-shadow);
}

.plan-modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1.5rem 2rem;
  border-bottom: 1px solid var(--border);
  background: var(--meta-bg);
}

.plan-modal-header h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: 1.5rem;
  font-weight: 600;
}

.plan-modal-content {
  padding: 2rem;
  overflow-y: auto;
  flex: 1;
}

.loading-state,
.error-state,
.empty-plan {
  text-align: center;
  padding: 3rem 1rem;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 4px solid var(--secondary-bg);
  border-top: 4px solid var(--primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 1rem;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.retry-btn {
  margin-top: 1rem;
  padding: 0.5rem 1.5rem;
  background: var(--primary);
  color: #fff;
  border: none;
  border-radius: 10px;
  cursor: pointer;
}

.plan-info {
  margin-bottom: 2rem;
  padding: 1.5rem;
  background: var(--meta-bg);
  border-radius: 10px;
}

.info-item {
  margin-bottom: 0.75rem;
}

.info-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
  margin-top: 0.75rem;
}

.info-label {
  color: var(--text-muted);
  font-weight: 500;
  margin-right: 0.5rem;
}

.info-value {
  color: var(--text-primary);
  font-weight: 600;
}

.outline-section {
  margin-bottom: 2rem;
}

.outline-section h4 {
  margin: 0 0 1rem 0;
  color: var(--text-primary);
  font-size: 1.2rem;
}

.outline-display {
  padding: 1.5rem;
  background: var(--meta-bg);
  border-radius: 10px;
  color: var(--text-muted);
  line-height: 2;
  white-space: pre-wrap;
  word-wrap: break-word;
  max-height: 300px;
  overflow-y: auto;
}

.volumes-section {
  margin-bottom: 2rem;
}

.volumes-section h4 {
  margin: 0 0 1rem 0;
  color: var(--text-primary);
  font-size: 1.2rem;
}

.volumes-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 1rem;
}

.volume-card {
  background: var(--panel-bg);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 1rem;
  transition: transform 0.2s, box-shadow 0.2s;
}

.volume-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--panel-shadow);
}

.volume-card-header {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-bottom: 0.75rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid var(--border);
}

.volume-num {
  color: var(--badge-text);
  font-weight: 600;
  font-size: 0.9rem;
}

.volume-title {
  color: var(--text-primary);
  font-weight: 500;
  font-size: 1rem;
}

.volume-card-body {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.volume-theme-text {
  color: var(--text-muted);
  font-size: 0.85rem;
  margin: 0;
}

.volume-chapters {
  color: var(--text-muted);
  font-size: 0.85rem;
  margin: 0;
}

.plan-actions {
  margin-top: 2rem;
  text-align: center;
}

.edit-btn {
  padding: 0.75rem 2rem;
  background: var(--primary);
  color: #fff;
  border: none;
  border-radius: 10px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.edit-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.35);
}

.plan-edit {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

.plan-edit .form-group {
  margin-bottom: 0;
}

.plan-edit .form-group label {
  display: block;
  margin-bottom: 0.5rem;
  color: #555;
  font-weight: 500;
}

.plan-edit .form-group input,
.plan-edit .form-group textarea {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid var(--input-border);
  border-radius: 10px;
  font-size: 1rem;
  font-family: inherit;
}

.plan-edit .form-group input:focus,
.plan-edit .form-group textarea:focus {
  outline: none;
  border-color: var(--primary);
}

.plan-edit .form-group textarea {
  resize: vertical;
  min-height: 200px;
}

.form-actions {
  display: flex;
  gap: 1rem;
  justify-content: flex-end;
}

.save-btn {
  padding: 0.75rem 2rem;
  background: #22c55e;
  color: #fff;
  border: none;
  border-radius: 10px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.save-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(34, 197, 94, 0.35);
}

.save-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.cancel-btn {
  padding: 0.75rem 2rem;
  background: var(--secondary-bg);
  color: var(--secondary-text);
  border: none;
  border-radius: 10px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
}

.cancel-btn:hover {
  background: #d1d5db;
}

@media (max-width: 768px) {
  .plan-modal-dialog {
    max-width: 95%;
  }
  
  .info-row,
  .form-row {
    grid-template-columns: 1fr;
  }
  
  .volumes-list {
    grid-template-columns: 1fr;
  }
}
</style>
