import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

/**
 * Vite 配置。
 * - dev 端口 5174（避开 ERP 的 5173）；/api 代理到认证中心后端 8082；
 * - @ 指向 src，import 一律用 '@/...'，避免 ../.. 相对路径。
 */
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': path.resolve(__dirname, 'src') },
  },
  server: {
    port: 5174,
    proxy: {
      '/api': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
    },
  },
})
