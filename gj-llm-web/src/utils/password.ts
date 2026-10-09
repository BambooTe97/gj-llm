import type { FormItemRule } from 'element-plus'

/**
 * 密码复杂度策略（与后端 gj.llm.auth.password-policy 默认值保持一致）：
 * 长度 8-100 + 大写/小写/数字/特殊字符四类取三 + 可选禁止包含用户名。
 * 后端 PasswordPolicyValidator 为最终兜底。
 */
const MIN_LENGTH = 8
const MAX_LENGTH = 100
const MIN_CATEGORIES = 3

/** 检查密码是否满足复杂度要求（长度 + 四类字符取三） */
export function isStrongPassword(value: string): boolean {
  if (value.length < MIN_LENGTH || value.length > MAX_LENGTH) {
    return false
  }
  let categories = 0
  if (/[A-Z]/.test(value)) categories++
  if (/[a-z]/.test(value)) categories++
  if (/\d/.test(value)) categories++
  if (/[^A-Za-z0-9]/.test(value)) categories++
  return categories >= MIN_CATEGORIES
}

/**
 * 密码强度校验规则（用于 el-form rules，trigger: blur）。
 *
 * @param username 可选，传入时禁止密码包含用户名（与后端 forbidUsername 对齐）
 */
export function passwordStrengthRule(username?: string): FormItemRule {
  return {
    validator: (_rule, value: string, callback) => {
      if (!value) {
        // 空值交给 required 规则处理
        callback()
        return
      }
      if (value.length < MIN_LENGTH) {
        callback(new Error(`密码长度至少 ${MIN_LENGTH} 位`))
        return
      }
      if (value.length > MAX_LENGTH) {
        callback(new Error(`密码长度不能超过 ${MAX_LENGTH} 位`))
        return
      }
      if (!isStrongPassword(value)) {
        callback(new Error(`密码需包含大写字母、小写字母、数字、特殊字符中的至少 ${MIN_CATEGORIES} 类`))
        return
      }
      if (username && value.toLowerCase().includes(username.toLowerCase())) {
        callback(new Error('密码不能包含用户名'))
        return
      }
      callback()
    },
    trigger: 'blur',
  }
}
