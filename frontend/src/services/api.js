import axios from 'axios'

const http = axios.create({ baseURL: '/api' })

// ── Projects ──────────────────────────────────────────────────────────────────
export const getProjects    = ()       => http.get('/projects').then(r => r.data)
export const getProject     = (id)     => http.get(`/projects/${id}`).then(r => r.data)
export const createProject  = (name)   => http.post('/projects', { name }).then(r => r.data)
export const deleteProject  = (id)     => http.delete(`/projects/${id}`)

export const setGithubSource = (id, payload) =>
  http.post(`/projects/${id}/source/github`, payload).then(r => r.data)

export const uploadSource = (id, file) => {
  const form = new FormData()
  form.append('file', file)
  return http.post(`/projects/${id}/source/upload`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }).then(r => r.data)
}

// ── Migration ─────────────────────────────────────────────────────────────────
export const startMigration = (projectId) =>
  http.post(`/projects/${projectId}/migrate`).then(r => r.data)

export const getRuns = (projectId) =>
  http.get(`/projects/${projectId}/runs`).then(r => r.data)

export const getLogs = (projectId, runId) =>
  http.get(`/projects/${projectId}/runs/${runId}/logs`).then(r => r.data)

/**
 * Open an SSE connection to stream migration logs.
 * Returns a cleanup function that closes the EventSource.
 */
export const streamLogs = (projectId, runId, onLog, onDone, onError) => {
  const es = new EventSource(`/api/projects/${projectId}/runs/${runId}/stream`)
  es.onmessage = (e) => onLog(JSON.parse(e.data))
  es.addEventListener('done', (e) => {
    onDone(JSON.parse(e.data))
    es.close()
  })
  es.onerror = (e) => {
    onError?.(e)
    es.close()
  }
  return () => es.close()
}
