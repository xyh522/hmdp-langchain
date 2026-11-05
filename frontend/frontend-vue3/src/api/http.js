import axios from 'axios'
import router from '../router'
import { clearCustomerSession } from '../utils/customerSession'

const http = axios.create({ baseURL: '/api', timeout: 8000 })
http.interceptors.request.use(config => {
  const token = sessionStorage.getItem('token')
  if (token) config.headers.authorization = token
  return config
})
http.interceptors.response.use(response => {
  if (!response.data?.success) throw new Error(response.data?.errorMsg || '请求失败')
  return response.data.data
}, error => {
  if (error.response?.status === 401) {
    sessionStorage.removeItem('token')
    clearCustomerSession()
    router.push('/login')
  }
  return Promise.reject(new Error(error.response?.data?.errorMsg || error.message || '服务器异常'))
})
export default http
