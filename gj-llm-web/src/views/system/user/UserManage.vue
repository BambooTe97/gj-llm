<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Search, Delete, EditPen, Key } from '@element-plus/icons-vue'
import { userApi, roleApi } from '@/api/modules/system'
import type { Role, SysUser } from '@/api/types'

const list = ref<SysUser[]>([])
const roles = ref<Role[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const keyword = ref('')

const drawerVisible = ref(false)
const drawerTitle = ref('')
const formRef = ref<FormInstance>()
const saving = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)

const form = ref({
  username: '',
  password: '',
  nickname: '',
  email: '',
  status: 1,
  roleIds: [] as number[],
})

const rules = computed<FormRules>(() => ({
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度为 3-50 个字符', trigger: 'blur' },
  ],
  password: isEdit.value
    ? []
    : [
        { required: true, message: '请输入密码', trigger: 'blur' },
        { min: 6, message: '密码长度至少 6 位', trigger: 'blur' },
      ],
}))

async function loadList() {
  loading.value = true
  try {
    const res = await userApi.getList(currentPage.value, pageSize.value, keyword.value || undefined)
    const d = res.data.data
    list.value = d?.records || []
    total.value = d?.total || 0
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  const res = await roleApi.getList()
  roles.value = res.data.data || []
}

function handleSearch() {
  currentPage.value = 1
  loadList()
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
  drawerTitle.value = '新增用户'
  form.value = { username: '', password: '', nickname: '', email: '', status: 1, roleIds: [] }
  drawerVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  editId.value = row.id
  drawerTitle.value = '编辑用户'
  form.value = {
    username: row.username,
    password: '',
    nickname: row.nickname || '',
    email: row.email || '',
    status: row.status,
    roleIds: ((row.roles || []) as Role[]).map((r) => r.id),
  }
  drawerVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (isEdit.value && editId.value) {
      await userApi.update(editId.value, {
        nickname: form.value.nickname,
        email: form.value.email,
        status: form.value.status,
        roleIds: form.value.roleIds,
      })
      ElMessage.success('更新成功')
    } else {
      await userApi.create({
        username: form.value.username,
        password: form.value.password,
        nickname: form.value.nickname,
        email: form.value.email,
        roleIds: form.value.roleIds,
      })
      ElMessage.success('创建成功')
    }
    drawerVisible.value = false
    await loadList()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除用户 "${row.username}" 吗？`, '确认删除', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
    })
  } catch {
    return
  }
  try {
    await userApi.delete(row.id)
    ElMessage.success('删除成功')
    if (list.value.length === 1 && currentPage.value > 1) currentPage.value--
    await loadList()
  } catch {
    /* 拦截器统一处理 */
  }
}

async function handleResetPwd(row: any) {
  try {
    const { value } = await ElMessageBox.prompt('请输入新密码（至少 6 位）', `重置 "${row.username}" 的密码`, {
      inputPattern: /^.{6,}$/,
      inputErrorMessage: '密码至少 6 位',
      confirmButtonText: '重置',
      cancelButtonText: '取消',
    })
    await userApi.resetPassword(row.id, value)
    ElMessage.success('密码重置成功')
  } catch {
    /* 用户取消 */
  }
}

function roleNames(row: any): string {
  return ((row.roles || []) as Role[]).map((r) => r.name).join('、') || '-'
}

function formatTime(s?: string): string {
  if (!s) return '-'
  const d = new Date(s)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(() => {
  loadList()
  loadRoles()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>用户管理</h2>
      <div class="page-header__actions">
        <el-input
          v-model="keyword"
          placeholder="搜索用户名/昵称"
          clearable
          :prefix-icon="Search"
          style="width: 240px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button type="primary" :icon="Plus" v-permission="'system:user:add'" @click="handleCreate">
          新增用户
        </el-button>
      </div>
    </div>

    <el-table :data="list" v-loading="loading" border stripe class="page-table">
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="nickname" label="昵称" min-width="120" />
      <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
      <el-table-column label="角色" min-width="160">
        <template #default="{ row }">{{ roleNames(row) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small" effect="light">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="160">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <div class="op-cell">
            <el-button text type="primary" size="small" :icon="EditPen" v-permission="'system:user:edit'" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button text type="warning" size="small" :icon="Key" v-permission="'system:user:resetPwd'" @click="handleResetPwd(row)">
              重置密码
            </el-button>
            <el-button v-if="row.username !== 'admin'" text type="danger" size="small" :icon="Delete" v-permission="'system:user:remove'" @click="handleDelete(row)">
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
    <el-drawer
      v-model="drawerVisible"
      :title="drawerTitle"
      direction="rtl"
      size="460px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="isEdit" placeholder="登录用户名" />
        </el-form-item>
        <el-form-item v-if="!isEdit" label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="选填" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="选填" />
        </el-form-item>
        <el-form-item v-if="isEdit" label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple placeholder="分配角色" style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
        </div>
      </template>
    </el-drawer>
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
</style>
