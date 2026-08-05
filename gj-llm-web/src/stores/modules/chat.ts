import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { ChatMessage } from '@/api/types'
import { chatApi } from '@/api/modules/chat'

/** 单个对话的运行时状态（按 conversationId 分桶，互不干扰） */
interface ChatSession {
  messages: ChatMessage[]
  streaming: boolean
  currentAssistantMsg: string
  thinking: string
  references: Array<{ rank: number; content: string; score: number }>
  /** 是否已加载过历史消息（幂等守卫，避免覆盖流式中的本地状态） */
  loaded: boolean
  abortController: AbortController | null
  /** 流式内部暂存量：done 事件返回的真实消息 ID */
  streamingMessageId: string
  /** 流式内部暂存量：done 事件返回的完整 thinking */
  streamingThinking: string
}

function createEmptySession(): ChatSession {
  return {
    messages: [],
    streaming: false,
    currentAssistantMsg: '',
    thinking: '',
    references: [],
    loaded: false,
    abortController: null,
    streamingMessageId: '',
    streamingThinking: '',
  }
}

export const useChatStore = defineStore('chat', () => {
  /** 按对话 ID 隔离的状态桶 */
  const sessions = ref<Record<string, ChatSession>>({})
  /** 当前激活的对话 ID（null 表示新建对话的空白态） */
  const activeId = ref<string | null>(null)

  /** 获取某个对话的桶（不存在则懒创建） */
  function getSession(id: string): ChatSession {
    if (!sessions.value[id]) {
      sessions.value[id] = createEmptySession()
    }
    return sessions.value[id]
  }

  /** 空白态用的稳定空对象（activeId 为 null 时返回，避免模板判空；不在此对象上做写入） */
  const emptySession = createEmptySession()

  /** 当前激活的 session（可能为空态） */
  const activeSession = computed<ChatSession>(() => {
    if (activeId.value && sessions.value[activeId.value]) {
      return sessions.value[activeId.value]
    }
    return emptySession
  })

  // ===== 对外暴露的响应式状态（均读取 active session，模板用法不变） =====
  const messages = computed(() => activeSession.value.messages)
  const streaming = computed(() => activeSession.value.streaming)
  const currentAssistantMsg = computed(() => activeSession.value.currentAssistantMsg)
  const thinking = computed(() => activeSession.value.thinking)
  const references = computed(() => activeSession.value.references)

  /** 切换激活对话；null 表示进入新建对话空白态（不销毁其它桶） */
  function setActive(id: string | null) {
    activeId.value = id
    if (id) getSession(id) // 懒创建
  }

  /** 该对话是否已加载过历史 */
  function isLoaded(id: string): boolean {
    return !!sessions.value[id]?.loaded
  }

  function setMessages(list: ChatMessage[]) {
    if (!activeId.value) return
    const s = getSession(activeId.value)
    s.messages = list
  }

  function addMessage(msg: ChatMessage) {
    if (!msg.conversationId) return
    getSession(msg.conversationId).messages.push(msg)
  }

  /** 清空激活对话的状态（保留其它对话） */
  function clearMessages() {
    if (!activeId.value) return
    const s = sessions.value[activeId.value]
    if (!s) return
    Object.assign(s, createEmptySession())
  }

  function setStreaming(val: boolean) {
    if (!activeId.value) return
    getSession(activeId.value).streaming = val
  }

  function appendStreamContent(text: string) {
    if (!activeId.value) return
    getSession(activeId.value).currentAssistantMsg += text
  }

  function setReferences(refs: Array<{ rank: number; content: string; score: number }>) {
    if (!activeId.value) return
    getSession(activeId.value).references = refs
  }

  /** 把流式输出提交为一条正式的助手消息 */
  function commitStreamMessage(conversationId: string) {
    const s = getSession(conversationId)
    const msg: ChatMessage = {
      id: s.streamingMessageId || Date.now().toString(),
      conversationId,
      role: 'assistant',
      content: s.currentAssistantMsg,
      thinking: s.streamingThinking || s.thinking || undefined,
      createdAt: new Date().toISOString(),
    }
    s.messages.push(msg)
    s.currentAssistantMsg = ''
    s.thinking = ''
    s.streamingThinking = ''
    s.streamingMessageId = ''
    s.references = []
  }

  /** 中止流式请求（默认中止激活对话；可指定 convId 中止后台对话） */
  function abortStream(convId?: string) {
    const id = convId ?? activeId.value
    if (!id) return
    const s = sessions.value[id]
    if (!s) return
    s.abortController?.abort()
    s.streaming = false
  }

  /** 流式发送消息（始终操作 convId 自己的桶，切走对话也不受影响） */
  async function sendMessageStream(
    conversationId: string,
    content: string,
    datasetId?: string,
    enableThinking?: boolean,
  ) {
    const s = getSession(conversationId)
    // 标记为已加载，避免后续 watcher 触发 loadHistory 覆盖本地状态
    s.loaded = true

    // 添加用户消息
    s.messages.push({
      id: Date.now().toString(),
      conversationId,
      role: 'user',
      content,
      createdAt: new Date().toISOString(),
    })

    s.streaming = true
    s.currentAssistantMsg = ''
    s.thinking = ''
    s.streamingThinking = ''
    s.streamingMessageId = ''
    s.references = []
    s.abortController = new AbortController()

    try {
      const response = await fetch(
        `${import.meta.env.VITE_API_BASE_URL}/v1/chat/send/stream`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${localStorage.getItem('ACCESS_TOKEN')}`,
          },
          body: JSON.stringify({ conversationId, content, datasetId, enableThinking }),
          signal: s.abortController.signal,
        },
      )

      if (!response.ok) {
        // 认证失败 -> 跳转登录页
        if (response.status === 401 || response.status === 403) {
          localStorage.removeItem('ACCESS_TOKEN')
          const redirect = encodeURIComponent(window.location.pathname + window.location.search)
          window.location.href = `/login?redirect=${redirect}`
          return
        }
        throw new Error(`HTTP ${response.status}`)
      }

      const reader = response.body?.getReader()
      if (!reader) throw new Error('No reader')

      const decoder = new TextDecoder()
      let buffer = ''

      let done = false
      while (!done) {
        const result = await reader.read()
        done = result.done
        if (done) break
        const { value } = result

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('data:')) {
            const jsonStr = line.slice(5).trim()
            if (!jsonStr || jsonStr === '[DONE]') continue
            try {
              const event = JSON.parse(jsonStr)
              switch (event.type) {
                case 'thinking':
                  if (event.content) {
                    s.thinking = event.content
                  }
                  break
                case 'references':
                  if (event.items) {
                    s.references = event.items
                  }
                  break
                case 'content':
                  if (event.content) {
                    s.currentAssistantMsg += event.content
                  }
                  break
                case 'done':
                  // 流结束，记录后端返回的真实消息 ID 和 thinking
                  if (event.messageId) {
                    s.streamingMessageId = String(event.messageId)
                  }
                  if (event.thinking) {
                    s.streamingThinking = event.thinking
                  }
                  break
                case 'error':
                  console.error('SSE error:', event.message)
                  s.thinking = ''
                  break
              }
            } catch {
              // 非 JSON 则忽略
            }
          }
        }
      }

      // 流结束后提交消息（thinking 在 done 事件中已暂存到 streamingThinking）
      if (s.currentAssistantMsg) {
        commitStreamMessage(conversationId)
      }
    } catch (e: unknown) {
      if (e instanceof DOMException && e.name === 'AbortError') {
        // 用户主动停止：提交已生成的部分内容
        if (s.currentAssistantMsg) {
          commitStreamMessage(conversationId)
        }
        return
      }
      console.error('流式请求失败:', e)
      s.thinking = ''
      // 异常情况也保留已接收的内容
      if (s.currentAssistantMsg) {
        commitStreamMessage(conversationId)
      }
    } finally {
      s.streaming = false
      s.abortController = null
    }
  }

  /** 加载历史消息（幂等：已加载则跳过，流式中的对话不会被覆盖） */
  async function loadHistory(conversationId: string) {
    const s = getSession(conversationId)
    if (s.loaded) return // 幂等守卫
    try {
      const res = await chatApi.getMessages(conversationId)
      s.messages = res.data.data || []
    } catch {
      s.messages = []
    } finally {
      s.loaded = true
    }
  }

  return {
    // 状态
    sessions,
    activeId,
    messages,
    streaming,
    currentAssistantMsg,
    thinking,
    references,
    // 方法
    setActive,
    isLoaded,
    setMessages,
    addMessage,
    clearMessages,
    setStreaming,
    appendStreamContent,
    setReferences,
    commitStreamMessage,
    abortStream,
    sendMessageStream,
    loadHistory,
  }
})
