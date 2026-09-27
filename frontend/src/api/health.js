import { API_BASE } from './client'

export async function fetchSystemHealth() {
  const response = await fetch(`${API_BASE}/health/detail`)
  if (!response.ok) {
    throw new Error(`Health check failed with status ${response.status}`)
  }
  return response.json()
}