<template>
  <div class="page">
    <div class="page-header">
      <div class="page-header__title">
        <h2>字典管理</h2>
        <span class="page-header__desc">运行时可维护的键值字典，业务下拉/枚举统一从这里取</span>
      </div>
      <div class="page-header__actions">
        <el-button type="primary" :icon="Plus" v-permission="'system:dict:add'" @click="openTypeCreate">新增类型</el-button>
      </div>
    </div>

    <!-- 过滤条 -->
    <div class="page-filter">
      <el-input
        v-model="typeKeyword"
        placeholder="名称 / 类型编码"
        clearable
        style="width: 220px"
        @keyup.enter="handleTypeSearch"
      />
      <el-select v-model="typeStatus" placeholder="状态" clearable style="width: 120px" @change="handleTypeSearch">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="handleTypeSearch">查询</el-button>
    </div>

    <!-- 字典类型表 -->
    <el-table v-loading="loading" :data="typeList" class="page-table">
      <el-table-column prop="name" label="字典名称" min-width="140" />
      <el-table-column prop="type" label="类型编码" min-width="160">
        <template #default="{ row }">
          <el-text type="primary" size="small">{{ row.type }}</el-text>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }: { row: any }">
          <div class="op-cell">
            <el-button text type="primary" size="small" :icon="List" @click="openDataDrawer(row)">字典数据</el-button>
            <el-button text type="primary" size="small" :icon="EditPen" v-permission="'system:dict:edit'" @click="openTypeEdit(row)">
              编辑
            </el-button>
            <el-button text type="danger" size="small" :icon="Delete" v-permission="'system:dict:remove'" @click="handleTypeDelete(row)">
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
        @current-change="loadTypes"
        @size-change="handleTypeSearch"
      />
    </div>

    <!-- 类型新增/编辑 -->
    <el-dialog v-model="typeDialogVisible" :title="typeDialogTitle" width="480px" :close-on-click-modal="false" destroy-on-close>
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="90px">
        <el-form-item label="字典名称" prop="name">
          <el-input v-model="typeForm.name" placeholder="如：用户状态" maxlength="50" />
        </el-form-item>
        <el-form-item label="类型编码" prop="type">
          <el-input
            v-model="typeForm.type"
            placeholder="如：sys_user_status"
            maxlength="64"
            :disabled="isTypeEdit"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="typeForm.status" :active-value="1" :inactive-value="0" active-text="启用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="typeForm.remark" type="textarea" :rows="2" maxlength="200" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="typeSubmitting" @click="submitType">确定</el-button>
      </template>
    </el-dialog>

    <!-- 字典数据抽屉 -->
    <el-drawer
      v-model="dataDrawerVisible"
      :title="`字典数据 - ${activeType?.name || ''}（${activeType?.type || ''}）`"
      direction="rtl"
      size="640px"
      destroy-on-close
    >
      <div class="drawer-toolbar">
        <el-input
          v-model="dataKeyword"
          placeholder="标签 / 键值"
          clearable
          style="width: 180px"
          @keyup.enter="handleDataSearch"
        />
        <el-button v-permission="'system:dict:add'" type="primary" :icon="Plus" size="small" @click="openDataCreate">
          新增数据
        </el-button>
      </div>

      <el-table v-loading="dataLoading" :data="dataList" size="small" class="page-table">
        <el-table-column prop="label" label="标签" min-width="110" />
        <el-table-column prop="dictValue" label="键值" min-width="100" />
        <el-table-column prop="sort" label="排序" width="60" align="center" />
        <el-table-column label="状态" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="100" show-overflow-tooltip />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }: { row: any }">
            <div class="op-cell">
              <el-button text type="primary" size="small" v-permission="'system:dict:edit'" @click="openDataEdit(row)">编辑</el-button>
              <el-button text type="danger" size="small" v-permission="'system:dict:remove'" @click="handleDataDelete(row)">
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="page-pagination">
        <el-pagination
          v-model:current-page="dataCurrentPage"
          v-model:page-size="dataPageSize"
          :total="dataTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, prev, pager, next"
          background
          small
          @current-change="loadDatas"
          @size-change="handleDataSearch"
        />
      </div>
    </el-drawer>

    <!-- 数据新增/编辑 -->
    <el-dialog v-model="dataDialogVisible" :title="dataDialogTitle" width="440px" :close-on-click-modal="false" destroy-on-close append-to-body>
      <el-form ref="dataFormRef" :model="dataForm" :rules="dataRules" label-width="80px">
        <el-form-item label="标签" prop="label">
          <el-input v-model="dataForm.label" placeholder="显示名称" maxlength="50" />
        </el-form-item>
        <el-form-item label="键值" prop="dictValue">
          <el-input v-model="dataForm.dictValue" placeholder="存储值" maxlength="64" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="dataForm.sort" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="dataForm.status" :active-value="1" :inactive-value="0" active-text="启用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dataForm.remark" type="textarea" :rows="2" maxlength="200" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dataDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dataSubmitting" @click="submitData">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, EditPen, List, Plus, Search } from '@element-plus/icons-vue'
import { dictDataApi, dictTypeApi, type DictDataItem, type DictTypeItem } from '@/api/modules/dict'
import { useDictStore } from '@/stores/modules/dict'

defineOptions({ name: 'DictManage' })

const dictStore = useDictStore()

// ==================== 字典类型 ====================
const loading = ref(false)
const typeList = ref<DictTypeItem[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const typeKeyword = ref('')
const typeStatus = ref<number | undefined>(undefined)

const typeDialogVisible = ref(false)
const typeDialogTitle = ref('新增类型')
const typeSubmitting = ref(false)
const typeFormRef = ref()
const isTypeEdit = ref(false)
const editTypeId = ref('')

const typeForm = ref({ name: '', type: '', status: 1, remark: '' })
const typeRules = {
  name: [{ required: true, message: '请输入字典名称', trigger: 'blur' }],
  type: [
    { required: true, message: '请输入类型编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: '字母开头，仅含字母/数字/下划线', trigger: 'blur' },
  ],
}

async function loadTypes() {
  loading.value = true
  try {
    const d = await dictTypeApi.page({
      page: currentPage.value,
      size: pageSize.value,
      keyword: typeKeyword.value || undefined,
      status: typeStatus.value,
    })
    typeList.value = d.records
    total.value = d.total
  } finally {
    loading.value = false
  }
}

function handleTypeSearch() {
  currentPage.value = 1
  void loadTypes()
}

function openTypeCreate() {
  isTypeEdit.value = false
  editTypeId.value = ''
  typeDialogTitle.value = '新增类型'
  typeForm.value = { name: '', type: '', status: 1, remark: '' }
  typeDialogVisible.value = true
}

function openTypeEdit(row: DictTypeItem) {
  isTypeEdit.value = true
  editTypeId.value = String(row.id)
  typeDialogTitle.value = '编辑类型'
  typeForm.value = { name: row.name, type: row.type, status: row.status, remark: row.remark ?? '' }
  typeDialogVisible.value = true
}

async function submitType() {
  const ok = await typeFormRef.value?.validate().catch(() => false)
  if (!ok) return
  typeSubmitting.value = true
  try {
    if (isTypeEdit.value) {
      await dictTypeApi.update(editTypeId.value, typeForm.value)
      ElMessage.success('更新成功')
    } else {
      await dictTypeApi.create(typeForm.value)
      ElMessage.success('创建成功')
    }
    typeDialogVisible.value = false
    dictStore.forceReload()
    await loadTypes()
  } catch {
    // 错误已由 axios 拦截器统一处理
  } finally {
    typeSubmitting.value = false
  }
}

async function handleTypeDelete(row: DictTypeItem) {
  try {
    await ElMessageBox.confirm(`确定删除字典类型「${row.name}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await dictTypeApi.remove(String(row.id))
    ElMessage.success('删除成功')
    dictStore.forceReload()
    if (typeList.value.length === 1 && currentPage.value > 1) currentPage.value--
    await loadTypes()
  } catch {
    // 错误已由 axios 拦截器统一处理
  }
}

// ==================== 字典数据 ====================
const dataDrawerVisible = ref(false)
const activeType = ref<DictTypeItem | null>(null)
const dataLoading = ref(false)
const dataList = ref<DictDataItem[]>([])
const dataCurrentPage = ref(1)
const dataPageSize = ref(10)
const dataTotal = ref(0)
const dataKeyword = ref('')

const dataDialogVisible = ref(false)
const dataDialogTitle = ref('新增数据')
const dataSubmitting = ref(false)
const dataFormRef = ref()
const isDataEdit = ref(false)
const editDataId = ref('')

const dataForm = ref({ label: '', dictValue: '', sort: 0, status: 1, remark: '' })
const dataRules = {
  label: [{ required: true, message: '请输入标签', trigger: 'blur' }],
  dictValue: [{ required: true, message: '请输入键值', trigger: 'blur' }],
}

function openDataDrawer(row: DictTypeItem) {
  activeType.value = row
  dataCurrentPage.value = 1
  dataKeyword.value = ''
  dataDrawerVisible.value = true
  void loadDatas()
}

async function loadDatas() {
  if (!activeType.value) return
  dataLoading.value = true
  try {
    const d = await dictDataApi.page({
      page: dataCurrentPage.value,
      size: dataPageSize.value,
      dictType: activeType.value.type,
      keyword: dataKeyword.value || undefined,
    })
    dataList.value = d.records
    dataTotal.value = d.total
  } finally {
    dataLoading.value = false
  }
}

function handleDataSearch() {
  dataCurrentPage.value = 1
  void loadDatas()
}

function openDataCreate() {
  isDataEdit.value = false
  editDataId.value = ''
  dataDialogTitle.value = '新增数据'
  dataForm.value = { label: '', dictValue: '', sort: 0, status: 1, remark: '' }
  dataDialogVisible.value = true
}

function openDataEdit(row: DictDataItem) {
  isDataEdit.value = true
  editDataId.value = String(row.id)
  dataDialogTitle.value = '编辑数据'
  dataForm.value = { label: row.label, dictValue: row.dictValue, sort: row.sort, status: row.status, remark: row.remark ?? '' }
  dataDialogVisible.value = true
}

async function submitData() {
  const ok = await dataFormRef.value?.validate().catch(() => false)
  if (!ok) return
  dataSubmitting.value = true
  try {
    if (isDataEdit.value) {
      await dictDataApi.update(editDataId.value, dataForm.value)
      ElMessage.success('更新成功')
    } else {
      await dictDataApi.create(activeType.value!.type, dataForm.value)
      ElMessage.success('创建成功')
    }
    dataDialogVisible.value = false
    dictStore.forceReload()
    await loadDatas()
  } catch {
    // 错误已由 axios 拦截器统一处理
  } finally {
    dataSubmitting.value = false
  }
}

async function handleDataDelete(row: DictDataItem) {
  try {
    await ElMessageBox.confirm(`确定删除字典数据「${row.label}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await dictDataApi.remove(String(row.id))
    ElMessage.success('删除成功')
    dictStore.forceReload()
    if (dataList.value.length === 1 && dataCurrentPage.value > 1) dataCurrentPage.value--
    await loadDatas()
  } catch {
    // 错误已由 axios 拦截器统一处理
  }
}

onMounted(loadTypes)
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

  .drawer-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 12px;
  }
}
</style>
