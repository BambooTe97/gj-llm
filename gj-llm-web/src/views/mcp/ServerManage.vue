<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, EditPen, Link, VideoPlay, RefreshRight } from '@element-plus/icons-vue'
import { mcpServerApi } from '@/api/modules/mcp'
import { useUserStore } from '@/stores/modules/user'
import type { McpServerConfig, McpTool } from '@/api/types'

const userStore = useUserStore()
const canEdit = computed(() => userStore.hasPermission('mcp:server:edit'))

const list = ref<McpServerConfig[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// ===== 新增/编辑抽屉 =====
const drawerVisible = ref(false)
const drawerTitle = ref('')
const saving = ref(false)
const isEdit = ref(false)
const editId = ref<string | null>(null)
const formRef = ref()
const form = ref({
  name: '',
  transport: 'STREAMABLE_HTTP' as 'STREAMABLE_HTTP' | 'SSE',
  endpoint: '',
  authHeaderName: '',
  authHeaderValue: '',
  enabled: true,
  remark: '',
})
/** 编辑时是否已配置凭据（决定凭据输入框占位文案） */
const hasAuth = ref(false)

const rules = {
  name: [
    { required: true, message: '请输入连接标识', trigger: 'blur' },
    { max: 64, message: '标识不超过 64 个字符', trigger: 'blur' },
  ],
  transport: [{ required: true, message: '请选择传输类型', trigger: 'change' }],
  endpoint: [
    { required: true, message: '请输入服务地址', trigger: 'blur' },
    { pattern: /^https?:\/\//, message: '地址需以 http(s):// 开头', trigger: 'blur' },
  ],
}

// ===== 连接测试 / 工具预览 =====
const testingId = ref<string | null>(null)
const toolsVisible = ref(false)
const toolsLoading = ref(false)
const toolsServerName = ref('')
const toolsList = ref<McpTool[]>([])

async function loadList() {
  loading.value = true
  try {
    const res = await mcpServerApi.getList(currentPage.value, pageSize.value)
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
  isEdit.value = false
  editId.value = null
  hasAuth.value = false
  drawerTitle.value = '新增外部服务'
  form.value = { name: '', transport: 'STREAMABLE_HTTP', endpoint: '', authHeaderName: '', authHeaderValue: '', enabled: true, remark: '' }
  drawerVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  editId.value = row.id
  hasAuth.value = row.hasAuth
  drawerTitle.value = '编辑外部服务'
  form.value = {
    name: row.name,
    transport: row.transport,
    endpoint: row.endpoint,
    authHeaderName: row.authHeaderName || '',
    authHeaderValue: '',
    enabled: row.enabled === 1,
    remark: row.remark || '',
  }
  drawerVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      name: form.value.name,
      transport: form.value.transport,
      endpoint: form.value.endpoint,
      authHeaderName: form.value.authHeaderName || undefined,
      // 掩码/空 = 保留原值（后端约定）
      authHeaderValue: form.value.authHeaderValue || undefined,
      enabled: form.value.enabled,
      remark: form.value.remark || undefined,
    }
    if (isEdit.value && editId.value) {
      await mcpServerApi.update(editId.value, payload)
      ElMessage.success('更新成功')
    } else {
      await mcpServerApi.create(payload)
      ElMessage.success('创建成功')
    }
    drawerVisible.value = false
    await loadList()
  } catch {
    /* 拦截器统一处理 */
  } finally {
    saving.value = false
  }
}

async function handleEnabledChange(row: any, val: boolean) {
  try {
    await mcpServerApi.updateEnabled(row.id, val)
    ElMessage.success(val ? '已启用，正在建立连接' : '已停用，连接已断开')
    await loadList() // 刷新健康状态
  } catch {
    row.enabled = val ? 0 : 1 // 失败回滚开关
  }
}

async function handleTest(row: any) {
  testingId.value = row.id
  try {
    const res = await mcpServerApi.test(row.id)
    const r = res.data.data
    if (r?.ok) {
      ElMessage.success(`连接成功，发现 ${r.toolCount} 个工具`)
    } else {
      ElMessage.error(r?.message || '连接失败')
    }
    await loadList() // 刷新健康状态/工具数
  } catch {
    /* 拦截器统一处理 */
  } finally {
    testingId.value = null
  }
}

async function handleShowTools(row: any) {
  toolsServerName.value = row.name
  toolsList.value = []
  toolsVisible.value = true
  toolsLoading.value = true
  try {
    const res = await mcpServerApi.listTools(row.id)
    toolsList.value = res.data.data || []
  } catch {
    /* 拦截器统一处理 */
  } finally {
    toolsLoading.value = false
  }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除外部服务 "${row.name}" 吗？`, '确认删除', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
    })
  } catch {
    return
  }
  try {
    await mcpServerApi.delete(row.id)
    ElMessage.success('删除成功')
    if (list.value.length === 1 && currentPage.value > 1) currentPage.value--
    await loadList()
  } catch {
    /* 拦截器统一处理 */
  }
}

function healthTagType(status: string) {
  if (status === 'UP') return 'success'
  if (status === 'DOWN') return 'danger'
  return 'info'
}
function healthText(status: string) {
  if (status === 'UP') return '在线'
  if (status === 'DOWN') return '离线'
  return '未知'
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
      <h2>外部服务管理</h2>
      <div class="page-header__actions">
        <el-button type="primary" :icon="Plus" v-permission="'mcp:server:create'" @click="handleCreate">
          新增服务
        </el-button>
      </div>
    </div>

    <el-alert
      class="page-alert"
      type="info"
      :closable="false"
      title="接入外部 MCP Server 后，其工具在对话的 tool-call 智能体中对模型可用；凭据加密存储，启停运行时生效"
      show-icon
    />

    <el-table :data="list" v-loading="loading" border stripe class="page-table">
      <el-table-column prop="name" label="连接标识" min-width="120" show-overflow-tooltip />
      <el-table-column label="传输" width="130" align="center">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ row.transport === 'SSE' ? 'SSE' : 'Streamable HTTP' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="endpoint" label="服务地址" min-width="220" show-overflow-tooltip />
      <el-table-column label="认证" width="80" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.hasAuth" size="small" type="success" effect="light">已配置</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="健康" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="healthTagType(row.healthStatus)" size="small" effect="light">
            {{ healthText(row.healthStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="工具数" width="80" align="center">
        <template #default="{ row }">{{ row.toolCount ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="启用" width="80" align="center">
        <template #default="{ row }">
          <el-switch
            v-if="canEdit"
            :model-value="row.enabled === 1"
            @change="(val: any) => handleEnabledChange(row, val)"
          />
          <el-tag v-else :type="row.enabled === 1 ? 'success' : 'danger'" size="small" effect="light">
            {{ row.enabled === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最近健康" width="150">
        <template #default="{ row }">{{ formatTime(row.lastHealthyAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <div class="op-cell">
            <el-button
              text type="primary" size="small" :icon="Link" :loading="testingId === row.id"
              v-permission="'mcp:server:edit'"
              @click="handleTest(row)"
            >
              测试
            </el-button>
            <el-button text type="primary" size="small" :icon="VideoPlay" @click="handleShowTools(row)">
              工具
            </el-button>
            <el-button text type="primary" size="small" :icon="EditPen" v-permission="'mcp:server:edit'" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button text type="danger" size="small" :icon="Delete" v-permission="'mcp:server:remove'" @click="handleDelete(row)">
              删除
            </el-button>
          </div>
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

    <!-- 新增/编辑抽屉 -->
    <el-drawer v-model="drawerVisible" :title="drawerTitle" direction="rtl" size="480px" :close-on-click-modal="false" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="连接标识" prop="name">
          <el-input v-model="form.name" placeholder="如：天气服务" maxlength="64" />
        </el-form-item>
        <el-form-item label="传输类型" prop="transport">
          <el-select v-model="form.transport" style="width: 100%">
            <el-option label="Streamable HTTP（推荐）" value="STREAMABLE_HTTP" />
            <el-option label="SSE（兼容旧版）" value="SSE" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务地址" prop="endpoint">
          <el-input v-model="form.endpoint" placeholder="http://host:port/mcp" />
        </el-form-item>
        <el-form-item label="认证头名">
          <el-input v-model="form.authHeaderName" placeholder="如 Authorization / X-Api-Key，可空" />
        </el-form-item>
        <el-form-item label="认证头值">
          <el-input
            v-model="form.authHeaderValue"
            type="password"
            show-password
            :placeholder="hasAuth ? '已配置，留空保持不变' : '明文提交，落库前加密'"
            autocomplete="new-password"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" placeholder="选填" />
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="form.enabled" active-text="保存后立即建立连接" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 工具清单预览 -->
    <el-dialog v-model="toolsVisible" :title="`工具清单 - ${toolsServerName}`" width="640px">
      <el-table :data="toolsList" v-loading="toolsLoading" border max-height="420">
        <el-table-column prop="name" label="工具名" width="180" show-overflow-tooltip>
          <template #default="{ row }"><code class="tool-name">{{ row.name }}</code></template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      </el-table>
      <div v-if="!toolsLoading && toolsList.length === 0" class="tools-empty">
        <el-icon :size="28"><RefreshRight /></el-icon>
        <p>暂无工具，请先测试连接</p>
      </div>
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
.op-cell {
  display: flex;
  flex-wrap: nowrap;
  gap: 4px;
  :deep(.el-button + .el-button) {
    margin-left: 0;
  }
}
.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
.tool-name {
  font-family: 'SF Mono', Menlo, Consolas, monospace;
  font-size: 12px;
  color: #0071e3;
}
.tools-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 24px 0;
  color: #aeaeb2;

  p {
    margin: 0;
    font-size: 13px;
  }
}
</style>
