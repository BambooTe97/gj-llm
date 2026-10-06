<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { mcpAuditApi } from '@/api/modules/mcp'
import type { McpAuditLog } from '@/api/types'

const list = ref<McpAuditLog[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

// 筛选条件
const direction = ref('')
const status = ref('')
const toolName = ref('')

async function loadList() {
  loading.value = true
  try {
    const res = await mcpAuditApi.getList(currentPage.value, pageSize.value, {
      direction: direction.value || undefined,
      status: status.value || undefined,
      toolName: toolName.value || undefined,
    })
    const d = res.data.data
    list.value = d?.records || []
    total.value = d?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadList()
}
function handleReset() {
  direction.value = ''
  status.value = ''
  toolName.value = ''
  handleSearch()
}
function handlePageChange(p: number) {
  currentPage.value = p
  loadList()
}
function handleSizeChange(s: number) {
  pageSize.value = s
  currentPage.value = 1
  loadList()
}

function directionText(d: string) {
  return d === 'SERVER_CALLED' ? '被外部调用' : '调用外部'
}
function directionTagType(d: string) {
  return d === 'SERVER_CALLED' ? 'warning' : 'primary'
}
function statusTagType(s: string) {
  if (s === 'SUCCESS') return 'success'
  if (s === 'TIMEOUT') return 'warning'
  return 'danger'
}
function statusText(s: string) {
  if (s === 'SUCCESS') return '成功'
  if (s === 'TIMEOUT') return '超时'
  return '失败'
}

function formatTime(s?: string | null): string {
  if (!s) return '-'
  const d = new Date(s)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

onMounted(loadList)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>审计日志</h2>
      <div class="page-header__filters">
        <el-select v-model="direction" placeholder="方向" clearable style="width: 130px" @change="handleSearch">
          <el-option label="被外部调用" value="SERVER_CALLED" />
          <el-option label="调用外部" value="CLIENT_CALL" />
        </el-select>
        <el-select v-model="status" placeholder="结果" clearable style="width: 110px" @change="handleSearch">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="ERROR" />
          <el-option label="超时" value="TIMEOUT" />
        </el-select>
        <el-input
          v-model="toolName"
          placeholder="工具名"
          clearable
          :prefix-icon="Search"
          style="width: 180px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
    </div>

    <el-table :data="list" v-loading="loading" border stripe class="page-table">
      <el-table-column label="时间" width="160">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="方向" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="directionTagType(row.direction)" size="small" effect="light">
            {{ directionText(row.direction) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="toolName" label="工具" min-width="140" show-overflow-tooltip />
      <el-table-column label="来源/目标" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.direction === 'SERVER_CALLED' ? (row.username || '-') : (row.serverName || '-') }}
        </template>
      </el-table-column>
      <el-table-column prop="clientIp" label="客户端 IP" width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.clientIp || '-' }}</template>
      </el-table-column>
      <el-table-column prop="paramsDigest" label="参数摘要" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.paramsDigest || '-' }}</template>
      </el-table-column>
      <el-table-column label="结果" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.resultStatus)" size="small" effect="light">
            {{ statusText(row.resultStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="耗时" width="90" align="right">
        <template #default="{ row }">{{ row.costMs }}ms</template>
      </el-table-column>
      <el-table-column prop="errorMessage" label="错误信息" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.errorMessage || '-' }}</template>
      </el-table-column>
    </el-table>

    <div class="page-pagination" v-if="total > 0">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<style lang="scss" scoped>
.page {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 22px;
  overflow: auto;
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: 12px;

  h2 {
    font-size: 20px;
    font-weight: 700;
    color: #1d1d1f;
    margin: 0;
  }
  &__filters {
    display: flex;
    gap: 10px;
    align-items: center;
    flex-wrap: wrap;
  }
}
.page-table {
  flex: 1;
  border-radius: 12px;
  overflow: hidden;
}
.page-pagination {
  display: flex;
  justify-content: center;
  margin-top: 18px;
  flex-shrink: 0;
}
</style>
