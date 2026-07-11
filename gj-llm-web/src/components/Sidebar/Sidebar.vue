<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAppStore } from '@/stores/modules/app'
import { ChatDotRound, Collection, Setting } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const appStore = useAppStore()

const isChatActive = computed(() => route.path.startsWith('/chat'))
const isDatasetsActive = computed(() => route.path.startsWith('/datasets'))
const isSettingsActive = computed(() => route.path.startsWith('/settings'))

/** 记录点击按钮的屏幕 Y 位置 → 作为展开动画的原点 */
function handleNavClick(path: string, event: MouseEvent) {
  const el = event.currentTarget as HTMLElement
  const rect = el.getBoundingClientRect()
  // 按钮中心 Y → 换算到内容区坐标系（减去 header 56px）
  const contentTop = (document.querySelector('.layout-main') as HTMLElement)?.getBoundingClientRect().top ?? 0
  const originY = rect.top + rect.height / 2 - contentTop
  document.documentElement.style.setProperty('--spring-origin-y', originY + 'px')
  document.documentElement.style.setProperty('--spring-origin-x', '0px')
  router.push(path)
}
</script>

<template>
  <div class="sidebar">
    <!-- Logo 区域 -->
    <div class="sidebar-logo">
      <Transition name="logo-swap" mode="out-in">
        <span v-if="!appStore.sidebarCollapsed" key="full" class="sidebar-logo__text">
          <span class="sidebar-logo__gradient">GJ</span><span class="sidebar-logo__suffix">-LLM</span>
        </span>
        <span v-else key="icon" class="sidebar-logo__icon">G</span>
      </Transition>
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

    <!-- 导航菜单 -->
    <nav class="sidebar-nav">
      <div
        class="sidebar-nav__item"
        :class="{ active: isChatActive }"
        @click="handleNavClick('/chat', $event)"
        title="聊天"
      >
        <span class="sidebar-nav__icon sidebar-nav__icon--chat">
          <el-icon :size="18"><ChatDotRound /></el-icon>
        </span>
        <span v-if="!appStore.sidebarCollapsed" class="sidebar-nav__title">聊天</span>
      </div>

      <div
        class="sidebar-nav__item"
        :class="{ active: isDatasetsActive }"
        @click="handleNavClick('/datasets', $event)"
        title="知识库"
      >
        <span class="sidebar-nav__icon sidebar-nav__icon--kb">
          <el-icon :size="18"><Collection /></el-icon>
        </span>
        <span v-if="!appStore.sidebarCollapsed" class="sidebar-nav__title">知识库</span>
      </div>
    </nav>

    <div class="sidebar-spacer" />

    <!-- 底部设置 -->
    <div class="sidebar-footer" v-if="!appStore.sidebarCollapsed">
      <div
        class="sidebar-nav__item"
        :class="{ active: isSettingsActive }"
        @click="handleNavClick('/settings', $event)"
      >
        <span class="sidebar-nav__icon sidebar-nav__icon--settings">
          <el-icon :size="18"><Setting /></el-icon>
        </span>
        <span class="sidebar-nav__title">设置</span>
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
$item-radius: 9px;
$icon-size: 34px;

$text-primary: #1d1d1f;
$text-secondary: #6e6e73;
$text-tertiary: #aeaeb2;
$blue-accent: #007aff;
$blue-bg: rgba(0, 122, 255, 0.1);
$blue-bg-hover: rgba(0, 122, 255, 0.16);
$hover-bg: rgba(0, 0, 0, 0.04);
$active-bg: rgba(0, 122, 255, 0.1);

.sidebar {
  display: flex;
  flex-direction: column;
  height: 100%;
  color: $text-primary;
  // 透明 — 玻璃效果在父级 layout-aside 上
  background: transparent;
}

// ========================= Logo =========================
.sidebar-logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  -webkit-app-region: drag;
  app-region: drag;
  // 底部分隔 — 用渐变代替硬边框
  border-bottom: 0.5px solid rgba(0, 0, 0, 0.06);

  &__text {
    font-size: 18px;
    font-weight: 700;
    letter-spacing: -0.01em;
    user-select: none;
    display: flex;
    align-items: baseline;
  }

  &__gradient {
    background: linear-gradient(135deg, #007aff, #5856d6);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }

  &__suffix {
    color: $text-primary;
    -webkit-text-fill-color: $text-primary;
  }

  &__icon {
    width: 36px;
    height: 36px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 17px;
    font-weight: 800;
    color: #fff;
    background: linear-gradient(135deg, #007aff, #5856d6);
    border-radius: 9px;
    box-shadow: 0 2px 8px rgba(0, 122, 255, 0.28);
  }

  &__pin {
    position: absolute;
    right: 8px;
    top: 50%;
    transform: translateY(-50%);
    width: 26px;
    height: 26px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 6px;
    color: $text-tertiary;
    cursor: pointer;
    transition: color 0.2s ease, background 0.2s ease;

    &:hover {
      color: $text-primary;
      background: $hover-bg;
    }
    &:active { transform: translateY(-50%) scale(0.92); }

    &.is-pinned {
      color: $blue-accent;
      background: $blue-bg;
      svg { transform: rotate(-45deg); }
      &:hover { background: $blue-bg-hover; }
    }

    svg {
      transition: transform 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
      transform-origin: center center;
    }
  }
}

// ========================= 导航区域 =========================
.sidebar-nav {
  padding: 12px 10px 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sidebar-nav__item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border-radius: $item-radius;
  cursor: pointer;
  font-size: 13.5px;
  font-weight: 460;
  color: $text-primary;
  position: relative;
  transition: background 0.18s ease, transform 0.18s ease;

  &::before {
    content: '';
    position: absolute;
    left: 0; top: 50%;
    transform: translateY(-50%) scaleY(0);
    width: 3px; height: 18px;
    border-radius: 0 3px 3px 0;
    background: $blue-accent;
    transition: transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
  }

  &:hover { background: $hover-bg; }
  &:active { transform: scale(0.97); transition: transform 0.1s ease; }

  &.active {
    background: $active-bg;
    &::before { transform: translateY(-50%) scaleY(1); }
    .sidebar-nav__icon { box-shadow: 0 2px 8px rgba(0, 122, 255, 0.18); }
    .sidebar-nav__title { font-weight: 560; }
  }
}

// ========================= 图标容器 =========================
.sidebar-nav__icon {
  width: $icon-size;
  height: $icon-size;
  border-radius: 7.5px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background 0.2s ease, color 0.2s ease, box-shadow 0.2s ease;

  &--chat     { background: rgba(0, 122, 255, 0.08); color: $blue-accent; }
  &--kb       { background: rgba(88, 86, 214, 0.08); color: #5856d6; }
  &--settings { background: rgba(0, 0, 0, 0.04); color: $text-secondary; }
}

.sidebar-nav__title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar-spacer { flex: 1; }

// ========================= 底部 =========================
.sidebar-footer {
  padding: 8px 10px;
  border-top: 0.5px solid rgba(0, 0, 0, 0.06);

  &--collapsed {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
    padding: 12px 0;
  }

  &__pin-btn {
    color: $text-tertiary !important;
    padding: 6px !important;
    border-radius: 8px;
    transition: color 0.2s ease, background 0.2s ease;

    &:hover { color: $text-primary !important; background: $hover-bg !important; }

    &.is-pinned {
      color: $blue-accent !important;
      background: $blue-bg !important;
      svg { transform: rotate(-45deg); }
      &:hover { background: $blue-bg-hover !important; }
    }

    svg {
      transition: transform 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
      transform-origin: center center;
    }
  }
}

// ========================= 折叠态 Logo 切换 =========================
.logo-swap-enter-active {
  transition: opacity 0.2s ease, transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.logo-swap-leave-active {
  transition: opacity 0.12s ease, transform 0.12s ease;
}
.logo-swap-enter-from,
.logo-swap-leave-to {
  opacity: 0; transform: scale(0.85);
}
</style>
