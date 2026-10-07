<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/modules/user'
import { useNotificationStore } from '@/stores/modules/notification'
import { notifyApi } from '@/api/modules/notify'
import * as Icons from '@element-plus/icons-vue'
import type { Menu } from '@/api/types'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const notificationStore = useNotificationStore()

/** 测试通知发送中 */
const sending = ref(false)

/** 发送测试通知：在线经 WS 实时弹 toast；离线落库后刷新列表可见（两种路径都能验证） */
async function handleSendTest() {
  sending.value = true
  try {
    await notifyApi.sendTest({
      title: '手动测试通知',
      content: `${new Date().toLocaleTimeString()} 来自 ${userStore.username || '当前用户'} 的长连接推送测试`,
      level: 'success',
    })
    if (!notificationStore.connected) {
      await notificationStore.refresh()
    }
  } finally {
    sending.value = false
  }
}

/** 顶层导航菜单（由后端菜单树驱动） */
const navItems = computed<Menu[]>(() => userStore.menus)

/** 当前激活的顶层菜单索引（含子菜单路径前缀匹配） */
const activeIndex = computed(() => {
  const path = route.path
  const idx = navItems.value.findIndex((m) => {
    if (m.path && path === m.path) return true
    return (m.children || []).some(
      (c) => !!c.path && (path === c.path || path.startsWith(c.path + '/')),
    )
  })
  return idx >= 0 ? idx : -1
})

/** 按图标名解析 Element Plus 图标组件，缺省回退 Menu 图标 */
function resolveIcon(name?: string | null) {
  if (!name) return Icons.Menu
  return (Icons as Record<string, unknown>)[name] || Icons.Menu
}

/** 点击导航：目录跳首个子菜单，菜单跳自身路径 */
function handleNav(menu: Menu) {
  if (menu.type === 'M' && menu.children?.length) {
    const first = menu.children.find((c) => c.type !== 'B' && c.path)
    if (first?.path) {
      router.push(first.path)
      return
    }
  }
  if (menu.path) {
    router.push(menu.path)
  }
}

function handleLogout() {
  notificationStore.disconnect()
  userStore.logout()
}
</script>

<template>
  <div class="app-header">
    <!-- ===== Logo ===== -->
    <div class="header-logo" @click="router.push('/chat')">
      <svg viewBox="0 0 36 36" width="36" height="36" fill="none" class="header-logo__icon">
        <rect width="36" height="36" rx="9" fill="url(#logoGrad)" />
        <text x="18" y="24" text-anchor="middle" fill="#fff" font-size="18" font-weight="750">G</text>
        <defs>
          <linearGradient id="logoGrad" x1="0" y1="0" x2="36" y2="36" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stop-color="#6366f1" />
            <stop offset="100%" stop-color="#8b5cf6" />
          </linearGradient>
        </defs>
      </svg>
      <span class="header-logo__text">
        <span class="header-logo__brand">GJ</span><span class="header-logo__suffix">-LLM</span>
      </span>
    </div>

    <!-- ===== 导航（动态菜单） ===== -->
    <nav class="header-nav">
      <div
        v-for="(item, index) in navItems"
        :key="item.id"
        class="header-nav__item"
        :class="{ active: index === activeIndex }"
        @click="handleNav(item)"
      >
        <span class="header-nav__icon">
          <el-icon :size="20"><component :is="resolveIcon(item.icon)" /></el-icon>
        </span>
        <span class="header-nav__label">{{ item.name }}</span>
      </div>
    </nav>


    <!-- ===== 用户区（通知铃铛 + 账号） ===== -->
    <div class="header-user">
      <!-- 消息通知（gj-netty 长连接） -->
      <div v-permission="'notify:view'" class="header-bell">
        <el-popover placement="bottom-end" :width="380" trigger="click" :teleported="false">
          <template #reference>
            <el-badge
              :value="notificationStore.unreadCount"
              :hidden="notificationStore.unreadCount === 0"
              :max="99"
            >
              <el-icon :size="20" class="header-bell__icon"><component :is="Icons.Bell" /></el-icon>
            </el-badge>
          </template>

          <div class="notify-panel">
            <div class="notify-panel__header">
              <span class="notify-panel__title">消息通知</span>
              <el-button
                v-if="notificationStore.unreadCount > 0"
                link
                type="primary"
                size="small"
                @click="notificationStore.markAllRead()"
              >
                全部已读
              </el-button>
            </div>
            <el-scrollbar max-height="360px">
              <template v-if="notificationStore.list.length">
                <div
                  v-for="item in notificationStore.list"
                  :key="item.id"
                  class="notify-item"
                  :class="{ 'notify-item--unread': item.readFlag === 0 }"
                  @click="notificationStore.markRead(item)"
                >
                  <div class="notify-item__row">
                    <span class="notify-item__title">{{ item.title }}</span>
                    <span v-if="item.readFlag === 0" class="notify-item__dot" />
                  </div>
                  <div class="notify-item__content">{{ item.content }}</div>
                  <div class="notify-item__time">{{ item.createdAt }}</div>
                </div>
              </template>
              <el-empty v-else description="暂无通知" :image-size="60" />
            </el-scrollbar>
            <div class="notify-panel__footer">
              <el-button size="small" :loading="sending" @click="handleSendTest">发一条测试通知</el-button>
              <span class="notify-panel__status" :class="{ 'is-online': notificationStore.connected }">
                {{ notificationStore.connected ? '已连接' : '未连接' }}
              </span>
            </div>
          </div>
        </el-popover>
      </div>

      <el-dropdown trigger="click" placement="bottom-end" :teleported="false">
        <div class="header-user__trigger">
          <el-avatar :size="32" icon="UserFilled" />
          <span class="header-user__name">{{ userStore.username || '用户' }}</span>
          <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
            <polyline points="6 9 12 15 18 9" />
          </svg>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="router.push('/settings')">
              <el-icon><component :is="Icons.Setting" /></el-icon>设置
            </el-dropdown-item>
            <el-dropdown-item divided @click="handleLogout">
              <el-icon><component :is="Icons.SwitchButton" /></el-icon>退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.app-header {
  display: flex;
  align-items: center;
  height: 100%;
  padding: 0 28px;
}

// ========================= Logo =========================
.header-logo {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  user-select: none;
  margin-right: 40px;

  &__icon {
    filter: drop-shadow(0 2px 6px rgba(99, 102, 241, 0.3));
  }

  &__text {
    font-size: 18px;
    font-weight: 700;
    letter-spacing: -0.01em;
    display: flex;
    align-items: baseline;
  }

  &__brand {
    background: linear-gradient(135deg, #818cf8 0%, #a78bfa 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
    font-weight: 820;
  }

  &__suffix {
    color: #e0e0e0;
  }
}

// ========================= 导航 =========================
.header-nav {
  display: flex;
  align-items: stretch;
  flex: 1;
  height: 100%;
}

.header-nav__item {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  padding: 0 32px;
  height: 100%;
  cursor: pointer;
  user-select: none;
  position: relative;
  color: #9ca3af;
  transition: color 0.25s ease, background 0.25s ease;

  &::after {
    content: '';
    position: absolute;
    bottom: 0;
    left: 50%;
    width: calc(100% - 48px);
    height: 2.5px;
    border-radius: 3px 3px 0 0;
    background: #818cf8;
    transform: translateX(-50%) scaleX(0);
    transition: transform 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
  }

  &:hover {
    color: #e5e7eb;
    background: rgba(255, 255, 255, 0.04);
  }

  &.active {
    color: #f9fafb;

    .header-nav__icon {
      color: #818cf8;
    }

    &::after {
      transform: translateX(-50%) scaleX(1);
    }
  }
}

.header-nav__icon {
  display: flex;
  align-items: center;
  color: #6b7280;
  transition: color 0.25s ease;
}

.header-nav__label {
  font-size: 15px;
  font-weight: 500;
  letter-spacing: 0.04em;
  white-space: nowrap;
}

// ========================= 消息通知 =========================
.header-bell {
  display: flex;
  align-items: center;

  &__icon {
    color: #9ca3af;
    cursor: pointer;
    transition: color 0.25s ease;
    padding: 6px;
    border-radius: 8px;

    &:hover {
      color: #e5e7eb;
      background: rgba(255, 255, 255, 0.06);
    }
  }
}

// 铃铛面板（深色头部的浅色弹层）
.notify-panel {
  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 4px 10px;
    border-bottom: 1px solid #f0f0f0;
  }

  &__title {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
  }

  &__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 4px 2px;
    border-top: 1px solid #f0f0f0;
  }

  &__status {
    font-size: 11px;
    color: #c0c4cc;

    &.is-online {
      color: #67c23a;
    }
  }
}

.notify-item {
  padding: 10px 4px;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
  transition: background 0.2s ease;

  &:hover {
    background: #f7f8fa;
  }

  &--unread {
    .notify-item__title {
      color: #303133;
      font-weight: 600;
    }
  }

  &__row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }

  &__title {
    font-size: 13px;
    color: #606266;
  }

  &__dot {
    flex-shrink: 0;
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: #f56c6c;
  }

  &__content {
    margin-top: 3px;
    font-size: 12px;
    color: #909399;
    line-height: 1.5;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  &__time {
    margin-top: 4px;
    font-size: 11px;
    color: #c0c4cc;
  }
}

// ========================= 用户 =========================
.header-user {
  flex-shrink: 0;
  margin-left: 40px;
  display: flex;
  align-items: center;
  gap: 8px;

  &__trigger {
    display: flex;
    align-items: center;
    gap: 9px;
    cursor: pointer;
    padding: 5px 12px 5px 5px;
    border-radius: 22px;
    transition: background 0.2s ease;

    &:hover {
      background: rgba(255, 255, 255, 0.06);
    }
  }

  &__name {
    font-size: 14px;
    color: #d1d5db;
    font-weight: 500;
    user-select: none;
  }

  svg {
    color: #6b7280;
  }
}
</style>
