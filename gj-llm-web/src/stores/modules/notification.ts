import { ref } from 'vue'
import { defineStore } from 'pinia'
import { ElNotification } from 'element-plus'
import { notifyApi, type NotifyItem } from '@/api/modules/notify'
import { WsClient, type WsEnvelope } from '@/utils/websocket'

/**
 * 消息通知 store —— gj-netty 长连接的业务接入点：
 *
 * - init()：登录后调用（DefaultLayout 挂载时），建立 WS 连接并拉取列表/未读数
 * - notify.message 下行 → ElNotification 弹 toast + 列表/角标更新
 * - 已读上报优先走 WS 上行（notify.read），未连接时 REST 兜底
 * - 重连成功（含断线、服务端重启后）自动 refresh 补拉离线通知
 */
export const useNotificationStore = defineStore('notification', () => {
  const list = ref<NotifyItem[]>([])
  const unreadCount = ref(0)
  const connected = ref(false)

  const ws = new WsClient()

  ws.onStatusChange = (up) => {
    connected.value = up
    // 每次连上（首次或重连）都补拉一次，覆盖离线期间的落库通知
    if (up) void refresh()
  }

  ws.onMessage = (envelope: WsEnvelope) => {
    if (envelope.type === 'notify.message') {
      handlePush(envelope.payload)
    }
  }

  // ==================== 生命周期 ====================

  /** 登录后初始化：建连 + 拉取（幂等，可重复调用） */
  async function init(): Promise<void> {
    if (ws.connected || connected.value) {
      await refresh()
      return
    }
    ws.reset()
    ws.connect()
    await refresh()
  }

  /** 登出时调用：主动断开并清空状态 */
  function disconnect(): void {
    ws.close()
    list.value = []
    unreadCount.value = 0
    connected.value = false
  }

  // ==================== 下行处理 ====================

  function handlePush(payload: unknown): void {
    const item = payload as NotifyItem | null
    if (!item || !item.id) return
    unreadCount.value++
    list.value.unshift({ ...item, readFlag: 0 })
    ElNotification({
      title: item.title || '新通知',
      message: item.content || '',
      type: (item.level as 'info' | 'success' | 'warning' | 'error') || 'info',
      duration: 4500,
    })
  }

  // ==================== 数据操作 ====================

  async function refresh(): Promise<void> {
    try {
      const [items, count] = await Promise.all([notifyApi.list(), notifyApi.unreadCount()])
      list.value = items
      unreadCount.value = count
    } catch {
      // 错误已由 axios 拦截器统一提示
    }
  }

  async function markRead(item: NotifyItem): Promise<void> {
    if (item.readFlag === 1) return
    // WS 在线走上行回执（演示基座双向能力），未连接走 REST 兜底
    if (!ws.send('notify.read', { ids: [item.id] })) {
      await notifyApi.markRead(item.id)
    }
    item.readFlag = 1
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  }

  async function markAllRead(): Promise<void> {
    await notifyApi.markAllRead()
    list.value.forEach((i) => {
      i.readFlag = 1
    })
    unreadCount.value = 0
  }

  return { list, unreadCount, connected, init, disconnect, refresh, markRead, markAllRead }
})
