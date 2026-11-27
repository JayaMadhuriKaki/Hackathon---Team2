import path from 'node:path'
import { fileURLToPath } from 'node:url'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// Component/unit tests live in ../tests/frontend/unit (CLAUDE.md repo map). Bare imports from
// there are resolved against this package's node_modules.
const here = path.dirname(fileURLToPath(import.meta.url))
const nm = path.join(here, 'node_modules')

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: [
      {
        find: /^(react|react-dom|react-router-dom|react-router|@testing-library\/[^/]+)(\/.*)?$/,
        replacement: `${nm}/$1$2`,
      },
    ],
  },
  server: { fs: { allow: [path.join(here, '..')] } },
  test: {
    root: path.join(here, '..'),
    include: ['tests/frontend/unit/**/*.test.{js,jsx}'],
    environment: 'jsdom',
    globals: true,
    setupFiles: [path.join(here, '../tests/frontend/unit/setup.js')],
  },
})
