import { computed, reactive } from 'vue'
import { archiveNovelProject, getNovelProject, listNovelProjects, saveNovelProject } from '@/services/novelApi'

const STORAGE_KEY = 'novel-workspace.currentNovelId'

function readStoredNovelId() {
  if (typeof window === 'undefined') return ''
  try {
    return window.localStorage.getItem(STORAGE_KEY) || ''
  } catch {
    return ''
  }
}

function persistNovelId(novelId) {
  if (typeof window === 'undefined') return
  try {
    if (novelId) {
      window.localStorage.setItem(STORAGE_KEY, novelId)
    } else {
      window.localStorage.removeItem(STORAGE_KEY)
    }
  } catch {
    // ignore persistence failure
  }
}

const state = reactive({
  initialized: false,
  loading: false,
  novels: [],
  currentNovelId: readStoredNovelId(),
  currentNovel: null,
})

async function loadNovels() {
  state.loading = true
  try {
    state.novels = await listNovelProjects()
    if (state.currentNovelId && !state.novels.some((item) => item.novelId === state.currentNovelId)) {
      setCurrentNovelId('')
      state.currentNovel = null
    }
  } finally {
    state.loading = false
  }
  return state.novels
}

async function refreshCurrentNovel() {
  if (!state.currentNovelId) {
    state.currentNovel = null
    return null
  }
  state.currentNovel = await getNovelProject(state.currentNovelId)
  return state.currentNovel
}

function setCurrentNovelId(novelId) {
  state.currentNovelId = novelId || ''
  persistNovelId(state.currentNovelId)
}

async function selectNovel(novelId) {
  setCurrentNovelId(novelId)
  return await refreshCurrentNovel()
}

async function saveNovel(payload) {
  const saved = await saveNovelProject(payload)
  await loadNovels()
  if (saved?.novelId) {
    await selectNovel(saved.novelId)
  }
  return saved
}

async function archiveNovel(novelId) {
  await archiveNovelProject(novelId)
  if (state.currentNovelId === novelId) {
    setCurrentNovelId('')
    state.currentNovel = null
  }
  await loadNovels()
}

async function initWorkspace() {
  if (!state.initialized) {
    state.initialized = true
    await loadNovels()
  }
  if (state.currentNovelId) {
    try {
      await refreshCurrentNovel()
    } catch {
      setCurrentNovelId('')
      state.currentNovel = null
    }
  }
}

export function useNovelWorkspaceStore() {
  return {
    state,
    novels: computed(() => state.novels),
    currentNovelId: computed(() => state.currentNovelId),
    currentNovel: computed(() => state.currentNovel),
    loading: computed(() => state.loading),
    initWorkspace,
    loadNovels,
    refreshCurrentNovel,
    saveNovel,
    archiveNovel,
    selectNovel,
    setCurrentNovelId,
  }
}
