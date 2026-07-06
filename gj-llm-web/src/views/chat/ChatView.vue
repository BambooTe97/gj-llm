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
      <div class="chat-empty__icon">
        <el-icon :size="48"><ChatDotRound /></el-icon>
      </div>
      <p class="chat-empty__text">开始一段新的对话</p>
      <p class="chat-empty__hint">在下方输入消息，Enter 发送</p>
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

  &::-webkit-scrollbar {
    width: 5px;
  }
  &::-webkit-scrollbar-thumb {
    background-color: rgba(0, 0, 0, 0.1);
    border-radius: 3px;
  }
}

.chat-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;

  &__icon {
    width: 80px;
    height: 80px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.6);
    backdrop-filter: blur(16px);
    -webkit-backdrop-filter: blur(16px);
    border: 1px solid rgba(255, 255, 255, 0.4);
    display: flex;
    align-items: center;
    justify-content: center;
    color: #86868b;
    margin-bottom: 8px;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.04);
  }

  &__text {
    font-size: 16px;
    color: #515154;
    font-weight: 500;
  }

  &__hint {
    font-size: 13px;
    color: #aeaeb2;
  }
}
</style>
