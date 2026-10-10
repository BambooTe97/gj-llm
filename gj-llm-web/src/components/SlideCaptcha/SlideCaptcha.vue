<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Right } from '@element-plus/icons-vue'
import type { CaptchaResponse } from '@/api/types'

/**
 * 滑动验证码（受控组件）—— 验证码数据由父级（CaptchaDialog）注入，本组件只负责拖动交互。
 *
 * <p>拖动滑块使拼图对齐缺口，松手即视为完成并通知父级（自动提交登录）；
 * 拖动距离需越过阈值才生效，避免误触点按直接放行。</p>
 *
 * <p>图片必须 1:1 渲染（禁止 CSS 缩放），否则前端像素与后端校验坐标错位。</p>
 */
defineOptions({ name: 'SlideCaptcha' })

const props = defineProps<{ captcha: CaptchaResponse | null }>()
const emit = defineEmits<{ completed: [] }>()

/** 有效拖动阈值（px）：低于此值视为误触，回弹且不触发提交 */
const MIN_DRAG = 10

/** 拼图块画布左缘 x 坐标（即提交给后端的 slideX） */
const dragX = ref(0)
const dragging = ref(false)
/** 松手且拖动有效后置位，锁定拖动直至父级注入新验证码 */
const done = ref(false)

watch(
  () => props.captcha,
  () => {
    dragX.value = 0
    done.value = false
  },
)

const bgUrl = computed(() => (props.captcha ? `data:image/png;base64,${props.captcha.bgImage}` : ''))
const puzzleUrl = computed(() => (props.captcha ? `data:image/png;base64,${props.captcha.puzzleImage}` : ''))
const puzzleStyle = computed(() => ({
  top: `${props.captcha?.puzzleY ?? 0}px`,
  left: `${dragX.value}px`,
}))

/** 拼图块画布边长（从图片自然尺寸读取，避免与后端常量硬编码不一致） */
const pieceSize = ref(64)
function onPuzzleLoad(e: Event) {
  pieceSize.value = (e.target as HTMLImageElement).naturalWidth || 64
}

/** 当前登录附带参数；未注入验证码或未完成拖动时返回 undefined（父组件展开时安全跳过） */
function getData(): { captchaToken: string; slideX: number } | undefined {
  if (!props.captcha || !done.value) return undefined
  return { captchaToken: props.captcha.captchaToken || '', slideX: Math.round(dragX.value) }
}

// ==================== 拖动 ====================
let startX = 0
let startDragX = 0

function onPointerDown(e: PointerEvent) {
  if (done.value || !props.captcha) return
  dragging.value = true
  startX = e.clientX
  startDragX = dragX.value
  ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
}

function onPointerMove(e: PointerEvent) {
  if (!dragging.value || done.value || !props.captcha) return
  const max = Math.max((props.captcha.width ?? 300) - pieceSize.value, 0)
  dragX.value = Math.min(Math.max(startDragX + (e.clientX - startX), 0), max)
}

function onPointerUp() {
  if (!dragging.value) return
  dragging.value = false
  // 拼图对齐型验证码：拖到尽头几乎必然校验失败，故"松手即提交"而非拖到最大值
  if (dragX.value >= MIN_DRAG) {
    done.value = true
    emit('completed')
  } else {
    // 误触点按：回弹复位
    dragX.value = 0
  }
}

defineExpose({ getData })
</script>

<template>
  <div v-if="captcha" class="slide-captcha">
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
    </div>

    <div class="slide-captcha__track">
      <div class="slide-captcha__track-text">
        {{ done ? '验证中...' : dragX > 0 ? '松开自动登录' : '按住滑块，拖动拼图对齐缺口' }}
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
