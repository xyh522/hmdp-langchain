export function customerSessionId() {
  let id = sessionStorage.getItem('customer-session-id')
  if (!id) {
    id = globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`
    sessionStorage.setItem('customer-session-id', id)
  }
  return id
}
export const clearCustomerSession = () => sessionStorage.removeItem('customer-session-id')
