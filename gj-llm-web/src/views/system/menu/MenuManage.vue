<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Delete, EditPen } from '@element-plus/icons-vue'
import { apiApi, menuApi } from '@/api/modules/system'
import type { ApiItem, Menu, MenuType } from '@/api/types'

const treeData = ref<Menu[]>([])
const apiList = ref<ApiItem[]>([])
const loading = ref(false)

const drawerVisible = ref(false)
const drawerTitle = ref('')
const formRef = ref<FormInstance>()
const saving = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)

const form = ref({
  parentId: 0,
  name: '',
  type: 'C' as MenuType,
  path: '',
  component: '',
  perms: '',
  icon: '',
  sort: 0,
  visible: 1,
  status: 1,
  apiIds: [] as number[],
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
}

const typeOptions: { label: string; value: MenuType }[] = [
  { label: '目录', value: 'M' },
  { label: '菜单', value: 'C' },
  { label: '按钮', value: 'B' },
]

// 父菜单选择树（含"顶层"虚拟根，id=0）
const parentTreeData = ref<Menu[]>([])

async function loadTree() {
  loading.value = true
  try {
    const res = await menuApi.getTree()
    treeData.value = res.data.data || []
    parentTreeData.value = [
      { id: 0, parentId: -1, name: '顶层', type: 'M', sort: 0, visible: 1, status: 1, children: treeData.value } as Menu,
    ]
  } finally {
    loading.value = false
  }
}

function handleCreate(parent?: any) {
  isEdit.value = false
  editId.value = null
  drawerTitle.value = '新增菜单'
  form.value = {
    parentId: parent ? parent.id : 0,
    name: '', type: 'C', path: '', component: '', perms: '', icon: '', sort: 0, visible: 1, status: 1,
    apiIds: [],
  }
  drawerVisible.value = true
}

async function handleEdit(row: any) {
  isEdit.value = true
  editId.value = row.id
  drawerTitle.value = '编辑菜单'
  form.value = {
    parentId: row.parentId,
    name: row.name,
    type: row.type,
    path: row.path || '',
    component: row.component || '',
    perms: row.perms || '',
    icon: row.icon || '',
    sort: row.sort,
    visible: row.visible,
    status: row.status,
    apiIds: [],
  }
  // 加载已关联接口
  const res = await menuApi.getApiIds(row.id)
  form.value.apiIds = (res.data.data || []).map(Number)
  drawerVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = { ...form.value }
    // 按钮类型无需路由路径与组件
    if (form.value.type === 'B') {
      payload.path = ''
      payload.component = ''
    }
    let menuId: number
    if (isEdit.value && editId.value) {
      await menuApi.update(editId.value, payload)
      menuId = editId.value
      ElMessage.success('更新成功')
    } else {
      const res = await menuApi.create(payload)
      menuId = res.data.data.id
      ElMessage.success('创建成功')
    }
    // 保存接口关联（全量替换）
    await menuApi.assignApis(menuId, form.value.apiIds)
    drawerVisible.value = false
    await loadTree()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除菜单 "${row.name}" 吗？`, '确认删除', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
    })
  } catch {
    return
  }
  try {
    await menuApi.delete(row.id)
    ElMessage.success('删除成功')
    await loadTree()
  } catch {
    /* 拦截器统一处理 */
  }
}

function typeLabel(t: MenuType): string {
  return { M: '目录', C: '菜单', B: '按钮' }[t]
}

function typeTagType(t: MenuType): 'primary' | 'success' | 'info' {
  return ({ M: 'primary', C: 'success', B: 'info' } as const)[t]
}

async function loadApiList() {
  const res = await apiApi.getList()
  apiList.value = res.data.data || []
}

onMounted(() => {
  loadTree()
  loadApiList()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>菜单管理</h2>
      <el-button type="primary" :icon="Plus" v-permission="'system:menu:add'" @click="handleCreate()">
        新增顶级菜单
      </el-button>
    </div>

    <el-table
      :data="treeData"
      v-loading="loading"
      row-key="id"
      :tree-props="{ children: 'children' }"
      border
      default-expand-all
      class="page-table"
    >
      <el-table-column prop="name" label="菜单名称" min-width="180" />
      <el-table-column label="类型" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="typeTagType(row.type)" size="small" effect="light">{{ typeLabel(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="path" label="路由路径" min-width="160" show-overflow-tooltip />
      <el-table-column prop="component" label="组件" min-width="180" show-overflow-tooltip />
      <el-table-column prop="perms" label="权限标识" min-width="170" show-overflow-tooltip />
      <el-table-column prop="icon" label="图标" width="120" show-overflow-tooltip />
      <el-table-column prop="sort" label="排序" width="80" align="center" />
      <el-table-column label="显示" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.visible === 1 ? 'success' : 'info'" size="small" effect="plain">
            {{ row.visible === 1 ? '显示' : '隐藏' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.type !== 'B'"
            text type="primary" size="small" :icon="Plus"
            v-permission="'system:menu:add'"
            @click="handleCreate(row)"
          >
            子级
          </el-button>
          <el-button text type="primary" size="small" :icon="EditPen" v-permission="'system:menu:edit'" @click="handleEdit(row)">
            编辑
          </el-button>
          <el-button text type="danger" size="small" :icon="Delete" v-permission="'system:menu:remove'" @click="handleDelete(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑 -->
    <el-drawer v-model="drawerVisible" :title="drawerTitle" direction="rtl" size="480px" :close-on-click-modal="false" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="父菜单">
          <el-tree-select
            v-model="form.parentId"
            :data="parentTreeData"
            node-key="id"
            :props="{ label: 'name', children: 'children' }"
            check-strictly
            default-expand-all
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="菜单类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio v-for="o in typeOptions" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="菜单名称" prop="name">
          <el-input v-model="form.name" placeholder="展示用名称" />
        </el-form-item>
        <el-form-item v-if="form.type !== 'B'" label="路由路径">
          <el-input v-model="form.path" placeholder="如 /system/user" />
        </el-form-item>
        <el-form-item v-if="form.type !== 'B'" label="组件路径">
          <el-input v-model="form.component" placeholder="如 system/user/UserManage（相对 views）" />
        </el-form-item>
        <el-form-item label="权限标识">
          <el-input v-model="form.perms" placeholder="如 system:user:list" />
        </el-form-item>
        <el-form-item v-if="form.type !== 'B'" label="图标">
          <el-input v-model="form.icon" placeholder="Element Plus 图标名，如 Setting" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="999" />
        </el-form-item>
        <el-form-item v-if="form.type !== 'B'" label="是否显示">
          <el-switch v-model="form.visible" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="关联接口">
          <el-select
            v-model="form.apiIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择该权限点控制的接口"
            style="width: 100%"
          >
            <el-option
              v-for="api in apiList"
              :key="api.id"
              :label="`${api.httpMethod} ${api.path}`"
              :value="api.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
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
}
.page-table {
  flex: 1;
  border-radius: 12px;
  overflow: hidden;
}
.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
