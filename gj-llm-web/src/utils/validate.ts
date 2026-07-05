/** 是否有效的手机号 */
export function isMobile(value: string): boolean {
  return /^1[3-9]\d{9}$/.test(value)
}

/** 是否有效的邮箱 */
export function isEmail(value: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)
}

/** 是否为空字符串 */
export function isEmpty(value: string): boolean {
  return value.trim() === ''
}
