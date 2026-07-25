<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader/AppHeader.vue'
import ChatSubPanel from '@/components/ChatSubPanel/ChatSubPanel.vue'

const route = useRoute()
const showChatSubPanel = computed(() => route.path.startsWith('/chat'))
</script>

<template>
  <div class="default-layout">
    <!-- ====== 横批顶栏 ====== -->
    <header class="layout-header">
      <AppHeader />
    </header>

    <!-- ====== 主体 ====== -->
    <div class="layout-body">
      <Transition name="sub-panel-slide">
        <div v-if="showChatSubPanel" class="layout-sub-panel">
          <ChatSubPanel />
        </div>
      </Transition>

      <main class="layout-main">
        <router-view v-slot="{ Component }">
          <Transition name="spring" mode="out-in">
            <component :is="Component" :key="route.path" />
          </Transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.default-layout {
  width: 100%;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(170deg, #f2f2f5 0%, #fafafc 30%, #f5f5f7 70%, #f8f8fa 100%);
}

// ========================= 横批顶栏 =========================
.layout-header {
  flex-shrink: 0;
  height: 68px;
  position: relative;
  z-index: 10;
  background: #1e2130;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.08),
    0 4px 16px rgba(0, 0, 0, 0.06);
}

.layout-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}
.layout-sub-panel {
  flex-shrink: 0;
  z-index: 2;
}
.layout-main {
  flex: 1;
  overflow: hidden;
  padding: 0;
  background: transparent;
}

// ========================= 滑入 =========================
.sub-panel-slide-enter-active {
  transition: width 0.35s cubic-bezier(0.25, 0.1, 0.25, 1);
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

// ========================= 页面切换 =========================
.spring-enter-active {
  animation: fabric-in 0.7s cubic-bezier(0.22, 0.6, 0.15, 1) both;
}
.spring-leave-active {
  animation: fabric-out 0.25s cubic-bezier(0.4, 0, 0.6, 1) both;
}

@keyframes fabric-in {
  0% {
    clip-path: inset(0% 98% 0% 0);
    filter: blur(24px) brightness(0.5);
    opacity: 0;
    transform: translateY(8px);
  }
  20% {
    filter: blur(14px) brightness(0.7);
    opacity: 0.4;
  }
  40% {
    clip-path: inset(0% 50% 0% 0);
    filter: blur(6px) brightness(0.88);
    opacity: 0.75;
    transform: translateY(-2px);
  }
  60% {
    clip-path: inset(0% 5% 0% 0);
    filter: blur(1.5px) brightness(0.98);
    opacity: 1;
    transform: translateY(1px);
  }
  80% {
    clip-path: inset(0% 0% 0% 0);
    filter: blur(0.3px) brightness(1);
    transform: translateY(-0.5px);
  }
  100% {
    clip-path: inset(0 0 0 0);
    filter: blur(0px) brightness(1);
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes fabric-out {
  0% {
    clip-path: inset(0 0 0 0);
    filter: blur(0px) brightness(1);
    opacity: 1;
  }
  100% {
    clip-path: inset(0% 99% 0% 0);
    filter: blur(20px) brightness(0.4);
    opacity: 0;
    transform: translateY(-6px);
  }
}
</style>
