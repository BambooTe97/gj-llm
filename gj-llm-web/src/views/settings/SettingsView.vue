<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormItemRule, type FormRules } from 'element-plus'
import { authApi } from '@/api/modules/auth'
import { passwordStrengthRule } from '@/utils/password'

defineOptions({ name: 'SettingsView' })

// ---- 修改密码 ----
const pwdFormRef = ref<FormInstance>()
const pwdSubmitting = ref(false)
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const pwdRules = computed<FormRules>(() => ({
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    passwordStrengthRule(),
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule: FormItemRule, value: string, callback) => {
        if (value && value !== pwdForm.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}))

async function handleChangePassword() {
  const valid = await pwdFormRef.value?.validate().catch(() => false)
  if (!valid) return
  pwdSubmitting.value = true
  try {
    await authApi.changePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    ElMessage.success('密码修改成功')
    pwdFormRef.value?.resetFields()
  } catch {
    // 拦截器统一处理错误提示
  } finally {
    pwdSubmitting.value = false
  }
}
</script>

<template>
  <div class="settings-view">
    <h2>系统设置</h2>

    <div class="glass-card">
      <div class="glass-card__header">
        <span>修改密码</span>
      </div>
      <div class="glass-card__body">
        <el-form
          ref="pwdFormRef"
          class="pwd-form"
          :model="pwdForm"
          :rules="pwdRules"
          label-position="top"
          @submit.prevent
        >
          <el-form-item label="原密码" prop="oldPassword">
            <el-input
              v-model="pwdForm.oldPassword"
              type="password"
              show-password
              placeholder="请输入原密码"
            />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input
              v-model="pwdForm.newPassword"
              type="password"
              show-password
              placeholder="至少 8 位，含大写字母、小写字母、数字、特殊字符中的 3 类"
            />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input
              v-model="pwdForm.confirmPassword"
              type="password"
              show-password
              placeholder="请再次输入新密码"
              @keyup.enter="handleChangePassword"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="pwdSubmitting" @click="handleChangePassword">保存</el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>

    <div class="glass-card">
      <div class="glass-card__header">
        <span>关于</span>
      </div>
      <div class="glass-card__body">
        <div class="about-info">
          <div class="about-info__item">
            <span class="about-info__label">应用名称</span>
            <span class="about-info__value">GJ-LLM</span>
          </div>
          <div class="about-info__item">
            <span class="about-info__label">版本</span>
            <span class="about-info__value">1.0.0</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.settings-view {
  height: 100%;
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
  max-width: 640px;
  transition: all 0.25s cubic-bezier(0.25, 0.1, 0.25, 1);

  &:hover {
    box-shadow: 0 12px 40px rgba(0, 0, 0, 0.07), 0 4px 12px rgba(0, 0, 0, 0.04);
  }

  &__header {
    padding: 18px 24px 0;
    font-size: 15px;
    font-weight: 600;
    color: #1d1d1f;
  }

  &__body {
    padding: 16px 24px;
  }
}

// ---- 修改密码表单 ----
.pwd-form {
  max-width: 360px;
}

.about-info {
  &__item {
    display: flex;
    align-items: center;
    padding: 8px 0;
  }

  &__label {
    width: 100px;
    color: #86868b;
    font-size: 13px;
  }

  &__value {
    color: #1d1d1f;
    font-weight: 500;
  }
}
</style>
