import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    Components({
      dirs: ['src/training/components'],
      dts: 'src/training/components.d.ts',
      resolvers: [ElementPlusResolver()]
    })
  ],
  server: {
    strictPort: true,
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY_URL || 'http://127.0.0.1:8081',
        changeOrigin: true
      }
    }
  },
  optimizeDeps: {
    entries: ['index.html', 'connected.html', 'demo.html'],
    include: [
      'vue',
      'vue-router',
      'pinia',
      'element-plus',
      'element-plus/es',
      'element-plus/es/components/**/style/css',
      'element-plus/es/locale/lang/zh-cn',
      '@element-plus/icons-vue',
      'echarts/core',
      'echarts/charts',
      'echarts/components',
      'echarts/renderers'
    ]
  },
  build: {
    outDir: 'dist',
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules')) {
            if (id.includes('echarts') || id.includes('zrender')) return 'charts'
            if (id.includes('element-plus') || id.includes('@popperjs')) return 'element'
            return 'vendor'
          }
        }
      },
      input: { main: 'index.html', connected: 'connected.html', demo: 'demo.html' }
    }
  }
})
