/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  // 关闭 preflight，避免与 AntD 样式互相覆盖（按钮/边框重置冲突）
  corePlugins: { preflight: false },
  theme: {
    extend: {
      colors: {
        // 与 AntD 主题 token 保持一致的扩展色板
        erp: {
          primary: '#1668dc',
          bg: '#f5f7fa',
        },
      },
    },
  },
  plugins: [],
}
