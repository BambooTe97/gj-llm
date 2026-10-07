<template>
  <div class="page">
    <div class="page-header">
      <div class="page-header__title">
        <h2>操作日志</h2>
        <span class="page-header__desc">管理端写操作审计（@OperLog 切面自动记录，敏感字段已脱敏）</span>
      </div>
      <div class="page-header__actions">
        <el-button :icon="Refresh" @click="loadList">刷新</el-button>
        <el-button type="danger" :icon="Delete" v-permission="'system:log:clear'" @click="handleClear">清空日志</el-button>
      </div>
    </div>

    <div class="page-filter">
      <el-input v-model="module" placeholder="模块（如：认证管理）" clearable style="width: 180px" @keyup.enter="handleSearch" />
      <el-input v-model="operator" placeholder="操作人" clearable style="width: 150px" @keyup.enter="handleSearch" />
      <el-select v-model="status" placeholder="状态" clearable style="width: 120px" @change="handleSearch">
        <el-option label="成功" :value="1" />
        <el-option label="失败" :value="0" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" class="page-table">
      <el-table-column prop="module" label="模块" width="110" />
      <el-table-column prop="type" label="类型" width="110" />
      <el-table-column prop="method" label="方法" min-width="200" show-overflow-tooltip />
      <el-table-column prop="operator" label="操作人" width="110" />
      <el-table-column prop="ip" label="IP" width="130" />
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{ row.status === 1 ? '成功' : '失败' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="耗时" width="90" align="center">
        <template #default="{ row }">
          <span>{{ row.costMs }}ms</span>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="时间" width="180" />
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }: { row: any }">
          <div class="op-cell">
            <el-button text type="primary" size="small" @click="openDetail(row)">详情</el-button>
          </div>
        </template>
      </el-table-column>
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

    <el-drawer v-model="detailVisible" title="日志详情" direction="rtl" size="560px" destroy-on-close>
      <template v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="模块">{{ detail.module }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ detail.type }}</el-descriptions-item>
          <el-descriptions-item label="方法">{{ detail.method }}</el-descriptions-item>
          <el-descriptions-item label="请求">
            <el-tag size="small" class="uri-tag">{{ detail.requestMethod }}</el-tag>
            <span class="uri-text">{{ detail.requestUri }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="操作人">{{ detail.operator }}（ID: {{ detail.userId ?? '-' }}）</el-descriptions-item>
          <el-descriptions-item label="IP">{{ detail.ip }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="detail.status === 1 ? 'success' : 'danger'" size="small">
              {{ detail.status === 1 ? '成功' : '失败' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="耗时">{{ detail.costMs }}ms</el-descriptions-item>
          <el-descriptions-item label="时间">{{ detail.createdAt }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.errorMsg" label="错误信息">
            <pre class="log-pre log-pre--error">{{ detail.errorMsg }}</pre>
          </el-descriptions-item>
        </el-descriptions>

        <template v-if="detail.params">
          <h4 class="detail-section">请求参数</h4>
          <pre class="log-pre">{{ formatJson(detail.params) }}</pre>
        </template>
        <template v-if="detail.result">
          <h4 class="detail-section">返回结果</h4>
          <pre class="log-pre">{{ formatJson(detail.result) }}</pre>
        </template>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Refresh, Search } from '@element-plus/icons-vue'
import { operLogApi, type OperLogItem } from '@/api/modules/log'

defineOptions({ name: 'OperLogManage' })

const loading = ref(false)
const list = ref<OperLogItem[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const module = ref('')
const operator = ref('')
const status = ref<number | undefined>(undefined)

const detailVisible = ref(false)
const detail = ref<OperLogItem | null>(null)

async function loadList() {
  loading.value = true
  try {
    const d = await operLogApi.page({
      page: currentPage.value,
      size: pageSize.value,
      module: module.value || undefined,
      operator: operator.value || undefined,
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

function openDetail(row: OperLogItem) {
  detail.value = row
  detailVisible.value = true
}

/** params/result 存的是 JSON 字符串；能解析就美化缩进，不能就原样显示 */
function formatJson(raw: string): string {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

async function handleClear() {
  try {
    await ElMessageBox.confirm('确定清空全部操作日志吗？该操作不可恢复。', '警告', { type: 'warning' })
  } catch {
    return
  }
  try {
    await operLogApi.clearAll()
    ElMessage.success('已清空')
    currentPage.value = 1
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

  .page-filter {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 14px;
  }

  .page-table {
    width: 100%;
  }

  .page-pagination {
    display: flex;
    justify-content: flex-end;
    margin-top: 14px;
  }

  .op-cell {
    display: flex;
    flex-wrap: nowrap;
    gap: 4px;
    :deep(.el-button + .el-button) {
      margin-left: 0;
    }
  }

  .uri-tag {
    margin-right: 6px;
  }

  .uri-text {
    font-size: 12px;
    word-break: break-all;
  }

  .detail-section {
    margin: 14px 0 6px;
    font-size: 13px;
  }

  .log-pre {
    margin: 0;
    padding: 8px;
    max-height: 260px;
    overflow: auto;
    font-size: 12px;
    line-height: 1.5;
    background: var(--el-fill-color-light);
    border-radius: 4px;
    white-space: pre-wrap;
    word-break: break-all;

    &--error {
      color: var(--el-color-danger);
    }
  }
}
</style>
