import type { RouteRecordRaw } from 'vue-router'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import BlankLayout from '@/layouts/BlankLayout.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    component: BlankLayout,
    meta: { title: '登录', noAuth: true },
    children: [
      {
        path: '',
        name: 'Login',
        component: () => import('@/views/login/LoginView.vue'),
      },
    ],
  },
  {
    path: '/',
    component: DefaultLayout,
    redirect: '/chat',
    children: [
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('@/views/chat/ChatView.vue'),
        meta: { title: '对话' },
      },
      {
        path: 'chat/:id',
        name: 'ChatDetail',
        component: () => import('@/views/chat/ChatView.vue'),
        meta: { title: '对话' },
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/settings/SettingsView.vue'),
        meta: { title: '设置' },
      },
      {
        path: 'files',
        name: 'Files',
        component: () => import('@/views/file/FileView.vue'),
        meta: { title: '知识库' },
      },
      {
        path: 'vector-models',
        name: 'VectorModels',
        component: () => import('@/views/vectormodel/VectorModelView.vue'),
        meta: { title: '向量模型库' },
      },
    ],
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '404', noAuth: true },
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404',
  },
]

export default routes
