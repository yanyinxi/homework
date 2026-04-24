/**
 * main.ts — 应用入口
 *
 * 注册：Vue 应用 + Element Plus + Pinia + Vue Router + ECharts
 */

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as echarts from 'echarts'

import App from './App.vue'
import router from './router'

const app = createApp(App)
const windowWithEcharts = window as unknown as { echarts: typeof echarts }

// 注册 ECharts 到全局
windowWithEcharts.echarts = echarts

// 注册 Element Plus（中文语言包）
app.use(ElementPlus, { locale: zhCn })

// 批量注册 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 注册 Pinia 全局状态管理
app.use(createPinia())

// 注册 Vue Router
app.use(router)

app.mount('#app')
