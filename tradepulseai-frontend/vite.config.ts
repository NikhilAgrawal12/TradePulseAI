import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react({
    // @ts-ignore
    babel: {
      plugins: [['babel-plugin-react-compiler', { target: '19' }]],
    },
  })],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:4004'
      },
      '/auth': {
        target: 'http://localhost:4004'
      }
    }
  }
})