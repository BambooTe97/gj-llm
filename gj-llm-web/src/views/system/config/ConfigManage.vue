<template>
  <div class="page">
    <div class="page-header">
      <div class="page-header__title">
        <h2>参数配置</h2>
        <span class="page-header__desc">系统级参数（键值对），运行时可调无需重启</span>
      </div>
      <div class="page-header__actions">
        <el-button type="primary" :icon="Plus" v-permission="'system:config:add'" @click="openCreate">新增参数</el-button>
      </div>
    </div>

    <div class="page-filter">
      <el-input
        v-model="keyword"
        placeholder="参数名称 / 键名"
        clearable
        style="width: 240px"
        @keyup.enter="handleSearch"
      />
      <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" class="page-table">
      <el-table-column prop="name" label="参数名称" min-width="150" />
      <el-table-column prop="configKey" label="键名" min-width="180">
        <template #default="{ row }">
          <el-text type="primary" size="small">{{ row.configKey }}</el-text>
        </template>
      </el-table-column>
      <el-table-column prop="configValue" label="键值" min-width="160" show-overflow-tooltip />
      <el-table-column label="内置" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.builtIn === 1 ? 'warning' : 'info'" size="small">{{ row.builtIn === 1 ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }: { row: any }">
          <div class="op-cell">
            <el-button text type="primary" size="small" :icon="EditPen" v-permission="'system:config:edit'" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button
              v-if="row.builtIn !== 1"
              text
              type="danger"
              size="small"
              :icon="Delete"
              v-permission="'system:config:remove'"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px" :close-on-click-modal="false" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="参数名称" prop="name">
          <el-input v-model="form.name" placeholder="如：站点名称" maxlength="50" />
        </el-form-item>
        <el-form-item label="键名" prop="configKey">
          <el-input
            v-model="form.configKey"
            placeholder="如：sys.site.name"
            maxlength="100"
            :disabled="isEdit"
          />
        </el-form-item>
        <el-form-item label="键值" prop="configValue">
          <el-input v-model="form.configValue" placeholder="参数值" maxlength="200" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, EditPen, Plus, Search } from '@element-plus/icons-vue'
import { configApi, type SysConfigItem } from '@/api/modules/config'

defineOptions({ name: 'ConfigManage' })

const loading = ref(false)
const list = ref<SysConfigItem[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const keyword = ref('')

const dialogVisible = ref(false)
const dialogTitle = ref('新增参数')
const submitting = ref(false)
const formRef = ref()
const isEdit = ref(false)
const editId = ref('')

const form = ref({ name: '', configKey: '', configValue: '', remark: '' })
const rules = {
  name: [{ required: true, message: '请输入参数名称', trigger: 'blur' }],
  configKey: [{ required: true, message: '请输入键名', trigger: 'blur' }],
  configValue: [{ required: true, message: '请输入键值', trigger: 'blur' }],
}

async function loadList() {
  loading.value = true
  try {
    const d = await configApi.page({
      page: currentPage.value,
      size: pageSize.value,
      keyword: keyword.value || undefined,
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

function openCreate() {
  isEdit.value = false
  editId.value = ''
  dialogTitle.value = '新增参数'
  form.value = { name: '', configKey: '', configValue: '', remark: '' }
  dialogVisible.value = true
}

function openEdit(row: SysConfigItem) {
  isEdit.value = true
  editId.value = String(row.id)
  dialogTitle.value = '编辑参数'
  form.value = { name: row.name, configKey: row.configKey, configValue: row.configValue, remark: row.remark ?? '' }
  dialogVisible.value = true
}

async function submit() {
  const ok = await formRef.value?.validate().catch(() => false)
  if (!ok) return
  submitting.value = true
  try {
    if (isEdit.value) {
      // configKey 不可改，编辑时后端忽略该字段
      await configApi.update(editId.value, form.value)
      ElMessage.success('更新成功')
    } else {
      await configApi.create(form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await loadList()
  } catch {
    // 错误已由 axios 拦截器统一处理
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: SysConfigItem) {
  try {
    await ElMessageBox.confirm(`确定删除参数「${row.name}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await configApi.remove(String(row.id))
    ElMessage.success('删除成功')
    if (list.value.length === 1 && currentPage.value > 1) currentPage.value--
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
}
</style>
