<script setup lang="ts">
import { useAppStore } from '@/stores/modules/app'

const appStore = useAppStore()
</script>

<template>
  <div class="settings-view">
    <h2>系统设置</h2>

    <el-card class="settings-card">
      <template #header><span>外观</span></template>
      <el-form label-width="120px">
        <el-form-item label="侧边栏">
          <el-switch
            :model-value="appStore.sidebarCollapsed"
            active-text="收起"
            inactive-text="展开"
            @change="appStore.toggleSidebar()"
          />
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.settings-view {
  padding: 24px;
  max-width: 800px;

  h2 {
    margin-bottom: 24px;
    font-size: 20px;
  }
}

.settings-card {
  margin-bottom: 16px;
}
</style>
