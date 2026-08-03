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
    // 根布局：业务菜单（聊天/知识库/系统管理）由路由守卫动态注册到此，
    // 详情页与个人设置保留为静态路由。
    path: '/',
    component: DefaultLayout,
    name: 'Layout',
    redirect: '/chat',
    children: [
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/settings/SettingsView.vue'),
        meta: { title: '设置' },
      },
      {
        path: 'chat/:id',
        name: 'ChatDetail',
        component: () => import('@/views/chat/ChatView.vue'),
        meta: { title: '对话' },
      },
      {
        path: 'datasets/:id',
        name: 'DatasetDetail',
        component: () => import('@/views/dataset/DatasetDetailView.vue'),
        meta: { title: '知识库详情' },
      },
      {
        path: 'files',
        redirect: '/datasets',
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
