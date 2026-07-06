import { useChatStore } from '@/stores/modules/chat'
import { useConversationStore } from '@/stores/modules/conversation'
import { chatApi } from '@/api/modules/chat'

export function useChat() {
  const chatStore = useChatStore()
  const conversationStore = useConversationStore()

  /** 发送消息（非流式） */
  async function send(text: string) {
    if (!text.trim()) return

    let convId = conversationStore.currentId
    if (!convId) {
      const conv = await conversationStore.create()
      if (!conv) return
      convId = conv.id
    }

    // 添加用户消息
    chatStore.addMessage({
      id: Date.now().toString(),
      conversationId: convId,
      role: 'user',
      content: text,
      createdAt: new Date().toISOString(),
    })

    chatStore.setStreaming(true)
    try {
      const res = await chatApi.sendMessage({ conversationId: convId, content: text })
      chatStore.addMessage(res.data.data)
    } finally {
      chatStore.setStreaming(false)
    }
  }

  return { send }
}
