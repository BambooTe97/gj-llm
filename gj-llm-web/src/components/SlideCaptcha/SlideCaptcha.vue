<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Right } from '@element-plus/icons-vue'
import { authApi } from '@/api/modules/auth'
import type { CaptchaResponse } from '@/api/types'

/**
 * 滑动验证码（自研）。
 *
 * <p>挂载时向后端拉取拼图；后端开关关闭（enabled=false）时整块不渲染。
 * 拖动滑块使拼图对齐缺口，登录时父组件通过 {@code getData()} 取附带参数；
 * 登录失败后调用 {@code refresh()} 刷新。</p>
 *
 * <p>图片必须 1:1 渲染（禁止 CSS 缩放），否则前端像素与后端校验坐标错位。</p>
 */
defineOptions({ name: 'SlideCaptcha' })

const enabled = ref(false)
const loading = ref(false)
const captcha = ref<CaptchaResponse | null>(null)
/** 拼图块画布左缘 x 坐标（即提交给后端的 slideX） */
const dragX = ref(0)
const dragging = ref(false)

const bgUrl = computed(() => (captcha.value ? `data:image/png;base64,${captcha.value.bgImage}` : ''))
const puzzleUrl = computed(() => (captcha.value ? `data:image/png;base64,${captcha.value.puzzleImage}` : ''))
const puzzleStyle = computed(() => ({
  top: `${captcha.value?.puzzleY ?? 0}px`,
  left: `${dragX.value}px`,
}))

/** 拼图块画布边长（从图片自然尺寸读取，避免与后端常量硬编码不一致） */
const pieceSize = ref(64)
function onPuzzleLoad(e: Event) {
  pieceSize.value = (e.target as HTMLImageElement).naturalWidth || 64
}

/** 拉取/刷新验证码（登录失败后由父组件调用） */
async function refresh() {
  loading.value = true
  try {
    const res = await authApi.generateCaptcha()
    const data = res.data.data
    enabled.value = !!data?.enabled
    captcha.value = data?.enabled ? data : null
    dragX.value = 0
  } catch {
    // 拉取失败视为不可用，登录走无验证码参数（后端开关开启时会拒绝并提示）
    enabled.value = false
  } finally {
    loading.value = false
  }
}

/** 当前登录附带参数；开关关闭或未就绪时返回 undefined（父组件展开时安全跳过） */
function getData(): { captchaToken: string; slideX: number } | undefined {
  if (!enabled.value || !captcha.value) return undefined
  return { captchaToken: captcha.value.captchaToken || '', slideX: Math.round(dragX.value) }
}

// ==================== 拖动 ====================
let startX = 0
let startDragX = 0

function onPointerDown(e: PointerEvent) {
  if (!enabled.value || !captcha.value) return
  dragging.value = true
  startX = e.clientX
  startDragX = dragX.value
  ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
}

function onPointerMove(e: PointerEvent) {
  if (!dragging.value || !captcha.value) return
  const max = Math.max((captcha.value.width ?? 300) - pieceSize.value, 0)
  dragX.value = Math.min(Math.max(startDragX + (e.clientX - startX), 0), max)
}

function onPointerUp() {
  dragging.value = false
}

defineExpose({ refresh, getData })

onMounted(refresh)
</script>

<template>
  <div v-if="enabled" class="slide-captcha">
    <div class="slide-captcha__canvas">
      <img
        v-if="captcha"
        class="slide-captcha__bg"
        :src="bgUrl"
        width="300"
        height="100"
        alt="验证码"
        draggable="false"
      />
      <img
        v-if="captcha"
        class="slide-captcha__piece"
        :class="{ 'is-dragging': dragging }"
        :src="puzzleUrl"
        :style="puzzleStyle"
        alt=""
        draggable="false"
        @load="onPuzzleLoad"
      />
      <div v-if="loading" class="slide-captcha__loading">加载中...</div>
    </div>

    <div class="slide-captcha__track">
      <div class="slide-captcha__track-text">
        {{ dragX > 0 ? '松开后点击登录' : '按住滑块，拖动拼图对齐缺口' }}
      </div>
      <div
        class="slide-captcha__handle"
        :class="{ 'is-active': dragging }"
        :style="{ left: `${dragX}px` }"
        @pointerdown="onPointerDown"
        @pointermove="onPointerMove"
        @pointerup="onPointerUp"
        @pointercancel="onPointerUp"
      >
        <el-icon><Right /></el-icon>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.slide-captcha {
  margin-bottom: 18px;
  user-select: none;

  &__canvas {
    position: relative;
    width: 300px;
    height: 100px;
    margin: 0 auto 10px;
    border-radius: 10px;
    overflow: hidden;
    background: rgba(255, 255, 255, 0.06);
  }

  // 图片 1:1 渲染，禁止缩放（坐标系对齐的关键）
  &__bg {
    display: block;
    width: 300px;
    height: 100px;
  }

  &__piece {
    position: absolute;
    // 不设 width/height：按图片自然尺寸 1:1 渲染
    cursor: grab;
    touch-action: none;
    filter: drop-shadow(0 2px 6px rgba(0, 0, 0, 0.4));

    &.is-dragging {
      cursor: grabbing;
    }
  }

  &__loading {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    color: rgba(200, 210, 225, 0.5);
  }

  &__track {
    position: relative;
    width: 300px;
    height: 40px;
    margin: 0 auto;
    border-radius: 10px;
    background: rgba(255, 255, 255, 0.06);
    border: 0.5px solid rgba(255, 255, 255, 0.12);
    overflow: hidden;
  }

  &__track-text {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    color: rgba(200, 210, 225, 0.4);
    letter-spacing: 0.05em;
    pointer-events: none;
  }

  &__handle {
    position: absolute;
    top: 0;
    width: 40px;
    height: 40px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, #0066d6, #3d8ef7);
    color: #fff;
    cursor: grab;
    touch-action: none;
    border-radius: 10px;
    transition: background 0.2s;

    &.is-active {
      cursor: grabbing;
      background: linear-gradient(135deg, #0a72e0, #4d9cf9);
    }
  }
}
</style>
