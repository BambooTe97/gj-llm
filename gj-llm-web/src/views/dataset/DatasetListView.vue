<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { datasetApi } from '@/api/modules/dataset'
import type { Dataset } from '@/api/types'

const router = useRouter()

// ---- 列表 ----
const list = ref<Dataset[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const searchKeyword = ref('')

const filteredList = computed(() => {
  if (!searchKeyword.value) return list.value
  const kw = searchKeyword.value.toLowerCase()
  return list.value.filter(d => d.name.toLowerCase().includes(kw))
})

// ---- 新建/编辑弹窗 ----
const dialogVisible = ref(false)
const dialogTitle = ref('新建知识库')
const formRef = ref<FormInstance>()
const saving = ref(false)
const isEdit = ref(false)
const editId = ref<string | null>(null)

const form = ref({
  name: '',
  description: '',
  embeddingModel: '',
  vectorStoreType: '',
  collectionName: '',
  chunkStrategy: 'general' as 'general' | 'custom',
  chunkSize: 800,
  chunkOverlap: 100,
})

const rules: FormRules = {
  name: [
    { required: true, message: '请输入知识库名称', trigger: 'blur' },
    { max: 100, message: '最长 100 个字符', trigger: 'blur' },
  ],
  embeddingModel: [
    { required: true, message: '请选择 Embedding 模型', trigger: 'change' },
  ],
  vectorStoreType: [
    { required: true, message: '请选择向量数据库类型', trigger: 'change' },
  ],
  collectionName: [
    { required: true, message: '请输入集合名称', trigger: 'blur' },
    { max: 100, message: '最长 100 个字符', trigger: 'blur' },
  ],
}

// ---- 下拉选项 ----
const embeddingModelOptions = [
  { label: 'bge-m3:latest (Ollama)', value: 'bge-m3:latest' },
  { label: 'BGE-Large-ZH', value: 'BGE-Large-ZH' },
  { label: 'text-embedding-ada-002 (OpenAI)', value: 'text-embedding-ada-002' },
  { label: 'all-MiniLM-L6-v2 (HuggingFace)', value: 'all-MiniLM-L6-v2' },
]

const vectorStoreOptions = [
  { label: 'Milvus', value: 'Milvus' },
  { label: 'PostgreSQL (pgvector)', value: 'PostgreSQL' },
  { label: 'Elasticsearch', value: 'Elasticsearch' },
]

// ---- 格式化 ----
function formatTime(dateStr: string): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function statusLabel(status: string): string {
  const map: Record<string, string> = { READY: '就绪', INDEXING: '索引中', ERROR: '异常' }
  return map[status] || status
}

function statusType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
    READY: 'success', INDEXING: 'warning', ERROR: 'danger',
  }
  return map[status] || 'info'
}

// ---- 加载列表 ----
async function loadList() {
  loading.value = true
  try {
    const res = await datasetApi.getList(currentPage.value, pageSize.value)
    const data = res.data.data
    list.value = data?.records || []
    total.value = data?.total || 0
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) { currentPage.value = page; loadList() }
function handleSizeChange(size: number) { pageSize.value = size; currentPage.value = 1; loadList() }

// ---- 新建 ----
function handleCreate() {
  isEdit.value = false
  editId.value = null
  dialogTitle.value = '新建知识库'
  form.value = {
    name: '', description: '', embeddingModel: '', vectorStoreType: '',
    collectionName: '', chunkStrategy: 'general', chunkSize: 800, chunkOverlap: 100,
  }
  dialogVisible.value = true
}

// ---- 编辑（配置） ----
function handleEdit(row: Dataset) {
  isEdit.value = true
  editId.value = row.id
  dialogTitle.value = '配置知识库'
  form.value = {
    name: row.name,
    description: row.description || '',
    embeddingModel: row.embeddingModel,
    vectorStoreType: row.vectorStoreType,
    collectionName: row.collectionName,
    chunkStrategy: 'custom',
    chunkSize: row.chunkSize,
    chunkOverlap: row.chunkOverlap,
  }
  dialogVisible.value = true
}

// ---- 提交 ----
async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    if (isEdit.value && editId.value) {
      await datasetApi.update(editId.value, {
        name: form.value.name,
        description: form.value.description,
        embeddingModel: form.value.embeddingModel,
        chunkSize: form.value.chunkStrategy === 'custom' ? form.value.chunkSize : undefined,
        chunkOverlap: form.value.chunkStrategy === 'custom' ? form.value.chunkOverlap : undefined,
      })
      ElMessage.success('更新成功')
    } else {
      await datasetApi.create({
        name: form.value.name,
        description: form.value.description,
        embeddingModel: form.value.embeddingModel,
        vectorStoreType: form.value.vectorStoreType,
        collectionName: form.value.collectionName,
        chunkSize: form.value.chunkStrategy === 'custom' ? form.value.chunkSize : 800,
        chunkOverlap: form.value.chunkStrategy === 'custom' ? form.value.chunkOverlap : 100,
      })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await loadList()
  } finally {
    saving.value = false
  }
}

// ---- 删除 ----
async function handleDelete(row: Dataset) {
  try {
    await ElMessageBox.confirm(
      `确定要删除知识库 "${row.name}" 吗？该操作将同时删除关联的所有文件和向量数据。`,
      '确认删除',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch { return }

  try {
    await datasetApi.deleteById(row.id)
    ElMessage.success('删除成功')
    if (list.value.length === 1 && currentPage.value > 1) currentPage.value--
    await loadList()
  } catch { /* 拦截器统一处理 */ }
}

// ---- 进入详情 ----
function handleEnterDetail(row: Dataset) {
  router.push(`/datasets/${row.id}`)
}

onMounted(() => { loadList() })
</script>

<template>
  <div class="ds-list">
    <!-- 顶部操作栏 -->
    <div class="ds-header">
      <h2>知识库</h2>
      <div class="ds-header__actions">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索知识库名称"
          clearable
          :prefix-icon="Search"
          style="width: 260px"
        />
        <el-button type="primary" @click="handleCreate">
          <el-icon><Plus /></el-icon>
          新建知识库
        </el-button>
      </div>
    </div>

    <!-- 卡片列表 -->
    <div class="ds-cards" v-loading="loading">
      <div
        v-for="row in filteredList"
        :key="row.id"
        class="ds-card"
        @click="handleEnterDetail(row)"
      >
        <div class="ds-card__header">
          <span class="ds-card__name">{{ row.name }}</span>
          <el-tag :type="statusType(row.status)" size="small" effect="light">
            {{ statusLabel(row.status) }}
          </el-tag>
        </div>

        <div class="ds-card__body">
          <div class="ds-card__info">
            <span class="ds-card__label">向量库</span>
            <span class="ds-card__value">{{ row.vectorStoreType }}</span>
          </div>
          <div class="ds-card__info">
            <span class="ds-card__label">Embedding</span>
            <span class="ds-card__value">{{ row.embeddingModel }}</span>
          </div>
          <div class="ds-card__info">
            <span class="ds-card__label">集合</span>
            <code class="ds-card__code">{{ row.collectionName }}</code>
          </div>
        </div>

        <div class="ds-card__stats">
          <div class="ds-card__stat">
            <span class="ds-card__stat-num">{{ row.docCount }}</span>
            <span class="ds-card__stat-label">文档</span>
          </div>
          <div class="ds-card__stat">
            <span class="ds-card__stat-num">{{ row.segmentCount }}</span>
            <span class="ds-card__stat-label">向量</span>
          </div>
          <div class="ds-card__stat ds-card__stat--time">
            <span class="ds-card__stat-label">更新于</span>
            <span class="ds-card__stat-time">{{ formatTime(row.updatedAt) }}</span>
          </div>
        </div>

        <div class="ds-card__actions" @click.stop>
          <el-button text size="small" type="primary" @click="handleEdit(row)">
            <el-icon><Setting /></el-icon>
            配置
          </el-button>
          <el-button text size="small" type="danger" @click="handleDelete(row)">
            <el-icon><Delete /></el-icon>
            删除
          </el-button>
        </div>
      </div>

      <div v-if="!loading && filteredList.length === 0" class="ds-empty">
        <el-icon :size="48"><FolderOpened /></el-icon>
        <span>暂无知识库</span>
        <span class="ds-empty__hint">点击"新建知识库"开始创建</span>
      </div>
    </div>

    <!-- 分页 -->
    <div class="ds-pagination" v-if="total > 0">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[6, 12, 24, 48]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <!-- 新建/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="560px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" label-position="right">
        <!-- 基础信息 -->
        <el-divider content-position="left">基础信息</el-divider>
        <el-form-item label="知识库名称" prop="name">
          <el-input v-model="form.name" placeholder="如：医疗法规库" maxlength="100" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="选填"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>

        <!-- 模型配置 -->
        <el-divider content-position="left">模型与存储配置</el-divider>
        <el-form-item label="Embedding 模型" prop="embeddingModel">
          <el-select v-model="form.embeddingModel" placeholder="选择 Embedding 模型" style="width: 100%">
            <el-option
              v-for="opt in embeddingModelOptions" :key="opt.value"
              :label="opt.label" :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="向量数据库类型" prop="vectorStoreType" v-if="!isEdit">
          <el-select v-model="form.vectorStoreType" placeholder="选择向量数据库" style="width: 100%">
            <el-option
              v-for="opt in vectorStoreOptions" :key="opt.value"
              :label="opt.label" :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="集合名称" prop="collectionName" v-if="!isEdit">
          <el-input v-model="form.collectionName" placeholder="自动生成或手动输入" maxlength="100" />
        </el-form-item>
        <el-form-item label="集合名称" v-else>
          <el-input :model-value="form.collectionName" disabled />
        </el-form-item>

        <!-- 处理参数 -->
        <el-divider content-position="left">处理参数</el-divider>
        <el-form-item label="切片策略">
          <el-radio-group v-model="form.chunkStrategy">
            <el-radio value="general">通用（默认参数）</el-radio>
            <el-radio value="custom">自定义</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="form.chunkStrategy === 'custom'">
          <el-form-item label="Chunk Size">
            <el-input-number
              v-model="form.chunkSize"
              :min="100" :max="8000" :step="100"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="Overlap">
            <el-input-number
              v-model="form.chunkOverlap"
              :min="0" :max="1000" :step="10"
              style="width: 100%"
            />
          </el-form-item>
        </template>
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
.ds-list {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 24px;
  overflow: auto;
}

// ---- 顶部操作栏 ----
.ds-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
  flex-shrink: 0;

  h2 {
    font-size: 22px;
    font-weight: 700;
    color: #1d1d1f;
    letter-spacing: -0.01em;
    margin: 0;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 12px;
  }
}

// ---- 卡片网格 ----
.ds-cards {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 16px;
  align-content: start;
}

// ---- 单个卡片 ----
.ds-card {
  background: rgba(255, 255, 255, 0.62);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 14px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.25, 0.1, 0.25, 1);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.04);

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 28px rgba(0, 0, 0, 0.08);
    border-color: rgba(0, 113, 227, 0.2);
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 14px;
  }

  &__name {
    font-size: 16px;
    font-weight: 600;
    color: #1d1d1f;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    flex: 1;
    margin-right: 8px;
  }

  &__body {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin-bottom: 14px;
    padding-bottom: 14px;
    border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  }

  &__info {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
  }

  &__label {
    color: #86868b;
    min-width: 72px;
    flex-shrink: 0;
  }

  &__value {
    color: #1d1d1f;
    font-weight: 500;
  }

  &__code {
    font-family: 'SF Mono', 'Fira Code', monospace;
    font-size: 12px;
    background: rgba(0, 113, 227, 0.07);
    color: #0071e3;
    padding: 2px 8px;
    border-radius: 5px;
  }

  &__stats {
    display: flex;
    gap: 20px;
    margin-bottom: 12px;
  }

  &__stat {
    display: flex;
    flex-direction: column;
    gap: 2px;

    &--time {
      margin-left: auto;
      align-items: flex-end;
    }
  }

  &__stat-num {
    font-size: 18px;
    font-weight: 700;
    color: #1d1d1f;
  }

  &__stat-label {
    font-size: 11px;
    color: #86868b;
    text-transform: uppercase;
    letter-spacing: 0.04em;
  }

  &__stat-time {
    font-size: 11px;
    color: #aeaeb2;
  }

  &__actions {
    display: flex;
    gap: 4px;
    padding-top: 8px;
    border-top: 1px solid rgba(0, 0, 0, 0.04);
  }
}

// ---- 空状态 ----
.ds-empty {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 60px 20px;
  color: #aeaeb2;

  .el-icon { color: #d2d2d7; }

  span { font-size: 15px; }

  &__hint {
    font-size: 13px !important;
    color: #c7c7cc !important;
  }
}

// ---- 分页 ----
.ds-pagination {
  display: flex;
  justify-content: center;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid rgba(210, 210, 215, 0.3);
  flex-shrink: 0;
}
</style>
