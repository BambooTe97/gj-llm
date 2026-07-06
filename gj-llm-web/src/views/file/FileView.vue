<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fileApi } from '@/api/modules/file'
import type { FileRecord } from '@/api/types'
import type { UploadInstance, UploadRawFile } from 'element-plus'

const fileList = ref<FileRecord[]>([])
const loading = ref(false)
const uploading = ref(false)
const uploadRef = ref<UploadInstance>()

// ---- 分页 ----
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

/** 格式化文件大小 */
function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}

/** 格式化时间 */
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

/** 获取文件类型标签颜色 */
function getTypeColor(ext: string): string {
  const map: Record<string, string> = {
    pdf: 'danger',
    doc: 'primary', docx: 'primary',
    xls: 'success', xlsx: 'success',
    ppt: 'warning', pptx: 'warning',
    txt: 'info', md: 'info',
    csv: 'success',
  }
  return map[ext?.toLowerCase()] || 'info'
}

/** 加载文件列表 */
async function loadFiles() {
  loading.value = true
  try {
    const res = await fileApi.getList(currentPage.value, pageSize.value)
    const data = res.data.data
    fileList.value = data?.list || []
    total.value = data?.total || 0
  } catch (err: any) {
    console.error('加载文件列表失败:', err?.message, err)
    // 拦截器统一处理
  } finally {
    loading.value = false
  }
}

/** 翻页 */
function handlePageChange(page: number) {
  currentPage.value = page
  loadFiles()
}

function handleSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  loadFiles()
}

/** 上传前校验 */
function beforeUpload(rawFile: UploadRawFile) {
  // 大小限制由后端 spring.servlet.multipart.max-file-size 控制
  return true
}

/** 上传文件 */
async function handleUpload(options: { file: UploadRawFile }) {
  uploading.value = true
  try {
    await fileApi.upload(options.file as File)
    ElMessage.success(`"${options.file.name}" 上传成功`)
    currentPage.value = 1
    await loadFiles()
  } catch {
    // 拦截器统一处理
  } finally {
    uploading.value = false
    uploadRef.value?.clearFiles()
  }
}

/** 下载文件 */
async function handleDownload(file: FileRecord) {
  try {
    await fileApi.download(file.id, file.originalName)
  } catch {
    ElMessage.error('下载失败')
  }
}

/** 删除文件 */
async function handleDelete(file: FileRecord) {
  try {
    await ElMessageBox.confirm(
      `确定要删除文件 "${file.originalName}" 吗？删除后不可恢复。`,
      '确认删除',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }

  try {
    await fileApi.deleteById(file.id)
    ElMessage.success('删除成功')
    // 如果当前页删空了，回到上一页
    if (fileList.value.length === 1 && currentPage.value > 1) {
      currentPage.value--
    }
    await loadFiles()
  } catch {
    // 拦截器统一处理
  }
}

onMounted(() => {
  loadFiles()
})
</script>

<template>
  <div class="file-view">
    <h2>知识库</h2>

    <!-- 上传区域 -->
    <div class="glass-card file-upload-card">
      <div class="glass-card__header">
        <span>上传文件</span>
      </div>
      <div class="glass-card__body">
        <el-upload
          ref="uploadRef"
          drag
          :show-file-list="true"
          :before-upload="beforeUpload"
          :http-request="handleUpload"
          :limit="1"
          accept=".pdf,.doc,.docx,.txt,.md,.png,.jpg,.jpeg,.gif,.csv,.xls,.xlsx,.ppt,.pptx"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">
            将文件拖到此处，或 <em>点击选择文件</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">
              支持 PDF、Word、Excel、PPT、图片、文本、Markdown 等格式，单个文件不超过 100MB
            </div>
          </template>
        </el-upload>
      </div>
    </div>

    <!-- 文件列表 -->
    <div class="glass-card file-list-card">
      <div class="glass-card__header">
        <span>文件列表（{{ total }}）</span>
      </div>
      <div class="glass-card__body">
        <el-table
          :data="fileList"
          v-loading="loading"
          stripe
          style="width: 100%"
          empty-text="暂无上传文件"
        >
          <el-table-column label="文件名" min-width="240">
            <template #default="{ row }">
              <div class="file-name-cell">
                <el-icon><Document /></el-icon>
                <span>{{ row.originalName }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="90" align="center">
            <template #default="{ row }">
              <el-tag
                :type="getTypeColor(row.extension)"
                size="small"
                effect="light"
                round
              >
                {{ row.extension?.toUpperCase() || '-' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="大小" width="110" align="right">
            <template #default="{ row }">
              {{ formatSize(row.size) }}
            </template>
          </el-table-column>
          <el-table-column label="创建者" width="120" align="center">
            <template #default="{ row }">
              {{ row.createBy || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="170" align="center">
            <template #default="{ row }">
              {{ formatTime(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" align="center" fixed="right">
            <template #default="{ row }">
              <el-button text type="primary" size="small" @click="handleDownload(row)">
                <el-icon><Download /></el-icon>
                下载
              </el-button>
              <el-button text type="danger" size="small" @click="handleDelete(row)">
                <el-icon><Delete /></el-icon>
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div class="file-pagination" v-if="total > 0">
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
  </div>
</template>

<style lang="scss" scoped>
.file-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 24px;
  overflow: auto;

  h2 {
    margin-bottom: 24px;
    font-size: 22px;
    font-weight: 700;
    color: #1d1d1f;
    letter-spacing: -0.01em;
  }
}

// ---- 玻璃卡片 ----
.glass-card {
  background: rgba(255, 255, 255, 0.62);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.05), 0 2px 8px rgba(0, 0, 0, 0.03);
  margin-bottom: 16px;
  transition: all 0.25s cubic-bezier(0.25, 0.1, 0.25, 1);

  &:hover {
    box-shadow: 0 12px 40px rgba(0, 0, 0, 0.07), 0 4px 12px rgba(0, 0, 0, 0.04);
  }

  &__header {
    padding: 16px 20px 0;
    font-size: 15px;
    font-weight: 600;
    color: #1d1d1f;
  }

  &__body {
    padding: 16px 20px;
  }
}

.file-upload-card {
  flex-shrink: 0;
}

.file-list-card {
  flex: 1;
  display: flex;
  flex-direction: column;

  .glass-card__body {
    flex: 1;
    display: flex;
    flex-direction: column;
  }
}

.file-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;

  .el-icon {
    flex-shrink: 0;
    color: var(--el-color-primary);
  }
}

.file-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid rgba(210, 210, 215, 0.3);
}
</style>
