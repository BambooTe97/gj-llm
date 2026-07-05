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
  background-color: #1d1e2c;
  transition: width 0.3s;
  overflow: hidden;
}

.layout-header {
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
  padding: 0 20px;
}

.layout-main {
  background-color: #f5f7fa;
  padding: 0;
  overflow: hidden;
}
</style>
