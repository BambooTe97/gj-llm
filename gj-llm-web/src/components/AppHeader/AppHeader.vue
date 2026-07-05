<script setup lang="ts">
import { useAppStore } from '@/stores/modules/app'
import { useUserStore } from '@/stores/modules/user'
import { useRouter } from 'vue-router'

const appStore = useAppStore()
const userStore = useUserStore()
const router = useRouter()

function handleLogout() {
  userStore.logout()
}
</script>

<template>
  <div class="app-header">
    <div class="app-header__left">
      <el-button text @click="appStore.toggleSidebar()">
        <el-icon :size="20"><Fold v-if="!appStore.sidebarCollapsed" /><Expand v-else /></el-icon>
      </el-button>
    </div>

    <div class="app-header__right">
      <el-dropdown trigger="click">
        <span class="app-header__user">
          <el-avatar :size="32" icon="UserFilled" />
          <span class="app-header__username">{{ userStore.username || '用户' }}</span>
        </span>
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
  justify-content: space-between;
  height: 100%;
}

.app-header__right {
  display: flex;
  align-items: center;
}

.app-header__user {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.app-header__username {
  font-size: 14px;
  color: #303133;
}
</style>
