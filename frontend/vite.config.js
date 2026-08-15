import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), tailwindcss()],
  test: {
    environment: 'jsdom',
    // The default 'forks' pool is unreliable on Windows, and spawning several
    // workers at once can outrun the pool startup timeout. Run sequentially
    // in a single thread pool worker for stability.
    pool: 'threads',
    maxWorkers: 1,
    fileParallelism: false,
    poolTimeout: 120000,
  },
})
