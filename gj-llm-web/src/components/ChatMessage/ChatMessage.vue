<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import type { ChatMessage } from '@/api/types'
import { renderMarkdown } from '@/utils/markdown'

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

/** 等待阶段：流式已开始，但还没收到任何 thinking / content（展示跳动三点，避免空白） */
const isWaitingPhase = computed(
  () => !!props.streaming && !hasContent.value && !thinkingText.value
)

/**
 * 思考阶段：流式传输中、有思考内容、但正文还没来。
 * 此时只展示思考框（自动展开），不展示空的白色正文框。
 */
const isThinkingPhase = computed(
  () => !!props.streaming && !!thinkingText.value && !hasContent.value
)

/** 思考阶段自动展开；正文一到就收起 */
watch(isThinkingPhase, (val) => {
  if (val) {
    thinkingExpanded.value = true
  } else if (hasContent.value) {
    thinkingExpanded.value = false
  }
})

// ===== 助手正文 Markdown 渲染 =====
// 流式时 content 逐 token 追加，每次都全量 re-parse Markdown 很浪费，
// 这里按 RENDER_THROTTLE_MS 节流重渲染（80ms 一帧足够顺滑）；
// 非流式（历史消息 / 终态）直接同步渲染，首屏无闪烁。
const renderedHtml = ref('')
const RENDER_THROTTLE_MS = 80
let pendingTimer: number | null = null
let lastRenderAt = 0

function renderNow() {
  renderedHtml.value = renderMarkdown(props.message.content || '', {
    streaming: !!props.streaming,
  })
}

watch(
  () => props.message.content,
  () => {
    if (!props.streaming) {
      if (pendingTimer !== null) {
        clearTimeout(pendingTimer)
        pendingTimer = null
      }
      renderNow()
      return
    }
    const elapsed = performance.now() - lastRenderAt
    if (elapsed >= RENDER_THROTTLE_MS) {
      lastRenderAt = performance.now()
      renderNow()
    } else if (pendingTimer === null) {
      pendingTimer = window.setTimeout(() => {
        pendingTimer = null
        lastRenderAt = performance.now()
        renderNow()
      }, RENDER_THROTTLE_MS - elapsed)
    }
  },
  { immediate: true },
)

/** 流结束 -> 立即终渲染（此时语法已完整，不再做流式兼容处理） */
watch(
  () => props.streaming,
  (val) => {
    if (!val) {
      if (pendingTimer !== null) {
        clearTimeout(pendingTimer)
        pendingTimer = null
      }
      renderNow()
    }
  },
)

onBeforeUnmount(() => {
  if (pendingTimer !== null) clearTimeout(pendingTimer)
})

/** 代码块复制按钮：v-html 内容无法直接绑定事件，在容器上事件委托 */
async function onContentClick(e: MouseEvent) {
  const target = e.target as HTMLElement
  const btn = target.closest('[data-code-copy]') as HTMLButtonElement | null
  if (!btn) return
  const text = btn.closest('.md-code')?.querySelector('code')?.textContent ?? ''
  if (!text) return

  try {
    await navigator.clipboard.writeText(text)
  } catch {
    // 剪贴板 API 不可用（如非安全上下文）时回退 execCommand
    const ta = document.createElement('textarea')
    ta.value = text
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
  }
  btn.textContent = '已复制 ✓'
  setTimeout(() => {
    btn.textContent = '复制'
  }, 1500)
}
</script>

<template>
  <div class="chat-message" :class="[`chat-message--${message.role}`]">
    <div class="chat-message__body">
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

      <!-- 等待阶段：流式已开始但还没收到任何 token，展示跳动三点避免空白 -->
      <div v-if="isWaitingPhase" class="chat-message__waiting">
        <span class="thinking-dots"><span></span><span></span><span></span></span>
        <span class="chat-message__waiting-text">正在思考…</span>
      </div>

      <!-- 正文 -- 仅在非思考阶段且有内容时展示；助手消息渲染 Markdown，用户消息保持纯文本 -->
      <div
        v-if="!isThinkingPhase && (hasContent || !streaming)"
        class="chat-message__content"
        :class="{ streaming: streaming }"
        @click="onContentClick"
      >
        <!-- 内容已经过 DOMPurify 消毒（utils/markdown.ts），可安全 v-html -->
        <!-- eslint-disable-next-line vue/no-v-html -->
        <div v-if="message.role === 'assistant'" class="md-body" v-html="renderedHtml"></div>
        <template v-else>{{ message.content }}</template>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.chat-message {
  display: flex;
  margin-bottom: 24px;

  &--user {
    justify-content: flex-end;
    .chat-message__content {
      background: linear-gradient(135deg, #0071e3, #3395ff);
      color: #fff;
      border-radius: 16px 6px 16px 16px;
      box-shadow: 0 4px 16px rgba(0, 113, 227, 0.3);
    }
    .chat-message__body {
      align-items: flex-end;
    }
  }

  &--assistant {
    justify-content: flex-start;
    .chat-message__content {
      background: rgba(255, 255, 255, 0.7);
      backdrop-filter: blur(12px);
      -webkit-backdrop-filter: blur(12px);
      border: 1px solid rgba(255, 255, 255, 0.4);
      border-radius: 6px 16px 16px 16px;
      box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
    }
    .chat-message__body {
      align-items: flex-start;
    }
  }
}

.chat-message__body {
  max-width: 70%;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

/* ====== 等待阶段（跳动三点） ====== */
.chat-message__waiting {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.4);
  border-radius: 6px 16px 16px 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.chat-message__waiting-text {
  font-size: 13px;
  color: #86868b;
}

.thinking-dots {
  display: inline-flex;
  gap: 4px;
  align-items: center;

  span {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: #0071e3;
    opacity: 0.4;
    animation: dotBounce 1.2s infinite ease-in-out;
  }

  span:nth-child(2) {
    animation-delay: 0.15s;
  }
  span:nth-child(3) {
    animation-delay: 0.3s;
  }
}

@keyframes dotBounce {
  0%, 80%, 100% {
    transform: scale(0.6);
    opacity: 0.4;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

/* ====== 思考框 ====== */
.chat-message__think {
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
.chat-message__content {
  padding: 12px 16px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
  color: #1d1d1f;
}

/* 流式光标：挂在渲染后正文的最后一个元素末尾（段落场景下紧跟文字） */
.chat-message__content.streaming .md-body > :last-child::after {
  content: '';
  display: inline-block;
  width: 2px;
  height: 1em;
  margin-left: 2px;
  vertical-align: text-bottom;
  background: #0071e3;
  animation: blink 1s infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
</style>

<style lang="scss">
/* ====== 暗色模式气泡适配 ====== */
/* 不 scoped：html.dark 根类在组件作用域之外；类名本组件独有，无泄漏风险。
   与 markdown.scss 的暗色代码主题配套，否则深色代码块会压在白色气泡上。 */
html.dark {
  .chat-message--assistant .chat-message__content {
    background: rgba(30, 32, 48, 0.78);
    border-color: rgba(255, 255, 255, 0.1);
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.3);
  }

  .chat-message__waiting {
    background: rgba(30, 32, 48, 0.78);
    border-color: rgba(255, 255, 255, 0.1);
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.3);
  }

  .chat-message__waiting-text {
    color: #9aa4b2;
  }

  .thinking-dots span {
    background: #58a6ff;
  }

  .chat-message__think {
    background: rgba(255, 255, 255, 0.05);
    border-color: rgba(255, 255, 255, 0.1);

    &:hover {
      background: rgba(255, 255, 255, 0.08);
    }
  }

  .chat-message__think-header {
    color: #9aa4b2;
  }

  .chat-message__think-body {
    color: #a5b4c8;
  }

  .chat-message__content.streaming .md-body > :last-child::after {
    background: #58a6ff;
  }
}
</style>
