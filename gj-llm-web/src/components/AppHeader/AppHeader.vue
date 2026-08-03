<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/modules/user'
import { ChatDotRound, Collection, Setting, SwitchButton } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const navItems = [
  { path: '/chat', label: '聊天', icon: ChatDotRound, match: (p: string) => p.startsWith('/chat') },
  { path: '/datasets', label: '知识库', icon: Collection, match: (p: string) => p.startsWith('/datasets') },
]

const activeIndex = computed(() => {
  const idx = navItems.findIndex((item) => item.match(route.path))
  return idx >= 0 ? idx : 0
})

function handleLogout() {
  userStore.logout()
}
</script>

<template>
  <div class="app-header">
    <!-- ===== Logo ===== -->
    <div class="header-logo" @click="router.push('/chat')">
      <svg viewBox="0 0 36 36" width="36" height="36" fill="none" class="header-logo__icon">
        <rect width="36" height="36" rx="9" fill="url(#logoGrad)" />
        <text x="18" y="24" text-anchor="middle" fill="#fff" font-size="18" font-weight="750">G</text>
        <defs>
          <linearGradient id="logoGrad" x1="0" y1="0" x2="36" y2="36" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stop-color="#6366f1" />
            <stop offset="100%" stop-color="#8b5cf6" />
          </linearGradient>
        </defs>
      </svg>
      <span class="header-logo__text">
        <span class="header-logo__brand">GJ</span><span class="header-logo__suffix">-LLM</span>
      </span>
    </div>

    <!-- ===== 导航 ===== -->
    <nav class="header-nav">
      <div
        v-for="(item, index) in navItems"
        :key="item.path"
        class="header-nav__item"
        :class="{ active: index === activeIndex }"
        @click="router.push(item.path)"
      >
        <span class="header-nav__icon">
          <el-icon :size="20"><component :is="item.icon" /></el-icon>
        </span>
        <span class="header-nav__label">{{ item.label }}</span>
      </div>
    </nav>

    <!-- ===== 用户 ===== -->
    <div class="header-user">
      <el-dropdown trigger="click" placement="bottom-end" :teleported="false">
        <div class="header-user__trigger">
          <el-avatar :size="32" icon="UserFilled" />
          <span class="header-user__name">{{ userStore.username || '用户' }}</span>
          <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
            <polyline points="6 9 12 15 18 9" />
          </svg>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="router.push('/settings')">
              <el-icon><Setting /></el-icon>设置
            </el-dropdown-item>
            <el-dropdown-item divided @click="handleLogout">
              <el-icon><SwitchButton /></el-icon>退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.app-header {
  display: flex;
  align-items: center;
  height: 100%;
  padding: 0 28px;
}

// ========================= Logo =========================
.header-logo {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  user-select: none;
  margin-right: 40px;

  &__icon {
    filter: drop-shadow(0 2px 6px rgba(99, 102, 241, 0.3));
  }

  &__text {
    font-size: 18px;
    font-weight: 700;
    letter-spacing: -0.01em;
    display: flex;
    align-items: baseline;
  }

  &__brand {
    background: linear-gradient(135deg, #818cf8 0%, #a78bfa 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
    font-weight: 820;
  }

  &__suffix {
    color: #e0e0e0;
  }
}

// ========================= 导航 =========================
.header-nav {
  display: flex;
  align-items: stretch;
  flex: 1;
  height: 100%;
}

.header-nav__item {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  padding: 0 32px;
  height: 100%;
  cursor: pointer;
  user-select: none;
  position: relative;
  color: #9ca3af;
  transition: color 0.25s ease, background 0.25s ease;

  &::after {
    content: '';
    position: absolute;
    bottom: 0;
    left: 50%;
    width: calc(100% - 48px);
    height: 2.5px;
    border-radius: 3px 3px 0 0;
    background: #818cf8;
    transform: translateX(-50%) scaleX(0);
    transition: transform 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
  }

  &:hover {
    color: #e5e7eb;
    background: rgba(255, 255, 255, 0.04);
  }

  &.active {
    color: #f9fafb;

    .header-nav__icon {
      color: #818cf8;
    }

    &::after {
      transform: translateX(-50%) scaleX(1);
    }
  }
}

.header-nav__icon {
  display: flex;
  align-items: center;
  color: #6b7280;
  transition: color 0.25s ease;
}

.header-nav__label {
  font-size: 15px;
  font-weight: 500;
  letter-spacing: 0.04em;
  white-space: nowrap;
}

// ========================= 用户 =========================
.header-user {
  flex-shrink: 0;
  margin-left: 40px;

  &__trigger {
    display: flex;
    align-items: center;
    gap: 9px;
    cursor: pointer;
    padding: 5px 12px 5px 5px;
    border-radius: 22px;
    transition: background 0.2s ease;

    &:hover {
      background: rgba(255, 255, 255, 0.06);
    }
  }

  &__name {
    font-size: 14px;
    color: #d1d5db;
    font-weight: 500;
    user-select: none;
  }

  svg {
    color: #6b7280;
  }
}
</style>
