<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Delete, EditPen, Share } from '@element-plus/icons-vue'
import { roleApi, menuApi } from '@/api/modules/system'
import { deptApi, type SysDept } from '@/api/modules/dept'
import type { Menu, Role } from '@/api/types'

const list = ref<Role[]>([])
const loading = ref(false)

const drawerVisible = ref(false)
const drawerTitle = ref('')
const formRef = ref<FormInstance>()
const saving = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)

// ---- 数据范围（角色数据权限五档，与后端 RoleEntity 档位常量对齐） ----
const DATA_SCOPES = [
  { value: 1, label: '全部数据' },
  { value: 2, label: '自定义部门' },
  { value: 3, label: '本部门' },
  { value: 4, label: '本部门及以下' },
  { value: 5, label: '仅本人' },
]

const form = ref({ name: '', code: '', description: '', dataScope: 1, deptIds: [] as number[] })
const deptTree = ref<SysDept[]>([])

const rules: FormRules = {
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
}

// ---- 分配菜单 ----
const menuDrawerVisible = ref(false)
const menuTreeRef = ref()
const menuTreeData = ref<Menu[]>([])
const currentRoleId = ref<number | null>(null)
const assigning = ref(false)

async function loadList() {
  loading.value = true
  try {
    const res = await roleApi.getList()
    list.value = res.data.data || []
  } finally {
    loading.value = false
  }
}

async function loadMenuTree() {
  const res = await menuApi.getTree()
  menuTreeData.value = res.data.data || []
}

async function loadDeptTree() {
  if (deptTree.value.length) return
  try {
    deptTree.value = await deptApi.tree()
  } catch {
    /* 拦截器统一处理 */
  }
}

function handleCreate() {
  isEdit.value = false
  editId.value = null
  drawerTitle.value = '新增角色'
  form.value = { name: '', code: '', description: '', dataScope: 1, deptIds: [] }
  void loadDeptTree()
  drawerVisible.value = true
}

async function handleEdit(row: any) {
  isEdit.value = true
  editId.value = row.id
  drawerTitle.value = '编辑角色'
  form.value = {
    name: row.name,
    code: row.code,
    description: row.description || '',
    dataScope: row.dataScope ?? 1,
    deptIds: [],
  }
  void loadDeptTree()
  drawerVisible.value = true
  // 自定义部门档回显已选部门
  if (form.value.dataScope === 2) {
    try {
      const res = await roleApi.getDeptIds(row.id)
      form.value.deptIds = (res.data.data || []) as number[]
    } catch {
      /* 拦截器统一处理 */
    }
  }
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      name: form.value.name,
      description: form.value.description,
      dataScope: form.value.dataScope,
      deptIds: form.value.dataScope === 2 ? form.value.deptIds : [],
    }
    if (isEdit.value && editId.value) {
      await roleApi.update(editId.value, payload)
      ElMessage.success('更新成功')
    } else {
      await roleApi.create({ ...payload, code: form.value.code })
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
    await ElMessageBox.confirm(`确定删除角色 "${row.name}" 吗？`, '确认删除', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
    })
  } catch {
    return
  }
  try {
    await roleApi.delete(row.id)
    ElMessage.success('删除成功')
    await loadList()
  } catch {
    /* 拦截器统一处理 */
  }
}

async function handleAssignMenu(row: any) {
  currentRoleId.value = row.id
  if (!menuTreeData.value.length) await loadMenuTree()
  menuDrawerVisible.value = true
  const res = await roleApi.getMenuIds(row.id)
  const ids = new Set(res.data.data || [])
  await nextTick()
  // 仅勾选叶子节点，避免父节点勾选导致全选子节点
  menuTreeRef.value?.setCheckedKeys(getLeafCheckedIds(menuTreeData.value, ids))
}

/** 收集树中属于 idSet 的叶子节点 ID（叶子=无子节点） */
function getLeafCheckedIds(menus: Menu[], idSet: Set<number>): number[] {
  const result: number[] = []
  const walk = (list: Menu[]) => {
    for (const m of list) {
      const children = m.children || []
      if (children.length === 0) {
        if (idSet.has(m.id)) result.push(m.id)
      } else {
        walk(children)
      }
    }
  }
  walk(menus)
  return result
}

async function handleAssignSubmit() {
  if (!currentRoleId.value) return
  assigning.value = true
  try {
    const checked = menuTreeRef.value.getCheckedKeys() as number[]
    const halfChecked = menuTreeRef.value.getHalfCheckedKeys() as number[]
    await roleApi.assignMenus(currentRoleId.value, [...checked, ...halfChecked])
    ElMessage.success('菜单分配成功')
    menuDrawerVisible.value = false
  } finally {
    assigning.value = false
  }
}

function formatTime(s?: string): string {
  if (!s) return '-'
  const d = new Date(s)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(() => {
  loadList()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>角色管理</h2>
      <el-button type="primary" :icon="Plus" v-permission="'system:role:add'" @click="handleCreate">
        新增角色
      </el-button>
    </div>

    <el-table :data="list" v-loading="loading" border stripe class="page-table">
      <el-table-column prop="name" label="角色名称" min-width="140" />
      <el-table-column prop="code" label="编码" min-width="120" />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" :icon="EditPen" v-permission="'system:role:edit'" @click="handleEdit(row)">
            编辑
          </el-button>
          <el-button text type="success" size="small" :icon="Share" v-permission="'system:role:edit'" @click="handleAssignMenu(row)">
            分配菜单
          </el-button>
          <el-button text type="danger" size="small" :icon="Delete" v-permission="'system:role:remove'" @click="handleDelete(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="drawerTitle"
      direction="rtl"
      size="440px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" placeholder="如：系统管理员" />
        </el-form-item>
        <el-form-item label="角色编码" prop="code">
          <el-input v-model="form.code" :disabled="isEdit" placeholder="如：ADMIN（编辑时不可改）" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="选填" />
        </el-form-item>
        <el-form-item label="数据范围">
          <el-select v-model="form.dataScope" style="width: 100%">
            <el-option v-for="s in DATA_SCOPES" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.dataScope === 2" label="可见部门">
          <el-tree-select
            v-model="form.deptIds"
            :data="deptTree"
            node-key="id"
            :props="{ label: 'name', children: 'children' }"
            multiple
            show-checkbox
            check-strictly
            default-expand-all
            placeholder="选择该角色可见的部门"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 分配菜单 -->
    <el-drawer v-model="menuDrawerVisible" title="分配菜单" direction="rtl" size="480px" :close-on-click-modal="false">
      <el-tree
        ref="menuTreeRef"
        :data="menuTreeData"
        node-key="id"
        show-checkbox
        default-expand-all
        :props="{ label: 'name', children: 'children' }"
      />
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="menuDrawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="assigning" @click="handleAssignSubmit">保存</el-button>
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
