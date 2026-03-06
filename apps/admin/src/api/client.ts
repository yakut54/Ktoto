import axios from 'axios'

export const apiClient = axios.create({
  baseURL: '/',
  headers: { 'Content-Type': 'application/json' },
})

// Attach token from localStorage on every request
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('ktoto_admin_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Redirect to login on 401
apiClient.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('ktoto_admin_token')
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)
