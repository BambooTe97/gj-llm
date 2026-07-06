<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/modules/user'
import type { FormInstance, FormRules } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const errorMessage = ref('')

const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 32, message: '用户名长度在 2 到 32 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度在 6 到 32 个字符', trigger: 'blur' },
  ],
}

async function handleLogin() {
  errorMessage.value = ''
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  const result = await userStore.login(form)
  loading.value = false

  if (result.success) {
    const redirect = (route.query.redirect as string) || '/chat'
    router.push(redirect)
  } else {
    errorMessage.value = result.message || '用户名或密码错误'
  }
}
</script>

<template>
  <div class="login-container">
    <!-- 背景装饰 -->
    <div class="login-bg-orb login-bg-orb--1"></div>
    <div class="login-bg-orb login-bg-orb--2"></div>
    <div class="login-bg-orb login-bg-orb--3"></div>

    <div class="login-card">
      <div class="login-icon">
        <div class="login-icon__circle">
          <el-icon :size="32"><Cpu /></el-icon>
        </div>
      </div>
      <h2 class="login-title">GJ-LLM</h2>
      <p class="login-subtitle">LLM 管理平台</p>

      <!-- 登录错误提示 -->
      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        :closable="false"
        show-icon
        class="login-error"
      />

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password
            @keyup.enter="handleLogin" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="loading" style="width: 100%" @click="handleLogin">
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.login-container {
  width: 100%;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0a1628 0%, #1a2a4a 30%, #1d3a5c 60%, #0f1f38 100%);
  overflow: hidden;
  position: relative;
}

// ---- 背景光晕装饰 ----
.login-bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.3;
  pointer-events: none;

  &--1 {
    width: 400px;
    height: 400px;
    background: radial-gradient(circle, rgba(0, 113, 227, 0.5), transparent);
    top: -100px;
    right: -80px;
    animation: float 8s ease-in-out infinite;
  }

  &--2 {
    width: 300px;
    height: 300px;
    background: radial-gradient(circle, rgba(52, 199, 89, 0.4), transparent);
    bottom: -60px;
    left: -60px;
    animation: float 10s ease-in-out infinite reverse;
  }

  &--3 {
    width: 200px;
    height: 200px;
    background: radial-gradient(circle, rgba(255, 159, 10, 0.35), transparent);
    top: 40%;
    left: 50%;
    animation: float 7s ease-in-out infinite 2s;
  }
}

@keyframes float {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(30px, -20px) scale(1.05); }
  66% { transform: translate(-20px, 15px) scale(0.95); }
}

.login-card {
  width: 420px;
  padding: 44px 40px 40px;
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(28px);
  -webkit-backdrop-filter: blur(28px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 20px;
  box-shadow:
    0 20px 60px rgba(0, 0, 0, 0.2),
    0 4px 16px rgba(0, 0, 0, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.4);
  position: relative;
  z-index: 1;
}

.login-icon {
  display: flex;
  justify-content: center;
  margin-bottom: 16px;

  &__circle {
    width: 64px;
    height: 64px;
    border-radius: 50%;
    background: linear-gradient(135deg, #0071e3, #4d9ff7);
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    box-shadow: 0 8px 24px rgba(0, 113, 227, 0.35);
  }
}

.login-title {
  text-align: center;
  font-size: 28px;
  font-weight: 700;
  color: #1d1d1f;
  margin-bottom: 4px;
  letter-spacing: -0.02em;
}

.login-subtitle {
  text-align: center;
  font-size: 14px;
  color: #86868b;
  margin-bottom: 32px;
}

.login-error {
  margin-bottom: 16px;
}
</style>
