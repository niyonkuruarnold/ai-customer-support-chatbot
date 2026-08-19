import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), tailwindcss()],
  test: {
    // happy-dom avoids the undici/CacheStorage collision that jsdom triggers
    // in Node 20 ("TypeError: webidl.util.markAsUncloneable is not a function").
    // It does not mutate globalThis the way jsdom's undici polyfill does,
    // so the test runner stays stable regardless of pool strategy.
    environment: 'happy-dom',
    pool: 'forks',
    poolTimeout: 120000,
  },
})
