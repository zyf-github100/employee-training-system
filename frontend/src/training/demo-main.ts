import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'
import 'element-plus/theme-chalk/el-message.css'
import 'element-plus/theme-chalk/el-message-box.css'
import 'element-plus/theme-chalk/el-overlay.css'
import 'element-plus/theme-chalk/el-loading.css'
import App from './DemoApp.vue'
import { router } from './demo-router'
import './styles.css'
import './experience.css'
import './management.css'

const app = createApp(App)
app.use(createPinia()).use(router).use(ElLoading)
app.config.globalProperties.$message = ElMessage
app.config.globalProperties.$confirm = ElMessageBox.confirm
app.mount('#app')

import './live/demo-banner.css'
