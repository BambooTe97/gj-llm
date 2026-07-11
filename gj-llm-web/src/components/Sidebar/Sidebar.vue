<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { useAppStore } from '@/stores/modules/app'
import { useConversationStore } from '@/stores/modules/conversation'
import { useChatStore } from '@/stores/modules/chat'

const router = useRouter()
const route = useRoute()
const appStore = useAppStore()
const conversationStore = useConversationStore()
const chatStore = useChatStore()

function handleNewChat() {
  chatStore.clearMessages()
  conversationStore.setCurrentId(null)
  router.push('/chat')
}

function handleSelect(id: number | string) {
  conversationStore.setCurrentId(id)
  router.push(`/chat/${id}`)
}

function handleDelete(id: number | string) {
  conversationStore.remove(id)
}
</script>

<template>
  <div class="sidebar">
    <!-- Logo 区域 -->
    <div class="sidebar-logo">
      <Transition name="logo-swap" mode="out-in">
        <span v-if="!appStore.sidebarCollapsed" key="full" class="sidebar-logo__text">
          <span class="sidebar-logo__gradient">GJ</span>-LLM
        </span>
        <span v-else key="icon" class="sidebar-logo__icon">G</span>
      </Transition>
      <!-- 图钉固定（右上角小图标） -->
      <div
        v-if="!appStore.sidebarCollapsed"
        class="sidebar-logo__pin"
        :class="{ 'is-pinned': appStore.sidebarPinned }"
        @click.stop="appStore.togglePin()"
        title="固定侧边栏"
      >
        <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 2v3.5M12 5.5L9.5 3M12 5.5L14.5 3"/>
          <line x1="12" y1="10" x2="12" y2="22"/>
          <rect x="8" y="5.5" width="8" height="4.5" rx="1"/>
        </svg>
      </div>
    </div>

    <!-- 新建对话 -->
    <div class="sidebar-action">
      <el-button type="primary" style="width: 100%" @click="handleNewChat">
        <el-icon><Plus /></el-icon>
        <span v-if="!appStore.sidebarCollapsed">新建对话</span>
      </el-button>
    </div>

    <!-- 会话列表 -->
    <div class="sidebar-list" v-if="!appStore.sidebarCollapsed">
      <!-- 浏览入口 — 独立分组 -->
      <div class="sidebar-section">
        <div class="sidebar-section__label">浏览</div>
        <div
          class="sidebar-list__item sidebar-list__item--kb"
          :class="{ active: route.path.startsWith('/datasets') }"
          @click="router.push('/datasets')"
        >
          <span class="sidebar-list__icon sidebar-list__icon--kb">
            <el-icon :size="16"><Collection /></el-icon>
          </span>
          <span class="sidebar-list__title">知识库</span>
          <el-icon class="sidebar-list__arrow" :size="14"><ArrowRight /></el-icon>
        </div>
      </div>

      <!-- 对话列表 -->
      <div class="sidebar-section">
        <div class="sidebar-section__label">对话</div>
        <TransitionGroup name="conv-list">
          <div
            v-for="(item, index) in conversationStore.list"
            :key="item.id"
            class="sidebar-list__item"
            :class="{ active: item.id === conversationStore.currentId }"
            :style="{ '--delay': index * 0.04 + 's' }"
            @click="handleSelect(item.id)"
          >
            <span class="sidebar-list__icon sidebar-list__icon--chat">
              <el-icon :size="16"><ChatDotRound /></el-icon>
            </span>
            <span class="sidebar-list__title">{{ item.title || '新对话' }}</span>
            <el-button
              text
              size="small"
              class="sidebar-list__delete"
              @click.stop="handleDelete(item.id)"
            >
              <el-icon :size="14"><Delete /></el-icon>
            </el-button>
          </div>
        </TransitionGroup>

        <div v-if="conversationStore.list.length === 0" class="sidebar-empty">
          <el-icon :size="28"><ChatLineSquare /></el-icon>
          <span>暂无对话</span>
          <span class="sidebar-empty__hint">点击上方按钮开始</span>
        </div>
      </div>
    </div>

    <!-- 底部设置 -->
    <div class="sidebar-footer" v-if="!appStore.sidebarCollapsed">
      <div class="sidebar-footer__inner" @click="router.push('/settings')">
        <span class="sidebar-list__icon sidebar-list__icon--settings">
          <el-icon :size="16"><Setting /></el-icon>
        </span>
        <span class="sidebar-footer__text">设置</span>
      </div>
    </div>
    <div class="sidebar-footer sidebar-footer--collapsed" v-else>
      <el-button
        text
        class="sidebar-footer__pin-btn"
        :class="{ 'is-pinned': appStore.sidebarPinned }"
        @click="appStore.togglePin()"
        title="固定侧边栏"
      >
        <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 2v3.5M12 5.5L9.5 3M12 5.5L14.5 3"/>
          <line x1="12" y1="10" x2="12" y2="22"/>
          <rect x="8" y="5.5" width="8" height="4.5" rx="1"/>
        </svg>
      </el-button>
      <el-button text @click="router.push('/settings')">
        <el-icon :size="18"><Setting /></el-icon>
      </el-button>
    </div>
  </div>
</template>

<style lang="scss" scoped>
// ========================= 变量 =========================
$item-radius: 10px;
$icon-size: 32px;

// ========================= 侧边栏主体 =========================
.sidebar {
  display: flex;
  flex-direction: column;
  height: 100%;
  color: rgba(255, 255, 255, 0.9);
}

// ========================= Logo =========================
.sidebar-logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  overflow: hidden;

  &__text {
    font-size: 19px;
    font-weight: 700;
    color: #fff;
    letter-spacing: 0.03em;
    user-select: none;
  }

  &__gradient {
    background: linear-gradient(135deg, #5ea3f9, #0071e3);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }

  &__icon {
    width: 38px;
    height: 38px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 18px;
    font-weight: 800;
    color: #fff;
    background: linear-gradient(135deg, #0071e3, #4d9ff7);
    border-radius: 10px;
    box-shadow: 0 4px 12px rgba(0, 113, 227, 0.4);
  }

  // ---- 图钉（右上角） ----
  &__pin {
    position: absolute;
    right: 8px;
    top: 50%;
    transform: translateY(-50%);
    width: 28px;
    height: 28px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 7px;
    color: rgba(255, 255, 255, 0.35);
    cursor: pointer;
    transition:
      color 0.25s ease,
      background 0.2s ease,
      transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);

    &:hover {
      color: rgba(255, 255, 255, 0.8);
      background: rgba(255, 255, 255, 0.1);
    }

    &:active {
      transform: translateY(-50%) scale(0.9);
    }

    &.is-pinned {
      color: #80bdf9;
      background: rgba(0, 113, 227, 0.2);
      box-shadow: 0 0 8px rgba(0, 113, 227, 0.25);

      svg {
        transform: rotate(-45deg);
      }

      &:hover {
        background: rgba(0, 113, 227, 0.3);
        color: #fff;
      }
    }

    svg {
      transition: transform 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
      transform-origin: center center;
    }
  }
}

// ========================= 新建按钮区 =========================
.sidebar-action {
  padding: 14px 12px;
}

// ========================= 列表区域 =========================
.sidebar-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 10px;

  &::-webkit-scrollbar {
    width: 4px;
  }
  &::-webkit-scrollbar-thumb {
    background-color: rgba(255, 255, 255, 0.1);
    border-radius: 2px;
  }
}

// ========================= 分组 =========================
.sidebar-section {
  margin-bottom: 6px;

  &__label {
    font-size: 11px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.06em;
    color: rgba(255, 255, 255, 0.35);
    padding: 8px 12px 6px;
    user-select: none;
  }
}

// ========================= 列表项 =========================
.sidebar-list__item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border-radius: $item-radius;
  cursor: pointer;
  font-size: 13.5px;
  margin-bottom: 1px;
  color: rgba(255, 255, 255, 0.75);
  position: relative;
  transition:
    background 0.2s cubic-bezier(0.25, 0.1, 0.25, 1),
    color 0.2s cubic-bezier(0.25, 0.1, 0.25, 1),
    transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1),
    box-shadow 0.25s cubic-bezier(0.25, 0.1, 0.25, 1);
  animation: itemEnter 0.4s cubic-bezier(0.34, 1.56, 0.64, 1) both;
  animation-delay: var(--delay, 0s);

  // 左侧激活指示条
  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 50%;
    transform: translateY(-50%) scaleY(0);
    width: 3px;
    height: 20px;
    border-radius: 0 3px 3px 0;
    background: #fff;
    transition: transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
  }

  &:hover {
    background: rgba(255, 255, 255, 0.1);
    color: #fff;
    transform: translateX(2px);
    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.15);

    .sidebar-list__arrow {
      opacity: 1;
      transform: translateX(0);
    }

    .sidebar-list__icon--kb {
      background: rgba(0, 113, 227, 0.35);
      color: #80bdf9;
    }
  }

  &:active {
    transform: translateX(1px) scale(0.98);
    transition: transform 0.1s ease;
  }

  &.active {
    background: rgba(0, 113, 227, 0.28);
    color: #fff;
    box-shadow: 0 2px 12px rgba(0, 113, 227, 0.2), inset 0 1px 0 rgba(255, 255, 255, 0.05);

    &::before {
      transform: translateY(-50%) scaleY(1);
    }

    .sidebar-list__icon {
      box-shadow: 0 3px 10px rgba(0, 113, 227, 0.35);
    }

    .sidebar-list__icon--chat {
      background: rgba(0, 113, 227, 0.4);
      color: #fff;
    }
  }

  // KB 特殊样式
  &--kb {
    color: rgba(255, 255, 255, 0.85);

    &.active {
      background: rgba(0, 113, 227, 0.25);
    }
  }
}

// ========================= 图标容器 =========================
.sidebar-list__icon {
  width: $icon-size;
  height: $icon-size;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition:
    background 0.25s ease,
    color 0.25s ease,
    box-shadow 0.25s ease;

  &--kb {
    background: rgba(0, 113, 227, 0.2);
    color: #5ea3f9;
  }

  &--chat {
    background: rgba(255, 255, 255, 0.06);
    color: rgba(255, 255, 255, 0.5);
  }

  &--settings {
    background: rgba(255, 255, 255, 0.05);
    color: rgba(255, 255, 255, 0.55);
  }
}

// ========================= 标题 =========================
.sidebar-list__title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 450;
}

// ========================= 箭头 =========================
.sidebar-list__arrow {
  opacity: 0.4;
  color: rgba(255, 255, 255, 0.5);
  transform: translateX(-4px);
  transition:
    opacity 0.25s ease,
    transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
  flex-shrink: 0;
}

// ========================= 删除按钮 =========================
.sidebar-list__delete {
  opacity: 0;
  color: rgba(255, 255, 255, 0.45);
  padding: 4px !important;
  border-radius: 6px;
  transform: scale(0.8);
  transition:
    opacity 0.2s ease,
    color 0.2s ease,
    transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1),
    background 0.2s ease;

  &:hover {
    color: #ff453a;
    background: rgba(255, 69, 58, 0.15) !important;
    transform: scale(1.1);
  }

  &:active {
    transform: scale(0.95);
  }
}

.sidebar-list__item:hover .sidebar-list__delete {
  opacity: 1;
  transform: scale(1);
}

// ========================= 空状态 =========================
.sidebar-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 24px 16px;
  color: rgba(255, 255, 255, 0.3);
  font-size: 13px;
  text-align: center;
  animation: fadeIn 0.5s ease both;

  &__hint {
    font-size: 11px;
    color: rgba(255, 255, 255, 0.18);
  }
}

// ========================= 底部 =========================
.sidebar-footer {
  padding: 8px 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);

  &__inner {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 10px;
    border-radius: $item-radius;
    cursor: pointer;
    font-size: 13.5px;
    color: rgba(255, 255, 255, 0.65);
    transition:
      background 0.2s ease,
      color 0.2s ease,
      transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);

    &:hover {
      background: rgba(255, 255, 255, 0.08);
      color: #fff;
      transform: translateX(2px);

      .sidebar-list__icon--settings {
        background: rgba(255, 255, 255, 0.1);
        color: rgba(255, 255, 255, 0.8);
      }
    }

    &:active {
      transform: translateX(1px) scale(0.98);
    }
  }

  &__text {
    font-weight: 450;
  }

  &--collapsed {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
    padding: 12px 0;
  }

  // ---- 图钉按钮 ----
  &__pin-btn {
    color: rgba(255, 255, 255, 0.45);
    padding: 6px !important;
    border-radius: 8px;
    transition:
      color 0.25s ease,
      background 0.2s ease,
      transform 0.25s ease;

    &:hover {
      color: rgba(255, 255, 255, 0.85);
      background: rgba(255, 255, 255, 0.08) !important;
    }

    &.is-pinned {
      color: #80bdf9;
      background: rgba(0, 113, 227, 0.2) !important;

      svg {
        transform: rotate(-45deg);
      }

      &:hover {
        background: rgba(0, 113, 227, 0.3) !important;
        color: #fff;
      }
    }

    svg {
      transition: transform 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
      transform-origin: center center;
    }
  }
}

// ========================= 动画关键帧 =========================
@keyframes itemEnter {
  from {
    opacity: 0;
    transform: translateX(-16px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateX(0) scale(1);
  }
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to   { opacity: 1; transform: translateY(0); }
}

// ========================= TransitionGroup 动画 =========================
.conv-list-enter-active {
  transition:
    opacity 0.4s cubic-bezier(0.34, 1.56, 0.64, 1),
    transform 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.conv-list-leave-active {
  transition:
    opacity 0.25s ease,
    transform 0.25s ease;
}

.conv-list-enter-from {
  opacity: 0;
  transform: translateX(-20px) scale(0.92);
}

.conv-list-leave-to {
  opacity: 0;
  transform: translateX(-12px) scale(0.9);
}

.conv-list-move {
  transition: transform 0.35s cubic-bezier(0.25, 0.1, 0.25, 1);
}

// ========================= 折叠态 Logo 切换 =========================
.logo-swap-enter-active {
  transition:
    opacity 0.25s ease,
    transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.logo-swap-leave-active {
  transition:
    opacity 0.15s ease,
    transform 0.15s ease;
}

.logo-swap-enter-from {
  opacity: 0;
  transform: scale(0.8);
}

.logo-swap-leave-to {
  opacity: 0;
  transform: scale(0.8);
}
</style>
