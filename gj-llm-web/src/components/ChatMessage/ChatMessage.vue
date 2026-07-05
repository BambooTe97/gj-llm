<script setup lang="ts">
import type { ChatMessage } from '@/api/types'

defineProps<{
  message: ChatMessage
  streaming?: boolean
}>()
</script>

<template>
  <div class="chat-message" :class="[`chat-message--${message.role}`]">
    <div class="chat-message__avatar">
      <el-avatar v-if="message.role === 'user'" :size="36" icon="UserFilled" />
      <el-avatar v-else :size="36" style="background-color: #409eff" icon="Cpu" />
    </div>
    <div class="chat-message__body">
      <div class="chat-message__role">
        {{ message.role === 'user' ? '我' : 'GJ-LLM' }}
      </div>
      <div class="chat-message__content" :class="{ streaming: streaming }">
        {{ message.content }}
        <span v-if="streaming" class="cursor-blink">|</span>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.chat-message {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;

  &--assistant {
    .chat-message__content {
      background-color: #fff;
      border-radius: 4px 16px 16px 16px;
    }
  }

  &--user {
    flex-direction: row-reverse;
    .chat-message__content {
      background-color: #409eff;
      color: #fff;
      border-radius: 16px 4px 16px 16px;
    }
    .chat-message__role {
      text-align: right;
    }
  }
}

.chat-message__body {
  max-width: 70%;
  min-width: 120px;
}

.chat-message__role {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.chat-message__content {
  padding: 12px 16px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;

  &.streaming {
    background-color: #fff;
    border-radius: 4px 16px 16px 16px;
  }
}

.cursor-blink {
  animation: blink 1s infinite;
}

@keyframes blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0;
  }
}
</style>
