import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ChatMessage } from '@/api/types'
import { chatApi } from '@/api/modules/chat'

export const useChatStore = defineStore('chat', () => {
  const messages = ref<ChatMessage[]>([])
  const streaming = ref(false)
  const currentAssistantMsg = ref('')

  function setMessages(list: ChatMessage[]) {
    messages.value = list
  }

  function addMessage(msg: ChatMessage) {
    messages.value.push(msg)
  }

  function clearMessages() {
    messages.value = []
    currentAssistantMsg.value = ''
  }

  function setStreaming(val: boolean) {
    streaming.value = val
  }

  function appendStreamContent(text: string) {
    currentAssistantMsg.value += text
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
  }

  /** 发送消息（非流式） */
  async function sendMessage(conversationId: string, content: string) {
    addMessage({
      id: Date.now().toString(),
      conversationId,
      role: 'user',
      content,
      createdAt: new Date().toISOString(),
    })

    const res = await chatApi.sendMessage({ conversationId, content })
    addMessage(res.data.data)
  }

  return {
    messages,
    streaming,
    currentAssistantMsg,
    setMessages,
    addMessage,
    clearMessages,
    setStreaming,
    appendStreamContent,
    commitStreamMessage,
    sendMessage,
  }
})
