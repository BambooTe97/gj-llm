<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/modules/app'
import Sidebar from '@/components/Sidebar/Sidebar.vue'
import ChatSubPanel from '@/components/ChatSubPanel/ChatSubPanel.vue'
import AppHeader from '@/components/AppHeader/AppHeader.vue'

const route = useRoute()
const appStore = useAppStore()

const showChatSubPanel = computed(() => route.path.startsWith('/chat'))
</script>

<template>
  <el-container class="default-layout">
    <!-- 主侧边栏：3D 水滴玻璃面板 -->
    <el-aside
      :width="appStore.sidebarCollapsed ? '64px' : '260px'"
      class="layout-aside"
      @mouseenter="appStore.onSidebarEnter()"
      @mouseleave="appStore.onSidebarLeave()"
    >
      <Sidebar />
    </el-aside>

    <!-- 聊天子面板 -->
    <Transition name="sub-panel-slide">
      <div v-if="showChatSubPanel" class="layout-sub-panel">
        <ChatSubPanel />
      </div>
    </Transition>

    <!-- 主内容区 -->
    <el-container class="layout-content">
      <el-header height="56px" class="layout-header">
        <AppHeader />
      </el-header>
      <el-main class="layout-main">
        <router-view v-slot="{ Component }">
          <Transition name="spring" mode="out-in">
            <component :is="Component" :key="route.path" />
          </Transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style lang="scss" scoped>
// ========================= 布局 =========================
.default-layout {
  width: 100%;
  height: 100vh;
}

// ========================= 3D 水滴玻璃面板 — 侧边栏 =========================
.layout-aside {
  background:
    linear-gradient(180deg, rgba(255,255,255,0.52) 0%, rgba(255,255,255,0.2) 35%, rgba(255,255,255,0.1) 100%),
    rgba(238, 238, 243, 0.32);
  backdrop-filter: blur(40px) saturate(200%);
  -webkit-backdrop-filter: blur(40px) saturate(200%);
  border-right: 0.5px solid rgba(255, 255, 255, 0.5);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.6),
    inset 0 -1px 0 rgba(0, 0, 0, 0.04),
    4px 0 28px rgba(0, 0, 0, 0.07);
  transition: width 0.3s cubic-bezier(0.25, 0.1, 0.25, 1);
  overflow: hidden;
  z-index: 2;
}

.layout-sub-panel {
  flex-shrink: 0;
  z-index: 1;
}

.layout-content {
  overflow: hidden;
}

// ========================= 3D 水滴玻璃面板 — 顶部栏 =========================
.layout-header {
  background:
    linear-gradient(180deg, rgba(255,255,255,0.5) 0%, rgba(255,255,255,0.18) 100%),
    rgba(240, 240, 245, 0.3);
  backdrop-filter: blur(36px) saturate(200%);
  -webkit-backdrop-filter: blur(36px) saturate(200%);
  border-bottom: 0.5px solid rgba(255, 255, 255, 0.45);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.6),
    0 2px 20px rgba(0, 0, 0, 0.05);
  padding: 0 20px;
}

.layout-main {
  background: transparent;
  padding: 0;
  overflow: hidden;
}

// ========================= 子面板滑入/滑出 =========================
.sub-panel-slide-enter-active {
  transition: width 0.3s cubic-bezier(0.25, 0.1, 0.25, 1);
  overflow: hidden;
}

.sub-panel-slide-leave-active {
  transition: width 0.25s cubic-bezier(0.25, 0.1, 0.25, 1);
  overflow: hidden;
}

.sub-panel-slide-enter-from,
.sub-panel-slide-leave-to {
  width: 0 !important;
}

// ========================= 软布铺开展开动画 =========================
// 技巧：clip-path inset 做矩形展开 + 大 blur 把硬边柔化成布料质感
// blur 从 30px → 0，硬几何边界变成软布般渐渐浮现
// --spring-origin-y 由 Sidebar 点击时 JS 写入

.spring-enter-active {
  animation: fabric-in 0.75s cubic-bezier(0.22, 0.6, 0.15, 1) both;
}

.spring-leave-active {
  animation: fabric-out 0.3s cubic-bezier(0.4, 0, 0.6, 1) both;
}

@keyframes fabric-in {
  0% {
    // 从侧边栏边缘的窄竖条开始，大模糊让边缘像软布
    clip-path: inset(38% 94% 38% 0);
    filter: blur(28px) brightness(0.5) saturate(0.3);
    opacity: 0;
    transform: translateY(6px);
  }
  18% {
    // 布开始"松卷"，模糊减弱，画面渐亮
    filter: blur(18px) brightness(0.7) saturate(0.5);
    opacity: 0.45;
  }
  35% {
    // 布铺开过半
    clip-path: inset(15% 55% 15% 0);
    filter: blur(10px) brightness(0.85) saturate(0.75);
    opacity: 0.75;
    transform: translateY(-2px);
  }
  55% {
    // 近乎完全展开，还有一点柔边
    clip-path: inset(0% 8% 0% 0);
    filter: blur(3px) brightness(0.97) saturate(0.9);
    opacity: 1;
    transform: translateY(1px);
  }
  72% {
    // 轻微过冲 — 布"抖"了一下
    clip-path: inset(0% 0% 0% 0);
    filter: blur(0.8px) brightness(1) saturate(1);
    transform: translateY(-0.5px);
  }
  88% {
    // 二次微抖，布彻底平展
    filter: blur(0.2px) brightness(1) saturate(1);
    transform: translateY(0px);
  }
  100% {
    clip-path: inset(0 0 0 0);
    filter: blur(0px) brightness(1) saturate(1);
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes fabric-out {
  0% {
    clip-path: inset(0 0 0 0);
    filter: blur(0px) brightness(1);
    opacity: 1;
    transform: translateY(0);
  }
  100% {
    // 快速"卷回去"，模糊浮现，布消失
    clip-path: inset(35% 96% 35% 0);
    filter: blur(24px) brightness(0.4) saturate(0.2);
    opacity: 0;
    transform: translateY(-4px);
  }
}
</style>
