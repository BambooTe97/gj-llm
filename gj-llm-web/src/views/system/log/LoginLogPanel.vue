<template>
  <div class="log-panel">
    <div class="log-panel__toolbar">
      <el-input v-model="username" placeholder="登录账号" clearable style="width: 160px" @keyup.enter="handleSearch" />
      <el-input v-model="ip" placeholder="IP" clearable style="width: 140px" @keyup.enter="handleSearch" />
      <el-select v-model="status" placeholder="状态" clearable style="width: 120px" @change="handleSearch">
        <el-option label="成功" :value="1" />
        <el-option label="失败" :value="0" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
      <div class="log-panel__actions">
        <el-button :icon="Refresh" @click="loadList">刷新</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="list" class="page-table">
      <el-table-column prop="username" label="登录账号" width="130" />
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{ row.status === 1 ? '成功' : '失败' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="ip" label="IP" width="130" />
      <el-table-column prop="browser" label="浏览器" width="130" show-overflow-tooltip />
      <el-table-column prop="os" label="操作系统" width="140" show-overflow-tooltip />
      <el-table-column prop="msg" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column prop="loginTime" label="登录时间" width="180" />
    </el-table>

    <div class="page-pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="loadList"
        @size-change="handleSearch"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { loginLogApi, type LoginLogItem } from '@/api/modules/loginlog'

defineOptions({ name: 'LoginLogPanel' })

const loading = ref(false)
const list = ref<LoginLogItem[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const username = ref('')
const ip = ref('')
const status = ref<number | undefined>(undefined)

async function loadList() {
  loading.value = true
  try {
    const d = await loginLogApi.page({
      page: currentPage.value,
      size: pageSize.value,
      username: username.value || undefined,
      ip: ip.value || undefined,
      status: status.value,
    })
    list.value = d.records
    total.value = d.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  void loadList()
}

onMounted(loadList)
</script>

<style scoped lang="scss">
.log-panel {
  padding-top: 8px;

  .log-panel__toolbar {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 14px;

    .log-panel__actions {
      margin-left: auto;
      display: flex;
      gap: 8px;
    }
  }

  .page-table {
    width: 100%;
  }

  .page-pagination {
    display: flex;
    justify-content: flex-end;
    margin-top: 14px;
  }
}
</style>
