import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react({
    // @ts-expect-error Vite's React plugin types do not expose the Babel option used by the React compiler plugin.
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
      },
      '/ws': {
        target: 'ws://localhost:4004',
        ws: true
      }
    }
  }
})