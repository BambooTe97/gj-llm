import { useChatStore } from '@/stores/modules/chat'
import { useConversationStore } from '@/stores/modules/conversation'

/**
 * 对话组合式函数 -- 封装流式发送消息的完整流程。
 *
 * 注意：当前 ChatView 直接调用 store，未使用此 composable；保留以供复用，
 * 签名与 store 保持一致（datasetId 为 string、enableThinking 默认开启）。
 */
export function useChat() {
  const chatStore = useChatStore()
  const conversationStore = useConversationStore()

  /** 流式发送消息 */
  async function send(text: string, datasetId?: string, enableThinking = true) {
    if (!text.trim()) return

    let convId = conversationStore.currentId
    if (!convId) {
      const conv = await conversationStore.create(undefined, datasetId)
      if (!conv) return
      convId = String(conv.id)
    }

    chatStore.setActive(convId)
    await chatStore.sendMessageStream(convId, text, datasetId, enableThinking)

    // 刷新会话列表（更新标题等）
    await conversationStore.fetchList()
  }

  return { send }
}
