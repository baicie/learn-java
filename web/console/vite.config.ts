/// <reference types="vitest" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'

export default defineConfig({
  plugins: [tailwindcss(), react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules')) {
            if (id.includes('@tanstack/react-query') || id.includes('react-router-dom')) {
              return 'vendor-app'
            }
            if (id.includes('react-markdown')) {
              return 'vendor-markdown'
            }
            if (id.includes('@base-ui') || id.includes('lucide-react') || id.includes('sonner')) {
              return 'vendor-ui'
            }
          }
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        'src/**/*.{test,spec}.{ts,tsx}',
        'src/test/**',
        'src/main.tsx',
        'src/components/ui/**',
        'src/lib/**',
      ],
      thresholds: {
        // AGENTS.md §21.7 — domain/service ≥ 80%、controller ≥ 60%
        // 前端 MVP 阶段先要求整体 ≥ 50%，组件层 ≥ 40%
        lines: 50,
        functions: 50,
        branches: 40,
        statements: 50,
      },
    },
  },
})
