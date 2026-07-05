<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="error-page">
    <h1>500</h1>
    <p>服务器内部错误</p>
    <el-button type="primary" @click="router.push('/chat')">返回首页</el-button>
  </div>
</template>

<style lang="scss" scoped>
.error-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
  gap: 16px;

  h1 {
    font-size: 72px;
    color: #f56c6c;
  }

  p {
    font-size: 16px;
    color: #909399;
  }
}
</style>
