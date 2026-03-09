<template>
  <div class="rag-page">
    <section class="hero">
      <div>
        <p class="eyebrow">RAG 工作台</p>
        <h1>小说记忆检索与调试台</h1>
        <p class="hero-desc">
          统一查看多层记忆、结构化过滤、语义检索结果和 explain 分数拆解。
        </p>
      </div>
      <div class="hero-actions">
        <button class="btn-secondary" @click="useCurrentNovel">使用当前小说</button>
        <button class="btn-ghost" @click="resetAll">重置全部</button>
        <button class="btn-primary" @click="refreshCurrentView">刷新当前视图</button>
      </div>
      <p class="current-tip">{{ currentNovelTip }}</p>
    </section>

    <section class="summary-grid">
      <article class="summary-card">
        <span class="summary-label">文档总数</span>
        <strong>{{ total }}</strong>
        <small>当前筛选下的向量文档</small>
      </article>
      <article class="summary-card">
        <span class="summary-label">当前页</span>
        <strong>{{ page }}/{{ totalPages }}</strong>
        <small>每页 {{ pageSize }} 条</small>
      </article>
      <article class="summary-card">
        <span class="summary-label">检索命中</span>
        <strong>{{ searchResult.length }}</strong>
        <small>最近一次语义检索结果</small>
      </article>
      <article class="summary-card">
        <span class="summary-label">当前视图</span>
        <strong>{{ activeTab === 'documents' ? '文档浏览' : '语义检索' }}</strong>
        <small>{{ activeFilterSummary }}</small>
      </article>
    </section>

    <section class="workspace">
      <aside class="sidebar">
        <div class="panel">
          <div class="panel-head">
            <h3>文档筛选</h3>
            <button class="link-btn" @click="clearDocFilters">清空</button>
          </div>
          <div class="form-grid">
            <label class="field">
              <span>小说ID</span>
              <input v-model="novelId" type="text" placeholder="可选" />
            </label>
            <label class="field">
              <span>章节ID</span>
              <input v-model="chapterId" type="text" placeholder="可选" />
            </label>
            <label class="field">
              <span>记忆类型</span>
              <select v-model="memoryType">
                <option value="">全部</option>
                <option v-for="item in memoryTypeOptions" :key="item.value" :value="item.value">
                  {{ item.label }}
                </option>
              </select>
            </label>
            <label class="field">
              <span>人物</span>
              <input v-model="character" type="text" placeholder="如 韩立" />
            </label>
            <label class="field">
              <span>剧情线程</span>
              <input v-model="plotThread" type="text" placeholder="如 神秘玉佩" />
            </label>
          </div>
          <div class="panel-actions">
            <button class="btn-primary" @click="applyDocFilters">查询文档</button>
          </div>
        </div>

        <div class="panel">
          <div class="panel-head">
            <h3>检索调试</h3>
            <button class="link-btn" @click="clearSearchFilters">清空</button>
          </div>
          <div class="form-grid">
            <label class="field field-full">
              <span>检索词</span>
              <textarea
                v-model="searchQuery"
                rows="3"
                placeholder="输入当前剧情摘要、角色行为或伏笔关键词"
                @keyup.enter.ctrl="doSearch"
              />
            </label>
            <label class="field">
              <span>记忆层</span>
              <select v-model="searchMemoryType">
                <option value="">全部记忆层</option>
                <option v-for="item in memoryTypeOptions" :key="item.value" :value="item.value">
                  {{ item.label }}
                </option>
              </select>
            </label>
            <label class="field">
              <span>人物过滤</span>
              <input v-model="searchCharacter" type="text" placeholder="如 韩立" />
            </label>
            <label class="field">
              <span>地点过滤</span>
              <input v-model="searchLocation" type="text" placeholder="如 青云宗" />
            </label>
            <label class="field">
              <span>剧情线程</span>
              <input v-model="searchPlotThread" type="text" placeholder="如 神秘玉佩" />
            </label>
            <label class="field">
              <span>TopK</span>
              <select v-model.number="topK">
                <option :value="3">3</option>
                <option :value="5">5</option>
                <option :value="8">8</option>
              </select>
            </label>
            <label class="field">
              <span>章节起点</span>
              <input v-model="chapterFrom" type="number" min="1" placeholder="可选" />
            </label>
            <label class="field">
              <span>章节终点</span>
              <input v-model="chapterTo" type="number" min="1" placeholder="可选" />
            </label>
            <label class="field field-checkbox">
              <span>检索解释</span>
              <input v-model="explain" type="checkbox" />
            </label>
            <label class="field">
              <span>对比维度</span>
              <select v-model="compareMode">
                <option value="memoryType">记忆层对比</option>
                <option value="topK">TopK 对比</option>
              </select>
            </label>
            <label class="field">
              <span>对比过滤策略</span>
              <select v-model="compareFilterMode">
                <option value="strict">严格：沿用当前全部过滤</option>
                <option value="core">核心：仅保留小说、Query、人物</option>
              </select>
            </label>
          </div>
          <div class="quick-tags">
            <button
              v-for="item in memoryTypeOptions"
              :key="item.value"
              class="tag-btn"
              :class="{ active: searchMemoryType === item.value }"
              @click="toggleSearchMemoryType(item.value)"
            >
              {{ item.label }}
            </button>
          </div>
          <div class="panel-actions">
            <button class="btn-primary" :disabled="searchLoading" @click="doSearch">
              {{ searchLoading ? '检索中...' : '执行检索' }}
            </button>
            <button class="btn-secondary" :disabled="compareLoading" @click="runCompareMode">
              {{ compareLoading ? '对比中...' : '执行对比' }}
            </button>
            <button class="btn-secondary" @click="applySearchToDocs">同步到文档筛选</button>
            <button class="btn-ghost" @click="copyRegressionCase">复制回归用例</button>
          </div>
        </div>
      </aside>

      <section class="content-area">
        <div class="toolbar">
          <div class="tabs">
            <button
              class="tab"
              :class="{ active: activeTab === 'documents' }"
              @click="activeTab = 'documents'"
            >
              文档浏览
            </button>
            <button
              class="tab"
              :class="{ active: activeTab === 'search' }"
              @click="activeTab = 'search'"
            >
              语义检索
            </button>
          </div>
          <div class="chips">
            <span v-for="chip in activeFilterChips" :key="chip" class="chip">{{ chip }}</span>
            <span v-if="!activeFilterChips.length" class="chip muted">当前没有筛选条件</span>
          </div>
        </div>

        <p v-if="errorMessage" class="error-banner">{{ errorMessage }}</p>
        <p v-if="copyFeedback" class="success-banner">{{ copyFeedback }}</p>

        <div v-if="activeTab === 'search' && searchQueryPayload" class="regression-panel">
          <div class="panel-head">
            <div>
              <h3>检索快照</h3>
              <span class="panel-subtitle">参考大厂 RAG 工作台常见做法：保留查询快照，便于回归和复盘</span>
            </div>
            <div class="panel-actions compact">
              <button class="btn-ghost" @click="copySearchPayload">复制参数 JSON</button>
              <button class="btn-primary" @click="copyRegressionCase">复制回归用例</button>
            </div>
          </div>
          <div class="snapshot-grid">
            <div class="snapshot-card">
              <span class="summary-label">Query</span>
              <strong>{{ searchQuery }}</strong>
              <small>当前结构化检索输入</small>
            </div>
            <div class="snapshot-card">
              <span class="summary-label">过滤条件</span>
              <strong>{{ snapshotFilterText }}</strong>
              <small>人物 / 地点 / 线程 / 章节范围</small>
            </div>
            <div class="snapshot-card">
              <span class="summary-label">TopK / Explain</span>
              <strong>{{ topK }} / {{ explain ? '开启' : '关闭' }}</strong>
              <small>用于回归时复现排序行为</small>
            </div>
          </div>
          <pre class="snapshot-code">{{ regressionCasePreview }}</pre>
        </div>

        <div v-if="activeTab === 'search' && compareResults.length" class="compare-panel">
          <div class="panel-head">
            <div>
              <h3>检索结果对比模式</h3>
              <span class="panel-subtitle">同一 query 对比不同 {{ compareMode === 'memoryType' ? 'memoryType' : 'TopK' }} 的命中差异</span>
            </div>
            <div class="chips">
              <span class="chip">{{ compareMode === 'memoryType' ? '维度: 记忆层' : '维度: TopK' }}</span>
              <span class="chip">{{ compareResults.length }} 组结果</span>
            </div>
          </div>
          <div class="compare-grid">
            <article v-for="group in compareResults" :key="group.key" class="compare-card">
              <div class="compare-card-head">
                <div>
                  <strong>{{ group.label }}</strong>
                  <p class="panel-subtitle">命中 {{ group.list.length }} 条</p>
                </div>
                <button class="btn-ghost compact-btn" :disabled="!group.list.length" @click="openCompareGroupTopHit(group)">查看首条</button>
              </div>
              <div v-if="!group.list.length" class="empty compare-empty">暂无结果</div>
              <div v-else class="compare-list">
                <div v-for="(item, index) in group.list.slice(0, 3)" :key="`${group.key}-${index}`" class="compare-item">
                  <div class="result-top">
                    <span class="memory-pill">{{ memoryLabel(item.metadata?.memoryType) }}</span>
                    <strong class="score">{{ scoreStr(item.finalScore ?? item.score) }}</strong>
                  </div>
                  <div class="hit-tags">
                    <span v-for="tag in buildHitExplainTags(item)" :key="tag" class="hit-tag">{{ tag }}</span>
                  </div>
                  <p class="result-source">
                    {{ item.metadata?.chapterTitle || '未命名章节' }}
                    <span v-if="item.metadata?.sceneTitle"> / {{ item.metadata.sceneTitle }}</span>
                  </p>
                  <p class="result-excerpt">
                    {{ (item.content || '').slice(0, 120) }}{{ item.content && item.content.length > 120 ? '…' : '' }}
                  </p>
                </div>
              </div>
            </article>
          </div>
        </div>

        <div v-if="activeTab === 'search' && showZeroResultDiagnosis" class="diagnosis-panel">
          <div class="panel-head">
            <div>
              <h3>零结果诊断</h3>
              <span class="panel-subtitle">当前检索条件较严，工作台推测以下条件可能限制了召回</span>
            </div>
            <span class="chip muted">0 命中分析</span>
          </div>
          <div class="diagnosis-grid">
            <div class="diagnosis-card">
              <span class="summary-label">当前 Query</span>
              <strong>{{ searchQuery || '未填写' }}</strong>
              <small>{{ snapshotFilterText }}</small>
            </div>
            <div class="diagnosis-card">
              <span class="summary-label">建议优先检查</span>
              <strong>{{ zeroResultHint }}</strong>
              <small>先放宽最可能导致 0 条的条件</small>
            </div>
          </div>
          <div class="chips">
            <span v-for="item in zeroResultReasons" :key="item" class="chip">{{ item }}</span>
          </div>
          <div class="panel-actions">
            <button class="btn-primary" @click="retryWithoutPlotThread">移除剧情线程后重试</button>
            <button class="btn-secondary" @click="retryWithoutLocation">移除地点后重试</button>
            <button class="btn-ghost" @click="retryKeepOnlyCharacter">仅保留人物过滤</button>
            <button class="btn-ghost" @click="switchToCoreCompare">切换为核心对比策略</button>
          </div>
        </div>

        <div class="content-grid">
          <div class="list-panel">
            <template v-if="activeTab === 'documents'">
              <div class="panel-head">
                <h3>文档列表</h3>
                <span class="panel-subtitle">按 metadata 浏览当前小说的记忆层</span>
              </div>

              <div class="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>ID</th>
                      <th>记忆类型</th>
                      <th>小说/卷/章</th>
                      <th>场景</th>
                      <th>内容摘要</th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-if="loading">
                      <td colspan="6" class="empty">加载中…</td>
                    </tr>
                    <tr v-else-if="!docList.length">
                      <td colspan="6" class="empty">暂无文档</td>
                    </tr>
                    <tr
                      v-for="d in docList"
                      :key="d.id"
                      class="clickable-row"
                      :class="{ selected: selectedDocument?.id === d.id }"
                      @click="selectDocument(d)"
                    >
                      <td>{{ shortId(d.id) }}</td>
                      <td>
                        <span class="memory-pill">{{ memoryLabel(d.metadata?.memoryType) }}</span>
                      </td>
                      <td>{{ docMeta(d).novel }} {{ docMeta(d).vol }} {{ docMeta(d).ch }}</td>
                      <td>{{ docMeta(d).scene }}</td>
                      <td class="content-cell" :title="d.contentSummary">{{ (d.contentSummary || '').slice(0, 120) }}</td>
                      <td>
                        <button class="btn-del" @click.stop="deleteDoc(d.id)">删除</button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>

              <div class="pagination">
                <button class="btn-ghost" :disabled="page <= 1 || loading" @click="goToPage(page - 1)">上一页</button>
                <span>第 {{ page }} / {{ totalPages }} 页</span>
                <button class="btn-ghost" :disabled="page >= totalPages || loading" @click="goToPage(page + 1)">下一页</button>
              </div>
            </template>

            <template v-else>
              <div class="panel-head">
                <h3>检索结果</h3>
                <span class="panel-subtitle">查看 memoryType、metadata 命中和 explain 分数</span>
              </div>

              <div v-if="searchLoading" class="empty search-empty">检索中…</div>
              <div v-else-if="!searchResult.length" class="empty search-empty">暂无检索结果</div>
              <div v-else class="result-list">
                <article
                  v-for="(item, i) in searchResult"
                  :key="item.id || i"
                  class="result-card"
                  :class="{ selected: selectedSearchResult === item }"
                  @click="selectSearchResult(item)"
                >
                  <div class="result-top">
                    <div>
                      <span class="memory-pill">{{ memoryLabel(item.metadata?.memoryType) }}</span>
                      <strong class="result-title">命中 {{ i + 1 }}</strong>
                    </div>
                    <strong class="score">{{ scoreStr(item.finalScore ?? item.score) }}</strong>
                  </div>
                  <p class="result-source">
                    {{ item.metadata?.chapterTitle || '未命名章节' }}
                    <span v-if="item.metadata?.sceneTitle"> / {{ item.metadata.sceneTitle }}</span>
                  </p>
                  <div class="meta-row">
                    <span v-if="item.metadata?.characters">人物: {{ item.metadata.characters }}</span>
                    <span v-if="item.metadata?.location">地点: {{ item.metadata.location }}</span>
                    <span v-if="item.metadata?.plotThreads">线程: {{ item.metadata.plotThreads }}</span>
                  </div>
                  <p class="result-excerpt">
                    {{ (item.content || '').slice(0, 160) }}{{ item.content && item.content.length > 160 ? '…' : '' }}
                  </p>
                </article>
              </div>
            </template>
          </div>

          <aside class="detail-panel">
            <div class="panel-head">
              <h3>{{ activeTab === 'documents' ? '文档详情' : '命中详情' }}</h3>
              <span class="panel-subtitle">
                {{ activeTab === 'documents' ? '查看 metadata 和摘要预览' : '查看 explain 分数和命中依据' }}
              </span>
            </div>

            <template v-if="activeTab === 'documents'">
              <div v-if="selectedDocument" class="detail-content">
                <div class="detail-section">
                  <h4>{{ memoryLabel(selectedDocument.metadata?.memoryType) }}</h4>
                  <p class="detail-subtitle">{{ shortId(selectedDocument.id) }}</p>
                </div>
                <div class="detail-actions">
                  <button class="btn-primary" @click="openDocumentModal">弹窗查看全文</button>
                  <button class="btn-ghost" @click="copySelectedDocumentMetadata">复制 metadata</button>
                </div>
                <div class="detail-section">
                  <h5>关键信息</h5>
                  <div class="chips">
                    <span v-if="selectedDocument.metadata?.location" class="chip">地点: {{ selectedDocument.metadata.location }}</span>
                    <span v-if="selectedDocument.metadata?.characters" class="chip">人物: {{ selectedDocument.metadata.characters }}</span>
                    <span v-if="selectedDocument.metadata?.sceneType" class="chip">场景类型: {{ selectedDocument.metadata.sceneType }}</span>
                    <span v-if="selectedDocument.metadata?.chapterNumber" class="chip">章节: 第{{ selectedDocument.metadata.chapterNumber }}章</span>
                  </div>
                </div>
                <div class="detail-section">
                  <h5>摘要预览</h5>
                  <pre class="preview-text">{{ selectedDocument.contentSummary || '暂无内容' }}</pre>
                </div>
                <div class="detail-section">
                  <h5>完整内容</h5>
                  <pre v-if="detailLoading" class="preview-text">加载中…</pre>
                  <pre v-else class="preview-text">{{ selectedDocument.content || selectedDocument.contentSummary || '暂无内容' }}</pre>
                </div>
                <div class="detail-section">
                  <h5>Metadata</h5>
                  <div class="kv-list">
                    <div v-for="(value, key) in selectedDocument.metadata || {}" :key="key" class="kv-item">
                      <span>{{ key }}</span>
                      <strong>{{ formatMetaValue(value) }}</strong>
                    </div>
                  </div>
                </div>
              </div>
              <div v-else class="empty detail-empty">选择左侧文档以查看详情</div>
            </template>

            <template v-else>
              <div v-if="selectedSearchResult" class="detail-content">
                <div class="detail-section">
                  <h4>{{ memoryLabel(selectedSearchResult.metadata?.memoryType) }}</h4>
                  <p class="detail-subtitle">
                    {{ selectedSearchResult.metadata?.chapterTitle || '未命名章节' }}
                    <span v-if="selectedSearchResult.metadata?.sceneTitle"> / {{ selectedSearchResult.metadata.sceneTitle }}</span>
                  </p>
                </div>
                <div class="detail-actions">
                  <button class="btn-primary" @click="openSearchResultModal">弹窗查看命中</button>
                  <button class="btn-ghost" @click="copyRegressionCase">复制回归用例</button>
                </div>
                <div class="detail-section">
                  <h5>命中来源解释</h5>
                  <div class="hit-tags">
                    <span v-for="tag in buildHitExplainTags(selectedSearchResult)" :key="tag" class="hit-tag">{{ tag }}</span>
                    <span v-if="!buildHitExplainTags(selectedSearchResult).length" class="chip muted">暂无解释标签</span>
                  </div>
                  <div class="explain-list">
                    <div v-for="item in buildHitExplainItems(selectedSearchResult)" :key="item.label" class="explain-item">
                      <div class="explain-head">
                        <strong>{{ item.label }}</strong>
                        <span class="explain-state" :class="{ matched: item.matched, missed: !item.matched }">
                          {{ item.matched ? '已命中' : '未命中' }}
                        </span>
                      </div>
                      <p>{{ item.detail }}</p>
                    </div>
                  </div>
                </div>
                <div v-if="selectedSearchResult.explain" class="detail-section">
                  <h5>Explain 分数</h5>
                  <div class="score-bars">
                    <div class="score-row">
                      <span>相似度</span>
                      <div class="bar-track"><div class="bar-fill similarity" :style="{ width: scoreWidth(selectedSearchResult.explain.similarityScore) }" /></div>
                      <strong>{{ scoreStr(selectedSearchResult.explain.similarityScore) }}</strong>
                    </div>
                    <div class="score-row">
                      <span>时间衰减</span>
                      <div class="bar-track"><div class="bar-fill recency" :style="{ width: scoreWidth(selectedSearchResult.explain.recencyScore) }" /></div>
                      <strong>{{ scoreStr(selectedSearchResult.explain.recencyScore) }}</strong>
                    </div>
                    <div class="score-row">
                      <span>最终分</span>
                      <div class="bar-track"><div class="bar-fill final" :style="{ width: scoreWidth(selectedSearchResult.explain.finalScore) }" /></div>
                      <strong>{{ scoreStr(selectedSearchResult.explain.finalScore) }}</strong>
                    </div>
                  </div>
                </div>
                <div class="detail-section">
                  <h5>命中内容</h5>
                  <pre class="preview-text">{{ selectedSearchResult.content || '暂无内容' }}</pre>
                </div>
                <div class="detail-section">
                  <h5>Metadata</h5>
                  <div class="kv-list">
                    <div v-for="(value, key) in selectedSearchResult.metadata || {}" :key="key" class="kv-item">
                      <span>{{ key }}</span>
                      <strong>{{ formatMetaValue(value) }}</strong>
                    </div>
                  </div>
                </div>
              </div>
              <div v-else class="empty detail-empty">执行检索并选择一条命中结果</div>
            </template>
          </aside>
        </div>
      </section>
    </section>

    <div v-if="detailModal.visible" class="modal-mask" @click.self="closeDetailModal">
      <div class="detail-modal">
        <div class="modal-header">
          <div>
            <p class="eyebrow modal-eyebrow">{{ detailModal.mode === 'document' ? '文档详情弹窗' : '命中详情弹窗' }}</p>
            <h3>{{ detailModal.title }}</h3>
            <p class="detail-subtitle">{{ detailModal.subtitle }}</p>
          </div>
          <div class="panel-actions compact">
            <button class="btn-ghost" @click="copyModalContent">复制全文</button>
            <button class="btn-secondary" @click="closeDetailModal">关闭</button>
          </div>
        </div>

        <div class="modal-body">
          <div v-if="detailModal.explain" class="detail-section">
            <h5>Explain 分数</h5>
            <div class="score-bars">
              <div class="score-row">
                <span>相似度</span>
                <div class="bar-track"><div class="bar-fill similarity" :style="{ width: scoreWidth(detailModal.explain.similarityScore) }" /></div>
                <strong>{{ scoreStr(detailModal.explain.similarityScore) }}</strong>
              </div>
              <div class="score-row">
                <span>时间衰减</span>
                <div class="bar-track"><div class="bar-fill recency" :style="{ width: scoreWidth(detailModal.explain.recencyScore) }" /></div>
                <strong>{{ scoreStr(detailModal.explain.recencyScore) }}</strong>
              </div>
              <div class="score-row">
                <span>最终分</span>
                <div class="bar-track"><div class="bar-fill final" :style="{ width: scoreWidth(detailModal.explain.finalScore) }" /></div>
                <strong>{{ scoreStr(detailModal.explain.finalScore) }}</strong>
              </div>
            </div>
          </div>

          <div class="detail-section">
            <h5>完整内容</h5>
            <pre class="preview-text modal-text">{{ detailModal.content || '暂无内容' }}</pre>
          </div>

          <div class="detail-section">
            <h5>Metadata</h5>
            <div class="kv-list">
              <div v-for="(value, key) in detailModal.metadata || {}" :key="key" class="kv-item">
                <span>{{ key }}</span>
                <strong>{{ formatMetaValue(value) }}</strong>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { getRagDocuments, searchRag, deleteRagDocument, getRagDocumentDetail } from '@/services/novelApi'
import { useNovelWorkspaceStore } from '@/stores/novelWorkspace'

const memoryTypeOptions = [
  { value: 'chapter_summary', label: '章节摘要' },
  { value: 'scene_summary', label: '场景摘要' },
  { value: 'scene_fulltext', label: '场景正文' },
  { value: 'character_memory', label: '角色记忆' },
  { value: 'plot_thread_summary', label: '剧情线程' },
]

const novelId = ref('')
const chapterId = ref('')
const memoryType = ref('')
const character = ref('')
const plotThread = ref('')

const searchQuery = ref('')
const searchMemoryType = ref('')
const searchCharacter = ref('')
const searchLocation = ref('')
const searchPlotThread = ref('')
const chapterFrom = ref('')
const chapterTo = ref('')
const topK = ref(3)
const explain = ref(true)
const compareMode = ref('memoryType')
const compareFilterMode = ref('strict')

const activeTab = ref('documents')
const docList = ref([])
const total = ref(0)
const loading = ref(false)
const detailLoading = ref(false)
const searchLoading = ref(false)
const compareLoading = ref(false)
const searchResult = ref([])
const compareResults = ref([])
const searchQueryPayload = ref(null)
const errorMessage = ref('')
const copyFeedback = ref('')
const page = ref(1)
const pageSize = 20

const selectedDocumentId = ref('')
const selectedDocumentDetail = ref(null)
const selectedSearchIndex = ref(-1)
const detailModal = ref({
  visible: false,
  mode: 'document',
  title: '',
  subtitle: '',
  content: '',
  metadata: null,
  explain: null,
})

const workspaceStore = useNovelWorkspaceStore()

const currentNovelTip = computed(() => {
  const current = workspaceStore.currentNovel.value
  return current ? `当前小说：${current.title || current.novelId}` : '当前未选择小说，可在小说管理页切换'
})

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

const selectedDocument = computed(() => selectedDocumentDetail.value || docList.value.find((item) => item.id === selectedDocumentId.value) || null)
const selectedSearchResult = computed(() => searchResult.value[selectedSearchIndex.value] || null)

const activeFilterChips = computed(() => {
  const chips = []
  if (novelId.value.trim()) chips.push(`小说: ${novelId.value.trim()}`)
  if (chapterId.value.trim()) chips.push(`章节ID: ${chapterId.value.trim()}`)
  if (memoryType.value) chips.push(`文档层: ${memoryLabel(memoryType.value)}`)
  if (character.value.trim()) chips.push(`人物: ${character.value.trim()}`)
  if (plotThread.value.trim()) chips.push(`剧情线程: ${plotThread.value.trim()}`)
  if (activeTab.value === 'search') {
    if (searchMemoryType.value) chips.push(`检索层: ${memoryLabel(searchMemoryType.value)}`)
    if (searchCharacter.value.trim()) chips.push(`检索人物: ${searchCharacter.value.trim()}`)
    if (searchLocation.value.trim()) chips.push(`地点: ${searchLocation.value.trim()}`)
    if (searchPlotThread.value.trim()) chips.push(`检索线程: ${searchPlotThread.value.trim()}`)
    if (searchQuery.value.trim()) chips.push(`Query: ${searchQuery.value.trim()}`)
  }
  return chips
})

const activeFilterSummary = computed(() => {
  if (!activeFilterChips.value.length) return '未设置筛选'
  return activeFilterChips.value.slice(0, 2).join(' / ')
})

const showZeroResultDiagnosis = computed(() => {
  return activeTab.value === 'search'
    && !searchLoading.value
    && !compareLoading.value
    && !searchResult.value.length
    && compareResults.value.every((group) => !group.list.length)
    && !!searchQuery.value.trim()
})

const zeroResultReasons = computed(() => {
  const reasons = []
  if (searchPlotThread.value.trim()) reasons.push(`剧情线程过滤过严: ${searchPlotThread.value.trim()}`)
  if (searchLocation.value.trim()) reasons.push(`地点过滤过严: ${searchLocation.value.trim()}`)
  if (searchCharacter.value.trim()) reasons.push(`人物过滤: ${searchCharacter.value.trim()}`)
  if (searchMemoryType.value) reasons.push(`限定记忆层: ${memoryLabel(searchMemoryType.value)}`)
  if (chapterFrom.value || chapterTo.value) {
    reasons.push(`章节范围限制: ${chapterFrom.value || '-'} ~ ${chapterTo.value || '-'}`)
  }
  if (!reasons.length) reasons.push('当前主要依赖 query 本身，建议检查 embedding 或历史文档是否存在')
  return reasons
})

const zeroResultHint = computed(() => {
  if (searchPlotThread.value.trim()) return '剧情线程过滤通常最容易导致 0 条'
  if (searchLocation.value.trim()) return '地点过滤次之，建议先验证地点命名是否一致'
  if (searchMemoryType.value) return '记忆层过滤过窄，建议先放宽 memoryType'
  return '建议切换到核心对比策略，仅保留小说、Query、人物'
})

const snapshotFilterText = computed(() => {
  const parts = []
  if (searchMemoryType.value) parts.push(memoryLabel(searchMemoryType.value))
  if (searchCharacter.value.trim()) parts.push(`人物=${searchCharacter.value.trim()}`)
  if (searchLocation.value.trim()) parts.push(`地点=${searchLocation.value.trim()}`)
  if (searchPlotThread.value.trim()) parts.push(`线程=${searchPlotThread.value.trim()}`)
  if (chapterFrom.value || chapterTo.value) {
    parts.push(`章节=${chapterFrom.value || '-'}~${chapterTo.value || '-'}`)
  }
  return parts.length ? parts.join(' / ') : '无额外过滤'
})

const regressionCasePreview = computed(() => generateRegressionCaseText())

function memoryLabel(value) {
  return memoryTypeOptions.find((item) => item.value === value)?.label || value || '未知类型'
}

function shortId(value) {
  return value ? `${String(value).slice(0, 8)}…` : '-'
}

function scoreStr(score) {
  return score != null ? `${(Number(score) * 100).toFixed(1)}%` : '-'
}

function scoreWidth(score) {
  if (score == null || Number.isNaN(Number(score))) return '0%'
  return `${Math.max(0, Math.min(100, Number(score) * 100))}%`
}

function formatMetaValue(value) {
  if (Array.isArray(value)) return value.join(', ')
  if (value == null || value === '') return '-'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function normalizeText(value) {
  return String(value || '').trim().toLowerCase()
}

function splitMetaValue(value) {
  if (Array.isArray(value)) return value.map((item) => String(item).trim()).filter(Boolean)
  if (value == null) return []
  return String(value)
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

function safeJson(value) {
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

function docMeta(d) {
  const meta = d.metadata || {}
  return {
    novel: meta.novelId || '-',
    vol: meta.volumeNumber != null ? `卷${meta.volumeNumber}` : '',
    ch: meta.chapterNumber != null ? `章${meta.chapterNumber}` : meta.chapterTitle || '',
    scene: meta.sceneTitle || meta.sceneNumber || '-',
  }
}

function syncDocumentSelection() {
  if (!docList.value.length) {
    selectedDocumentId.value = ''
    selectedDocumentDetail.value = null
    return
  }
  const exists = docList.value.some((item) => item.id === selectedDocumentId.value)
  if (!exists) {
    selectDocument(docList.value[0])
  }
}

function syncSearchSelection() {
  if (!searchResult.value.length) {
    selectedSearchIndex.value = -1
    return
  }
  if (selectedSearchIndex.value < 0 || selectedSearchIndex.value >= searchResult.value.length) {
    selectedSearchIndex.value = 0
  }
}

async function loadDocs() {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await getRagDocuments({
      novelId: novelId.value.trim(),
      chapterId: chapterId.value.trim(),
      memoryType: memoryType.value,
      character: character.value.trim(),
      plotThread: plotThread.value.trim(),
      page: page.value,
      size: pageSize,
    })
    docList.value = res.list
    total.value = res.total
    syncDocumentSelection()
  } catch (e) {
    console.error(e)
    errorMessage.value = e.message || String(e)
    docList.value = []
    total.value = 0
    selectedDocumentId.value = ''
    selectedDocumentDetail.value = null
  } finally {
    loading.value = false
  }
}

async function doSearch() {
  const q = searchQuery.value.trim()
  if (!q) {
    alert('请输入检索词')
    return
  }
  searchLoading.value = true
  errorMessage.value = ''
  activeTab.value = 'search'
  try {
    const payload = {
      novelId: novelId.value.trim(),
      topK: topK.value,
      memoryTypes: searchMemoryType.value ? [searchMemoryType.value] : [],
      characters: searchCharacter.value.trim() ? [searchCharacter.value.trim()] : [],
      location: searchLocation.value.trim(),
      plotThread: searchPlotThread.value.trim(),
      chapterFrom: chapterFrom.value || '',
      chapterTo: chapterTo.value || '',
      explain: explain.value,
    }
    const { list, query } = await searchRag(q, payload)
    searchResult.value = list
    searchQueryPayload.value = query || payload
    compareResults.value = []
    syncSearchSelection()
  } catch (e) {
    console.error(e)
    errorMessage.value = e.message || String(e)
    searchResult.value = []
    selectedSearchIndex.value = -1
  } finally {
    searchLoading.value = false
  }
}

async function deleteDoc(id) {
  if (!confirm('确定删除该文档？')) return
  try {
    await deleteRagDocument(id)
    await loadDocs()
  } catch (e) {
    alert(`删除失败: ${e.message || e}`)
  }
}

async function selectDocument(item) {
  const id = item?.id || ''
  selectedDocumentId.value = id
  selectedDocumentDetail.value = item ? { ...item } : null
  if (!id) return
  detailLoading.value = true
  try {
    const detail = await getRagDocumentDetail(id)
    if (selectedDocumentId.value === id && detail) {
      selectedDocumentDetail.value = detail
    }
  } catch (e) {
    console.error(e)
  } finally {
    if (selectedDocumentId.value === id) {
      detailLoading.value = false
    }
  }
}

function selectSearchResult(item) {
  selectedSearchIndex.value = searchResult.value.findIndex((entry) => entry === item)
}

function applyDocFilters() {
  page.value = 1
  activeTab.value = 'documents'
  loadDocs()
}

function applySearchToDocs() {
  if (searchMemoryType.value) memoryType.value = searchMemoryType.value
  if (searchCharacter.value.trim()) character.value = searchCharacter.value.trim()
  if (searchPlotThread.value.trim()) plotThread.value = searchPlotThread.value.trim()
  page.value = 1
  activeTab.value = 'documents'
  loadDocs()
}

function clearDocFilters() {
  chapterId.value = ''
  memoryType.value = ''
  character.value = ''
  plotThread.value = ''
}

function clearSearchFilters() {
  searchQuery.value = ''
  searchMemoryType.value = ''
  searchCharacter.value = ''
  searchLocation.value = ''
  searchPlotThread.value = ''
  chapterFrom.value = ''
  chapterTo.value = ''
  topK.value = 3
  explain.value = true
  compareResults.value = []
}

function resetAll() {
  novelId.value = workspaceStore.currentNovelId.value || ''
  clearDocFilters()
  clearSearchFilters()
  searchResult.value = []
  compareResults.value = []
  searchQueryPayload.value = null
  selectedSearchIndex.value = -1
  page.value = 1
  activeTab.value = 'documents'
  loadDocs()
}

function refreshCurrentView() {
  if (activeTab.value === 'search' && searchQuery.value.trim()) {
    doSearch()
    return
  }
  loadDocs()
}

function useCurrentNovel() {
  novelId.value = workspaceStore.currentNovelId.value || ''
  page.value = 1
  loadDocs()
}

function goToPage(nextPage) {
  const target = Math.max(1, Math.min(totalPages.value, nextPage))
  if (target === page.value) return
  page.value = target
  loadDocs()
}

function toggleSearchMemoryType(value) {
  searchMemoryType.value = searchMemoryType.value === value ? '' : value
}

function showCopyFeedback(message) {
  copyFeedback.value = message
  window.clearTimeout(showCopyFeedback.timer)
  showCopyFeedback.timer = window.setTimeout(() => {
    copyFeedback.value = ''
  }, 2200)
}

showCopyFeedback.timer = null

async function copyText(text, successMessage) {
  if (!text) return
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      const textarea = document.createElement('textarea')
      textarea.value = text
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
    }
    showCopyFeedback(successMessage)
  } catch (e) {
    console.error(e)
    alert('复制失败，请检查浏览器权限')
  }
}

function buildSearchPayload() {
  return {
    q: searchQuery.value.trim(),
    novelId: novelId.value.trim(),
    topK: topK.value,
    memoryTypes: searchMemoryType.value ? [searchMemoryType.value] : [],
    characters: searchCharacter.value.trim() ? [searchCharacter.value.trim()] : [],
    location: searchLocation.value.trim(),
    plotThread: searchPlotThread.value.trim(),
    chapterFrom: chapterFrom.value || '',
    chapterTo: chapterTo.value || '',
    explain: explain.value,
  }
}

function buildCompareBasePayload() {
  const base = buildSearchPayload()
  if (compareFilterMode.value === 'core') {
    return {
      ...base,
      memoryTypes: [],
      location: '',
      plotThread: '',
      chapterFrom: '',
      chapterTo: '',
    }
  }
  return base
}

function buildComparePayloads() {
  const base = buildCompareBasePayload()
  if (compareMode.value === 'topK') {
    return [
      { key: 'topk-3', label: 'TopK = 3', payload: { ...base, topK: 3 } },
      { key: 'topk-5', label: 'TopK = 5', payload: { ...base, topK: 5 } },
      { key: 'topk-8', label: 'TopK = 8', payload: { ...base, topK: 8 } },
    ]
  }
  return [
    { key: 'all', label: '全部记忆层', payload: { ...base, memoryTypes: [] } },
    { key: 'chapter_summary', label: '章节摘要', payload: { ...base, memoryTypes: ['chapter_summary'] } },
    { key: 'scene_summary', label: '场景摘要', payload: { ...base, memoryTypes: ['scene_summary'] } },
    { key: 'character_memory', label: '角色记忆', payload: { ...base, memoryTypes: ['character_memory'] } },
    { key: 'plot_thread_summary', label: '剧情线程', payload: { ...base, memoryTypes: ['plot_thread_summary'] } },
  ]
}

async function runCompareMode() {
  const q = searchQuery.value.trim()
  if (!q) {
    alert('请先输入检索词')
    return
  }
  compareLoading.value = true
  errorMessage.value = ''
  activeTab.value = 'search'
  try {
    const groups = buildComparePayloads()
    const results = await Promise.all(groups.map(async (group) => {
      const { list } = await searchRag(q, group.payload)
      return {
        key: group.key,
        label: group.label,
        payload: group.payload,
        list,
      }
    }))
    compareResults.value = results
    searchQueryPayload.value = searchQueryPayload.value || buildSearchPayload()
    if (!searchResult.value.length && results[0]?.list?.length) {
      searchResult.value = results[0].list
      syncSearchSelection()
    }
  } catch (e) {
    console.error(e)
    errorMessage.value = e.message || String(e)
    compareResults.value = []
  } finally {
    compareLoading.value = false
  }
}

function generateRegressionCaseText() {
  const payload = searchQueryPayload.value || buildSearchPayload()
  const queryText = payload.q || payload.queryText || searchQuery.value.trim()
  const selected = selectedSearchResult.value || searchResult.value[0] || null
  const assertions = []
  assertions.push(`1. 请求应成功，返回结果数 >= ${Math.min(topK.value, 1)}`)
  if (payload.memoryTypes?.length) {
    assertions.push(`2. 结果应优先命中记忆层：${payload.memoryTypes.join(', ')}`)
  }
  if (payload.characters?.length) {
    assertions.push(`3. 首条结果 metadata.characters 应包含：${payload.characters.join(', ')}`)
  }
  if (payload.location) {
    assertions.push(`4. 首条结果 metadata.location 应包含：${payload.location}`)
  }
  if (payload.plotThread) {
    assertions.push(`5. 首条结果 metadata.plotThreads 或 plotThreadIds 应关联：${payload.plotThread}`)
  }
  if (payload.explain) {
    assertions.push('6. 返回结果应包含 explain.similarityScore / recencyScore / finalScore')
  }
  if (selected?.metadata?.memoryType) {
    assertions.push(`7. 当前人工确认样本的 memoryType 为：${selected.metadata.memoryType}`)
  }

  return [
    '【RAG 回归测试用例】',
    `名称：${queryText || '未命名检索用例'}`,
    '目标：验证当前检索参数下的召回稳定性、结构化过滤和 explain 输出。',
    '',
    '请求参数：',
    safeJson({
      ...payload,
      q: queryText,
    }),
    '',
    '人工确认样本：',
    selected ? safeJson({
      memoryType: selected.metadata?.memoryType || '',
      chapterTitle: selected.metadata?.chapterTitle || '',
      sceneTitle: selected.metadata?.sceneTitle || '',
      location: selected.metadata?.location || '',
      characters: selected.metadata?.characters || '',
      plotThreads: selected.metadata?.plotThreads || '',
      finalScore: selected.finalScore ?? selected.score ?? null,
    }) : '当前没有命中样本，可先执行检索后复制。',
    '',
    '期望断言：',
    ...assertions,
  ].join('\n')
}

async function copySearchPayload() {
  const payload = searchQueryPayload.value || buildSearchPayload()
  await copyText(safeJson(payload), '已复制检索参数 JSON')
}

async function copyRegressionCase() {
  if (!searchQuery.value.trim()) {
    alert('请先输入检索词并执行检索')
    return
  }
  await copyText(generateRegressionCaseText(), '已复制回归测试用例')
}

function matchesFilterValue(expected, actual) {
  if (!expected) return false
  const expectedText = normalizeText(expected)
  if (!expectedText) return false
  return splitMetaValue(actual).some((item) => normalizeText(item).includes(expectedText))
}

function buildHitExplainItems(result) {
  if (!result) return []
  const meta = result.metadata || {}
  const items = []
  if (searchMemoryType.value) {
    items.push({
      label: '记忆层过滤',
      matched: normalizeText(meta.memoryType) === normalizeText(searchMemoryType.value),
      detail: `期望 ${memoryLabel(searchMemoryType.value)}，实际 ${memoryLabel(meta.memoryType)}`,
    })
  }
  if (searchCharacter.value.trim()) {
    items.push({
      label: '人物过滤',
      matched: matchesFilterValue(searchCharacter.value, meta.characters),
      detail: `期望人物 ${searchCharacter.value.trim()}，实际 ${meta.characters || '无人物 metadata'}`,
    })
  }
  if (searchLocation.value.trim()) {
    items.push({
      label: '地点过滤',
      matched: matchesFilterValue(searchLocation.value, meta.location),
      detail: `期望地点 ${searchLocation.value.trim()}，实际 ${meta.location || '无地点 metadata'}`,
    })
  }
  if (searchPlotThread.value.trim()) {
    const actualThread = meta.plotThreads || meta.plotThreadIds || ''
    items.push({
      label: '剧情线程过滤',
      matched: matchesFilterValue(searchPlotThread.value, actualThread),
      detail: `期望线程 ${searchPlotThread.value.trim()}，实际 ${actualThread || '无线程 metadata'}`,
    })
  }
  if (chapterFrom.value || chapterTo.value) {
    const chapterNum = Number(meta.chapterNumber)
    const from = chapterFrom.value ? Number(chapterFrom.value) : null
    const to = chapterTo.value ? Number(chapterTo.value) : null
    const matched = !Number.isNaN(chapterNum)
      && (from == null || chapterNum >= from)
      && (to == null || chapterNum <= to)
    items.push({
      label: '章节范围过滤',
      matched,
      detail: `期望 ${from || '-'} ~ ${to || '-'}，实际章节 ${meta.chapterNumber || '未知'}`,
    })
  }
  if (result.explain) {
    items.push({
      label: '排序解释',
      matched: true,
      detail: `相似度 ${scoreStr(result.explain.similarityScore)} / 时间衰减 ${scoreStr(result.explain.recencyScore)} / 最终分 ${scoreStr(result.explain.finalScore)}`,
    })
  }
  return items
}

function buildHitExplainTags(result) {
  const items = buildHitExplainItems(result)
  return items.filter((item) => item.matched).map((item) => item.label)
}

async function copySelectedDocumentMetadata() {
  if (!selectedDocument.value?.metadata) return
  await copyText(safeJson(selectedDocument.value.metadata), '已复制文档 metadata')
}

function openDocumentModal() {
  if (!selectedDocument.value) return
  detailModal.value = {
    visible: true,
    mode: 'document',
    title: memoryLabel(selectedDocument.value.metadata?.memoryType),
    subtitle: `${shortId(selectedDocument.value.id)} ${selectedDocument.value.metadata?.chapterTitle || ''}`.trim(),
    content: selectedDocument.value.content || selectedDocument.value.contentSummary || '',
    metadata: selectedDocument.value.metadata || {},
    explain: null,
  }
}

function openSearchResultModal() {
  if (!selectedSearchResult.value) return
  detailModal.value = {
    visible: true,
    mode: 'search',
    title: memoryLabel(selectedSearchResult.value.metadata?.memoryType),
    subtitle: `${selectedSearchResult.value.metadata?.chapterTitle || ''} ${selectedSearchResult.value.metadata?.sceneTitle ? `/ ${selectedSearchResult.value.metadata.sceneTitle}` : ''}`.trim(),
    content: selectedSearchResult.value.content || '',
    metadata: selectedSearchResult.value.metadata || {},
    explain: selectedSearchResult.value.explain || null,
  }
}

function openCompareGroupTopHit(group) {
  if (!group?.list?.length) return
  const first = group.list[0]
  detailModal.value = {
    visible: true,
    mode: 'search',
    title: `${group.label} / ${memoryLabel(first.metadata?.memoryType)}`,
    subtitle: `${first.metadata?.chapterTitle || ''} ${first.metadata?.sceneTitle ? `/ ${first.metadata.sceneTitle}` : ''}`.trim(),
    content: first.content || '',
    metadata: first.metadata || {},
    explain: first.explain || null,
  }
}

function retryWithoutPlotThread() {
  searchPlotThread.value = ''
  doSearch()
}

function retryWithoutLocation() {
  searchLocation.value = ''
  doSearch()
}

function retryKeepOnlyCharacter() {
  searchLocation.value = ''
  searchPlotThread.value = ''
  chapterFrom.value = ''
  chapterTo.value = ''
  doSearch()
}

function switchToCoreCompare() {
  compareFilterMode.value = 'core'
  runCompareMode()
}

function closeDetailModal() {
  detailModal.value.visible = false
}

async function copyModalContent() {
  await copyText(detailModal.value.content || '', '已复制全文内容')
}

watch(workspaceStore.currentNovelId, (id) => {
  if (id && !novelId.value) {
    novelId.value = id
    loadDocs()
  }
})

onMounted(async () => {
  await workspaceStore.initWorkspace()
  if (workspaceStore.currentNovelId.value) {
    novelId.value = workspaceStore.currentNovelId.value
  }
  loadDocs()
})
</script>

<style scoped>
.rag-page {
  min-height: 100vh;
  padding: 24px;
  background: var(--page-bg);
  color: var(--text-primary);
}

.hero,
.summary-card,
.panel,
.list-panel,
.detail-panel,
.regression-panel,
.detail-modal {
  background: var(--panel-bg);
  border: 1px solid var(--border);
  border-radius: var(--panel-radius);
  box-shadow: var(--panel-shadow);
}

.hero {
  padding: 20px 24px;
  margin-bottom: 16px;
  display: grid;
  gap: 14px;
}

.eyebrow {
  margin: 0 0 6px;
  color: var(--primary);
  font-size: 0.85rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero h1 {
  margin: 0;
  font-size: 1.9rem;
}

.hero-desc {
  margin: 8px 0 0;
  color: var(--text-muted);
}

.hero-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.summary-card {
  padding: 16px;
  display: grid;
  gap: 6px;
}

.summary-card strong {
  font-size: 1.5rem;
  color: var(--text-primary);
}

.summary-label {
  color: var(--primary);
  font-size: 0.85rem;
}

.summary-card small,
.current-tip,
.panel-subtitle,
.detail-subtitle {
  color: var(--text-muted);
}

.workspace {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 16px;
}

.sidebar {
  display: grid;
  gap: 16px;
  align-content: start;
}

.panel,
.list-panel,
.detail-panel,
.regression-panel {
  padding: 16px;
}

.panel-head,
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.panel-head h3,
.detail-section h4,
.detail-section h5 {
  margin: 0;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.field {
  display: grid;
  gap: 6px;
}

.field span {
  font-size: 0.85rem;
  color: var(--text-muted);
}

.field-full {
  grid-column: 1 / -1;
}

.field-checkbox {
  align-content: end;
}

.field input,
.field select,
.field textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--input-border);
  border-radius: 10px;
  background: var(--panel-bg);
  color: var(--text-primary);
}

.field textarea {
  resize: vertical;
  min-height: 84px;
}

.field-checkbox input {
  width: 18px;
  height: 18px;
  padding: 0;
}

.panel-actions,
.quick-tags,
.chips,
.detail-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.panel-actions {
  margin-top: 12px;
}

.panel-actions.compact {
  margin-top: 0;
}

.quick-tags {
  margin-top: 12px;
}

.btn-primary,
.btn-secondary,
.btn-ghost,
.btn-del,
.link-btn,
.tab,
.tag-btn {
  border: none;
  cursor: pointer;
  transition: 0.2s ease;
}

.btn-primary,
.btn-secondary,
.btn-ghost {
  padding: 10px 14px;
  border-radius: 10px;
  font-weight: 600;
}

.btn-primary {
  background: var(--primary);
  color: #fff;
}

.btn-primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn-secondary {
  background: var(--secondary-bg);
  color: var(--secondary-text);
}

.btn-ghost,
.tag-btn,
.tab {
  background: var(--meta-bg);
  color: var(--text-primary);
  border: 1px solid var(--input-border);
}

.btn-del {
  padding: 6px 10px;
  border-radius: 10px;
  background: var(--danger-bg);
  color: var(--danger-text);
  font-size: 0.82rem;
}

.link-btn {
  background: transparent;
  color: var(--primary);
  padding: 0;
}

.tag-btn {
  padding: 8px 10px;
  border-radius: 999px;
  font-size: 0.82rem;
}

.tag-btn.active,
.tab.active {
  background: var(--primary);
  color: #fff;
  border-color: transparent;
}

.toolbar {
  margin-bottom: 14px;
  align-items: flex-start;
  flex-direction: column;
}

.regression-panel {
  margin-bottom: 16px;
}

.compare-panel {
  margin-bottom: 16px;
  padding: 16px;
  background: var(--panel-bg);
  border: 1px solid var(--border);
  border-radius: var(--panel-radius);
}

.diagnosis-panel {
  margin-bottom: 16px;
  padding: 16px;
  background: var(--panel-bg);
  border: 1px solid var(--danger-bg);
  border-radius: var(--panel-radius);
}

.snapshot-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.snapshot-card {
  padding: 12px;
  border-radius: 12px;
  background: var(--meta-bg);
  border: 1px solid var(--border);
  display: grid;
  gap: 6px;
}

.snapshot-card strong {
  color: var(--text-primary);
  font-size: 1rem;
  word-break: break-word;
}

.snapshot-code {
  margin: 0;
  max-height: 280px;
  overflow: auto;
  white-space: pre-wrap;
  line-height: 1.55;
  background: var(--panel-bg);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 12px;
  color: var(--text-primary);
}

.compare-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.diagnosis-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.compare-card {
  padding: 12px;
  border-radius: 14px;
  background: var(--meta-bg);
  border: 1px solid var(--border);
  display: grid;
  gap: 10px;
}

.compare-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.compare-list {
  display: grid;
  gap: 8px;
}

.compare-item {
  padding: 10px;
  border-radius: 10px;
  background: var(--panel-bg);
  border: 1px solid var(--border);
}

.compare-empty {
  padding: 12px 0;
}

.diagnosis-card {
  padding: 12px;
  border-radius: 12px;
  background: var(--meta-bg);
  border: 1px solid var(--danger-bg);
  display: grid;
  gap: 6px;
}

.diagnosis-card strong {
  color: var(--text-primary);
  word-break: break-word;
}

.tabs {
  display: flex;
  gap: 8px;
}

.tab {
  padding: 10px 14px;
  border-radius: 999px;
  font-weight: 600;
}

.chips {
  min-height: 28px;
}

.chip,
.memory-pill {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 0.82rem;
}

.chip {
  background: var(--badge-bg);
  color: var(--badge-text);
}

.chip.muted {
  background: var(--secondary-bg);
  color: var(--text-muted);
}

.memory-pill {
  background: #dcfce7;
  color: #16a34a;
}

.content-area {
  min-width: 0;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(320px, 0.85fr);
  gap: 16px;
}

.table-wrap {
  overflow: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.92rem;
}

th,
td {
  padding: 12px 10px;
  text-align: left;
  border-bottom: 1px solid var(--border);
}

th {
  color: var(--primary);
  background: var(--meta-bg);
  position: sticky;
  top: 0;
}

.clickable-row {
  cursor: pointer;
}

.clickable-row:hover,
.clickable-row.selected {
  background: var(--active-row-bg);
}

.content-cell {
  max-width: 340px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pagination {
  margin-top: 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.result-list {
  display: grid;
  gap: 10px;
}

.result-card {
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 14px;
  background: var(--panel-bg);
  cursor: pointer;
}

.result-card.selected,
.result-card:hover {
  border-color: var(--primary);
  background: var(--active-row-bg);
}

.result-top,
.meta-row,
.score-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.result-title {
  margin-left: 8px;
}

.result-source,
.result-excerpt {
  margin: 8px 0 0;
}

.meta-row {
  margin-top: 8px;
  flex-wrap: wrap;
  justify-content: flex-start;
  color: var(--badge-text);
  font-size: 0.85rem;
}

.detail-panel {
  min-width: 0;
}

.detail-content {
  display: grid;
  gap: 16px;
}

.detail-actions {
  justify-content: flex-start;
}

.hit-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.hit-tag {
  display: inline-flex;
  align-items: center;
  padding: 4px 8px;
  border-radius: 999px;
  background: var(--badge-bg);
  border: 1px solid var(--primary);
  color: var(--badge-text);
  font-size: 0.78rem;
}

.explain-list {
  display: grid;
  gap: 10px;
}

.explain-item {
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--meta-bg);
  border: 1px solid var(--border);
}

.explain-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 6px;
}

.explain-item p {
  margin: 0;
  color: var(--text-primary);
  line-height: 1.5;
}

.explain-state {
  font-size: 0.78rem;
  padding: 3px 8px;
  border-radius: 999px;
  border: 1px solid transparent;
}

.explain-state.matched {
  color: #16a34a;
  background: #dcfce7;
  border-color: #86efac;
}

.explain-state.missed {
  color: var(--danger-text);
  background: var(--danger-bg);
  border-color: #fecaca;
}

.compact-btn {
  padding: 6px 10px;
  border-radius: 8px;
  font-size: 0.8rem;
}

.detail-section {
  display: grid;
  gap: 8px;
}

.preview-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
  background: var(--meta-bg);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 12px;
  color: var(--text-primary);
}

.kv-list {
  display: grid;
  gap: 8px;
}

.kv-item {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--meta-bg);
}

.kv-item span {
  color: var(--text-muted);
  font-size: 0.82rem;
}

.score {
  color: #16a34a;
}

.score-bars {
  display: grid;
  gap: 10px;
}

.score-row {
  gap: 12px;
}

.score-row span {
  width: 68px;
  color: var(--text-muted);
  flex-shrink: 0;
}

.score-row strong {
  width: 60px;
  text-align: right;
  flex-shrink: 0;
}

.bar-track {
  flex: 1;
  height: 10px;
  border-radius: 999px;
  background: var(--secondary-bg);
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 999px;
}

.bar-fill.similarity {
  background: var(--primary);
}

.bar-fill.recency {
  background: #8b5cf6;
}

.bar-fill.final {
  background: #22c55e;
}

.empty,
.search-empty,
.detail-empty {
  color: var(--text-muted);
}

.search-empty,
.detail-empty {
  padding: 24px 0;
}

.error-banner {
  margin: 0 0 14px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--danger-text);
  background: var(--danger-bg);
  color: var(--danger-text);
}

.success-banner {
  margin: 0 0 14px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid #22c55e;
  background: #dcfce7;
  color: #16a34a;
}

.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  z-index: 40;
}

.detail-modal {
  width: min(1100px, 100%);
  max-height: min(88vh, 920px);
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  overflow: hidden;
}

.modal-header {
  padding: 18px 20px;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.modal-eyebrow {
  margin-bottom: 4px;
}

.modal-body {
  overflow: auto;
  padding: 18px 20px 20px;
  display: grid;
  gap: 16px;
}

.modal-text {
  max-height: none;
  min-height: 220px;
}

@media (max-width: 1280px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .workspace,
  .content-grid {
    grid-template-columns: 1fr;
  }

  .snapshot-grid {
    grid-template-columns: 1fr;
  }

  .diagnosis-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .rag-page {
    padding: 12px;
  }

  .summary-grid,
  .form-grid {
    grid-template-columns: 1fr;
  }

  .hero-actions,
  .pagination,
  .panel-actions,
  .modal-header {
    flex-direction: column;
    align-items: stretch;
  }

  .modal-mask {
    padding: 12px;
  }
}
</style>
