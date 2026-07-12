<script setup lang="ts">
import { ref } from 'vue'

export interface ChatInputControls {
  datasetId?: string
  enableThinking: boolean
}

const props = defineProps<{
  disabled?: boolean
  datasets?: Array<{ id: string; name: string }>
  selectedDatasetId?: string
  enableThinking?: boolean
}>()

const emit = defineEmits<{
  send: [content: string, controls: ChatInputControls]
  stop: []
  'update:selectedDatasetId': [value: string | undefined]
  'update:enableThinking': [value: boolean]
}>()

const inputText = ref('')
const sending = ref(false)

function onDatasetChange(value: string | undefined) {
  emit('update:selectedDatasetId', value || undefined)
}

function onThinkingToggle(value: boolean) {
  emit('update:enableThinking', value)
}

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || sending.value || props.disabled) return

  sending.value = true
  inputText.value = ''
  await emit('send', text, {
    datasetId: props.selectedDatasetId,
    enableThinking: props.enableThinking ?? true,
  })
  sending.value = false
}

function handleKeydown(e: Event | KeyboardEvent) {
  const ke = e as KeyboardEvent
  if (ke.key === 'Enter' && !ke.shiftKey) {
    ke.preventDefault()
    handleSend()
  }
}
</script>

<template>
  <div class="chat-input">
    <div class="chat-input__box">
      <textarea
        v-model="inputText"
        class="chat-input__textarea"
        placeholder="输入消息，Enter 发送，Shift+Enter 换行"
        :disabled="sending"
        rows="2"
        @keydown="handleKeydown"
      />
      <div class="chat-input__bar">
        <!-- 左侧：知识库 + 思考开关 -->
        <div class="chat-input__bar-left">
          <div class="chat-input__ctl" v-if="datasets && datasets.length > 0">
            <el-select
              :model-value="selectedDatasetId"
              placeholder="知识库"
              clearable
              size="small"
              style="width: 110px"
              @change="onDatasetChange"
            >
              <el-option v-for="ds in datasets" :key="ds.id" :label="ds.name" :value="ds.id" />
            </el-select>
          </div>
          <button
            class="chat-input__think-btn"
            :class="{ active: enableThinking ?? true }"
            @click="onThinkingToggle(!(enableThinking ?? true))"
            title="开启后模型会先推理再回答，回答更严谨但稍慢"
          >
            <svg v-if="enableThinking ?? true" width="13" height="13" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
            </svg>
            <span>深度思考</span>
          </button>
        </div>
        <!-- 右侧：发送 / 停止按钮 -->
        <button
          v-if="!disabled"
          class="chat-input__send"
          :disabled="!inputText.trim() || sending"
          @click="handleSend"
          title="发送 (Enter)"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="22" y1="2" x2="11" y2="13" />
            <polygon points="22 2 15 22 11 13 2 9 22 2" />
          </svg>
        </button>
        <button
          v-else
          class="chat-input__stop"
          @click="emit('stop')"
          title="停止生成"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
            <rect x="4" y="4" width="16" height="16" rx="3" />
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.chat-input {
  padding: 12px 24px 16px;
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-top: 1px solid rgba(210, 210, 215, 0.4);
}

.chat-input__box {
  max-width: 900px;
  margin: 0 auto;
  background: #fff;
  border: 1px solid #d2d2d7;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: border-color 0.2s, box-shadow 0.2s;

  &:focus-within {
    border-color: #0071e3;
    box-shadow: 0 2px 16px rgba(0, 113, 227, 0.12);
  }
}

.chat-input__textarea {
  width: 100%;
  border: none;
  outline: none;
  resize: none;
  padding: 12px 14px 8px;
  font-size: 14px;
  font-family: inherit;
  line-height: 1.6;
  color: #1d1d1f;
  background: transparent;
  box-sizing: border-box;

  &::placeholder {
    color: #aeaeb2;
  }

  &:disabled {
    opacity: 0.5;
  }
}

.chat-input__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px 8px 12px;
}

.chat-input__bar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.chat-input__ctl {
  display: flex;
  align-items: center;
}

/* ====== 深度思考按钮 ====== */
.chat-input__think-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border: 1px solid #d2d2d7;
  border-radius: 14px;
  background: #fff;
  color: #86868b;
  font-size: 12px;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
  user-select: none;
  line-height: 1.4;

  svg {
    color: #86868b;
    transition: color 0.2s;
  }

  &:hover {
    border-color: #aeaeb2;
    color: #515154;
  }

  /* 开启状态 */
  &.active {
    background: linear-gradient(135deg, #0071e3, #4d9ff7);
    border-color: transparent;
    color: #fff;
    box-shadow: 0 2px 8px rgba(0, 113, 227, 0.3);

    svg {
      color: #ffd60a;
    }
  }
}

.chat-input__send {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 50%;
  background: #0071e3;
  color: #fff;
  cursor: pointer;
  transition: background 0.15s, transform 0.15s, opacity 0.15s;
  flex-shrink: 0;

  &:hover:not(:disabled) {
    background: #0077ed;
    transform: scale(1.06);
  }

  &:active:not(:disabled) {
    transform: scale(0.96);
  }

  &:disabled {
    background: #d2d2d7;
    cursor: not-allowed;
    opacity: 0.5;
  }
}

/* ====== 停止按钮 ====== */
.chat-input__stop {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1.5px solid #d2d2d7;
  border-radius: 50%;
  background: #f5f5f7;
  color: #86868b;
  cursor: pointer;
  transition: background 0.15s, transform 0.15s;
  flex-shrink: 0;

  &:hover {
    background: #e5e5ea;
    color: #515154;
    transform: scale(1.06);
  }

  &:active {
    transform: scale(0.96);
  }
}
</style>
