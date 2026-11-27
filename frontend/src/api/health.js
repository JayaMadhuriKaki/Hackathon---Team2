import { api } from './client.js'

export const getHealth = () => api.get('/health')
