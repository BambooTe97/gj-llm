<script setup lang="ts">
import { useAppStore } from '@/stores/modules/app'
import Sidebar from '@/components/Sidebar/Sidebar.vue'
import AppHeader from '@/components/AppHeader/AppHeader.vue'

const appStore = useAppStore()
</script>

<template>
  <el-container class="default-layout">
    <el-aside :width="appStore.sidebarCollapsed ? '64px' : '260px'" class="layout-aside">
      <Sidebar />
    </el-aside>
    <el-container>
      <el-header height="56px" class="layout-header">
        <AppHeader />
      </el-header>
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style lang="scss" scoped>
.default-layout {
  width: 100%;
  height: 100vh;
}

.layout-aside {
  background: linear-gradient(180deg, rgba(26, 28, 44, 0.9) 0%, rgba(32, 34, 52, 0.88) 100%);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border-right: 1px solid rgba(255, 255, 255, 0.06);
  transition: width 0.3s cubic-bezier(0.25, 0.1, 0.25, 1);
  overflow: hidden;
}

.layout-header {
  background: rgba(255, 255, 255, 0.65);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(210, 210, 215, 0.4);
  padding: 0 20px;
}

.layout-main {
  background: transparent;
  padding: 0;
  overflow: hidden;
}
</style>
