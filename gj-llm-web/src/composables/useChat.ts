import { useChatStore } from '@/stores/modules/chat'
import { useConversationStore } from '@/stores/modules/conversation'

/**
 * 对话组合式函数 —— 封装流式发送消息的完整流程。
 */
export function useChat() {
  const chatStore = useChatStore()
  const conversationStore = useConversationStore()

  /** 流式发送消息 */
  async function send(text: string, datasetId?: number) {
    if (!text.trim()) return

    let convId = conversationStore.currentId
    if (!convId) {
      const conv = await conversationStore.create(undefined, datasetId)
      if (!conv) return
      convId = conv.id
    }

    await chatStore.sendMessageStream(convId, text, datasetId)

    // 刷新会话列表（更新标题等）
    await conversationStore.fetchList()
  }

  return { send }
}
