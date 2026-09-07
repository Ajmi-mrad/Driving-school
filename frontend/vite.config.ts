import path from 'node:path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  server: {
    // Matches the gateway CORS allow-list and the Keycloak client's
    // registered redirect URIs (http://localhost:4200/*).
    port: 4200,
    strictPort: true,
  },
})