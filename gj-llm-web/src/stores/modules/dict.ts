import { ref } from 'vue'
import { defineStore } from 'pinia'
import { dictDataApi, type DictDataItem } from '@/api/modules/dict'

/**
 * 字典 store —— 业务组件消费字典的统一入口：
 *
 * - load(type)：取字典数据（status=1，sort 升序），进程内缓存避免重复请求
 * - force：绕过缓存强制拉取（字典管理页编辑后调用）
 * - forceReload()：字典被维护后全量失效，下次 load 重新请求
 */
export const useDictStore = defineStore('dict', () => {
  /** 缓存：dictType -> 启用字典项列表 */
  const cache = ref<Record<string, DictDataItem[]>>({})
  /** 防并发：同一 type 在途请求复用同一个 Promise */
  const pending: Record<string, Promise<DictDataItem[]>> = {}

  async function load(type: string, force = false): Promise<DictDataItem[]> {
    if (!force && cache.value[type]) return cache.value[type]
    if (!force && pending[type]) return pending[type]

    const promise = dictDataApi
      .listByType(type)
      .then((items) => {
        cache.value[type] = items
        return items
      })
      .finally(() => {
        delete pending[type]
      })
    pending[type] = promise
    return promise
  }

  /** 字典数据被维护后全量失效（管理页增删改后调用） */
  function forceReload(): void {
    cache.value = {}
  }

  return { cache, load, forceReload }
})
