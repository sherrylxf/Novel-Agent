<script setup>
import { RouterLink, RouterView } from 'vue-router'
import { computed, onMounted, watch } from 'vue'
import router from '@/router'
import { useNovelWorkspaceStore } from '@/stores/novelWorkspace'
import { useNovelGenerationStore } from '@/stores/novelGeneration'

const workspaceStore = useNovelWorkspaceStore()
const generationStore = useNovelGenerationStore()

const currentNovelText = computed(() => {
  const current = workspaceStore.currentNovel.value
  if (!current) return '当前未选择小说'
  return `当前小说：${current.title || current.novelId}`
})

onMounted(() => {
  workspaceStore.initWorkspace()
})

watch(generationStore.awaitingChapterReview, (waiting) => {
  if (waiting && router.currentRoute.value.path !== '/chapters') {
    router.push('/chapters')
  }
})
</script>

<template>
  <div class="app">
    <nav class="nav">
      <RouterLink to="/workspace">小说管理</RouterLink>
      <RouterLink to="/">小说生成</RouterLink>
      <RouterLink to="/chapters">章节管理</RouterLink>
      <RouterLink to="/configs">配置管理</RouterLink>
      <RouterLink to="/kg">知识图谱</RouterLink>
      <RouterLink to="/rag">RAG 文档库</RouterLink>
      <span class="current-novel">{{ currentNovelText }}</span>
    </nav>
    <main class="main">
      <RouterView />
    </main>
  </div>
</template>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

.app {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.nav {
  display: flex;
  gap: 16px;
  padding: 12px 20px;
  background: var(--panel-bg);
  border-bottom: 1px solid var(--border);
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);
}

.nav a {
  color: var(--primary);
  text-decoration: none;
  font-weight: 500;
}

.nav a:hover,
.nav a.router-link-active {
  color: var(--primary-hover);
  text-decoration: none;
}

.nav a.router-link-active {
  font-weight: 600;
}

.current-novel {
  margin-left: auto;
  color: var(--text-muted);
  font-size: 0.9rem;
}

.main {
  flex: 1;
  min-height: 0;
}
</style>
