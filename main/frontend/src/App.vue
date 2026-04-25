<template>
  <!-- Element Plus 标准后台布局：侧边栏 + 顶栏 + 主内容区 -->
  <el-container class="app-container">
    <!-- 侧边栏导航 -->
    <el-aside width="220px" class="app-aside">
      <div class="logo">
        <el-icon size="24"><VideoCamera /></el-icon>
        <span>素材管理后台</span>
      </div>

      <el-menu
        :default-active="activeMenu"
        router
        background-color="#001529"
        text-color="#ffffffa0"
        active-text-color="#ffffff"
        class="aside-menu"
      >
        <el-menu-item index="/">
          <el-icon><DataAnalysis /></el-icon>
          <span>数据概览</span>
        </el-menu-item>

        <el-menu-item index="/assets">
          <el-icon><Film /></el-icon>
          <span>素材列表</span>
        </el-menu-item>

        <el-menu-item index="/monitoring">
          <el-icon><Monitor /></el-icon>
          <span>运维监控</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 右侧主区域 -->
    <el-container direction="vertical">
      <!-- 顶栏 -->
      <el-header class="app-header">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="route.name !== 'Dashboard'">
              {{ route.meta.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-tag type="success" size="small">视频素材管理系统 v1.0</el-tag>
        </div>
      </el-header>

      <!-- 主内容区 -->
      <el-main class="app-main">
        <router-view v-slot="{ Component }">
          <!-- 路由切换动画 -->
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

// 当前激活菜单项（匹配路由 path）
const activeMenu = computed(() => {
  // 详情页激活素材列表菜单
  if (route.name === 'AssetDetail') return '/assets'
  return route.path
})
</script>

<style>
/* 全局重置 */
* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial,
    sans-serif;
  background-color: #f0f2f5;
  color: #333;
}

/* 整体布局 */
.app-container {
  height: 100vh;
  overflow: hidden;
}

/* 侧边栏 */
.app-aside {
  background-color: #001529;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 20px;
  color: #ffffff;
  font-size: 16px;
  font-weight: 600;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  flex-shrink: 0;
}

.aside-menu {
  flex: 1;
  border-right: none !important;
}

.aside-menu .el-menu-item {
  height: 48px;
}

.aside-menu .el-menu-item.is-active {
  background-color: #1890ff !important;
}

.aside-menu .el-menu-item:hover {
  background-color: rgba(255, 255, 255, 0.08) !important;
}

/* 顶栏 */
.app-header {
  background: #ffffff;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 56px !important;
  flex-shrink: 0;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

/* 主内容区 */
.app-main {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background-color: #f0f2f5;
}

/* 路由切换动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* ECharts 图表容器通用样式 */
.chart-container {
  width: 100%;
  min-height: 300px;
}

/* Element Plus 卡片通用样式 */
.el-card {
  border-radius: 8px;
}
</style>
