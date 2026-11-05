import http from './http'
import { customerSessionId } from '../utils/customerSession'
const sessionParams = () => ({ session_id: customerSessionId() })
export const listThreads = () => http.get('/customer', { params: sessionParams() })
export const createThread = () => http.post('/customer', sessionParams())
export const listMessages = threadId => http.get(`/customer/thread/${encodeURIComponent(threadId)}/messages`, { params: sessionParams() })

export async function streamChat(threadId, message, onChunk) {
  const token = sessionStorage.getItem('token')
  const response = await fetch('/api/customer/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream', ...(token ? { authorization: token } : {}) },
    body: JSON.stringify({ thread_id: threadId, session_id: customerSessionId(), message })
  })
  if (response.status === 401) {
    sessionStorage.removeItem('token')
    throw new Error('登录状态已失效')
  }
  if (!response.ok) throw new Error(`客服请求失败：${response.status}`)
  if (!response.body) throw new Error('浏览器不支持流式响应')
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  while (true) {
    const { value, done } = await reader.read()
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done })
    const events = buffer.split(/\r?\n\r?\n/)
    buffer = events.pop() || ''
    for (const event of events) {
      const data = event.split(/\r?\n/).filter(line => line.startsWith('data:')).map(line => line.slice(5).replace(/^ /, '')).join('\n')
      if (!data || data === '[DONE]') continue
      let payload
      try { payload = JSON.parse(data) } catch { payload = null }
      if (payload?.error) throw new Error(payload.error)
      onChunk(typeof payload?.content === 'string' ? payload.content : data)
    }
    if (done) break
  }
}
