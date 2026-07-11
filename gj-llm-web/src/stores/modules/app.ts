import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(true)
  const sidebarPinned = ref(false)
  const theme = ref<'light' | 'dark'>('light')

  function toggleSidebar() {
    if (sidebarPinned.value) {
      // 已固定 → 取消固定并收起
      sidebarPinned.value = false
      sidebarCollapsed.value = true
    } else {
      sidebarCollapsed.value = !sidebarCollapsed.value
    }
  }

  function togglePin() {
    sidebarPinned.value = !sidebarPinned.value
    if (sidebarPinned.value) {
      sidebarCollapsed.value = false
    }
  }

  function onSidebarEnter() {
    if (!sidebarPinned.value && sidebarCollapsed.value) {
      sidebarCollapsed.value = false
    }
  }

  function onSidebarLeave() {
    if (!sidebarPinned.value && !sidebarCollapsed.value) {
      sidebarCollapsed.value = true
    }
  }

  function setTheme(val: 'light' | 'dark') {
    theme.value = val
  }

  return {
    sidebarCollapsed, sidebarPinned, theme,
    toggleSidebar, togglePin,
    onSidebarEnter, onSidebarLeave,
    setTheme,
  }
})
