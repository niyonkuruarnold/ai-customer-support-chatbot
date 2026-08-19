import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), tailwindcss()],
  test: {
    // happy-dom avoids the undici/CacheStorage collision that jsdom triggers
    // in Node 20 ("TypeError: webidl.util.markAsUncloneable is not a function").
    // The setup file below provides an additional safety net by stubbing
    // globalThis.CacheStorage before any test imports run.
    environment: 'happy-dom',
    setupFiles: ['./src/test/setup.js'],
    pool: 'forks',
    poolTimeout: 120000,
  },
})
