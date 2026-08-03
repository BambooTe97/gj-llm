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
          <Transition name="spring">
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
  position: relative;
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
// 仅用 opacity + transform（GPU 合成属性），避免 filter: blur / clip-path
// 这类每帧重光栅化的昂贵动画导致切换时主线程被占满、数据请求发不出去。
// 去掉 mode="out-in" 后新旧页面会短暂并存：离场页绝对定位脱离文档流，
// 进场页占据正常位置，形成交叉淡入淡出，请求在新页 onMounted 时立即发出。
.spring-enter-active {
  transition:
    opacity 0.4s cubic-bezier(0.22, 0.6, 0.15, 1),
    transform 0.4s cubic-bezier(0.22, 0.6, 0.15, 1);
}
.spring-leave-active {
  position: absolute;
  inset: 0;
  transition:
    opacity 0.2s cubic-bezier(0.4, 0, 0.6, 1),
    transform 0.2s cubic-bezier(0.4, 0, 0.6, 1);
}
.spring-enter-from {
  opacity: 0;
  transform: translateY(12px);
}
.spring-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
