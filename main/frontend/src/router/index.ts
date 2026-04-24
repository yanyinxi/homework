/**
 * router/index.ts — Vue Router 4 路由配置
 *
 * 路由表：
 *   /         → Dashboard（仪表盘，三个 ECharts 图表）
 *   /assets   → AssetList（素材列表，过滤/排序/分页）
 *   /assets/:id → AssetDetail（素材详情）
 */

import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Dashboard',
    component: () => import('@/pages/Dashboard.vue'),
    meta: { title: '数据概览' },
  },
  {
    path: '/assets',
    name: 'AssetList',
    component: () => import('@/pages/AssetList.vue'),
    meta: { title: '素材列表' },
  },
  {
    path: '/assets/:id',
    name: 'AssetDetail',
    component: () => import('@/pages/AssetDetail.vue'),
    meta: { title: '素材详情' },
  },
  // 404 重定向到首页
  {
    path: '/:pathMatch(.*)*',
    redirect: '/',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    // 切换路由时滚动到顶部
    return { top: 0 }
  },
})

// 动态更新 document.title
router.afterEach((to) => {
  document.title = `${to.meta.title ?? '素材管理'} - 视频素材管理后台`
})

export default router
