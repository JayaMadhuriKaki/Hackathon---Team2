// Re-export Playwright from frontend/node_modules so the specs (which live outside frontend/)
// never pick up a different copy installed higher up the directory tree.
export { expect, test } from '../../../frontend/node_modules/@playwright/test/index.js'
