<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ChatMessage } from '@/api/types'
import { UserFilled, Cpu } from '@element-plus/icons-vue'

const props = defineProps<{
  message: ChatMessage
  streaming?: boolean
  /** 流式传输中的思考内容（仅在 streaming 模式下使用） */
  streamingThinking?: string
}>()

const thinkingExpanded = ref(false)

/** 有效的思考内容：优先来自 message.thinking（历史消息），其次来自 streamingThinking（流式中） */
const thinkingText = computed(() =>
  (props.message.thinking || props.streamingThinking || '').trim()
)

/** 是否有正文内容 */
const hasContent = computed(() => !!props.message.content?.trim())

/**
 * 思考阶段：流式传输中、有思考内容、但正文还没来。
 * 此时只展示思考框（自动展开），不展示空的白色正文框。
 */
const isThinkingPhase = computed(() =>
  props.streaming && !!thinkingText.value && !hasContent.value
)

/** 思考阶段自动展开；正文一到就收起 */
watch(isThinkingPhase, (val) => {
  if (val) {
    thinkingExpanded.value = true
  } else if (hasContent.value) {
    thinkingExpanded.value = false
  }
})
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

      <!-- 思考过程（有内容才显示；思考阶段自动展开，正文到了自动收起） -->
      <div
        v-if="thinkingText"
        class="chat-message__think"
        :class="{ expanded: thinkingExpanded, pulsing: isThinkingPhase }"
        @click="thinkingExpanded = !thinkingExpanded"
      >
        <div class="chat-message__think-header">
          <span>{{ isThinkingPhase ? '🧠 正在思考…' : '💭 思考过程' }}</span>
          <span class="chat-message__think-toggle">{{ thinkingExpanded ? '收起 ▲' : '展开 ▼' }}</span>
        </div>
        <div class="chat-message__think-body">{{ thinkingText }}</div>
      </div>

      <!-- 正文 —— 仅在非思考阶段且有内容时展示 -->
      <div
        v-if="!isThinkingPhase && (hasContent || !streaming)"
        class="chat-message__content"
        :class="{ streaming: streaming }"
      >
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

/* ====== 思考框 ====== */
.chat-message__think {
  margin-bottom: 6px;
  background: #f5f5f7;
  border: 1px solid #e5e5ea;
  border-radius: 10px;
  font-size: 12px;
  cursor: pointer;
  user-select: none;
  overflow: hidden;
  transition: background 0.15s;

  &:hover {
    background: #eeeef0;
  }

  /* 思考中：边框呼吸效果 */
  &.pulsing {
    border-color: #0071e3;
    animation: thinkPulse 2s ease-in-out infinite;
  }
}

@keyframes thinkPulse {
  0%, 100% { border-color: rgba(0, 113, 227, 0.3); }
  50%      { border-color: rgba(0, 113, 227, 0.7); }
}

.chat-message__think-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  color: #86868b;
  font-weight: 500;
}

.chat-message__think-toggle {
  font-size: 11px;
  color: #0071e3;
  opacity: 0.7;
}

.chat-message__think-body {
  display: none;
  padding: 0 10px 8px 12px;
  color: #6e6e73;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 120px;
  overflow-y: auto;

  .chat-message__think.expanded & {
    display: block;
  }
}

/* ====== 正文 ====== */
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
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
</style>
