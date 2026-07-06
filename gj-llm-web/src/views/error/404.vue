<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="error-page">
    <div class="error-card">
      <h1>404</h1>
      <p>页面不存在</p>
      <el-button type="primary" round @click="router.push('/chat')">返回首页</el-button>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.error-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: linear-gradient(135deg, #e8ecf1 0%, #f0f2f5 30%, #f5f5f7 60%, #e8ecf1 100%);
}

.error-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 48px 64px;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 20px;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.08), 0 4px 16px rgba(0, 0, 0, 0.04);

  h1 {
    font-size: 72px;
    font-weight: 700;
    background: linear-gradient(135deg, #0071e3, #4d9ff7);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
    line-height: 1;
  }

  p {
    font-size: 16px;
    color: #86868b;
    margin-bottom: 8px;
  }
}
</style>
