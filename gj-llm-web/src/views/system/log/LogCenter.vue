<template>
  <div class="page">
    <div class="page-header">
      <div class="page-header__title">
        <h2>日志管理</h2>
        <span class="page-header__desc">操作与登录审计日志（敏感字段已脱敏，超期自动清理）</span>
      </div>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane v-if="canSeeOperLog" label="操作日志" name="oper" lazy>
        <OperLogPanel />
      </el-tab-pane>
      <el-tab-pane v-if="canSeeLoginLog" label="登录日志" name="login" lazy>
        <LoginLogPanel />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useUserStore } from '@/stores/modules/user'
import OperLogPanel from './OperLogPanel.vue'
import LoginLogPanel from './LoginLogPanel.vue'

defineOptions({ name: 'LogCenter' })

const userStore = useUserStore()

/** Tab 显隐按权限点收敛（与后端四件套一致）：仅持有对应 list 权限的用户可见对应 Tab */
const canSeeOperLog = computed(() => userStore.hasPermission('system:log:list'))
const canSeeLoginLog = computed(() => userStore.hasPermission('system:loginlog:list'))

// 默认落在第一个可见的 Tab（两个都不可见的页面不会被菜单授权进来）
const activeTab = ref(canSeeOperLog.value ? 'oper' : 'login')
</script>

<style scoped lang="scss">
.page {
  padding: 16px;

  .page-header {
    margin-bottom: 10px;

    &__title {
      h2 {
        margin: 0 0 4px;
        font-size: 18px;
      }
    }

    &__desc {
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }
  }
}
</style>
