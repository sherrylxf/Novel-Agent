import { createRouter, createWebHistory } from 'vue-router'
import NovelGenerator from '@/components/NovelGenerator.vue'
import NovelWorkspace from '@/views/NovelWorkspace.vue'
import ChapterManager from '@/views/ChapterManager.vue'
import NovelConfigs from '@/views/NovelConfigs.vue'
import KnowledgeGraph from '@/views/KnowledgeGraph.vue'
import RAGDocuments from '@/views/RAGDocuments.vue'

const routes = [
  { path: '/', name: 'Home', component: NovelGenerator, meta: { title: '小说生成' } },
  { path: '/workspace', name: 'NovelWorkspace', component: NovelWorkspace, meta: { title: '小说管理' } },
  { path: '/chapters', name: 'ChapterManager', component: ChapterManager, meta: { title: '章节管理' } },
  { path: '/configs', name: 'NovelConfigs', component: NovelConfigs, meta: { title: '配置管理' } },
  { path: '/kg', name: 'KnowledgeGraph', component: KnowledgeGraph, meta: { title: '知识图谱' } },
  { path: '/rag', name: 'RAGDocuments', component: RAGDocuments, meta: { title: 'RAG 文档库' } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - Novel Agent` : 'Novel Agent'
})

export default router
