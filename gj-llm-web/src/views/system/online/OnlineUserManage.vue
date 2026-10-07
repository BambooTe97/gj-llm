<template>
  <div class="page">
    <div class="page-header">
      <div class="page-header__title">
        <h2>在线用户</h2>
        <span class="page-header__desc">当前有效登录会话（按 refreshToken 注册），可强制下线</span>
      </div>
      <div class="page-header__actions">
        <el-button :icon="Refresh" :loading="loading" @click="loadList">刷新</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="list" class="page-table">
      <el-table-column prop="username" label="账号" width="130" />
      <el-table-column prop="nickname" label="昵称" width="130">
        <template #default="{ row }">
          <span>{{ row.nickname || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="ip" label="登录 IP" width="140" />
      <el-table-column prop="browser" label="浏览器" min-width="130" />
      <el-table-column prop="os" label="操作系统" min-width="130" />
      <el-table-column prop="loginTime" label="登录时间" width="180" />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }: { row: any }">
          <div class="op-cell">
            <el-button
              text
              type="danger"
              size="small"
              :icon="CircleClose"
              v-permission="'system:online:forceLogout'"
              @click="handleForceLogout(row)"
            >
              强退
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && list.length === 0" description="暂无在线会话" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleClose, Refresh } from '@element-plus/icons-vue'
import { onlineUserApi, type OnlineUser } from '@/api/modules/online'

defineOptions({ name: 'OnlineUserManage' })

const loading = ref(false)
const list = ref<OnlineUser[]>([])

async function loadList() {
  loading.value = true
  try {
    list.value = await onlineUserApi.list()
  } finally {
    loading.value = false
  }
}

async function handleForceLogout(row: OnlineUser) {
  try {
    await ElMessageBox.confirm(
      `确定将「${row.nickname || row.username}」强制下线吗？其当前所有 Token 将立即失效。`,
      '提示',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await onlineUserApi.forceLogout(String(row.tokenId))
    ElMessage.success('已强制下线')
    await loadList()
  } catch {
    // 错误已由 axios 拦截器统一处理
  }
}

onMounted(loadList)
</script>

<style scoped lang="scss">
.page {
  padding: 16px;

  .page-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 14px;

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

    &__actions {
      display: flex;
      gap: 8px;
    }
  }

  .page-table {
    width: 100%;
  }

  .op-cell {
    display: flex;
    flex-wrap: nowrap;
    gap: 4px;
    :deep(.el-button + .el-button) {
      margin-left: 0;
    }
  }
}
</style>
