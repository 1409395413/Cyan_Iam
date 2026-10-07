import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

/**
 * 前端 dev server 通过 /api 代理访问 Java 后端，避免跨域。
 * 生产环境由 Nginx 反向代理，见 deploy/nginx.conf。
 */
export default defineConfig({
  plugins: [vue()],
  /**
   * 部署在子目录时（如 https://x.com/portfolio/）把 VITE_BASE 设成 /portfolio/。
   * 前端路由已经用 import.meta.env.BASE_URL，这里改一处就够了。
   * 部署在根域名下不用管，默认就是 '/'。
   */
  base: process.env.VITE_BASE || '/',
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://127.0.0.1:8080',
        changeOrigin: true
      },
      // 上传的素材由后端 serve 在 /media/**（生产环境由 Nginx 直出）
      '/media': {
        target: process.env.VITE_API_TARGET || 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    target: 'es2020',
    outDir: 'dist',
    assetsDir: 'assets',
    chunkSizeWarningLimit: 1200,
    rollupOptions: {
      output: {
        manualChunks: {
          vue: ['vue', 'vue-router']
        }
      }
    }
  }
})
