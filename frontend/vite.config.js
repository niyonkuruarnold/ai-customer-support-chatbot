import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), tailwindcss()],
  test: {
    environment: 'jsdom',
    // Use forks instead of threads to avoid the undici webidl error
    // ("TypeError: webidl.util.markAsUncloneable is not a function")
    // that occurs when vitest worker threads share undici internals in
    // Node 20. Forks give each test file its own process, isolating
    // undici/globalThis mutations.
    pool: 'forks',
    poolTimeout: 120000,
  },
})
