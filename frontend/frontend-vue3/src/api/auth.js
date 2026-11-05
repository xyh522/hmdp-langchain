import http from './http'
import { clearCustomerSession, customerSessionId } from '../utils/customerSession'
export const sendCode = phone => http.post('/user/code', null, { params: { phone } })
export async function login(credentials) {
  const token = await http.post('/user/login', credentials)
  sessionStorage.setItem('token', token)
  clearCustomerSession()
  customerSessionId()
  return token
}
export async function logout() {
  try { await http.post('/user/logout') }
  finally { sessionStorage.removeItem('token'); clearCustomerSession() }
}
export const currentUser = () => http.get('/user/me')
