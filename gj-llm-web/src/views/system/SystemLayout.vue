<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/modules/user'
import * as Icons from '@element-plus/icons-vue'
import type { Menu } from '@/api/types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 系统管理目录下的子菜单（菜单类型） */
const subMenus = computed<Menu[]>(() => {
  const sys = userStore.menus.find((m) => m.path === '/system')
  return (sys?.children || []).filter((c) => c.type === 'C')
})

const activePath = computed(() => route.path)

function resolveIcon(name?: string | null) {
  if (!name) return Icons.Menu
  return (Icons as Record<string, unknown>)[name] || Icons.Menu
}

function handleSub(menu: Menu) {
  if (menu.path) router.push(menu.path)
}

// 直接访问 /system 时重定向到首个子菜单
onMounted(() => {
  if (route.path === '/system' && subMenus.value.length) {
    router.replace(subMenus.value[0].path as string)
  }
})
</script>

<template>
  <div class="system-layout">
    <aside class="system-sidebar">
      <div class="system-sidebar__title">系统管理</div>
      <div
        v-for="m in subMenus"
        :key="m.id"
        class="system-sidebar__item"
        :class="{ active: activePath === m.path }"
        @click="handleSub(m)"
      >
        <el-icon :size="18"><component :is="resolveIcon(m.icon)" /></el-icon>
        <span>{{ m.name }}</span>
      </div>
    </aside>
    <main class="system-content">
      <router-view />
    </main>
  </div>
</template>

<style lang="scss" scoped>
.system-layout {
  display: flex;
  height: 100%;
  overflow: hidden;
}

.system-sidebar {
  flex-shrink: 0;
  width: 200px;
  padding: 20px 14px;
  border-right: 1px solid rgba(0, 0, 0, 0.06);
  background: rgba(255, 255, 255, 0.5);
  backdrop-filter: blur(16px);
  overflow-y: auto;

  &__title {
    font-size: 13px;
    font-weight: 600;
    color: #86868b;
    text-transform: uppercase;
    letter-spacing: 0.06em;
    padding: 4px 12px 14px;
  }

  &__item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 12px;
    margin-bottom: 4px;
    border-radius: 9px;
    cursor: pointer;
    color: #4b4b4f;
    font-size: 14px;
    font-weight: 500;
    transition: all 0.2s ease;

    &:hover {
      background: rgba(99, 102, 241, 0.08);
      color: #4f46e5;
    }

    &.active {
      background: linear-gradient(135deg, #6366f1, #8b5cf6);
      color: #fff;
      box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
    }
  }
}

.system-content {
  flex: 1;
  overflow: auto;
}
</style>
