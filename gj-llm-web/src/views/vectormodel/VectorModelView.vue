<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { vectorModelApi } from '@/api/modules/vectorModel'
import type { VectorModel } from '@/api/types'

const list = ref<VectorModel[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const dialogTitle = ref('新建向量模型库')
const formRef = ref<FormInstance>()
const saving = ref(false)

const isEdit = ref(false)
const editId = ref<number | null>(null)

const form = ref({
  typeCode: '',
  typeName: '',
  collectionName: '',
  description: '',
})

const rules: FormRules = {
  typeCode: [
    { required: true, message: '请输入类型编码', trigger: 'blur' },
    { max: 50, message: '最长 50 个字符', trigger: 'blur' },
  ],
  typeName: [
    { required: true, message: '请输入类型名称', trigger: 'blur' },
    { max: 100, message: '最长 100 个字符', trigger: 'blur' },
  ],
  collectionName: [
    { required: true, message: '请输入集合名称', trigger: 'blur' },
    { max: 100, message: '最长 100 个字符', trigger: 'blur' },
  ],
}

function formatTime(dateStr: string): string {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const h = String(date.getHours()).padStart(2, '0')
  const min = String(date.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${d} ${h}:${min}`
}

async function loadList() {
  loading.value = true
  try {
    const res = await vectorModelApi.getList(currentPage.value, pageSize.value)
    const data = res.data.data
    list.value = data?.records || []
    total.value = data?.total || 0
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadList()
}

function handleSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  loadList()
}

function handleCreate() {
  isEdit.value = false
  editId.value = null
  dialogTitle.value = '新建向量模型库'
  form.value = { typeCode: '', typeName: '', collectionName: '', description: '' }
  dialogVisible.value = true
}

function handleEdit(row: VectorModel) {
  isEdit.value = true
  editId.value = row.id
  dialogTitle.value = '编辑向量模型库'
  form.value = {
    typeCode: row.typeCode,
    typeName: row.typeName,
    collectionName: row.collectionName,
    description: row.description || '',
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    if (isEdit.value && editId.value) {
      await vectorModelApi.update(editId.value, {
        typeName: form.value.typeName,
        collectionName: form.value.collectionName,
        description: form.value.description,
      })
      ElMessage.success('更新成功')
    } else {
      await vectorModelApi.create({
        typeCode: form.value.typeCode,
        typeName: form.value.typeName,
        collectionName: form.value.collectionName,
        description: form.value.description,
      })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await loadList()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: VectorModel) {
  try {
    await ElMessageBox.confirm(
      `确定要删除向量模型库 "${row.typeName}" 吗？`,
      '确认删除',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }

  try {
    await vectorModelApi.deleteById(row.id)
    ElMessage.success('删除成功')
    if (list.value.length === 1 && currentPage.value > 1) {
      currentPage.value--
    }
    await loadList()
  } catch {
    // 拦截器统一处理
  }
}

async function handleToggleStatus(row: VectorModel) {
  const newStatus = row.status === 1 ? 0 : 1
  try {
    await vectorModelApi.update(row.id, { status: newStatus })
    row.status = newStatus
    ElMessage.success(newStatus === 1 ? '已启用' : '已禁用')
  } catch {
    // 拦截器统一处理
  }
}

onMounted(() => {
  loadList()
})
</script>

<template>
  <div class="vm-view">
    <div class="vm-header">
      <h2>向量模型库</h2>
      <el-button type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        新建模型库
      </el-button>
    </div>

    <div class="glass-card">
      <div class="glass-card__body">
        <el-table
          :data="list"
          v-loading="loading"
          stripe
          style="width: 100%"
          empty-text="暂无向量模型库"
        >
          <el-table-column label="类型编码" width="140">
            <template #default="{ row }">
              <el-tag effect="dark" round size="small">{{ row.typeCode }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="类型名称" min-width="160" prop="typeName" />
          <el-table-column label="集合名称" min-width="200">
            <template #default="{ row }">
              <code>{{ row.collectionName }}</code>
            </template>
          </el-table-column>
          <el-table-column label="描述" min-width="200" prop="description">
            <template #default="{ row }">
              {{ row.description || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-switch
                :model-value="row.status === 1"
                @change="handleToggleStatus(row)"
                size="small"
              />
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="170" align="center">
            <template #default="{ row }">
              {{ formatTime(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" align="center" fixed="right">
            <template #default="{ row }">
              <el-button text type="primary" size="small" @click="handleEdit(row)">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
              <el-button text type="danger" size="small" @click="handleDelete(row)">
                <el-icon><Delete /></el-icon>
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="vm-pagination" v-if="total > 0">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="[5, 10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>
      </div>
    </div>

    <!-- 新建/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="520px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        label-position="right"
      >
        <el-form-item label="类型编码" prop="typeCode">
          <el-input
            v-model="form.typeCode"
            placeholder="如：medical、story"
            :disabled="isEdit"
            maxlength="50"
          />
        </el-form-item>
        <el-form-item label="类型名称" prop="typeName">
          <el-input
            v-model="form.typeName"
            placeholder="如：医疗知识库"
            maxlength="100"
          />
        </el-form-item>
        <el-form-item label="集合名称" prop="collectionName">
          <el-input
            v-model="form.collectionName"
            placeholder="如：collection_medical"
            maxlength="100"
          />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="可选描述"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">
          {{ isEdit ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.vm-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 24px;
  overflow: auto;
}

.vm-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;

  h2 {
    font-size: 22px;
    font-weight: 700;
    color: #1d1d1f;
    letter-spacing: -0.01em;
    margin: 0;
  }
}

.glass-card {
  background: rgba(255, 255, 255, 0.62);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.05), 0 2px 8px rgba(0, 0, 0, 0.03);
  transition: all 0.25s cubic-bezier(0.25, 0.1, 0.25, 1);

  &:hover {
    box-shadow: 0 12px 40px rgba(0, 0, 0, 0.07), 0 4px 12px rgba(0, 0, 0, 0.04);
  }

  &__body {
    padding: 16px 20px;
  }
}

code {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 12.5px;
  background: rgba(0, 113, 227, 0.08);
  color: #0071e3;
  padding: 3px 8px;
  border-radius: 5px;
}

.vm-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid rgba(210, 210, 215, 0.3);
}
</style>
