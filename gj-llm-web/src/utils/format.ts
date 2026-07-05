/**
 * 格式化日期
 * @param date 日期字符串或 Date
 * @param format 格式（默认 yyyy-MM-dd HH:mm:ss）
 */
export function formatDate(date: string | Date, format = 'yyyy-MM-dd HH:mm:ss'): string {
  const d = typeof date === 'string' ? new Date(date) : date
  if (isNaN(d.getTime())) return ''

  const map: Record<string, string> = {
    yyyy: d.getFullYear().toString(),
    MM: String(d.getMonth() + 1).padStart(2, '0'),
    dd: String(d.getDate()).padStart(2, '0'),
    HH: String(d.getHours()).padStart(2, '0'),
    mm: String(d.getMinutes()).padStart(2, '0'),
    ss: String(d.getSeconds()).padStart(2, '0'),
  }

  return format.replace(/yyyy|MM|dd|HH|mm|ss/g, (key) => map[key])
}

/**
 * 截断文本，超出长度加省略号
 */
export function truncateText(text: string, maxLength = 50): string {
  if (text.length <= maxLength) return text
  return text.slice(0, maxLength) + '...'
}
