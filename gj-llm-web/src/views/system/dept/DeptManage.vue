<template>
  <div class="page">
    <div class="page-header">
      <div class="page-header__title">
        <h2>部门管理</h2>
        <span class="page-header__desc">维护组织架构树，用户可归属到指定部门并按部门过滤</span>
      </div>
      <div class="page-header__actions">
        <el-button :icon="Refresh" @click="loadTree">刷新</el-button>
        <el-button type="primary" :icon="Plus" v-permission="'system:dept:add'" @click="handleCreate()">
          新增部门
        </el-button>
      </div>
    </div>

    <el-table
      v-loading="loading"
      :data="treeData"
      row-key="id"
      :tree-props="{ children: 'children' }"
      default-expand-all
      class="page-table"
    >
      <el-table-column prop="name" label="部门名称" min-width="220" />
      <el-table-column prop="sort" label="排序" width="80" align="center" />
      <el-table-column prop="leader" label="负责人" width="120" />
      <el-table-column prop="phone" label="电话" width="140" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }: { row: any }">
          <div class="op-cell">
            <el-button text type="primary" size="small" :icon="Plus" v-permission="'system:dept:add'" @click="handleCreate(row)">
              下级
            </el-button>
            <el-button text type="primary" size="small" :icon="EditPen" v-permission="'system:dept:edit'" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button text type="danger" size="small" :icon="Delete" v-permission="'system:dept:remove'" @click="handleDelete(row)">
              删除
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" :close-on-click-modal="false" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="上级部门">
          <el-tree-select
            v-model="form.parentId"
            :data="parentTreeData"
            node-key="id"
            :props="{ label: 'name', children: 'children', disabled: isNodeDisabled }"
            check-strictly
            default-expand-all
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="部门名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入部门名称" maxlength="50" />
        </el-form-item>
        <el-form-item label="显示排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="form.leader" placeholder="选填" maxlength="30" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.phone" placeholder="选填" maxlength="20" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="选填" maxlength="50" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" />
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
import { Delete, EditPen, Plus, Refresh } from '@element-plus/icons-vue'
import { deptApi, type DeptWrite, type SysDept } from '@/api/modules/dept'

defineOptions({ name: 'DeptManage' })

const loading = ref(false)
const treeData = ref<SysDept[]>([])
/** 父级选择树：顶部包一层「顶级部门」（id=0 与后端根约定一致） */
const parentTreeData = ref<SysDept[]>([])
/** 编辑态下禁选自身及子孙（后端同样拒绝移入自身子孙） */
const disabledIds = ref<Set<string>>(new Set())

const dialogVisible = ref(false)
const dialogTitle = ref('新增部门')
const submitting = ref(false)
const formRef = ref()
const isEdit = ref(false)
const editId = ref('')

const defaultForm = (): DeptWrite & { parentId: string } => ({
  parentId: '0',
  name: '',
  sort: 0,
  leader: '',
  phone: '',
  email: '',
  status: 1,
})
const form = ref(defaultForm())

const rules = {
  name: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
}

function isNodeDisabled(data: { id?: string | number }): boolean {
  return disabledIds.value.has(String(data.id))
}

async function loadTree() {
  loading.value = true
  try {
    const tree = await deptApi.tree()
    treeData.value = tree
    parentTreeData.value = [
      {
        id: '0',
        parentId: '-1',
        name: '顶级部门',
        status: 1,
        children: tree,
      } as SysDept,
    ]
  } finally {
    loading.value = false
  }
}

function handleCreate(parent?: SysDept) {
  isEdit.value = false
  editId.value = ''
  dialogTitle.value = '新增部门'
  disabledIds.value = new Set()
  form.value = { ...defaultForm(), parentId: parent ? String(parent.id) : '0' }
  dialogVisible.value = true
}

function handleEdit(row: SysDept) {
  isEdit.value = true
  editId.value = String(row.id)
  dialogTitle.value = '编辑部门'
  form.value = {
    parentId: String(row.parentId),
    name: row.name,
    sort: row.sort ?? 0,
    leader: row.leader ?? '',
    phone: row.phone ?? '',
    email: row.email ?? '',
    status: row.status,
  }
  // 禁选自身 + 全部子孙（防移入自身子树）
  const ids = new Set<string>()
  const walk = (node: SysDept): void => {
    ids.add(String(node.id))
    node.children?.forEach(walk)
  }
  walk(row)
  disabledIds.value = ids
  dialogVisible.value = true
}

async function submit() {
  const ok = await formRef.value?.validate().catch(() => false)
  if (!ok) return
  submitting.value = true
  try {
    if (isEdit.value) {
      await deptApi.update(editId.value, form.value)
      ElMessage.success('更新成功')
    } else {
      await deptApi.create(form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await loadTree()
  } catch {
    // 错误已由 axios 拦截器统一处理
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: SysDept) {
  try {
    await ElMessageBox.confirm(`确定删除部门「${row.name}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deptApi.remove(String(row.id))
    ElMessage.success('删除成功')
    await loadTree()
  } catch {
    // 错误已由 axios 拦截器统一处理
  }
}

onMounted(loadTree)
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

  .page-table {
    width: 100%;
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
