import { storage } from '@/utils/storage'
import { TOKEN_KEY } from '@/constants'

/**
 * gj-netty 长连接客户端 —— 与基座协议信封对齐（GJ_NETTY_GUIDE.md 第五节）：
 *
 * - 连接：ws(s)://{host}:9090/ws?token={accessToken}
 * - 心跳：收到服务端 sys.heartbeat 后同名回复，刷新服务端读空闲（60s 断开）
 * - 重连：指数退避 1s → 30s 封顶；登出（令牌清除）后自动停止
 * - 上行：send(type, payload) 自增 seq；下行经 onMessage 分发给业务方
 */

/** 协议信封 —— 与后端 MessageEnvelope 对齐 */
export interface WsEnvelope {
  v: number
  type: string
  seq: number
  ack: boolean
  payload: unknown
  ts: number
}

export type WsMessageHandler = (envelope: WsEnvelope) => void
export type WsStatusHandler = (connected: boolean) => void

/** WS 端口（独立于 HTTP 8080，vite 代理不覆盖；可用 VITE_WS_PORT 覆盖） */
const WS_PORT = String(import.meta.env.VITE_WS_PORT ?? '9090')

const RECONNECT_BASE_MS = 1_000
const RECONNECT_MAX_MS = 30_000

export class WsClient {
  private ws: WebSocket | null = null
  private seq = 0
  private closedByUser = false
  private retryDelay = RECONNECT_BASE_MS
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null

  /** 业务消息回调（sys.* 已在内部消化，不会到达这里） */
  onMessage: WsMessageHandler | null = null
  /** 连接状态变化回调 */
  onStatusChange: WsStatusHandler | null = null

  get connected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN
  }

  /** 建立连接（重复调用幂等） */
  connect(): void {
    if (this.ws || this.closedByUser) return
    const token = storage.get<string>(TOKEN_KEY)
    if (!token) return

    const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const url = `${proto}://${window.location.hostname}:${WS_PORT}/ws?token=${encodeURIComponent(token)}`

    try {
      const ws = new WebSocket(url)
      this.ws = ws

      ws.onopen = () => {
        this.retryDelay = RECONNECT_BASE_MS
        this.onStatusChange?.(true)
      }
      ws.onmessage = (event: MessageEvent) => this.handleRaw(event.data)
      ws.onclose = () => {
        this.ws = null
        this.onStatusChange?.(false)
        this.scheduleReconnect()
      }
      ws.onerror = () => ws.close()
    } catch {
      this.ws = null
      this.scheduleReconnect()
    }
  }

  /** 上行一条业务消息（未连接返回 false，调用方可走 REST 兜底） */
  send(type: string, payload: unknown): boolean {
    if (!this.connected) return false
    const envelope: WsEnvelope = {
      v: 1,
      type,
      seq: ++this.seq,
      ack: false,
      payload,
      ts: Date.now(),
    }
    this.ws!.send(JSON.stringify(envelope))
    return true
  }

  /** 主动关闭（登出时调用），不再自动重连；重新 connect 前需 reset() */
  close(): void {
    this.closedByUser = true
    if (this.reconnectTimer !== null) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    this.ws?.close(1000, 'client_close')
    this.ws = null
    this.onStatusChange?.(false)
  }

  /** 复位手动关闭标记（重新登录后允许再连） */
  reset(): void {
    this.closedByUser = false
    this.retryDelay = RECONNECT_BASE_MS
  }

  // ==================== 私有 ====================

  private handleRaw(data: unknown): void {
    let envelope: WsEnvelope
    try {
      envelope = JSON.parse(String(data)) as WsEnvelope
    } catch {
      return
    }
    if (!envelope || typeof envelope.type !== 'string') return

    // 基座约定：服务端心跳 ping → 同名回复刷新读空闲
    if (envelope.type === 'sys.heartbeat') {
      this.send('sys.heartbeat', null)
      return
    }
    // 其余 sys.* 消息（ack / server.shutdown 等）暂不分发，业务只收自己的 topic
    if (envelope.type.startsWith('sys.')) return

    this.onMessage?.(envelope)
  }

  private scheduleReconnect(): void {
    if (this.closedByUser || this.reconnectTimer !== null) return
    // 令牌已清除（登出）则停止重连
    if (!storage.get<string>(TOKEN_KEY)) return

    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      this.retryDelay = Math.min(this.retryDelay * 2, RECONNECT_MAX_MS)
      this.connect()
    }, this.retryDelay)
  }
}
