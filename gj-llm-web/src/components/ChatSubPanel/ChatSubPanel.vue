<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useConversationStore } from '@/stores/modules/conversation'
import { useChatStore } from '@/stores/modules/chat'
import { Plus, ChatDotRound, Delete, ChatLineSquare } from '@element-plus/icons-vue'

const router = useRouter()
const conversationStore = useConversationStore()
const chatStore = useChatStore()

/** 正在重命名的会话 ID（null 表示无） */
const renamingId = ref<string | null>(null)
const renameTitle = ref('')
let renameInputRef: HTMLInputElement | null = null

function handleNewChat() {
  chatStore.clearMessages()
  conversationStore.setCurrentId(null)
  router.push('/chat')
}

function handleSelect(id: string) {
  if (renamingId.value) return  // 重命名中不切换
  conversationStore.setCurrentId(id)
  router.push(`/chat/${id}`)
}

function handleDelete(id: string) {
  conversationStore.remove(id)
}

/** 双击标题进入重命名模式 */
function startRename(id: string, currentTitle: string) {
  renamingId.value = id
  renameTitle.value = currentTitle || '新对话'
  nextTick(() => {
    renameInputRef?.focus()
    renameInputRef?.select()
  })
}

/** 确认重命名 */
async function confirmRename() {
  const id = renamingId.value
  const title = renameTitle.value.trim()
  if (id && title) {
    await conversationStore.rename(id, title)
  }
  renamingId.value = null
  renameTitle.value = ''
}

/** 取消重命名 */
function cancelRename() {
  renamingId.value = null
  renameTitle.value = ''
}

/** 重命名输入框按键处理 */
function handleRenameKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter') {
    e.preventDefault()
    confirmRename()
  } else if (e.key === 'Escape') {
    e.preventDefault()
    cancelRename()
  }
}
</script>

<template>
  <div class="chat-sub-panel">
    <!-- 新建对话 -->
    <div class="sub-panel-action">
      <button class="new-chat-btn" @click="handleNewChat">
        <el-icon :size="16"><Plus /></el-icon>
        <span>新建对话</span>
      </button>
    </div>

    <!-- 对话列表 -->
    <div class="sub-panel-list">
      <div class="sub-panel-section">
        <div class="sub-panel-section__label">对话</div>
        <TransitionGroup name="conv-list">
          <div
            v-for="(item, index) in conversationStore.list"
            :key="item.id"
            class="sub-panel-list__item"
            :class="{ active: item.id === conversationStore.currentId }"
            :style="{ '--delay': index * 0.04 + 's' }"
            @click="handleSelect(item.id)"
          >
            <span class="sub-panel-list__icon">
              <el-icon :size="16"><ChatDotRound /></el-icon>
            </span>
            <!-- 重命名输入框 -->
            <input
              v-if="renamingId === item.id"
              v-model="renameTitle"
              class="sub-panel-list__rename-input"
              @keydown="handleRenameKeydown"
              @blur="confirmRename"
              @click.stop
              :ref="(el: any) => { if (renamingId === item.id) renameInputRef = el as HTMLInputElement }"
            />
            <!-- 普通标题 -->
            <span
              v-else
              class="sub-panel-list__title"
              @dblclick.stop="startRename(item.id, item.title || '新对话')"
              :title="'双击重命名'"
            >{{ item.title || '新对话' }}</span>
            <el-button
              text
              size="small"
              class="sub-panel-list__delete"
              @click.stop="handleDelete(item.id)"
            >
              <el-icon :size="14"><Delete /></el-icon>
            </el-button>
          </div>
        </TransitionGroup>

        <div v-if="conversationStore.list.length === 0" class="sub-panel-empty">
          <el-icon :size="32"><ChatLineSquare /></el-icon>
          <span>暂无对话</span>
          <span class="sub-panel-empty__hint">点击上方按钮开始</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
// Apple 调色板
$text-primary: #1d1d1f;
$text-secondary: #6e6e73;
$text-tertiary: #aeaeb2;
$blue-accent: #007aff;
$blue-bg: rgba(0, 122, 255, 0.1);
$hover-bg: rgba(0, 0, 0, 0.04);
$active-bg: rgba(0, 122, 255, 0.1);
$border-color: rgba(0, 0, 0, 0.06);
$item-radius: 9px;
$icon-size: 32px;

.chat-sub-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 260px;
  // 3D 水滴玻璃面板
  background:
    linear-gradient(180deg, rgba(255,255,255,0.55) 0%, rgba(255,255,255,0.22) 35%, rgba(255,255,255,0.12) 100%),
    rgba(238, 238, 243, 0.35);
  backdrop-filter: blur(40px) saturate(200%);
  -webkit-backdrop-filter: blur(40px) saturate(200%);
  border-right: 0.5px solid rgba(255, 255, 255, 0.55);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.65),
    inset 0 -1px 0 rgba(0, 0, 0, 0.04),
    4px 0 28px rgba(0, 0, 0, 0.07);
  color: $text-primary;
}

// ========================= 新建按钮区 =========================
.sub-panel-action {
  padding: 14px 12px;
  border-bottom: 0.5px solid rgba(0, 0, 0, 0.06);
}

.new-chat-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 36px;
  padding: 0 12px;
  border: none;
  border-radius: 9px;
  font-size: 13.5px;
  font-weight: 500;
  font-family: inherit;
  color: $text-primary;
  background: rgba(0, 0, 0, 0.04);
  cursor: pointer;
  transition:
    background 0.18s ease,
    transform 0.15s ease,
    box-shadow 0.18s ease;

  &:hover {
    background: rgba(0, 0, 0, 0.07);
  }

  &:active {
    transform: scale(0.97);
    background: rgba(0, 0, 0, 0.1);
  }
}

// ========================= 列表区域 =========================
.sub-panel-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 10px;

  &::-webkit-scrollbar {
    width: 4px;
  }
  &::-webkit-scrollbar-thumb {
    background-color: rgba(0, 0, 0, 0.12);
    border-radius: 2px;
  }
}

// ========================= 分组 =========================
.sub-panel-section {
  margin-bottom: 6px;

  &__label {
    font-size: 11px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    color: $text-tertiary;
    padding: 8px 12px 6px;
    user-select: none;
  }
}

// ========================= 列表项 =========================
.sub-panel-list__item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border-radius: $item-radius;
  cursor: pointer;
  font-size: 13.5px;
  font-weight: 450;
  margin-bottom: 1px;
  color: $text-primary;
  position: relative;
  transition:
    background 0.18s ease,
    transform 0.18s ease;
  animation: itemEnter 0.35s cubic-bezier(0.34, 1.56, 0.64, 1) both;
  animation-delay: var(--delay, 0s);

  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 50%;
    transform: translateY(-50%) scaleY(0);
    width: 3px;
    height: 18px;
    border-radius: 0 3px 3px 0;
    background: $blue-accent;
    transition: transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
  }

  &:hover {
    background: $hover-bg;

    .sub-panel-list__delete {
      opacity: 1;
      transform: scale(1);
    }
  }

  &:active {
    transform: scale(0.97);
    transition: transform 0.1s ease;
  }

  &.active {
    background: $active-bg;

    &::before {
      transform: translateY(-50%) scaleY(1);
    }

    .sub-panel-list__icon {
      background: rgba(0, 122, 255, 0.16);
      color: $blue-accent;
      box-shadow: 0 2px 6px rgba(0, 122, 255, 0.14);
    }

    .sub-panel-list__title {
      font-weight: 560;
    }
  }
}

// ========================= 图标容器 =========================
.sub-panel-list__icon {
  width: $icon-size;
  height: $icon-size;
  border-radius: 7.5px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: rgba(0, 0, 0, 0.04);
  color: $text-secondary;
  transition:
    background 0.2s ease,
    color 0.2s ease,
    box-shadow 0.2s ease;
}

// ========================= 标题 =========================
.sub-panel-list__title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  user-select: none;
}

// ========================= 重命名输入框 =========================
.sub-panel-list__rename-input {
  flex: 1;
  height: 24px;
  padding: 0 6px;
  border: 1.5px solid $blue-accent;
  border-radius: 5px;
  font-size: 13px;
  font-family: inherit;
  font-weight: 450;
  color: $text-primary;
  background: rgba(255, 255, 255, 0.8);
  outline: none;
  box-shadow: 0 0 0 3px rgba(0, 122, 255, 0.12);
}

// ========================= 删除按钮 =========================
.sub-panel-list__delete {
  opacity: 0;
  color: $text-tertiary !important;
  padding: 4px !important;
  border-radius: 6px;
  transform: scale(0.8);
  transition:
    opacity 0.18s ease,
    color 0.18s ease,
    transform 0.18s cubic-bezier(0.34, 1.56, 0.64, 1),
    background 0.18s ease;

  &:hover {
    color: #ff3b30 !important;
    background: rgba(255, 59, 48, 0.08) !important;
    transform: scale(1.08);
  }

  &:active {
    transform: scale(0.93);
  }
}

// ========================= 空状态 =========================
.sub-panel-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 32px 16px;
  color: $text-tertiary;
  font-size: 13px;
  text-align: center;
  animation: fadeIn 0.5s ease both;

  &__hint {
    font-size: 11px;
    color: rgba(0, 0, 0, 0.18);
  }
}

// ========================= 动画关键帧 =========================
@keyframes itemEnter {
  from {
    opacity: 0;
    transform: translateX(-14px) scale(0.94);
  }
  to {
    opacity: 1;
    transform: translateX(0) scale(1);
  }
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(6px); }
  to   { opacity: 1; transform: translateY(0); }
}

// ========================= TransitionGroup 动画 =========================
.conv-list-enter-active {
  transition:
    opacity 0.35s cubic-bezier(0.34, 1.56, 0.64, 1),
    transform 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.conv-list-leave-active {
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}

.conv-list-enter-from {
  opacity: 0;
  transform: translateX(-16px) scale(0.9);
}

.conv-list-leave-to {
  opacity: 0;
  transform: translateX(-10px) scale(0.88);
}

.conv-list-move {
  transition: transform 0.3s cubic-bezier(0.25, 0.1, 0.25, 1);
}
</style>
