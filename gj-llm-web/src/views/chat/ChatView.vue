<script setup lang="ts">
import { watch, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useChatStore } from '@/stores/modules/chat'
import { useConversationStore } from '@/stores/modules/conversation'
import { datasetApi } from '@/api/modules/dataset'
import type { Dataset } from '@/api/types'
import { ChatDotRound } from '@element-plus/icons-vue'
import ChatMessage from '@/components/ChatMessage/ChatMessage.vue'
import ChatInput from '@/components/ChatInput/ChatInput.vue'

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()
const conversationStore = useConversationStore()

/** 知识库列表（用于选择器） */
const datasets = ref<Dataset[]>([])
const selectedDatasetId = ref<string | undefined>()

onMounted(async () => {
  // 加载知识库列表
  try {
    const res = await datasetApi.getList(1, 100)
    datasets.value = res.data.data.records || []
  } catch {
    datasets.value = []
  }

  // 初始化：加载会话列表
  await conversationStore.fetchList()
})

/** 路由参数变化时切换会话 & 加载历史消息 */
watch(
  () => route.params.id,
  async (newId) => {
    if (newId) {
      const convIdStr = String(newId)
      conversationStore.setCurrentId(convIdStr)
      // 仅当消息不属于当前会话时才从后端加载历史（避免覆盖 handleSend 中已添加的用户消息）
      const alreadyHasMessages = chatStore.messages.length > 0
        && String(chatStore.messages[0].conversationId) === convIdStr
      if (!alreadyHasMessages) {
        await chatStore.loadHistory(convIdStr)
      }
      // 恢复关联的知识库
      const conv = conversationStore.list.find((c) => String(c.id) === convIdStr)
      selectedDatasetId.value = conv?.datasetId != null ? String(conv.datasetId) : undefined
    } else {
      chatStore.clearMessages()
      selectedDatasetId.value = undefined
    }
  },
  { immediate: true },
)

/** 自动滚动到底部（新消息 / 思考中 / 流式输出变化时触发） */
watch(
  () => [chatStore.messages.length, chatStore.currentAssistantMsg, chatStore.thinking],
  () => {
    const el = document.querySelector('.chat-messages')
    if (el) {
      el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
    }
  },
)

/** 发送消息 */
async function handleSend(content: string) {
  // 确保有会话
  if (!conversationStore.currentId) {
    const conv = await conversationStore.create(undefined, selectedDatasetId.value)
    if (!conv) return
    router.push(`/chat/${conv.id}`)
  }

  const convId = conversationStore.currentId
  if (!convId) return

  await chatStore.sendMessageStream(convId, content, selectedDatasetId.value)

  // 刷新会话列表（更新标题等）
  await conversationStore.fetchList()
}

/** 切换知识库 */
function handleDatasetChange(datasetId: string | undefined) {
  selectedDatasetId.value = datasetId
}
</script>

<template>
  <div class="chat-view">
    <!-- 知识库选择器 -->
    <div class="chat-toolbar" v-if="datasets.length > 0">
      <span class="chat-toolbar__label">知识库：</span>
      <el-select
        v-model="selectedDatasetId"
        placeholder="通用对话（不限制知识库）"
        clearable
        size="small"
        style="width: 240px"
        @change="handleDatasetChange"
      >
        <el-option
          v-for="ds in datasets"
          :key="ds.id"
          :label="ds.name"
          :value="ds.id"
        />
      </el-select>
    </div>

    <!-- 消息列表 -->
    <div class="chat-messages" v-if="chatStore.messages.length > 0 || chatStore.streaming">
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
          createdAt: new Date().toISOString(),
        }"
        :streaming="true"
      />
      <!-- 思考中指示器 -->
      <div class="chat-thinking" v-if="chatStore.streaming && !chatStore.currentAssistantMsg">
        <div class="chat-thinking__dots">
          <span class="chat-thinking__dot" />
          <span class="chat-thinking__dot" />
          <span class="chat-thinking__dot" />
        </div>
        <span class="chat-thinking__text">{{ chatStore.thinking || '正在思考...' }}</span>
      </div>
      <!-- 引用来源 -->
      <div class="chat-references" v-if="chatStore.references.length > 0 && !chatStore.streaming">
        <div class="chat-references__title">📎 参考来源</div>
        <div
          class="chat-references__item"
          v-for="ref in chatStore.references"
          :key="ref.rank"
        >
          <span class="chat-references__rank">#{{ ref.rank }}</span>
          <span class="chat-references__score">{{ ref.score }}</span>
          <p class="chat-references__content">{{ ref.content }}...</p>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div class="chat-empty" v-else>
      <div class="chat-empty__icon">
        <el-icon :size="48"><ChatDotRound /></el-icon>
      </div>
      <p class="chat-empty__text">开始一段新的对话</p>
      <p class="chat-empty__hint">选择知识库可获得更精准的回答，Enter 发送消息</p>
    </div>

    <!-- 输入区域 -->
    <ChatInput @send="handleSend" :disabled="chatStore.streaming" />
  </div>
</template>

<style lang="scss" scoped>
.chat-view {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.chat-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  background: rgba(255, 255, 255, 0.4);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(210, 210, 215, 0.3);

  &__label {
    font-size: 13px;
    color: #515154;
    font-weight: 500;
    white-space: nowrap;
  }
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

.chat-references {
  margin: 0 0 16px 48px;
  padding: 12px 16px;
  background: rgba(245, 245, 247, 0.8);
  border-radius: 10px;
  border: 1px solid rgba(210, 210, 215, 0.4);
  font-size: 12px;

  &__title {
    font-weight: 600;
    color: #515154;
    margin-bottom: 8px;
  }

  &__item {
    display: flex;
    gap: 8px;
    align-items: flex-start;
    margin-bottom: 6px;
    line-height: 1.5;
  }

  &__rank {
    flex-shrink: 0;
    font-weight: 600;
    color: #0071e3;
    min-width: 20px;
  }

  &__score {
    flex-shrink: 0;
    color: #aeaeb2;
    font-size: 11px;
    min-width: 36px;
  }

  &__content {
    margin: 0;
    color: #86868b;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.chat-thinking {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 24px;
  padding-left: 48px;

  &__dots {
    display: flex;
    gap: 5px;
    align-items: center;
  }

  &__dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: #007aff;
    animation: thinkingBounce 1.4s ease-in-out infinite both;

    &:nth-child(1) { animation-delay: 0s; }
    &:nth-child(2) { animation-delay: 0.2s; }
    &:nth-child(3) { animation-delay: 0.4s; }
  }

  &__text {
    font-size: 13px;
    color: #86868b;
    font-weight: 450;
  }
}

@keyframes thinkingBounce {
  0%, 80%, 100% {
    transform: scale(0.6);
    opacity: 0.4;
  }
  40% {
    transform: scale(1);
    opacity: 1;
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
