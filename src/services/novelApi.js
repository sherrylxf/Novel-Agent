/**
 * Novel Agent API 服务
 */

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8091';

function streamNovelRequest(url, body, onProgress, onComplete, onError) {
  return new Promise((resolve, reject) => {
    fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body || {}),
    })
      .then((response) => {
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        function readStream() {
          reader
            .read()
            .then(({ done, value }) => {
              if (done) {
                resolve();
                return;
              }

              buffer += decoder.decode(value, { stream: true });
              const lines = buffer.split('\n');
              buffer = lines.pop() || ''; // 保留最后一个不完整的行

              for (const line of lines) {
                const trimmedLine = line.trim();
                if (trimmedLine.startsWith('data: ')) {
                  try {
                    const jsonStr = trimmedLine.substring(6).trim();
                    if (jsonStr) {
                      const data = JSON.parse(jsonStr);
                      handleMessage(data);
                    }
                  } catch (e) {
                    console.error('解析SSE消息失败:', e, trimmedLine);
                  }
                }
              }

              readStream();
            })
            .catch((error) => {
              console.error('读取流失败:', error);
              if (onError) {
                onError(error);
              }
              reject(error);
            });
        }

        function handleMessage(data) {
          switch (data.type) {
            case 'progress':
              if (onProgress) {
                onProgress(data);
              }
              break;
            case 'result':
              if (onProgress) {
                onProgress(data);
              }
              break;
            case 'waiting_for_approval':
              // 等待用户确认的消息
              if (onProgress) {
                onProgress(data);
              }
              // 注意：这里不resolve，等待用户确认后继续
              break;
            case 'complete':
              if (onComplete) {
                onComplete(data);
              }
              resolve(data);
              break;
            case 'error':
              const error = new Error(data.content || '未知错误');
              if (onError) {
                onError(error, data);
              }
              reject(error);
              break;
            default:
              console.warn('未知的消息类型:', data.type);
          }
        }

        readStream();
      })
      .catch((error) => {
        console.error('请求失败:', error);
        if (onError) {
          onError(error);
        }
        reject(error);
      });
  });
}

/**
 * 生成小说（SSE流式输出）
 * @param {Object} params - 请求参数
 * @param {string} params.genre - 题材
 * @param {string} params.coreConflict - 核心冲突
 * @param {string} params.worldSetting - 世界观设定
 * @param {string} params.sessionId - 会话ID
 * @param {number} params.maxStep - 最大执行步数（可选）
 * @param {Function} onProgress - 进度回调
 * @param {Function} onComplete - 完成回调
 * @param {Function} onError - 错误回调
 * @returns {Promise} 返回一个可取消的 Promise
 */
export function generateNovel(params, onProgress, onComplete, onError) {
  const { genre, coreConflict, worldSetting, novelId, sessionId, maxStep, targetWordCount } = params;

  const body = {
    genre,
    coreConflict,
    worldSetting,
    novelId,
    sessionId: sessionId || `session_${Date.now()}_${Math.random().toString(36).substring(2, 11)}`,
    maxStep: maxStep || 5,
  };
  if (targetWordCount != null && targetWordCount > 0) {
    body.targetWordCount = targetWordCount;
  }

  return streamNovelRequest(`${API_BASE_URL}/api/v1/novel/generate`, body, onProgress, onComplete, onError)
}

export function continueChapterGeneration(params, onProgress, onComplete, onError) {
  const { novelId, sessionId, continueMode, maxStep } = params
  return streamNovelRequest(`${API_BASE_URL}/api/v1/novel/continue-chapter`, {
    novelId,
    sessionId,
    continueMode,
    maxStep,
  }, onProgress, onComplete, onError)
}

/**
 * 用户确认继续执行
 * @param {string} sessionId - 会话ID
 * @param {boolean} approved - 是否同意继续（默认true）
 * @returns {Promise} 返回确认结果
 */
export async function approveAndContinue(sessionId, approved = true) {
  try {
    const response = await fetch(
      `${API_BASE_URL}/api/v1/novel/approve?sessionId=${encodeURIComponent(sessionId)}&approved=${approved}`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
      }
    );

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const data = await response.json();
    return data;
  } catch (error) {
    console.error('确认请求失败:', error);
    throw error;
  }
}

/**
 * 生成会话ID
 */
export function generateSessionId() {
  return `session_${Date.now()}_${Math.random().toString(36).substring(2, 11)}`;
}

/**
 * 保存小说规划
 * @param {Object} planData - 规划数据
 * @param {string} planData.planId - 规划ID
 * @param {string} planData.novelId - 小说ID
 * @param {number} planData.totalVolumes - 总卷数
 * @param {number} planData.chaptersPerVolume - 每卷章节数
 * @param {string} planData.overallOutline - 整体大纲
 * @param {Array} planData.volumePlans - 卷规划列表
 * @returns {Promise} 返回保存结果
 */
export async function saveNovelPlan(planData) {
  try {
    const response = await fetch(`${API_BASE_URL}/api/v1/novel/plan/save`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(planData),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const data = await response.json();
    return data;
  } catch (error) {
    console.error('保存小说规划失败:', error);
    throw error;
  }
}

/**
 * 根据规划ID查询小说规划
 * @param {string} planId - 规划ID
 * @returns {Promise} 返回规划数据
 */
export async function getNovelPlanByPlanId(planId) {
  try {
    const response = await fetch(`${API_BASE_URL}/api/v1/novel/plan/${encodeURIComponent(planId)}`);

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const data = await response.json();
    return data;
  } catch (error) {
    console.error('查询小说规划失败:', error);
    throw error;
  }
}

/**
 * 根据小说ID查询小说规划
 * @param {string} novelId - 小说ID
 * @returns {Promise} 返回规划数据
 */
export async function getNovelPlanByNovelId(novelId) {
  try {
    const response = await fetch(`${API_BASE_URL}/api/v1/novel/plan/novel/${encodeURIComponent(novelId)}`);

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const data = await response.json();
    return data;
  } catch (error) {
    console.error('查询小说规划失败:', error);
    throw error;
  }
}

/**
 * 更新小说规划
 * @param {Object} planData - 规划数据
 * @returns {Promise} 返回更新结果
 */
export async function updateNovelPlan(planData) {
  try {
    const response = await fetch(`${API_BASE_URL}/api/v1/novel/plan/update`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(planData),
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const data = await response.json();
    return data;
  } catch (error) {
    console.error('更新小说规划失败:', error);
    throw error;
  }
}

async function requestJson(url, options = {}) {
  const response = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    },
    ...options,
  })

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`)
  }

  return await response.json()
}

// ========== 工作台管理 API ==========

export async function listNovelProjects() {
  const data = await requestJson(`${API_BASE_URL}/api/v1/workspace/novels`)
  if (!data.success) throw new Error(data.message || '查询小说列表失败')
  return data.data || []
}

export async function getNovelProject(novelId) {
  const data = await requestJson(`${API_BASE_URL}/api/v1/workspace/novels/${encodeURIComponent(novelId)}`)
  if (!data.success) throw new Error(data.message || '查询小说详情失败')
  return data.data
}

export async function saveNovelProject(payload) {
  const data = await requestJson(`${API_BASE_URL}/api/v1/workspace/novels/save`, {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  })
  if (!data.success) throw new Error(data.message || '保存小说失败')
  return data.data
}

export async function archiveNovelProject(novelId) {
  const data = await requestJson(
    `${API_BASE_URL}/api/v1/workspace/novels/archive?novelId=${encodeURIComponent(novelId)}`,
    { method: 'POST' }
  )
  if (!data.success) throw new Error(data.message || '归档小说失败')
  return data
}

export async function listNovelConfigs(novelId = '', includeGlobal = true) {
  const query = new URLSearchParams({ includeGlobal: String(includeGlobal) })
  if (novelId) query.set('novelId', novelId)
  const data = await requestJson(`${API_BASE_URL}/api/v1/workspace/configs?${query}`)
  if (!data.success) throw new Error(data.message || '查询配置失败')
  return {
    list: data.data || [],
    globalConfigs: data.globalConfigs || [],
    novelConfigs: data.novelConfigs || [],
  }
}

export async function saveNovelConfig(payload) {
  const data = await requestJson(`${API_BASE_URL}/api/v1/workspace/configs/save`, {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  })
  if (!data.success) throw new Error(data.message || '保存配置失败')
  return data.data
}

export async function deleteNovelConfig(configId) {
  const data = await requestJson(`${API_BASE_URL}/api/v1/workspace/configs/${encodeURIComponent(configId)}`, {
    method: 'DELETE',
  })
  if (!data.success) throw new Error(data.message || '删除配置失败')
  return data
}

export async function listNovelChapters(novelId) {
  const data = await requestJson(`${API_BASE_URL}/api/v1/workspace/chapters?novelId=${encodeURIComponent(novelId)}`)
  if (!data.success) throw new Error(data.message || '查询章节失败')
  return data.data || []
}

export async function getNovelChapter(chapterId) {
  const data = await requestJson(`${API_BASE_URL}/api/v1/workspace/chapters/${encodeURIComponent(chapterId)}`)
  if (!data.success) throw new Error(data.message || '查询章节详情失败')
  return data.data
}

export async function saveNovelChapter(payload) {
  const data = await requestJson(`${API_BASE_URL}/api/v1/workspace/chapters/save`, {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  })
  if (!data.success) throw new Error(data.message || '保存章节失败')
  return data.data
}

export async function updateNovelChapter(payload) {
  const data = await requestJson(`${API_BASE_URL}/api/v1/workspace/chapters/update`, {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  })
  if (!data.success) throw new Error(data.message || '更新章节失败')
  return data.data
}

export async function deleteNovelChapter(chapterId) {
  const data = await requestJson(`${API_BASE_URL}/api/v1/workspace/chapters/${encodeURIComponent(chapterId)}`, {
    method: 'DELETE',
  })
  if (!data.success) throw new Error(data.message || '删除章节失败')
  return data
}

// ========== 知识图谱 API（走 Vite 代理 /api -> 后端） ==========

export async function getKgGraph(novelId = '') {
  const url = `${API_BASE_URL}/api/kg/graph?novelId=${encodeURIComponent(novelId)}`;
  const res = await fetch(url);
  let data;
  try {
    data = await res.json();
  } catch {
    throw new Error(res.status === 500 ? '服务端错误，请检查后端日志（Neo4j 是否已配置并启动）' : `请求失败 ${res.status}`);
  }
  if (!res.ok) {
    throw new Error(data?.message || `请求失败 ${res.status}`);
  }
  if (!data.success) throw new Error(data.message || '加载失败');
  return { nodes: data.nodes || [], edges: data.edges || [] };
}

export async function deleteKgCharacter(characterId) {
  const res = await fetch(`${API_BASE_URL}/api/kg/character?characterId=${encodeURIComponent(characterId)}`, { method: 'DELETE' });
  const data = await res.json();
  if (!data.success) throw new Error(data.message || '删除失败');
  return data;
}

export async function deleteKgForeshadowing(foreshadowingId) {
  const res = await fetch(`${API_BASE_URL}/api/kg/foreshadowing?foreshadowingId=${encodeURIComponent(foreshadowingId)}`, { method: 'DELETE' });
  const data = await res.json();
  if (!data.success) throw new Error(data.message || '删除失败');
  return data;
}

// ========== RAG 文档库 API ==========

export async function getRagDocuments(params = {}) {
  const {
    novelId = '',
    chapterId = '',
    memoryType = '',
    character = '',
    plotThread = '',
    page = 1,
    size = 20,
  } = params;
  const q = new URLSearchParams({ page, size });
  if (novelId) q.set('novelId', novelId);
  if (chapterId) q.set('chapterId', chapterId);
  if (memoryType) q.set('memoryType', memoryType);
  if (character) q.set('character', character);
  if (plotThread) q.set('plotThread', plotThread);
  const res = await fetch(`${API_BASE_URL}/api/rag/documents?${q}`);
  const data = await res.json();
  if (!data.success) throw new Error(data.message || '查询失败');
  return { list: data.data || [], total: data.total || 0 };
}

export async function searchRag(q, options = {}, legacyTopK) {
  let normalized = options
  if (typeof options === 'string') {
    normalized = {
      novelId: options,
      topK: legacyTopK || 10,
    }
  }
  const {
    novelId = '',
    topK = 3,
    memoryTypes = [],
    characters = [],
    location = '',
    plotThread = '',
    chapterFrom = '',
    chapterTo = '',
    explain = false,
  } = normalized || {}
  const params = new URLSearchParams({ q, topK })
  if (novelId) params.set('novelId', novelId)
  if (memoryTypes.length) params.set('memoryTypes', memoryTypes.join(','))
  if (characters.length) params.set('characters', characters.join(','))
  if (location) params.set('location', location)
  if (plotThread) params.set('plotThread', plotThread)
  if (chapterFrom !== '' && chapterFrom != null) params.set('chapterFrom', chapterFrom)
  if (chapterTo !== '' && chapterTo != null) params.set('chapterTo', chapterTo)
  if (explain) params.set('explain', 'true')
  const res = await fetch(`${API_BASE_URL}/api/rag/search?${params}`);
  const data = await res.json();
  if (!data.success) throw new Error(data.message || '检索失败');
  return {
    list: data.data || [],
    query: data.query || null,
  };
}

export async function deleteRagDocument(id) {
  const res = await fetch(`${API_BASE_URL}/api/rag/document?id=${encodeURIComponent(id)}`, { method: 'DELETE' });
  const data = await res.json();
  if (!data.success) throw new Error(data.message || '删除失败');
  return data;
}

export async function getRagDocumentDetail(id) {
  const res = await fetch(`${API_BASE_URL}/api/rag/document?id=${encodeURIComponent(id)}`)
  const data = await res.json()
  if (!data.success) throw new Error(data.message || '查询详情失败')
  return data.data || null
}
