import { ref } from 'vue'
import type { SendMessageRequest } from '@/api/types'

export function useStream() {
  const streaming = ref(false)
  const content = ref('')
  const error = ref<string | null>(null)
  let abortController: AbortController | null = null

  async function startStream(data: SendMessageRequest) {
    streaming.value = true
    content.value = ''
    error.value = null
    abortController = new AbortController()

    try {
      const response = await fetch(
        `${import.meta.env.VITE_API_BASE_URL}/chat/send/stream`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${localStorage.getItem('ACCESS_TOKEN')}`,
          },
          body: JSON.stringify(data),
          signal: abortController.signal,
        },
      )

      if (!response.ok) {
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
            if (jsonStr === '[DONE]') continue
            try {
              const parsed = JSON.parse(jsonStr)
              if (parsed.content) {
                content.value += parsed.content
              }
            } catch {
              // 非 JSON 则直接追加
              content.value += jsonStr
            }
          }
        }
      }
    } catch (e: unknown) {
      if (e instanceof DOMException && e.name === 'AbortError') return
      error.value = e instanceof Error ? e.message : '流式响应出错'
    } finally {
      streaming.value = false
      abortController = null
    }
  }

  function stopStream() {
    abortController?.abort()
    streaming.value = false
  }

  return { streaming, content, error, startStream, stopStream }
}
