<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
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

// ---- 粒子网络背景 ----
const canvasRef = ref<HTMLCanvasElement>()
let animId = 0

interface Particle {
  x: number; y: number;
  vx: number; vy: number;
  r: number;
}

onMounted(() => {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  let w = 0, h = 0
  const particles: Particle[] = []
  const PARTICLE_COUNT = 80
  const CONNECT_DIST = 140

  function resize() {
    w = canvas!.width = window.innerWidth
    h = canvas!.height = window.innerHeight
  }
  resize()
  window.addEventListener('resize', resize)

  // 初始化粒子
  for (let i = 0; i < PARTICLE_COUNT; i++) {
    particles.push({
      x: Math.random() * w,
      y: Math.random() * h,
      vx: (Math.random() - 0.5) * 0.5,
      vy: (Math.random() - 0.5) * 0.5,
      r: Math.random() * 2 + 1.2,
    })
  }

  function draw() {
    ctx!.clearRect(0, 0, w, h)

    // 更新 & 绘制粒子
    for (const p of particles) {
      p.x += p.vx
      p.y += p.vy
      if (p.x < 0) p.x = w
      if (p.x > w) p.x = 0
      if (p.y < 0) p.y = h
      if (p.y > h) p.y = 0

      ctx!.beginPath()
      ctx!.arc(p.x, p.y, p.r, 0, Math.PI * 2)
      ctx!.fillStyle = 'rgba(100, 160, 240, 0.55)'
      ctx!.fill()
    }

    // 连线
    for (let i = 0; i < particles.length; i++) {
      for (let j = i + 1; j < particles.length; j++) {
        const dx = particles[i].x - particles[j].x
        const dy = particles[i].y - particles[j].y
        const dist = Math.sqrt(dx * dx + dy * dy)
        if (dist < CONNECT_DIST) {
          const alpha = (1 - dist / CONNECT_DIST) * 0.18
          ctx!.beginPath()
          ctx!.moveTo(particles[i].x, particles[i].y)
          ctx!.lineTo(particles[j].x, particles[j].y)
          ctx!.strokeStyle = `rgba(100,160,240,${alpha})`
          ctx!.lineWidth = 0.6
          ctx!.stroke()
        }
      }
    }

    animId = requestAnimationFrame(draw)
  }

  draw()
})

onUnmounted(() => {
  cancelAnimationFrame(animId)
})
</script>

<template>
  <div class="login-container">
    <!-- 粒子网络画布 -->
    <canvas ref="canvasRef" class="login-canvas"></canvas>

    <!-- 动态光晕 -->
    <div class="login-orb login-orb--1"></div>
    <div class="login-orb login-orb--2"></div>
    <div class="login-orb login-orb--3"></div>

    <!-- 数据流线条（纯CSS） -->
    <div class="login-streams">
      <span v-for="i in 6" :key="i" class="login-stream" :style="{
        left: `${10 + (i - 1) * 16}%`,
        animationDelay: `${(i - 1) * 0.7}s`,
        animationDuration: `${3 + i * 0.6}s`,
        opacity: 0.06 + i * 0.015,
        width: `${1 + i * 0.3}px`,
      }"></span>
    </div>

    <!-- 登录卡片 -->
    <div class="login-card">
      <!-- AI 图标 -->
      <div class="login-icon">
        <div class="login-icon__ring"></div>
        <div class="login-icon__circle">
          <svg viewBox="0 0 48 48" width="30" height="30" fill="none">
            <!-- 神经网络节点 -->
            <circle cx="24" cy="10" r="3.5" fill="currentColor" opacity="0.9"/>
            <circle cx="14" cy="24" r="3.5" fill="currentColor" opacity="0.7"/>
            <circle cx="34" cy="24" r="3.5" fill="currentColor" opacity="0.7"/>
            <circle cx="18" cy="38" r="3.5" fill="currentColor" opacity="0.6"/>
            <circle cx="30" cy="38" r="3.5" fill="currentColor" opacity="0.6"/>
            <!-- 连线 -->
            <line x1="24" y1="13.5" x2="17" y2="21" stroke="currentColor" stroke-width="1.2" opacity="0.5"/>
            <line x1="24" y1="13.5" x2="31" y2="21" stroke="currentColor" stroke-width="1.2" opacity="0.5"/>
            <line x1="17" y1="26.5" x2="18.5" y2="34.5" stroke="currentColor" stroke-width="1.2" opacity="0.4"/>
            <line x1="31" y1="26.5" x2="29.5" y2="34.5" stroke="currentColor" stroke-width="1.2" opacity="0.4"/>
            <line x1="18" y1="24" x2="30" y2="24" stroke="currentColor" stroke-width="1" opacity="0.3"/>
          </svg>
        </div>
      </div>

      <h2 class="login-title">
        <span class="login-title__gradient">GJ-LLM</span>
      </h2>
      <p class="login-subtitle">智能知识库管理平台</p>

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
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            @keyup.enter="handleLogin"
          />
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
// ========================= 容器 =========================
.login-container {
  width: 100%;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(145deg, #060b18 0%, #0c1a30 25%, #0f2040 50%, #0b1628 75%, #060d1a 100%);
  overflow: hidden;
  position: relative;
}

// ========================= Canvas 粒子层 =========================
.login-canvas {
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
}

// ========================= 动态光晕 =========================
.login-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  pointer-events: none;
  z-index: 0;

  &--1 {
    width: 500px;
    height: 500px;
    background: radial-gradient(circle, rgba(0, 113, 227, 0.45), transparent);
    top: -150px;
    right: -100px;
    animation: orbFloat1 10s ease-in-out infinite;
  }

  &--2 {
    width: 380px;
    height: 380px;
    background: radial-gradient(circle, rgba(90, 180, 255, 0.3), transparent);
    bottom: -100px;
    left: -80px;
    animation: orbFloat2 12s ease-in-out infinite reverse;
  }

  &--3 {
    width: 260px;
    height: 260px;
    background: radial-gradient(circle, rgba(100, 140, 255, 0.35), transparent);
    top: 35%;
    left: 55%;
    animation: orbFloat3 8s ease-in-out infinite 3s;
  }
}

@keyframes orbFloat1 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33%  { transform: translate(40px, -30px) scale(1.08); }
  66%  { transform: translate(-25px, 20px) scale(0.93); }
}

@keyframes orbFloat2 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33%  { transform: translate(-35px, 25px) scale(1.06); }
  66%  { transform: translate(20px, -15px) scale(0.94); }
}

@keyframes orbFloat3 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33%  { transform: translate(25px, 20px) scale(1.1); }
  66%  { transform: translate(-20px, -25px) scale(0.9); }
}

// ========================= 数据流竖线 =========================
.login-streams {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
}

.login-stream {
  position: absolute;
  top: -120px;
  height: 200px;
  background: linear-gradient(
    180deg,
    transparent 0%,
    rgba(100, 160, 240, 0.5) 50%,
    transparent 100%
  );
  border-radius: 2px;
  animation: streamFall linear infinite;
}

@keyframes streamFall {
  0%   { transform: translateY(-200px); opacity: 0; }
  10%  { opacity: 1; }
  90%  { opacity: 1; }
  100% { transform: translateY(calc(100vh + 200px)); opacity: 0; }
}

// ========================= 登录卡片 =========================
.login-card {
  width: 420px;
  padding: 44px 40px 40px;
  background: rgba(255, 255, 255, 0.06);
  backdrop-filter: blur(40px);
  -webkit-backdrop-filter: blur(40px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 24px;
  box-shadow:
    0 24px 80px rgba(0, 0, 0, 0.45),
    0 4px 20px rgba(0, 0, 0, 0.25),
    inset 0 1px 0 rgba(255, 255, 255, 0.08);
  position: relative;
  z-index: 2;
}

// ========================= AI 图标 =========================
.login-icon {
  display: flex;
  justify-content: center;
  margin-bottom: 20px;
  position: relative;

  &__ring {
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    width: 90px;
    height: 90px;
    border-radius: 50%;
    border: 1.5px solid rgba(100, 160, 240, 0.2);
    animation: ringPulse 2.5s ease-in-out infinite;
  }

  &__circle {
    width: 68px;
    height: 68px;
    border-radius: 50%;
    background: linear-gradient(135deg, #0066d6, #3d8ef7);
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    box-shadow:
      0 8px 32px rgba(0, 113, 227, 0.45),
      0 0 60px rgba(0, 113, 227, 0.15);
    position: relative;
    z-index: 1;
    animation: iconGlow 3s ease-in-out infinite;
  }
}

@keyframes ringPulse {
  0%, 100% {
    transform: translate(-50%, -50%) scale(1);
    opacity: 0.6;
    border-color: rgba(100, 160, 240, 0.2);
  }
  50% {
    transform: translate(-50%, -50%) scale(1.25);
    opacity: 0;
    border-color: rgba(100, 160, 240, 0.05);
  }
}

@keyframes iconGlow {
  0%, 100% { box-shadow: 0 8px 32px rgba(0, 113, 227, 0.45), 0 0 60px rgba(0, 113, 227, 0.15); }
  50%      { box-shadow: 0 8px 40px rgba(0, 140, 255, 0.55), 0 0 80px rgba(0, 140, 255, 0.25); }
}

// ========================= 标题 =========================
.login-title {
  text-align: center;
  font-size: 30px;
  font-weight: 700;
  color: #e8ecf2;
  margin-bottom: 4px;
  letter-spacing: -0.02em;

  &__gradient {
    background: linear-gradient(135deg, #6db3f8, #a0cfff, #6db3f8);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
    background-size: 200% 200%;
    animation: titleShimmer 4s ease-in-out infinite;
  }
}

@keyframes titleShimmer {
  0%, 100% { background-position: 0% 50%; }
  50%      { background-position: 100% 50%; }
}

.login-subtitle {
  text-align: center;
  font-size: 14px;
  color: rgba(200, 210, 225, 0.55);
  margin-bottom: 32px;
  letter-spacing: 0.04em;
}

.login-error {
  margin-bottom: 16px;
}

// ========================= 输入框 — 白底亮色，在深色卡片上清晰可见 =========================
:deep(.el-form-item__label) {
  color: rgba(220, 225, 235, 0.8);
  font-weight: 500;
}

:deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(255, 255, 255, 0.25);
  box-shadow: none !important;
  border-radius: 10px;
  transition: border-color 0.2s, box-shadow 0.2s, background 0.2s;

  &:hover {
    border-color: rgba(255, 255, 255, 0.5);
    background: #fff;
  }

  &.is-focus {
    border-color: #3d8ef7;
    background: #fff;
    box-shadow: 0 0 0 3px rgba(61, 142, 247, 0.2) !important;
  }
}

:deep(.el-input__inner) {
  color: #1d1d1f;
}

:deep(.el-input__inner::placeholder) {
  color: #aeaeb2;
}
</style>
