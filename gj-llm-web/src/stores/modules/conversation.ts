import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Conversation } from '@/api/types'
import { conversationApi } from '@/api/modules/conversation'

export const useConversationStore = defineStore('conversation', () => {
  const list = ref<Conversation[]>([])
  const currentId = ref<string | null>(null)
  const loading = ref(false)

  function setCurrentId(id: string | null) {
    currentId.value = id
  }

  async function fetchList() {
    loading.value = true
    try {
      const res = await conversationApi.getList()
      list.value = res.data
    } finally {
      loading.value = false
    }
  }

  async function create(title?: string): Promise<Conversation | null> {
    try {
      const res = await conversationApi.create(title)
      list.value.unshift(res.data)
      currentId.value = res.data.id
      return res.data
    } catch {
      return null
    }
  }

  async function remove(id: string) {
    try {
      await conversationApi.remove(id)
      list.value = list.value.filter((c) => c.id !== id)
      if (currentId.value === id) {
        currentId.value = null
      }
    } catch {
      // 错误已在拦截器处理
    }
  }

  async function rename(id: string, title: string) {
    try {
      await conversationApi.rename(id, title)
      const target = list.value.find((c) => c.id === id)
      if (target) target.title = title
    } catch {
      // 错误已在拦截器处理
    }
  }

  return { list, currentId, loading, setCurrentId, fetchList, create, remove, rename }
})
