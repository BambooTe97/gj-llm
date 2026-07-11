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

const form = reactive({ username: '', password: '' })
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
    router.push((route.query.redirect as string) || '/chat')
  } else {
    errorMessage.value = result.message || '用户名或密码错误'
  }
}

// ===================================================================
// 太阳系 Canvas
// ===================================================================
const canvasRef = ref<HTMLCanvasElement>()
let animId = 0, w = 0, h = 0, time = 0

interface BgStar { x: number; y: number; r: number; alpha: number; twV: number; twP: number }
let bgStars: BgStar[] = []

interface PlanetData {
  name: string
  orbitRx: number; orbitRy: number
  angle: number; speed: number
  radius: number
  color: string; glowColor: string
  hasRing?: boolean; hasBands?: boolean
  isEarth?: boolean
}

let planets: PlanetData[] = []
let sunX = 0, sunY = 0, sunR = 0
let moonAngle = 0
const MOON_ORBIT = 22
const MOON_SPEED = 8.0
const MOON_R = 2.2

function init() {
  bgStars = []
  time = 0; moonAngle = 0

  // 太阳位置 — 居中偏左
  sunX = w * 0.33
  sunY = h * 0.5
  sunR = Math.min(w, h) * 0.06

  // 背景星
  for (let i = 0; i < 350; i++) {
    bgStars.push({
      x: Math.random() * w, y: Math.random() * h,
      r: Math.random() * 0.7 + 0.15,
      alpha: Math.random() * 0.5 + 0.1,
      twV: Math.random() * 0.4 + 0.05,
      twP: Math.random() * Math.PI * 2,
    })
  }

  // 行星轨道 — 半径相对于 min(w,h)
  const base = Math.min(w, h)
  planets = [
    { name: '水星',   orbitRx: 0.10, orbitRy: 0.04,  angle: Math.random() * Math.PI * 2, speed: 3.5,  radius: 2.5,  color: '#b0a090', glowColor: '#c0b8a8' },
    { name: '金星',   orbitRx: 0.15, orbitRy: 0.06,  angle: Math.random() * Math.PI * 2, speed: 2.6,  radius: 4.5,  color: '#e0c870', glowColor: '#f0d880' },
    { name: '地球',   orbitRx: 0.21, orbitRy: 0.085, angle: Math.random() * Math.PI * 2, speed: 2.0,  radius: 6.5,  color: '#4a90d9', glowColor: '#80c0ff', isEarth: true },
    { name: '火星',   orbitRx: 0.27, orbitRy: 0.11,  angle: Math.random() * Math.PI * 2, speed: 1.5,  radius: 3.8,  color: '#d05030', glowColor: '#e07050' },
    { name: '木星',   orbitRx: 0.35, orbitRy: 0.14,  angle: Math.random() * Math.PI * 2, speed: 0.75, radius: 16,   color: '#c89860', glowColor: '#d8b080', hasBands: true },
    { name: '土星',   orbitRx: 0.44, orbitRy: 0.175, angle: Math.random() * Math.PI * 2, speed: 0.55, radius: 12,   color: '#e0d090', glowColor: '#f0e0a0', hasRing: true },
    { name: '天王星', orbitRx: 0.52, orbitRy: 0.21,  angle: Math.random() * Math.PI * 2, speed: 0.4,  radius: 8,    color: '#80c8d8', glowColor: '#a0e0f0' },
    { name: '海王星', orbitRx: 0.59, orbitRy: 0.24,  angle: Math.random() * Math.PI * 2, speed: 0.3,  radius: 7.5,  color: '#4060e0', glowColor: '#6080f8' },
    { name: '冥王星', orbitRx: 0.66, orbitRy: 0.27,  angle: Math.random() * Math.PI * 2, speed: 0.2,  radius: 2,    color: '#c8c0b0', glowColor: '#d8d0c0' },
  ]
}

// ==================== 绘制太阳 ====================
function drawSun(ctx: CanvasRenderingContext2D) {
  // 外层日冕
  const corona = ctx.createRadialGradient(sunX, sunY, sunR * 0.8, sunX, sunY, sunR * 3.5)
  corona.addColorStop(0, 'rgba(255,200,60,0.25)')
  corona.addColorStop(0.3, 'rgba(255,150,30,0.10)')
  corona.addColorStop(0.6, 'rgba(255,100,20,0.03)')
  corona.addColorStop(1, 'rgba(0,0,0,0)')
  ctx.fillStyle = corona
  ctx.beginPath(); ctx.arc(sunX, sunY, sunR * 3.5, 0, Math.PI * 2); ctx.fill()

  // 中层辉光
  const mid = ctx.createRadialGradient(sunX, sunY, sunR * 0.6, sunX, sunY, sunR * 1.8)
  mid.addColorStop(0, 'rgba(255,240,180,0.6)')
  mid.addColorStop(0.5, 'rgba(255,180,40,0.15)')
  mid.addColorStop(1, 'rgba(0,0,0,0)')
  ctx.fillStyle = mid
  ctx.beginPath(); ctx.arc(sunX, sunY, sunR * 1.8, 0, Math.PI * 2); ctx.fill()

  // 核心
  const core = ctx.createRadialGradient(sunX, sunY, 0, sunX, sunY, sunR)
  core.addColorStop(0, '#fffef0')
  core.addColorStop(0.3, '#ffe880')
  core.addColorStop(0.7, '#ffb020')
  core.addColorStop(1, '#e07010')
  ctx.fillStyle = core
  ctx.beginPath(); ctx.arc(sunX, sunY, sunR, 0, Math.PI * 2); ctx.fill()
}

// ==================== 土星环 ====================
function drawSaturnRing(ctx: CanvasRenderingContext2D, x: number, y: number, depth: number) {
  const tiltY = 0.25 // 环的倾斜
  const rInner = 16, rOuter = 27

  ctx.save()
  ctx.translate(x, y)
  ctx.scale(1, tiltY)

  const ringG = ctx.createLinearGradient(-rOuter, 0, rOuter, 0)
  ringG.addColorStop(0, 'rgba(200,180,140,0)')
  ringG.addColorStop(0.2, 'rgba(220,200,160,0.08)')
  ringG.addColorStop(0.4, 'rgba(240,220,180,0.25)')
  ringG.addColorStop(0.48, 'rgba(200,180,140,0.05)')
  ringG.addColorStop(0.52, 'rgba(200,180,140,0.05)')
  ringG.addColorStop(0.7, 'rgba(220,200,160,0.15)')
  ringG.addColorStop(0.85, 'rgba(200,180,140,0.05)')
  ringG.addColorStop(1, 'rgba(180,160,120,0)')
  ctx.fillStyle = ringG
  ctx.beginPath()
  ctx.arc(0, 0, rOuter, 0, Math.PI * 2)
  ctx.arc(0, 0, rInner, 0, Math.PI * 2, true)
  ctx.fill()
  ctx.restore()
}

// ==================== 绘制行星 ====================
function drawPlanet(ctx: CanvasRenderingContext2D, p: PlanetData) {
  const px = sunX + p.orbitRx * Math.min(w, h) * Math.cos(p.angle)
  const py = sunY + p.orbitRy * Math.min(w, h) * Math.sin(p.angle)

  // 近大远小 — 根据在轨道上的前后位置调整大小
  const depthFactor = 1.0 + Math.sin(p.angle) * 0.28
  const r = p.radius * depthFactor

  if (r < 0.5) return

  // 大气辉光
  const glow = ctx.createRadialGradient(px, py, r * 0.8, px, py, r * 1.8)
  glow.addColorStop(0, p.glowColor.replace(')', ',0.25)').replace('rgb', 'rgba'))
  glow.addColorStop(0.5, p.glowColor.replace(')', ',0.06)').replace('rgb', 'rgba'))
  glow.addColorStop(1, 'rgba(0,0,0,0)')
  ctx.fillStyle = glow
  ctx.beginPath(); ctx.arc(px, py, r * 1.8, 0, Math.PI * 2); ctx.fill()

  // 球体
  const bodyG = ctx.createRadialGradient(px - r * 0.25, py - r * 0.25, r * 0.05, px, py, r)
  bodyG.addColorStop(0, lighten(p.color, 0.5))
  bodyG.addColorStop(0.3, p.color)
  bodyG.addColorStop(0.7, darken(p.color, 0.4))
  bodyG.addColorStop(1, darken(p.color, 0.75))
  ctx.fillStyle = bodyG
  ctx.beginPath(); ctx.arc(px, py, r, 0, Math.PI * 2); ctx.fill()

  // 地球特殊处理
  if (p.isEarth) {
    drawEarthDetails(ctx, px, py, r)
    // 月球
    const mx = px + MOON_ORBIT * Math.cos(moonAngle)
    const my = py + MOON_ORBIT * Math.sin(moonAngle) * 0.6  // 略微椭圆
    // 月球辉光
    const mGlow = ctx.createRadialGradient(mx, my, 0, mx, my, MOON_R * 3)
    mGlow.addColorStop(0, 'rgba(200,200,210,0.3)')
    mGlow.addColorStop(1, 'rgba(0,0,0,0)')
    ctx.fillStyle = mGlow
    ctx.beginPath(); ctx.arc(mx, my, MOON_R * 3, 0, Math.PI * 2); ctx.fill()
    // 月球本体
    const mG = ctx.createRadialGradient(mx - MOON_R * 0.3, my - MOON_R * 0.3, 0, mx, my, MOON_R)
    mG.addColorStop(0, '#f8f8f8')
    mG.addColorStop(0.5, '#c8c8c8')
    mG.addColorStop(1, '#505050')
    ctx.fillStyle = mG
    ctx.beginPath(); ctx.arc(mx, my, MOON_R, 0, Math.PI * 2); ctx.fill()
  }

  // 木星条纹
  if (p.hasBands) {
    ctx.save()
    ctx.beginPath(); ctx.arc(px, py, r, 0, Math.PI * 2); ctx.clip()
    for (let i = 0; i < 7; i++) {
      const by = py + (i / 3 - 1) * r
      const wobble = Math.sin(by * 0.1 + time) * r * 0.15
      ctx.fillStyle = i % 2 === 0
        ? 'rgba(160,100,50,0.15)'
        : 'rgba(220,180,120,0.08)'
      ctx.fillRect(px - r - 2, by - r * 0.09 + wobble, r * 2 + 4, r * 0.18)
    }
    ctx.restore()
  }

  // 土星环
  if (p.hasRing) {
    drawSaturnRing(ctx, px, py, depthFactor)
  }

  // 轨道线
  drawOrbit(ctx, p)
}

// ==================== 地球细节 ====================
function drawEarthDetails(ctx: CanvasRenderingContext2D, x: number, y: number, r: number) {
  ctx.save()
  ctx.beginPath(); ctx.arc(x, y, r, 0, Math.PI * 2); ctx.clip()

  // 绿色大陆块
  const continents = [
    { cx: -0.25, cy: -0.15, rx: 0.35, ry: 0.25, rot: 0.2 },
    { cx: 0.15, cy: 0.2, rx: 0.28, ry: 0.2, rot: -0.3 },
    { cx: -0.1, cy: 0.45, rx: 0.22, ry: 0.15, rot: 0.5 },
    { cx: 0.35, cy: -0.3, rx: 0.18, ry: 0.22, rot: -0.1 },
  ]
  for (const c of continents) {
    ctx.save()
    ctx.translate(x + c.cx * r, y + c.cy * r)
    ctx.rotate(c.rot)
    ctx.scale(1, 0.6)
    const g = ctx.createRadialGradient(0, 0, 0, 0, 0, c.rx * r)
    g.addColorStop(0, 'rgba(80,180,100,0.4)')
    g.addColorStop(0.6, 'rgba(40,140,60,0.2)')
    g.addColorStop(1, 'rgba(0,0,0,0)')
    ctx.fillStyle = g
    ctx.beginPath(); ctx.arc(0, 0, c.rx * r, 0, Math.PI * 2); ctx.fill()
    ctx.restore()
  }

  // 白色云层
  const clouds = [
    { cx: 0.1, cy: -0.35, rx: 0.3, ry: 0.08, rot: -0.1 },
    { cx: -0.3, cy: 0.1, rx: 0.25, ry: 0.06, rot: 0.3 },
    { cx: 0.2, cy: 0.3, rx: 0.2, ry: 0.05, rot: -0.2 },
  ]
  for (const c of clouds) {
    ctx.save()
    ctx.translate(x + c.cx * r, y + c.cy * r)
    ctx.rotate(c.rot)
    ctx.fillStyle = 'rgba(255,255,255,0.15)'
    ctx.beginPath()
    ctx.ellipse(0, 0, c.rx * r, c.ry * r, 0, 0, Math.PI * 2)
    ctx.fill()
    ctx.restore()
  }

  ctx.restore()

  // 额外大气光晕（比普通行星更亮）
  const atmoG = ctx.createRadialGradient(x, y, r * 0.85, x, y, r * 1.35)
  atmoG.addColorStop(0, 'rgba(100,180,255,0.15)')
  atmoG.addColorStop(0.5, 'rgba(60,140,240,0.06)')
  atmoG.addColorStop(1, 'rgba(0,0,0,0)')
  ctx.fillStyle = atmoG
  ctx.beginPath(); ctx.arc(x, y, r * 1.35, 0, Math.PI * 2); ctx.fill()
}

// ==================== 轨道线 ====================
function drawOrbit(ctx: CanvasRenderingContext2D, p: PlanetData) {
  const rx = p.orbitRx * Math.min(w, h)
  const ry = p.orbitRy * Math.min(w, h)
  ctx.strokeStyle = 'rgba(255,255,255,0.04)'
  ctx.lineWidth = 0.5
  ctx.beginPath()
  ctx.ellipse(sunX, sunY, rx, ry, 0, 0, Math.PI * 2)
  ctx.stroke()
}

// ==================== 颜色工具 ====================
function lighten(hex: string, amt: number): string {
  const num = parseInt(hex.slice(1), 16)
  const r = Math.min(255, (num >> 16) + 255 * amt)
  const g = Math.min(255, ((num >> 8) & 0xff) + 255 * amt)
  const b = Math.min(255, (num & 0xff) + 255 * amt)
  return `rgb(${r|0},${g|0},${b|0})`
}
function darken(hex: string, amt: number): string {
  const num = parseInt(hex.slice(1), 16)
  const r = (num >> 16) * (1 - amt)
  const g = ((num >> 8) & 0xff) * (1 - amt)
  const b = (num & 0xff) * (1 - amt)
  return `rgb(${r|0},${g|0},${b|0})`
}

// ==================== 主循环 ====================
function frame(ts: number) {
  const canvas = canvasRef.value; if (!canvas) return
  const ctx = canvas.getContext('2d')!
  const dt = Math.min((ts - ((frame as any)._ts || ts)) / 1000, 0.1)
  ;(frame as any)._ts = ts; time += dt

  // 深空底色
  ctx.fillStyle = '#020010'
  ctx.fillRect(0, 0, w, h)

  // 微弱的深空渐变 — 太阳周围稍亮
  const spaceG = ctx.createRadialGradient(sunX, sunY, sunR * 2, sunX, sunY, Math.max(w, h) * 1.2)
  spaceG.addColorStop(0, '#0a0a24')
  spaceG.addColorStop(0.5, '#040416')
  spaceG.addColorStop(1, '#010008')
  ctx.fillStyle = spaceG
  ctx.fillRect(0, 0, w, h)

  // 背景星
  for (const s of bgStars) {
    const tw = Math.sin(time * s.twV * 4 + s.twP) * 0.5 + 0.5
    const a = s.alpha * (0.4 + tw * 0.6)
    if (a < 0.02) continue
    ctx.fillStyle = `rgba(200,210,230,${a.toFixed(3)})`
    ctx.fillRect(s.x, s.y, s.r, s.r)
  }

  // 太阳
  drawSun(ctx)

  // 行星（远→近排序，远的先画）
  const sorted = [...planets].sort((a, b) => {
    const da = Math.sin(a.angle)
    const db = Math.sin(b.angle)
    return da - db  // sin 越小的（上方 = 远处）先画
  })
  for (const p of sorted) {
    drawPlanet(ctx, p)
  }

  // 更新角度
  for (const p of planets) {
    p.angle += p.speed * dt * 0.7
  }
  moonAngle += MOON_SPEED * dt

  animId = requestAnimationFrame(frame)
}

// ==================== resize & 生命周期 ====================
function resize() {
  const c = canvasRef.value; if (!c) return
  w = c.width = window.innerWidth; h = c.height = window.innerHeight
  init()
}

onMounted(() => {
  const c = canvasRef.value; if (!c) return
  w = c.width = window.innerWidth; h = c.height = window.innerHeight
  init(); animId = requestAnimationFrame(frame)
  window.addEventListener('resize', resize)
})

onUnmounted(() => {
  cancelAnimationFrame(animId)
  window.removeEventListener('resize', resize)
})
</script>

<template>
  <div class="login-container">
    <canvas ref="canvasRef" class="login-canvas"></canvas>

    <div class="login-card">
      <div class="login-icon">
        <div class="login-icon__ring"></div>
        <div class="login-icon__circle">
          <svg viewBox="0 0 48 48" width="30" height="30" fill="none">
            <circle cx="24" cy="10" r="3.5" fill="currentColor" opacity="0.9"/>
            <circle cx="14" cy="24" r="3.5" fill="currentColor" opacity="0.7"/>
            <circle cx="34" cy="24" r="3.5" fill="currentColor" opacity="0.7"/>
            <circle cx="18" cy="38" r="3.5" fill="currentColor" opacity="0.6"/>
            <circle cx="30" cy="38" r="3.5" fill="currentColor" opacity="0.6"/>
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

      <el-alert
        v-if="errorMessage" :title="errorMessage" type="error"
        :closable="false" show-icon class="login-error"
      />

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password" type="password" placeholder="请输入密码"
            show-password @keyup.enter="handleLogin"
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
.login-container {
  width: 100%; height: 100vh;
  display: flex; align-items: center; justify-content: flex-end;
  padding-right: 10%;
  background: #020010;
  overflow: hidden; position: relative;
}

.login-canvas {
  position: absolute; inset: 0; z-index: 0;
  pointer-events: none;
}

// ==================== 登录卡片 ====================
.login-card {
  width: 420px; padding: 44px 40px 40px;
  background: rgba(255, 255, 255, 0.04);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 0.5px solid rgba(255, 255, 255, 0.07);
  border-radius: 24px;
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.03),
    inset 0 1px 0 rgba(255, 255, 255, 0.04);
  position: relative; z-index: 2;
}

// ==================== AI 图标 ====================
.login-icon {
  display: flex; justify-content: center; margin-bottom: 20px; position: relative;

  &__ring {
    position: absolute; top: 50%; left: 50%;
    transform: translate(-50%, -50%);
    width: 90px; height: 90px; border-radius: 50%;
    border: 1.5px solid rgba(120, 160, 240, 0.18);
    animation: ringPulse 2.5s ease-in-out infinite;
  }

  &__circle {
    width: 68px; height: 68px; border-radius: 50%;
    background: linear-gradient(135deg, #0066d6, #3d8ef7);
    display: flex; align-items: center; justify-content: center;
    color: #fff;
    box-shadow:
      0 8px 32px rgba(0, 113, 227, 0.45),
      0 0 60px rgba(0, 113, 227, 0.15);
    position: relative; z-index: 1;
    animation: iconGlow 3s ease-in-out infinite;
  }
}

@keyframes ringPulse {
  0%, 100% { transform: translate(-50%, -50%) scale(1); opacity: 0.5; }
  50%      { transform: translate(-50%, -50%) scale(1.25); opacity: 0; }
}

@keyframes iconGlow {
  0%, 100% { box-shadow: 0 8px 32px rgba(0, 113, 227, 0.45), 0 0 60px rgba(0, 113, 227, 0.15); }
  50%      { box-shadow: 0 8px 40px rgba(0, 140, 255, 0.55), 0 0 80px rgba(0, 140, 255, 0.25); }
}

// ==================== 标题 ====================
.login-title {
  text-align: center; font-size: 30px; font-weight: 700;
  color: #e8ecf2; margin-bottom: 4px; letter-spacing: -0.02em;

  &__gradient {
    background: linear-gradient(135deg, #7db8f8, #b0d4ff, #7db8f8);
    -webkit-background-clip: text; -webkit-text-fill-color: transparent;
    background-clip: text; background-size: 200% 200%;
    animation: titleShimmer 4s ease-in-out infinite;
  }
}

@keyframes titleShimmer {
  0%, 100% { background-position: 0% 50%; }
  50%      { background-position: 100% 50%; }
}

.login-subtitle {
  text-align: center; font-size: 14px;
  color: rgba(200, 210, 225, 0.45);
  margin-bottom: 32px; letter-spacing: 0.04em;
}

.login-error { margin-bottom: 16px; }

// ==================== 表单 ====================
:deep(.el-form-item__label) {
  color: rgba(220, 225, 235, 0.7); font-weight: 500;
}

:deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.06);
  border: 0.5px solid rgba(255, 255, 255, 0.12);
  box-shadow: none !important; border-radius: 10px;
  transition: border-color 0.2s, box-shadow 0.2s, background 0.2s;

  &:hover { border-color: rgba(255, 255, 255, 0.25); background: rgba(255, 255, 255, 0.1); }
  &.is-focus {
    border-color: #4088e0; background: rgba(255, 255, 255, 0.12);
    box-shadow: 0 0 0 3px rgba(64, 136, 224, 0.18) !important;
  }
}

:deep(.el-input__inner) { color: #e8ecf2; }
:deep(.el-input__inner::placeholder) { color: rgba(200, 210, 225, 0.3); }
</style>
