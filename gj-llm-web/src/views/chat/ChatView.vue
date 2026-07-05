<script setup lang="ts">
import { useChatStore } from '@/stores/modules/chat'
import { useConversationStore } from '@/stores/modules/conversation'
import ChatMessage from '@/components/ChatMessage/ChatMessage.vue'
import ChatInput from '@/components/ChatInput/ChatInput.vue'

const chatStore = useChatStore()
const conversationStore = useConversationStore()

async function handleSend(content: string) {
  if (!conversationStore.currentId) {
    await conversationStore.create()
  }
  if (conversationStore.currentId) {
    await chatStore.sendMessage(conversationStore.currentId, content)
  }
}
</script>

<template>
  <div class="chat-view">
    <!-- 消息列表 -->
    <div class="chat-messages" v-if="chatStore.messages.length > 0">
      <ChatMessage
        v-for="msg in chatStore.messages"
        :key="msg.id"
        :message="msg"
      />
      <!-- 流式输出中的临时消息 -->
      <ChatMessage
        v-if="chatStore.streaming && chatStore.currentAssistantMsg"
        :message="{
          id: 'streaming',
          conversationId: conversationStore.currentId || '',
          role: 'assistant',
          content: chatStore.currentAssistantMsg,
          createdAt: '',
        }"
        :streaming="true"
      />
    </div>

    <!-- 空状态 -->
    <div class="chat-empty" v-else>
      <el-icon :size="64" color="#c0c4cc"><ChatDotRound /></el-icon>
      <p>开始一段新的对话</p>
    </div>

    <!-- 输入区域 -->
    <ChatInput @send="handleSend" />
  </div>
</template>

<style lang="scss" scoped>
.chat-view {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.chat-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: #909399;
  font-size: 16px;
}
</style>
