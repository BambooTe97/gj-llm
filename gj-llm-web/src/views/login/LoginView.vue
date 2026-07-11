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
// 吞噬星空风格宇宙 Canvas
// ===================================================================
const canvasRef = ref<HTMLCanvasElement>()
let animId = 0, w = 0, h = 0, time = 0
let nebulaTex: HTMLCanvasElement | null = null

// ---- 星 ----
interface Star { x: number; y: number; r: number; baseAlpha: number; twinkleV: number; twinkleP: number; hue: number; sat: number; vx: number; vy: number; speedMul: number }
interface HeroStar { x: number; y: number; r: number; hue: number; flareLen: number; twinkleV: number; twinkleP: number }
interface DustParticle { x: number; y: number; r: number; alpha: number; vx: number; vy: number; hue: number; life: number; maxLife: number }

let bgStars: Star[] = [], midStars: Star[] = [], nearStars: Star[] = []
let heroStars: HeroStar[] = []
let dustParticles: DustParticle[] = []

// ---- 行星 ----
interface Planet { cx: number; cy: number; r: number; rot: number; ringInner: number; ringOuter: number }

let planet: Planet

// ---- 流星 ----
interface Meteor { x: number; y: number; vx: number; vy: number; life: number; maxLife: number; len: number; hue: number }
let meteors: Meteor[] = []
let meteorTimer = 0

// ==================== 初始化 ====================
function init() {
  bgStars = []; midStars = []; nearStars = []; heroStars = []; dustParticles = []; meteors = []
  time = 0; meteorTimer = 0

  // —— 远层星 (400颗，极暗极小) ——
  for (let i = 0; i < 400; i++) {
    bgStars.push({
      x: Math.random() * w, y: Math.random() * h,
      r: Math.random() * 0.5 + 0.15,
      baseAlpha: Math.random() * 0.35 + 0.08,
      twinkleV: Math.random() * 0.3 + 0.05,
      twinkleP: Math.random() * Math.PI * 2,
      hue: Math.random() < 0.7 ? 220 + Math.random() * 40 : 30 + Math.random() * 20,
      sat: Math.random() * 30,
      vx: (Math.random() - 0.5) * 0.04, vy: (Math.random() - 0.5) * 0.04,
      speedMul: 0.3,
    })
  }

  // —— 中层星 (180颗) ——
  for (let i = 0; i < 180; i++) {
    midStars.push({
      x: Math.random() * w, y: Math.random() * h,
      r: Math.random() * 0.8 + 0.3,
      baseAlpha: Math.random() * 0.5 + 0.2,
      twinkleV: Math.random() * 0.5 + 0.1,
      twinkleP: Math.random() * Math.PI * 2,
      hue: [220, 230, 250, 210, 35, 0, 180][Math.floor(Math.random() * 7)],
      sat: Math.random() * 50,
      vx: (Math.random() - 0.5) * 0.15, vy: (Math.random() - 0.5) * 0.15,
      speedMul: 1,
    })
  }

  // —— 近层亮星 (45颗) ——
  for (let i = 0; i < 45; i++) {
    nearStars.push({
      x: Math.random() * w, y: Math.random() * h,
      r: Math.random() * 1.5 + 0.6,
      baseAlpha: Math.random() * 0.4 + 0.5,
      twinkleV: Math.random() * 0.8 + 0.25,
      twinkleP: Math.random() * Math.PI * 2,
      hue: [220, 240, 0, 35, 200][Math.floor(Math.random() * 5)],
      sat: Math.random() * 40,
      vx: (Math.random() - 0.5) * 0.35, vy: (Math.random() - 0.5) * 0.35,
      speedMul: 2.5,
    })
  }

  // —— 十字光芒恒星 (8颗) ——
  for (let i = 0; i < 8; i++) {
    heroStars.push({
      x: Math.random() * w * 0.8 + w * 0.1,
      y: Math.random() * h * 0.8 + h * 0.1,
      r: Math.random() * 1.5 + 0.8,
      hue: [220, 240, 30, 240, 220, 35, 200, 0][i],
      flareLen: 40 + Math.random() * 80,
      twinkleV: Math.random() * 0.4 + 0.3,
      twinkleP: Math.random() * Math.PI * 2,
    })
  }

  // —— 行星 ——
  planet = {
    cx: w * 0.8, cy: h * 0.55,
    r: Math.min(w * 0.1, 140),
    rot: 0,
    ringInner: Math.min(w * 0.13, 170),
    ringOuter: Math.min(w * 0.18, 230),
  }

  // —— 预渲染星云纹理 ——
  buildNebula()
}

// ==================== 星云纹理 ====================
function buildNebula() {
  const tw = Math.min(w, 1600), th = Math.min(h, 1000)
  nebulaTex = document.createElement('canvas')
  nebulaTex.width = tw; nebulaTex.height = th
  const ctx = nebulaTex.getContext('2d')!
  const sx = tw / w, sy = th / h

  // 深空底色
  const bg = ctx.createRadialGradient(tw * 0.35, th * 0.4, 0, tw * 0.5, th * 0.5, tw * 0.8)
  bg.addColorStop(0, '#0c0a20'); bg.addColorStop(0.5, '#060518'); bg.addColorStop(1, '#02000c')
  ctx.fillStyle = bg; ctx.fillRect(0, 0, tw, th)

  // 星云斑点 — 浓烈色彩
  const spots = [
    // 品红大星云 (左上→中央)
    { cx: 0.38, cy: 0.28, rx: 0.5, ry: 0.4, stops: ['rgba(140,20,80,0.30)','rgba(100,15,60,0.12)','rgba(40,5,30,0.03)','transparent'] },
    { cx: 0.32, cy: 0.32, rx: 0.35, ry: 0.3, stops: ['rgba(180,40,100,0.22)','rgba(120,25,70,0.08)','rgba(40,5,25,0.02)','transparent'] },
    // 青蓝星云 (右下)
    { cx: 0.72, cy: 0.68, rx: 0.45, ry: 0.38, stops: ['rgba(8,90,120,0.28)','rgba(6,60,90,0.10)','rgba(2,20,40,0.03)','transparent'] },
    { cx: 0.78, cy: 0.62, rx: 0.3, ry: 0.25, stops: ['rgba(15,130,170,0.18)','rgba(8,80,110,0.06)','transparent'] },
    // 深紫星云 (上部)
    { cx: 0.55, cy: 0.12, rx: 0.45, ry: 0.3, stops: ['rgba(70,15,100,0.24)','rgba(45,10,70,0.08)','rgba(15,3,25,0.02)','transparent'] },
    // 金色暖光 (左中)
    { cx: 0.15, cy: 0.55, rx: 0.28, ry: 0.35, stops: ['rgba(180,100,20,0.16)','rgba(120,60,10,0.06)','transparent'] },
    // 微小亮斑 — 星云中的"亮星云核"
    { cx: 0.4, cy: 0.3, rx: 0.12, ry: 0.08, stops: ['rgba(220,160,200,0.14)','rgba(180,120,160,0.04)','transparent'] },
    { cx: 0.7, cy: 0.65, rx: 0.1, ry: 0.07, stops: ['rgba(140,210,230,0.12)','rgba(100,180,200,0.03)','transparent'] },
  ]

  for (const s of spots) {
    const g = ctx.createRadialGradient(s.cx * tw, s.cy * th, 0, s.cx * tw, s.cy * th, s.rx * tw)
    for (let i = 0; i < s.stops.length; i++) {
      g.addColorStop(i / (s.stops.length - 1), s.stops[i])
    }
    ctx.fillStyle = g; ctx.fillRect(0, 0, tw, th)
  }

  // 暗尘带 — 用暗色渐变覆盖某些区域制造纵深感
  const darkBands = [
    { x1: 0.45, y1: 0, x2: 0.55, y2: 1, w: 0.08 },
    { x1: 0.2, y1: 0.4, x2: 0.35, y2: 0.9, w: 0.06 },
  ]
  for (const b of darkBands) {
    const g = ctx.createLinearGradient(b.x1 * tw, b.y1 * th, b.x2 * tw, b.y2 * th)
    g.addColorStop(0, 'rgba(2,1,8,0.0)')
    g.addColorStop(0.4, 'rgba(2,1,8,0.25)')
    g.addColorStop(0.6, 'rgba(2,1,8,0.25)')
    g.addColorStop(1, 'rgba(2,1,8,0.0)')
    ctx.fillStyle = g
    ctx.fillRect(b.x1 * tw - b.w * tw, 0, b.w * tw * 2, th)
  }
}

// ==================== 绘制十字光芒恒星 ====================
function drawHeroStar(ctx: CanvasRenderingContext2D, s: HeroStar) {
  const alpha = 0.55 + Math.sin(time * s.twinkleV + s.twinkleP) * 0.35
  if (alpha < 0.15) return
  const color = (a: number) => `hsla(${s.hue},40%,90%,${(a * alpha).toFixed(3)})`

  // 核心光点
  const coreG = ctx.createRadialGradient(s.x, s.y, 0, s.x, s.y, s.r * 3)
  coreG.addColorStop(0, color(0.95)); coreG.addColorStop(0.3, color(0.6)); coreG.addColorStop(1, 'transparent')
  ctx.fillStyle = coreG
  ctx.beginPath(); ctx.arc(s.x, s.y, s.r * 3, 0, Math.PI * 2); ctx.fill()

  // 十字光芒
  const fl = s.flareLen
  ctx.save()
  ctx.globalCompositeOperation = 'lighter'

  // 水平光芒
  const hGrad = ctx.createLinearGradient(s.x - fl, s.y, s.x + fl, s.y)
  hGrad.addColorStop(0, 'transparent')
  hGrad.addColorStop(0.3, color(0.15))
  hGrad.addColorStop(0.48, color(0.7))
  hGrad.addColorStop(0.5, color(0.9))
  hGrad.addColorStop(0.52, color(0.7))
  hGrad.addColorStop(0.7, color(0.15))
  hGrad.addColorStop(1, 'transparent')
  ctx.fillStyle = hGrad
  ctx.fillRect(s.x - fl, s.y - 0.4, fl * 2, 0.8)

  // 垂直光芒
  const vGrad = ctx.createLinearGradient(s.x, s.y - fl, s.x, s.y + fl)
  vGrad.addColorStop(0, 'transparent')
  vGrad.addColorStop(0.3, color(0.1))
  vGrad.addColorStop(0.48, color(0.5))
  vGrad.addColorStop(0.5, color(0.85))
  vGrad.addColorStop(0.52, color(0.5))
  vGrad.addColorStop(0.7, color(0.1))
  vGrad.addColorStop(1, 'transparent')
  ctx.fillStyle = vGrad
  ctx.fillRect(s.x - 0.4, s.y - fl, 0.8, fl * 2)

  // 对角光芒（短一些）
  const dLen = fl * 0.4
  for (let angle = 0.785; angle < Math.PI * 2; angle += Math.PI / 2) {
    const dx = Math.cos(angle), dy = Math.sin(angle)
    const dGrad = ctx.createLinearGradient(s.x, s.y, s.x + dx * dLen, s.y + dy * dLen)
    dGrad.addColorStop(0, color(0.5))
    dGrad.addColorStop(0.5, color(0.1))
    dGrad.addColorStop(1, 'transparent')
    ctx.strokeStyle = dGrad; ctx.lineWidth = 0.5
    ctx.beginPath(); ctx.moveTo(s.x, s.y); ctx.lineTo(s.x + dx * dLen, s.y + dy * dLen); ctx.stroke()
  }
  ctx.restore()
}

// ==================== 绘制行星 ====================
function drawPlanet(ctx: CanvasRenderingContext2D, p: Planet) {
  // 环 — 倾斜椭圆
  ctx.save()
  ctx.translate(p.cx, p.cy)
  ctx.scale(1, 0.28)
  ctx.rotate(-0.35)

  // 光环
  const ringG = ctx.createLinearGradient(-p.ringOuter, 0, p.ringOuter, 0)
  ringG.addColorStop(0, 'rgba(180,160,200,0)')
  ringG.addColorStop(0.15, 'rgba(200,180,220,0.10)')
  ringG.addColorStop(0.28, 'rgba(220,200,240,0.18)')  // 光环内侧更亮
  ringG.addColorStop(0.3, 'rgba(180,160,200,0.04)')
  ringG.addColorStop(0.43, 'rgba(140,120,160,0.0)')
  ringG.addColorStop(0.6, 'rgba(180,150,200,0.08)')
  ringG.addColorStop(0.75, 'rgba(200,180,220,0.14)')
  ringG.addColorStop(0.9, 'rgba(160,140,180,0.04)')
  ringG.addColorStop(1, 'rgba(120,100,140,0)')
  ctx.fillStyle = ringG
  ctx.beginPath()
  ctx.arc(0, 0, p.ringOuter, 0, Math.PI * 2)
  ctx.arc(0, 0, p.ringInner, 0, Math.PI * 2, true)
  ctx.fill()

  // 光环前面的辉光
  ctx.restore()

  // 行星大气辉光
  const atmoG = ctx.createRadialGradient(p.cx, p.cy, p.r * 0.9, p.cx, p.cy, p.r * 1.25)
  atmoG.addColorStop(0, 'rgba(140,160,200,0.08)')
  atmoG.addColorStop(0.6, 'rgba(80,120,180,0.04)')
  atmoG.addColorStop(1, 'rgba(0,0,0,0)')
  ctx.fillStyle = atmoG
  ctx.beginPath(); ctx.arc(p.cx, p.cy, p.r * 1.25, 0, Math.PI * 2); ctx.fill()

  // 球体
  const bodyG = ctx.createRadialGradient(
    p.cx - p.r * 0.25, p.cy - p.r * 0.25, p.r * 0.05,
    p.cx, p.cy, p.r
  )
  bodyG.addColorStop(0, '#f0e8d8'); bodyG.addColorStop(0.18, '#d8c098')
  bodyG.addColorStop(0.4, '#a08048'); bodyG.addColorStop(0.65, '#604020')
  bodyG.addColorStop(0.85, '#2a1508'); bodyG.addColorStop(1, '#0c0602')
  ctx.fillStyle = bodyG
  ctx.beginPath(); ctx.arc(p.cx, p.cy, p.r, 0, Math.PI * 2); ctx.fill()
}

// ==================== 绘制尘埃粒子 ====================
function drawDust(ctx: CanvasRenderingContext2D) {
  for (let i = dustParticles.length - 1; i >= 0; i--) {
    const dp = dustParticles[i]
    dp.life -= 0.016
    if (dp.life <= 0) { dustParticles.splice(i, 1); continue }
    const prog = dp.life / dp.maxLife
    const alpha = prog < 0.2 ? prog / 0.2 : 1 - prog
    dp.x += dp.vx * 0.016; dp.y += dp.vy * 0.016

    ctx.fillStyle = `hsla(${dp.hue},40%,85%,${(alpha * dp.alpha).toFixed(3)})`
    ctx.shadowColor = `hsla(${dp.hue},50%,80%,${(alpha * dp.alpha * 0.6).toFixed(3)})`
    ctx.shadowBlur = dp.r * 2
    ctx.beginPath(); ctx.arc(dp.x, dp.y, dp.r, 0, Math.PI * 2); ctx.fill()
  }
  ctx.shadowColor = 'transparent'; ctx.shadowBlur = 0
}

// ==================== 主循环 ====================
function frame(ts: number) {
  const canvas = canvasRef.value; if (!canvas) return
  const ctx = canvas.getContext('2d')!
  const dt = Math.min((ts - ((frame as any)._ts || ts)) / 1000, 0.1)
  ;(frame as any)._ts = ts; time += dt

  // 1. 深空底 + 星云纹理
  ctx.fillStyle = '#02000c'; ctx.fillRect(0, 0, w, h)

  if (nebulaTex) {
    // 星云纹理微呼吸
    const breathe = 1 + Math.sin(time * 0.12) * 0.04
    ctx.globalAlpha = 0.85 * breathe
    ctx.drawImage(nebulaTex, 0, 0, nebulaTex.width, nebulaTex.height, 0, 0, w, h)
    ctx.globalAlpha = 1
  }

  // 2. 远层星
  for (const s of bgStars) {
    s.x += s.vx * s.speedMul * dt * 10; s.y += s.vy * s.speedMul * dt * 10
    if (s.x < -5) s.x = w + 5; if (s.x > w + 5) s.x = -5
    if (s.y < -5) s.y = h + 5; if (s.y > h + 5) s.y = -5
    const tw = Math.sin(time * s.twinkleV * 6 + s.twinkleP) * 0.5 + 0.5
    const alpha = s.baseAlpha * (0.6 + tw * 0.4)
    if (alpha < 0.02) continue
    ctx.fillStyle = `hsla(${s.hue},${s.sat}%,85%,${alpha.toFixed(3)})`
    ctx.fillRect(s.x, s.y, s.r, s.r)
  }

  // 3. 中层星
  for (const s of midStars) {
    s.x += s.vx * s.speedMul * dt * 10; s.y += s.vy * s.speedMul * dt * 10
    if (s.x < -5) s.x = w + 5; if (s.x > w + 5) s.x = -5
    if (s.y < -5) s.y = h + 5; if (s.y > h + 5) s.y = -5
    const tw = Math.sin(time * s.twinkleV * 5 + s.twinkleP) * 0.5 + 0.5
    const alpha = s.baseAlpha * (0.5 + tw * 0.5)
    if (alpha < 0.03) continue
    ctx.fillStyle = `hsla(${s.hue},${s.sat}%,88%,${alpha.toFixed(3)})`
    ctx.beginPath(); ctx.arc(s.x, s.y, s.r, 0, Math.PI * 2); ctx.fill()
    // 小辉光
    if (alpha > 0.4) {
      ctx.fillStyle = `hsla(${s.hue},30%,90%,${(alpha * 0.15).toFixed(3)})`
      ctx.beginPath(); ctx.arc(s.x, s.y, s.r * 2.5, 0, Math.PI * 2); ctx.fill()
    }
  }

  // 4. 近层亮星
  for (const s of nearStars) {
    s.x += s.vx * s.speedMul * dt * 10; s.y += s.vy * s.speedMul * dt * 10
    if (s.x < -5) s.x = w + 5; if (s.x > w + 5) s.x = -5
    if (s.y < -5) s.y = h + 5; if (s.y > h + 5) s.y = -5
    const tw = Math.sin(time * s.twinkleV * 4 + s.twinkleP) * 0.5 + 0.5
    const alpha = s.baseAlpha * (0.5 + tw * 0.5)
    if (alpha < 0.05) continue
    const g = ctx.createRadialGradient(s.x, s.y, 0, s.x, s.y, s.r * 3)
    g.addColorStop(0, `hsla(${s.hue},30%,95%,${alpha.toFixed(3)})`)
    g.addColorStop(0.4, `hsla(${s.hue},30%,85%,${(alpha * 0.4).toFixed(3)})`)
    g.addColorStop(1, 'transparent')
    ctx.fillStyle = g
    ctx.beginPath(); ctx.arc(s.x, s.y, s.r * 3, 0, Math.PI * 2); ctx.fill()
  }

  // 5. 十字光芒恒星
  for (const s of heroStars) { drawHeroStar(ctx, s) }

  // 6. 行星
  planet.rot += dt * 0.04
  planet.cy += Math.sin(time * 0.15) * dt * 8  // 极其微弱的上下浮动
  drawPlanet(ctx, planet)

  // 7. 尘埃粒子
  if (dustParticles.length < 50 && Math.random() < dt * 15) {
    const angle = Math.random() * Math.PI * 2
    const dist = 80 + Math.random() * 300
    dustParticles.push({
      x: planet.cx + Math.cos(angle) * dist,
      y: planet.cy + Math.sin(angle) * dist,
      r: Math.random() * 0.8 + 0.2,
      alpha: Math.random() * 0.5 + 0.2,
      vx: (Math.random() - 0.5) * 25, vy: (Math.random() - 0.5) * 25,
      hue: [220, 240, 30, 200, 0][Math.floor(Math.random() * 5)],
      life: 3 + Math.random() * 8, maxLife: 5 + Math.random() * 8,
    })
  }
  drawDust(ctx)

  // 8. 流星
  meteorTimer -= dt
  if (meteorTimer <= 0) {
    meteors.push({
      x: Math.random() * w * 0.7, y: Math.random() * h * 0.2,
      vx: 300 + Math.random() * 400, vy: 200 + Math.random() * 500,
      life: 0, maxLife: 0.5 + Math.random() * 0.7,
      len: 50 + Math.random() * 100,
      hue: [220, 240, 0, 30][Math.floor(Math.random() * 4)],
    })
    meteorTimer = 1.5 + Math.random() * 5
  }
  for (let i = meteors.length - 1; i >= 0; i--) {
    const m = meteors[i]; m.life += dt
    if (m.life >= m.maxLife) { meteors.splice(i, 1); continue }
    const prog = m.life / m.maxLife
    const alpha = prog < 0.15 ? prog / 0.15 : 1 - prog
    const ex = m.x - (m.vx / 600) * m.len, ey = m.y - (m.vy / 600) * m.len
    const tg = ctx.createLinearGradient(m.x, m.y, ex, ey)
    tg.addColorStop(0, `hsla(${m.hue},30%,95%,${alpha.toFixed(3)})`)
    tg.addColorStop(1, 'transparent')
    ctx.strokeStyle = tg; ctx.lineWidth = 1.2; ctx.lineCap = 'round'
    ctx.beginPath(); ctx.moveTo(m.x, m.y); ctx.lineTo(ex, ey); ctx.stroke()

    ctx.fillStyle = `hsla(${m.hue},20%,100%,${alpha.toFixed(3)})`
    ctx.shadowColor = `hsla(${m.hue},30%,90%,${(alpha * 0.7).toFixed(3)})`
    ctx.shadowBlur = 5
    ctx.beginPath(); ctx.arc(m.x, m.y, 1.5, 0, Math.PI * 2); ctx.fill()
    ctx.shadowColor = 'transparent'; ctx.shadowBlur = 0

    m.x += m.vx * dt; m.y += m.vy * dt
  }

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
  nebulaTex = null
})
</script>

<template>
  <div class="login-container">
    <canvas ref="canvasRef" class="login-canvas"></canvas>

    <!-- 登录卡片 — 浮在宇宙之上 -->
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
  display: flex; align-items: center; justify-content: center;
  background: #02000c;
  overflow: hidden; position: relative;
}

.login-canvas {
  position: absolute; inset: 0; z-index: 0;
  pointer-events: none;
}

// ==================== 登录卡片 ====================
.login-card {
  width: 420px; padding: 44px 40px 40px;
  background: rgba(8, 10, 28, 0.5);
  backdrop-filter: blur(48px);
  -webkit-backdrop-filter: blur(48px);
  border: 0.5px solid rgba(255, 255, 255, 0.1);
  border-radius: 24px;
  box-shadow:
    0 24px 80px rgba(0, 0, 0, 0.6),
    0 4px 20px rgba(0, 0, 0, 0.35),
    0 0 120px rgba(80, 60, 160, 0.06),
    inset 0 1px 0 rgba(255, 255, 255, 0.05);
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
