<script setup lang="ts">
import type { ChatMessage } from '@/api/types'
import { UserFilled, Cpu } from '@element-plus/icons-vue'

defineProps<{
  message: ChatMessage
  streaming?: boolean
}>()
</script>

<template>
  <div class="chat-message" :class="[`chat-message--${message.role}`]">
    <div class="chat-message__avatar">
      <el-avatar v-if="message.role === 'user'" :size="36" :icon="UserFilled" />
      <el-avatar v-else :size="36" style="background: linear-gradient(135deg, #0071e3, #4d9ff7)" :icon="Cpu" />
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
      background: rgba(255, 255, 255, 0.7);
      backdrop-filter: blur(12px);
      -webkit-backdrop-filter: blur(12px);
      border: 1px solid rgba(255, 255, 255, 0.4);
      border-radius: 6px 16px 16px 16px;
      box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
    }
  }

  &--user {
    flex-direction: row-reverse;
    .chat-message__content {
      background: linear-gradient(135deg, #0071e3, #3395ff);
      color: #fff;
      border-radius: 16px 6px 16px 16px;
      box-shadow: 0 4px 16px rgba(0, 113, 227, 0.3);
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
  color: #86868b;
  margin-bottom: 4px;
  font-weight: 500;
}

.chat-message__content {
  padding: 12px 16px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;

  &.streaming {
    background: rgba(255, 255, 255, 0.7);
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);
    border: 1px solid rgba(255, 255, 255, 0.4);
    border-radius: 6px 16px 16px 16px;
    color: #1d1d1f;
  }
}

.cursor-blink {
  animation: blink 1s infinite;
  color: #0071e3;
  font-weight: 300;
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
