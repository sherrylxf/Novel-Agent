import { computed, reactive } from 'vue'
import { approveAndContinue, continueChapterGeneration, generateNovel, generateSessionId } from '@/services/novelApi'
import { useNovelWorkspaceStore } from '@/stores/novelWorkspace'

const state = reactive({
  isGenerating: false,
  progress: [],
  completed: false,
  novelId: '',
  error: null,
  sessionId: '',
  waitingApproval: false,
  approvalData: null,
  approvalStage: '',
  approvalNodeName: '',
  approvalNextStage: '',
  awaitingChapterReview: false,
  canContinueAfterReview: false,
  reviewRound: 0,
})

function extractNodeName(content = '') {
  return content.match(/节点\[(.*?)\]/)?.[1] || '未知节点'
}

function extractNextStage(content = '') {
  return content.match(/继续执行\[(.*?)\]/)?.[1] || ''
}

function clearApprovalState() {
  state.waitingApproval = false
  state.approvalData = null
  state.approvalStage = ''
  state.approvalNodeName = ''
  state.approvalNextStage = ''
}

function syncNovelFromPayload(payload) {
  const nextNovelId = payload?.novelId || payload?.data?.novelId || ''
  if (!nextNovelId || nextNovelId === state.novelId) return

  state.novelId = nextNovelId
  const workspaceStore = useNovelWorkspaceStore()
  workspaceStore.selectNovel(nextNovelId).catch(() => {})
}

function resetState(defaultNovelId = '') {
  state.isGenerating = false
  state.progress = []
  state.completed = false
  state.novelId = defaultNovelId || ''
  state.error = null
  state.sessionId = ''
  clearApprovalState()
  state.awaitingChapterReview = false
  state.canContinueAfterReview = false
}

function syncNovelId(novelId) {
  if (state.isGenerating) return
  state.novelId = novelId || ''
}

function handleWaitingApproval(data) {
  syncNovelFromPayload(data)
  state.isGenerating = false
  state.approvalData = data.data
  state.approvalStage = data.stage
  state.approvalNodeName = extractNodeName(data.content)
  state.approvalNextStage = extractNextStage(data.content)

  state.progress.push({
    ...data,
    timestamp: Date.now(),
  })

  if (data.stage === 'VALIDATION') {
    state.awaitingChapterReview = true
    state.canContinueAfterReview = state.approvalNextStage && state.approvalNextStage !== 'COMPLETE'
    state.reviewRound += 1
    state.waitingApproval = false
    return
  }

  state.waitingApproval = true
}

function handleProgress(data) {
  syncNovelFromPayload(data)
  if (data.type === 'waiting_for_approval') {
    handleWaitingApproval(data)
    return
  }

  state.progress.push({
    ...data,
    timestamp: Date.now(),
  })
}

function handleComplete(data) {
  state.completed = true
  state.novelId = data.novelId || state.novelId
  state.isGenerating = false
  state.waitingApproval = false
  state.canContinueAfterReview = false

  const workspaceStore = useNovelWorkspaceStore()
  if (state.novelId) {
    workspaceStore.selectNovel(state.novelId).catch(() => {})
  }
}

function handleError(err, data) {
  state.error = err?.message || data?.content || '生成失败'
  state.isGenerating = false
  state.waitingApproval = false
}

async function startGeneration(params) {
  if (state.isGenerating) return

  const workspaceStore = useNovelWorkspaceStore()
  const targetNovelId = params.novelId || workspaceStore.currentNovelId.value || ''

  resetState(targetNovelId)
  state.isGenerating = true
  state.sessionId = generateSessionId()

  return generateNovel(
    {
      ...params,
      novelId: targetNovelId || undefined,
      sessionId: state.sessionId,
    },
    handleProgress,
    handleComplete,
    handleError
  )
}

async function respondToApproval(approved) {
  if (!state.sessionId) {
    throw new Error('SessionId不存在')
  }

  await approveAndContinue(state.sessionId, approved)

  if (approved) {
    state.awaitingChapterReview = false
    state.canContinueAfterReview = false
    clearApprovalState()
    return
  }

  state.isGenerating = false
  state.awaitingChapterReview = false
  state.canContinueAfterReview = false
  state.progress.push({
    type: 'progress',
    stage: state.approvalStage,
    content: '用户取消执行',
    timestamp: Date.now(),
  })
  clearApprovalState()
}

async function continueNextChapter(novelId, options = {}) {
  if (state.isGenerating) return

  const workspaceStore = useNovelWorkspaceStore()
  const targetNovelId = novelId || state.novelId || workspaceStore.currentNovelId.value || ''
  if (!targetNovelId) {
    throw new Error('novelId不存在，无法继续创作')
  }

  state.error = null
  state.isGenerating = true
  state.completed = false
  clearApprovalState()
  state.waitingApproval = false
  state.awaitingChapterReview = false
  state.canContinueAfterReview = false
  if (!state.sessionId) {
    state.sessionId = generateSessionId()
  }

  return continueChapterGeneration(
    {
      novelId: targetNovelId,
      sessionId: state.sessionId,
      continueMode: options.continueMode || 'auto',
      maxStep: options.maxStep,
    },
    handleProgress,
    handleComplete,
    handleError
  )
}

export function useNovelGenerationStore() {
  return {
    state,
    isGenerating: computed(() => state.isGenerating),
    progress: computed(() => state.progress),
    completed: computed(() => state.completed),
    novelId: computed(() => state.novelId),
    error: computed(() => state.error),
    sessionId: computed(() => state.sessionId),
    waitingApproval: computed(() => state.waitingApproval),
    approvalData: computed(() => state.approvalData),
    approvalStage: computed(() => state.approvalStage),
    approvalNodeName: computed(() => state.approvalNodeName),
    approvalNextStage: computed(() => state.approvalNextStage),
    awaitingChapterReview: computed(() => state.awaitingChapterReview),
    canContinueAfterReview: computed(() => state.canContinueAfterReview),
    reviewRound: computed(() => state.reviewRound),
    startGeneration,
    continueNextChapter,
    respondToApproval,
    resetState,
    syncNovelId,
  }
}
