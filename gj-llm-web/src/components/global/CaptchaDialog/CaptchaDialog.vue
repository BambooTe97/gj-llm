<script setup lang="ts">
import { ref } from 'vue'
import SlideCaptcha from '@/components/SlideCaptcha/SlideCaptcha.vue'
import { authApi } from '@/api/modules/auth'
import type { CaptchaResponse } from '@/api/types'

/**
 * 登录安全验证弹窗 —— 点击登录按钮后弹出，内嵌滑动验证码。
 *
 * <p>{@code open()} 先拉取验证码，就绪后才显示弹窗：开关关闭（enabled=false）
 * 或首次拉取失败时不闪窗，直接 {@code emit('skip')} 放行登录（后端开启开关时
 * 会拒绝并提示）。滑块松手（拖动有效）→ {@code emit('pass')}。</p>
 *
 * <p>登录失败由父级调 {@code setError()} 在弹窗内展示错误，并 {@code refetch()}
 * 换新题（captchaToken 一次性消费）。refetch 失败不 skip，避免失败-重试循环。</p>
 */
defineOptions({ name: 'CaptchaDialog' })

const emit = defineEmits<{ pass: []; skip: []; cancel: [] }>()

const visible = ref(false)
const errorText = ref('')
const captcha = ref<CaptchaResponse | null>(null)
const slideRef = ref<InstanceType<typeof SlideCaptcha>>()

/**
 * 拉取验证码并展示。
 *
 * @param allowSkip 首次 open 时为 true —— 未启用/拉取失败直接关闭并 skip 放行登录；
 *                  refetch 时为 false —— 失败保留错误文案在弹窗内，不自动放行
 */
async function fetchCaptcha(allowSkip: boolean) {
  try {
    const res = await authApi.generateCaptcha()
    const data = res.data.data
    if (!data?.enabled) {
      captcha.value = null
      visible.value = false
      if (allowSkip) emit('skip')
      return
    }
    errorText.value = ''
    captcha.value = data
    visible.value = true
  } catch {
    captcha.value = null
    if (allowSkip) {
      visible.value = false
      emit('skip')
    }
    // 弹窗已打开时的刷新失败：captcha 置空隐藏滑块，保留错误文案
  }
}

/** 登录按钮点击后调用：拉取验证码并弹出 */
function open() {
  errorText.value = ''
  fetchCaptcha(true)
}

/** 登录失败后在弹窗内展示错误文案 */
function setError(text: string) {
  errorText.value = text
}

/** 换新题（旧 captchaToken 已随登录请求消费） */
function refetch() {
  fetchCaptcha(false)
}

/** 取滑块松手后的登录附带参数；未完成拖动时为 undefined（父组件展开时安全跳过） */
function getData(): { captchaToken: string; slideX: number } | undefined {
  return slideRef.value?.getData()
}

function close() {
  visible.value = false
}

defineExpose({ open, close, getData, setError, refetch })
</script>

<template>
  <el-dialog
    v-model="visible"
    title="安全验证"
    width="360"
    align-center
    :close-on-click-modal="false"
    @close="emit('cancel')"
  >
    <el-alert
      v-if="errorText" :title="errorText" type="error"
      :closable="false" show-icon class="captcha-dialog__error"
    />
    <SlideCaptcha ref="slideRef" :captcha="captcha" @completed="emit('pass')" />
  </el-dialog>
</template>

<style lang="scss" scoped>
.captcha-dialog__error {
  margin-bottom: 12px;
}
</style>
