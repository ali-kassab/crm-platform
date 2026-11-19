import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The API runs on :8080 in development; the browser only ever talks to this origin.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: process.env.VITE_API_PROXY ?? 'http://localhost:8080', changeOrigin: false },
    },
  },
  build: {
    // bundle budget (R9): scripts/check-bundle.mjs fails the build above the limits
    chunkSizeWarningLimit: 500,
    rolldownOptions: {
      output: {
        codeSplitting: {
          groups: [
            { name: 'react', test: /node_modules[\/](react|react-dom|scheduler|react-router)[\/]/ },
            { name: 'ui', test: /node_modules[\/](radix-ui|@radix-ui|lucide-react|sonner|class-variance-authority|tailwind-merge|clsx)[\/]/ },
            { name: 'data', test: /node_modules[\/](@tanstack|i18next|react-i18next|zod|react-hook-form|@hookform)[\/]/ },
          ],
        },
      },
    },
  },
})
