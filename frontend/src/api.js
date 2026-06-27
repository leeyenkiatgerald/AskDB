const API_BASE = import.meta.env.VITE_API_BASE_URL || ''

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    },
    ...options,
  })

  const text = await response.text()
  const data = text ? JSON.parse(text) : null

  if (!response.ok) {
    throw new Error(data?.error || `Request failed with status ${response.status}`)
  }

  return data
}

export function healthCheck() {
  return request('/api/health')
}

export function getSchema() {
  return request('/api/schema')
}

export function getHistory() {
  return request('/api/history')
}

export function connectDatabase(payload) {
  return request('/api/connect', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function runQuery(payload) {
  return request('/api/query', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}
