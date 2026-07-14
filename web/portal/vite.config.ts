/// <reference types="vitest/config" />
import path from 'path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { tanstackRouter } from '@tanstack/router-plugin/vite'
import { playwright } from '@vitest/browser-playwright'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    tanstackRouter({
      target: 'react',
      autoCodeSplitting: true,
    }),
    react(),
    tailwindcss(),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  optimizeDeps: {
    exclude: ['fsevents'],
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
    },
  },
  test: {
    include: ['src/**/*.test.{ts,tsx}'],
    silent: 'passed-only',
    unstubEnvs: true,
    setupFiles: ['./src/test-utils/vitest-setup.ts'],
    browser: {
      enabled: true,
      provider: playwright(),
      instances: [{ browser: 'chromium' }],
    },
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json-summary', 'html', 'lcov'],
      include: [
        'src/api/work-records/**/*.{ts,tsx}',
        'src/components/work-records/**/*.{ts,tsx}',
        'src/hooks/dictionaries/**/*.{ts,tsx}',
        'src/hooks/work-records/**/*.{ts,tsx}',
        'src/pages/dictionaries/**/*.{ts,tsx}',
        'src/pages/work-records/**/*.{ts,tsx}',
        'src/components/feedback/**/*.{ts,tsx}',
        'src/components/form/**/*.{ts,tsx}',
      ],
      exclude: [
        'src/components/ui/**',
        'src/assets/**',
        'src/tanstack-table.d.ts',
        'src/routeTree.gen.ts',
        'src/test-utils/**',
        'src/routes/**',
        '**/*.d.ts',
        '**/types.ts',
      ],
      thresholds: {
        statements: 58,
        branches: 50,
        functions: 47,
        lines: 59,
      },
    },
  },
})
