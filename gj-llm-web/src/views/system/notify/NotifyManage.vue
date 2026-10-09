<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Delete, Promotion, Search } from '@element-plus/icons-vue'
import { userApi } from '@/api/modules/system'
import { notifyAdminApi, type NotifyAdminItem } from '@/api/modules/notify'

// ==================== 列表状态 ====================
const list = ref<NotifyAdminItem[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const keyword = ref('')
const levelFilter = ref('')
const readFilter = ref<number | ''>('')

const levelMeta: Record<string, { label: string; tag: 'info' | 'success' | 'warning' | 'danger' }> = {
  info: { label: '提示', tag: 'info' },
  success: { label: '成功', tag: 'success' },
  warning: { label: '警告', tag: 'warning' },
  error: { label: '错误', tag: 'danger' },
}

async function loadList() {
  loading.value = true
  try {
    const { records, total: t } = await notifyAdminApi.page({
      page: currentPage.value,
      size: pageSize.value,
      keyword: keyword.value || undefined,
      level: levelFilter.value || undefined,
      readFlag: readFilter.value === '' ? undefined : Number(readFilter.value),
    })
    list.value = records
    total.value = t
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadList()
}

function handlePageChange() {
  loadList()
}

async function handleRemove(row: NotifyAdminItem) {
  try {
    await ElMessageBox.confirm(`确定删除通知 "${row.title}" 吗？`, '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await notifyAdminApi.remove(row.id)
    ElMessage.success('删除成功')
    if (list.value.length === 1 && currentPage.value > 1) currentPage.value--
    await loadList()
  } catch {
    /* 拦截器统一处理 */
  }
}

// ==================== 发送通知 ====================
const sendVisible = ref(false)
const sending = ref(false)
const sendFormRef = ref<FormInstance>()
const sendForm = ref({ userId: '', title: '', content: '', level: 'info' })

interface UserOption {
  id: string
  label: string
}
const userOptions = ref<UserOption[]>([])
const userLoading = ref(false)

const sendRules: FormRules = {
  userId: [{ required: true, message: '请选择接收用户', trigger: 'change' }],
  title: [
    { required: true, message: '请输入通知标题', trigger: 'blur' },
    { max: 128, message: '标题最长 128 字符', trigger: 'blur' },
  ],
  content: [{ max: 1024, message: '内容最长 1024 字符', trigger: 'blur' }],
}

/** 用户远程搜索（复用系统用户分页接口） */
async function searchUsers(query: string) {
  userLoading.value = true
  try {
    const res = await userApi.getList(1, 50, query || undefined)
    userOptions.value = (res.data.data?.records ?? []).map((u) => ({
      id: String(u.id),
      label: u.nickname ? `${u.nickname}（${u.username}）` : u.username,
    }))
  } finally {
    userLoading.value = false
  }
}

function handleSend() {
  sendForm.value = { userId: '', title: '', content: '', level: 'info' }
  userOptions.value = []
  void searchUsers('')
  sendVisible.value = true
}

async function submitSend() {
  const valid = await sendFormRef.value?.validate().catch(() => false)
  if (!valid) return
  sending.value = true
  try {
    await notifyAdminApi.send({ ...sendForm.value })
    ElMessage.success('发送成功（在线实时推送，离线落库待拉取）')
    sendVisible.value = false
    currentPage.value = 1
    await loadList()
  } catch {
    /* 拦截器统一处理 */
  } finally {
    sending.value = false
  }
}

onMounted(loadList)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>通知管理</h2>
      <div class="page-header__actions">
        <el-input
          v-model="keyword"
          placeholder="搜索标题/内容"
          clearable
          :prefix-icon="Search"
          style="width: 220px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select v-model="levelFilter" placeholder="级别" clearable style="width: 110px" @change="handleSearch">
          <el-option label="提示" value="info" />
          <el-option label="成功" value="success" />
          <el-option label="警告" value="warning" />
          <el-option label="错误" value="error" />
        </el-select>
        <el-select
          v-model="readFilter"
          placeholder="已读状态"
          clearable
          style="width: 110px"
          @change="handleSearch"
        >
          <el-option label="未读" :value="0" />
          <el-option label="已读" :value="1" />
        </el-select>
        <el-button type="primary" :icon="Promotion" @click="handleSend">发送通知</el-button>
      </div>
    </div>

    <el-table :data="list" v-loading="loading" border stripe class="page-table">
      <el-table-column label="接收人" min-width="140">
        <template #default="{ row }">
          {{ row.nickname ? `${row.nickname}（${row.username}）` : row.username }}
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
      <el-table-column prop="content" label="内容" min-width="220" show-overflow-tooltip />
      <el-table-column label="级别" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="levelMeta[row.level]?.tag || 'info'" size="small" effect="light">
            {{ levelMeta[row.level]?.label || row.level }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.readFlag === 1 ? 'success' : 'danger'" size="small" effect="light">
            {{ row.readFlag === 1 ? '已读' : '未读' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="发送时间" width="170" />
      <el-table-column prop="readAt" label="阅读时间" width="170">
        <template #default="{ row }">{{ row.readAt || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <div class="op-cell">
            <el-button v-permission="'notify:manage'" text type="danger" size="small" :icon="Delete" @click="handleRemove(row)">删除</el-button>
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
        @size-change="handlePageChange"
      />
    </div>

    <!-- 发送通知对话框 -->
    <el-dialog v-model="sendVisible" title="发送通知" width="520px" :close-on-click-modal="false" destroy-on-close>
      <el-form ref="sendFormRef" :model="sendForm" :rules="sendRules" label-width="90px">
        <el-form-item label="接收用户" prop="userId">
          <el-select
            v-model="sendForm.userId"
            filterable
            remote
            :remote-method="searchUsers"
            :loading="userLoading"
            placeholder="输入用户名搜索"
            style="width: 100%"
          >
            <el-option v-for="u in userOptions" :key="u.id" :label="u.label" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="sendForm.title" placeholder="通知标题" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input
            v-model="sendForm.content"
            type="textarea"
            :rows="4"
            placeholder="通知内容"
            maxlength="1024"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="级别" prop="level">
          <el-radio-group v-model="sendForm.level">
            <el-radio value="info">提示</el-radio>
            <el-radio value="success">成功</el-radio>
            <el-radio value="warning">警告</el-radio>
            <el-radio value="error">错误</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sendVisible = false">取消</el-button>
        <el-button type="primary" :loading="sending" @click="submitSend">发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.page {
  padding: 20px 24px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;

  h2 {
    margin: 0;
    font-size: 18px;
    font-weight: 600;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 10px;
  }
}

.page-table {
  width: 100%;
}

.page-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.op-cell {
  display: flex;
  align-items: center;
}
</style>
