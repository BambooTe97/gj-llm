<script setup lang="ts">
import { ref } from 'vue'

const emit = defineEmits<{
  send: [content: string]
}>()

const inputText = ref('')
const sending = ref(false)

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || sending.value) return

  sending.value = true
  inputText.value = ''
  await emit('send', text)
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
    <div class="chat-input__inner">
      <el-input
        v-model="inputText"
        type="textarea"
        :rows="3"
        placeholder="输入消息，Enter 发送，Shift+Enter 换行"
        resize="none"
        :disabled="sending"
        @keydown="handleKeydown"
      />
      <el-button
        type="primary"
        :disabled="!inputText.trim() || sending"
        :loading="sending"
        @click="handleSend"
      >
        发送
      </el-button>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.chat-input {
  padding: 16px 24px;
  background-color: #fff;
  border-top: 1px solid #e4e7ed;
}

.chat-input__inner {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  max-width: 900px;
  margin: 0 auto;

  :deep(.el-textarea__inner) {
    font-size: 14px;
    line-height: 1.6;
  }

  .el-button {
    flex-shrink: 0;
  }
}
</style>
