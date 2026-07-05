<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAppStore } from '@/stores/modules/app'
import { useConversationStore } from '@/stores/modules/conversation'
import { useChatStore } from '@/stores/modules/chat'

const router = useRouter()
const appStore = useAppStore()
const conversationStore = useConversationStore()
const chatStore = useChatStore()

function handleNewChat() {
  chatStore.clearMessages()
  conversationStore.setCurrentId(null)
  router.push('/chat')
}

function handleSelect(id: string) {
  conversationStore.setCurrentId(id)
  router.push(`/chat/${id}`)
}

function handleDelete(id: string) {
  conversationStore.remove(id)
}
</script>

<template>
  <div class="sidebar">
    <!-- Logo 区域 -->
    <div class="sidebar-logo">
      <span v-if="!appStore.sidebarCollapsed" class="sidebar-logo__text">GJ-LLM</span>
      <span v-else class="sidebar-logo__icon">G</span>
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
      <div
        v-for="item in conversationStore.list"
        :key="item.id"
        class="sidebar-list__item"
        :class="{ active: item.id === conversationStore.currentId }"
        @click="handleSelect(item.id)"
      >
        <el-icon><ChatDotRound /></el-icon>
        <span class="sidebar-list__title">{{ item.title || '新对话' }}</span>
        <el-button
          text
          size="small"
          class="sidebar-list__delete"
          @click.stop="handleDelete(item.id)"
        >
          <el-icon><Delete /></el-icon>
        </el-button>
      </div>

      <el-empty v-if="conversationStore.list.length === 0" description="暂无对话" :image-size="60" />
    </div>

    <!-- 底部设置 -->
    <div class="sidebar-footer" v-if="!appStore.sidebarCollapsed">
      <el-button text style="width: 100%" @click="router.push('/settings')">
        <el-icon><Setting /></el-icon>
        设置
      </el-button>
    </div>
    <div class="sidebar-footer" v-else>
      <el-button text @click="router.push('/settings')">
        <el-icon><Setting /></el-icon>
      </el-button>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  height: 100%;
  color: #e0e0e0;
}

.sidebar-logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);

  &__text {
    font-size: 18px;
    font-weight: 700;
    color: #fff;
  }

  &__icon {
    font-size: 20px;
    font-weight: 700;
    color: #409eff;
  }
}

.sidebar-action {
  padding: 16px;
}

.sidebar-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px;

  &__item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 12px;
    border-radius: 6px;
    cursor: pointer;
    font-size: 14px;
    margin-bottom: 2px;

    &:hover {
      background-color: rgba(255, 255, 255, 0.08);
    }

    &.active {
      background-color: rgba(64, 158, 255, 0.2);
      color: #fff;
    }

    .el-icon {
      flex-shrink: 0;
    }
  }

  &__title {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__delete {
    opacity: 0;
    color: #e0e0e0;
    &:hover {
      color: #f56c6c;
    }
  }

  &__item:hover &__delete {
    opacity: 1;
  }
}

.sidebar-footer {
  padding: 12px 8px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}
</style>
