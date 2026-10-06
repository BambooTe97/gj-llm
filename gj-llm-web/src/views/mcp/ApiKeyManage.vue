<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, CopyDocument, Key } from '@element-plus/icons-vue'
import { mcpApiKeyApi } from '@/api/modules/mcp'
import { useUserStore } from '@/stores/modules/user'
import type { McpApiKey } from '@/api/types'

const userStore = useUserStore()
const canEdit = computed(() => userStore.hasPermission('mcp:key:edit'))

const list = ref<McpApiKey[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// ===== 发放 Key 表单 =====
const createVisible = ref(false)
const saving = ref(false)
const formRef = ref()
const form = ref({ name: '', expiresInDays: undefined as number | undefined })
const rules = {
  name: [
    { required: true, message: '请输入 Key 用途名称', trigger: 'blur' },
    { max: 64, message: '名称不超过 64 个字符', trigger: 'blur' },
  ],
}

// ===== 发放成功：完整 Key 仅展示一次 =====
const fullKeyVisible = ref(false)
const fullKey = ref('')

async function loadList() {
  loading.value = true
  try {
    const res = await mcpApiKeyApi.getList(currentPage.value, pageSize.value)
    const d = res.data.data
    list.value = d?.records || []
    total.value = d?.total || 0
  } finally {
    loading.value = false
  }
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

function handleCreate() {
  form.value = { name: '', expiresInDays: undefined }
  createVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const res = await mcpApiKeyApi.create({
      name: form.value.name,
      expiresInDays: form.value.expiresInDays || undefined,
    })
    createVisible.value = false
    // 完整 Key 仅发放响应携带，立即弹出展示
    fullKey.value = res.data.data?.fullKey || ''
    fullKeyVisible.value = true
    await loadList()
  } catch {
    /* 拦截器统一处理 */
  } finally {
    saving.value = false
  }
}

async function handleCopyKey() {
  try {
    await navigator.clipboard.writeText(fullKey.value)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.warning('复制失败，请手动选择复制')
  }
}

async function handleStatusChange(row: any, val: number) {
  try {
    await mcpApiKeyApi.updateStatus(row.id, val)
    ElMessage.success(val === 1 ? '已启用' : '已停用')
  } catch {
    row.status = val === 1 ? 0 : 1 // 失败回滚开关
  }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(
      `确定删除 Key "${row.name}" 吗？使用该 Key 的客户端将立即失去访问能力。`,
      '确认删除',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await mcpApiKeyApi.delete(row.id)
    ElMessage.success('删除成功')
    if (list.value.length === 1 && currentPage.value > 1) currentPage.value--
    await loadList()
  } catch {
    /* 拦截器统一处理 */
  }
}

function formatTime(s?: string | null): string {
  if (!s) return '-'
  const d = new Date(s)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(loadList)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>API Key 管理</h2>
      <div class="page-header__actions">
        <el-button type="primary" :icon="Plus" v-permission="'mcp:key:create'" @click="handleCreate">
          发放 Key
        </el-button>
      </div>
    </div>

    <el-alert
      class="page-alert"
      type="info"
      :closable="false"
      title="外部客户端通过 API Key 访问平台 MCP 服务（/open/mcp），权限与所属用户的知识库可见域一致"
      show-icon
    />

    <el-table :data="list" v-loading="loading" border stripe class="page-table">
      <el-table-column prop="name" label="用途名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="username" label="所属用户" min-width="110" />
      <el-table-column label="Key 前缀" min-width="180">
        <template #default="{ row }">
          <code class="key-prefix">{{ row.keyPrefix }}********</code>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-switch
            v-if="canEdit"
            :model-value="row.status"
            :active-value="1"
            :inactive-value="0"
            @change="(val: any) => handleStatusChange(row, val)"
          />
          <el-tag v-else :type="row.status === 1 ? 'success' : 'danger'" size="small" effect="light">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="过期时间" width="160">
        <template #default="{ row }">
          {{ row.expiresAt ? formatTime(row.expiresAt) : '永不过期' }}
        </template>
      </el-table-column>
      <el-table-column label="最近使用" width="160">
        <template #default="{ row }">{{ formatTime(row.lastUsedAt) }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="160">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button
            text type="danger" size="small" :icon="Delete"
            v-permission="'mcp:key:remove'"
            @click="handleDelete(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="page-pagination" v-if="total > 0">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <!-- 发放 Key 抽屉 -->
    <el-drawer v-model="createVisible" title="发放 API Key" direction="rtl" size="420px" :close-on-click-modal="false" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用途名称" prop="name">
          <el-input v-model="form.name" placeholder="如：客服机器人接入" maxlength="64" />
        </el-form-item>
        <el-form-item label="有效天数">
          <el-input-number v-model="form.expiresInDays" :min="1" :max="3650" placeholder="留空=永不过期" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="createVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSubmit">发放</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 完整 Key 一次性展示 -->
    <el-dialog v-model="fullKeyVisible" title="Key 发放成功" width="560px" :close-on-click-modal="false">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="请立即保存完整 Key"
        description="平台只保存哈希摘要，完整 Key 关闭本窗口后无法再次查看，丢失只能吊销重发。"
      />
      <div class="fullkey-box">
        <code class="fullkey-box__code">{{ fullKey }}</code>
        <el-button type="primary" :icon="CopyDocument" size="small" @click="handleCopyKey">复制</el-button>
      </div>
      <template #footer>
        <el-button type="primary" :icon="Key" @click="fullKeyVisible = false">我已保存</el-button>
      </template>
    </el-dialog>
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

  h2 {
    font-size: 20px;
    font-weight: 700;
    color: #1d1d1f;
    margin: 0;
  }
  &__actions {
    display: flex;
    gap: 12px;
    align-items: center;
  }
}
.page-alert {
  margin-bottom: 14px;
  flex-shrink: 0;
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
.key-prefix {
  font-family: 'SF Mono', Menlo, Consolas, monospace;
  font-size: 12px;
  color: #515154;
  background: #f5f5f7;
  padding: 2px 8px;
  border-radius: 6px;
}
.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
.fullkey-box {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;

  &__code {
    flex: 1;
    font-family: 'SF Mono', Menlo, Consolas, monospace;
    font-size: 12px;
    word-break: break-all;
    line-height: 1.6;
    color: #1d1d1f;
    background: #f5f5f7;
    border: 1px solid #e5e5ea;
    border-radius: 8px;
    padding: 10px 12px;
  }
}
</style>
