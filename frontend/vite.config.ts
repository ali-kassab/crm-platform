import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// The API runs on :8080 in development; the browser only ever talks to this origin.
// Override the proxy targets per machine in frontend/.env.development.local (gitignored) or the shell;
// loadEnv is needed because Vite does not put .env files into process.env for the config.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, __dirname, 'VITE_')
  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: { '@': path.resolve(__dirname, './src') },
    },
    server: {
      port: 5173,
      proxy: {
        '/api': { target: env.VITE_API_PROXY || 'http://localhost:8080', changeOrigin: false },
        // NestJS realtime service (ADR-0015)
        '/realtime': { target: env.VITE_REALTIME_PROXY || 'http://localhost:3001', ws: true },
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
              { name: 'realtime', test: /node_modules[\/](socket.io-client|engine.io-client|socket.io-parser|engine.io-parser|@socket.io)[\/]/ },
            ],
          },
        },
      },
    },
  }
})
