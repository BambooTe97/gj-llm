import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ChatMessage } from '@/api/types'
import { chatApi } from '@/api/modules/chat'

export const useChatStore = defineStore('chat', () => {
  const messages = ref<ChatMessage[]>([])
  const streaming = ref(false)
  const currentAssistantMsg = ref('')
  const thinking = ref('')
  const references = ref<Array<{ rank: number; content: string; score: number }>>([])
  let abortController: AbortController | null = null

  function setMessages(list: ChatMessage[]) {
    messages.value = list
  }

  function addMessage(msg: ChatMessage) {
    messages.value.push(msg)
  }

  function clearMessages() {
    messages.value = []
    currentAssistantMsg.value = ''
    thinking.value = ''
    references.value = []
  }

  function setStreaming(val: boolean) {
    streaming.value = val
  }

  function appendStreamContent(text: string) {
    currentAssistantMsg.value += text
  }

  function setReferences(refs: Array<{ rank: number; content: string; score: number }>) {
    references.value = refs
  }

  function commitStreamMessage(conversationId: string) {
    const msg: ChatMessage = {
      id: Date.now().toString(),
      conversationId,
      role: 'assistant',
      content: currentAssistantMsg.value,
      createdAt: new Date().toISOString(),
    }
    messages.value.push(msg)
    currentAssistantMsg.value = ''
    references.value = []
  }

  /** 中止流式请求 */
  function abortStream() {
    abortController?.abort()
    streaming.value = false
  }

  /** 流式发送消息 */
  async function sendMessageStream(
    conversationId: string,
    content: string,
    datasetId?: string,
  ) {
    // 添加用户消息
    addMessage({
      id: Date.now().toString(),
      conversationId,
      role: 'user',
      content,
      createdAt: new Date().toISOString(),
    })

    streaming.value = true
    currentAssistantMsg.value = ''
    thinking.value = '正在思考...'
    references.value = []
    abortController = new AbortController()

    try {
      const response = await fetch(
        `${import.meta.env.VITE_API_BASE_URL}/v1/chat/send/stream`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${localStorage.getItem('ACCESS_TOKEN')}`,
          },
          body: JSON.stringify({ conversationId, content, datasetId }),
          signal: abortController.signal,
        },
      )

      if (!response.ok) {
        // 认证失败 → 跳转登录页
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
                    thinking.value = event.content
                  }
                  break
                case 'references':
                  if (event.items) {
                    references.value = event.items
                  }
                  break
                case 'content':
                  // 首段内容到达时清除思考状态
                  thinking.value = ''
                  if (event.content) {
                    currentAssistantMsg.value += event.content
                  }
                  break
                case 'done':
                  // 流结束，提交完整消息
                  break
                case 'error':
                  console.error('SSE error:', event.message)
                  thinking.value = ''
                  break
              }
            } catch {
              // 非 JSON 则忽略
            }
          }
        }
      }

      // 流结束后提交消息
      thinking.value = ''
      if (currentAssistantMsg.value) {
        commitStreamMessage(conversationId)
      }
    } catch (e: unknown) {
      if (e instanceof DOMException && e.name === 'AbortError') return
      console.error('流式请求失败:', e)
      thinking.value = ''
      // 保留已接收的内容作为消息
      if (currentAssistantMsg.value) {
        commitStreamMessage(conversationId)
      }
    } finally {
      streaming.value = false
      abortController = null
    }
  }

  /** 加载历史消息 */
  async function loadHistory(conversationId: string) {
    try {
      const res = await chatApi.getMessages(conversationId)
      messages.value = res.data.data || []
    } catch {
      messages.value = []
    }
  }

  return {
    messages,
    streaming,
    currentAssistantMsg,
    thinking,
    references,
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
