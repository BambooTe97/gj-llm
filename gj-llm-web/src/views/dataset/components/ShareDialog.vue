<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { datasetApi } from '@/api/modules/dataset'
import type { AclDetail, AclGrant, PrincipalOption } from '@/api/types'
import { Delete, Plus } from '@element-plus/icons-vue'

const props = defineProps<{
  modelValue: boolean
  datasetId: string | null
  datasetName?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  /** 共享设置发生变更（可见性/授权增删），父级可刷新列表 */
  (e: 'changed'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val: boolean) => emit('update:modelValue', val),
})

// ---- 详情 ----
const detail = ref<AclDetail | null>(null)
const loading = ref(false)

/** 是否可管理（owner/管理员，后端判定；不可管理时写控件禁用、仅可查看） */
const canManage = computed(() => !!detail.value?.canManage)

const visibility = ref<'PUBLIC' | 'RESTRICTED'>('PUBLIC')
const grants = ref<AclGrant[]>([])

async function loadDetail() {
  if (!props.datasetId) return
  loading.value = true
  try {
    const res = await datasetApi.getAcl(props.datasetId)
    detail.value = res.data.data
    visibility.value = detail.value?.visibility === 'RESTRICTED' ? 'RESTRICTED' : 'PUBLIC'
    grants.value = detail.value?.grants || []
  } catch {
    detail.value = null
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.modelValue, props.datasetId] as const,
  ([open]) => {
    if (open) {
      loadDetail()
      loadRoleOptions() // 角色列表只读开放,打开即拉取(角色极少变化,已有缓存不重复拉)
    }
  },
)

// ---- 可见性切换（即改即生效） ----
const switching = ref(false)
// el-radio-group change 回调参数为宽类型，内部收窄为合法取值
async function handleVisibilityChange(val: string | number | boolean | undefined) {
  const target: 'PUBLIC' | 'RESTRICTED' = val === 'RESTRICTED' ? 'RESTRICTED' : 'PUBLIC'
  if (!props.datasetId || !canManage.value) return
  switching.value = true
  try {
    await datasetApi.updateVisibility(props.datasetId, target)
    ElMessage.success(target === 'PUBLIC' ? '已设为全员可见' : '已设为仅授权用户可见')
    emit('changed')
  } catch {
    // 失败回退到详情里的原值
    visibility.value = detail.value?.visibility === 'RESTRICTED' ? 'RESTRICTED' : 'PUBLIC'
  } finally {
    switching.value = false
  }
}

// ---- 新增授权 ----
const principalType = ref<'user' | 'role'>('user')
const selectedPrincipal = ref<string | null>(null)
const granting = ref(false)
const userOptions = ref<PrincipalOption[]>([])
const roleOptions = ref<PrincipalOption[]>([])
const userSearching = ref(false)

async function searchUsers(keyword: string) {
  if (!props.datasetId || !keyword.trim()) {
    userOptions.value = []
    return
  }
  userSearching.value = true
  try {
    const res = await datasetApi.searchPrincipalUsers(props.datasetId, keyword.trim())
    userOptions.value = res.data.data || []
  } finally {
    userSearching.value = false
  }
}

async function loadRoleOptions() {
  if (!props.datasetId || roleOptions.value.length) return
  try {
    const res = await datasetApi.listPrincipalRoles(props.datasetId)
    roleOptions.value = res.data.data || []
  } catch {
    roleOptions.value = []
  }
}

const principalOptions = computed(() =>
  principalType.value === 'user' ? userOptions.value : roleOptions.value,
)

async function handleGrant() {
  if (!props.datasetId || !selectedPrincipal.value) return
  granting.value = true
  try {
    await datasetApi.grantAcl(props.datasetId, {
      principalType: principalType.value,
      principalId: selectedPrincipal.value,
    })
    ElMessage.success('授权成功')
    selectedPrincipal.value = null
    userOptions.value = []
    await loadDetail()
    emit('changed')
  } catch {
    /* 拦截器统一处理 */
  } finally {
    granting.value = false
  }
}

// ---- 移除授权 ----
const removingId = ref<string | null>(null)
async function handleRevoke(grant: AclGrant) {
  if (!props.datasetId) return
  removingId.value = grant.id
  try {
    await datasetApi.revokeAcl(props.datasetId, grant.id)
    ElMessage.success('已移除授权')
    await loadDetail()
    emit('changed')
  } catch {
    /* 拦截器统一处理 */
  } finally {
    removingId.value = null
  }
}

function typeLabel(type: AclGrant['principalType']): string {
  return type === 'role' ? '角色' : '用户'
}

function formatTime(dateStr?: string): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="`共享设置 · ${datasetName || '知识库'}`"
    width="520px"
    destroy-on-close
  >
    <div v-loading="loading" class="share-dialog">
      <!-- 可见性 -->
      <div class="share-dialog__section">
        <div class="share-dialog__section-title">可见范围</div>
        <el-radio-group
          v-model="visibility"
          :disabled="!canManage || switching"
          @change="handleVisibilityChange"
        >
          <el-radio value="PUBLIC">全员可见</el-radio>
          <el-radio value="RESTRICTED">仅授权用户可见</el-radio>
        </el-radio-group>
        <p class="share-dialog__hint">
          {{
            visibility === 'PUBLIC'
              ? '所有登录用户都能检索到该知识库并用于问答。'
              : '只有知识库所有者、管理员和下方被授权的用户/角色可以检索与问答。'
          }}
        </p>
      </div>

      <!-- 授权列表 -->
      <div class="share-dialog__section">
        <div class="share-dialog__section-title">已授权（{{ grants.length }}）</div>
        <div v-if="grants.length === 0" class="share-dialog__empty">
          暂无额外授权{{ visibility === 'RESTRICTED' ? '，当前仅所有者与管理员可见' : '' }}
        </div>
        <div v-for="grant in grants" :key="grant.id" class="share-dialog__grant">
          <span class="share-dialog__grant-type" :class="`is-${grant.principalType}`">
            {{ typeLabel(grant.principalType) }}
          </span>
          <span class="share-dialog__grant-name" :title="grant.principalName">
            {{ grant.principalName }}
          </span>
          <span class="share-dialog__grant-time">{{ formatTime(grant.createdAt) }}</span>
          <el-button
            v-if="canManage"
            text
            size="small"
            type="danger"
            :icon="Delete"
            :loading="removingId === grant.id"
            @click="handleRevoke(grant)"
          >
            移除
          </el-button>
        </div>
      </div>

      <!-- 新增授权 -->
      <template v-if="canManage">
        <div class="share-dialog__section">
          <div class="share-dialog__section-title">添加授权</div>
          <div class="share-dialog__add">
            <el-radio-group v-model="principalType" size="small">
              <el-radio-button value="user">用户</el-radio-button>
              <el-radio-button value="role">角色</el-radio-button>
            </el-radio-group>
            <el-select
              v-model="selectedPrincipal"
              :placeholder="principalType === 'user' ? '搜索用户昵称/用户名' : '选择角色'"
              filterable
              remote
              clearable
              size="default"
              class="share-dialog__select"
              :remote-method="principalType === 'user' ? searchUsers : undefined"
              :loading="userSearching"
            >
              <el-option
                v-for="opt in principalOptions"
                :key="opt.id"
                :label="opt.name"
                :value="opt.id"
              />
            </el-select>
            <el-button
              type="primary"
              size="default"
              :icon="Plus"
              :disabled="!selectedPrincipal"
              :loading="granting"
              @click="handleGrant"
            >
              授权
            </el-button>
          </div>
        </div>
      </template>
      <p v-else class="share-dialog__readonly-hint">当前账号为只读查看，仅所有者或管理员可修改。</p>
    </div>
  </el-dialog>
</template>

<style lang="scss" scoped>
.share-dialog {
  min-height: 200px;
  display: flex;
  flex-direction: column;
  gap: 20px;

  &__section-title {
    font-size: 13px;
    font-weight: 600;
    color: #515154;
    margin-bottom: 10px;
  }

  &__hint {
    margin: 8px 0 0;
    font-size: 12px;
    color: #aeaeb2;
    line-height: 1.5;
  }

  &__empty {
    font-size: 13px;
    color: #aeaeb2;
    padding: 8px 0;
  }

  &__grant {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 0;

    & + & {
      border-top: 1px solid rgba(0, 0, 0, 0.04);
    }
  }

  &__grant-type {
    flex-shrink: 0;
    font-size: 11px;
    padding: 1px 8px;
    border-radius: 8px;

    &.is-user {
      background: rgba(0, 113, 227, 0.08);
      color: #0071e3;
    }

    &.is-role {
      background: rgba(52, 199, 89, 0.12);
      color: #248a3d;
    }
  }

  &__grant-name {
    flex: 1;
    font-size: 13px;
    color: #1d1d1f;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__grant-time {
    flex-shrink: 0;
    font-size: 11px;
    color: #aeaeb2;
  }

  &__add {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  &__select {
    flex: 1;
    min-width: 180px;
  }

  &__readonly-hint {
    margin: 0;
    font-size: 12px;
    color: #aeaeb2;
  }
}
</style>
