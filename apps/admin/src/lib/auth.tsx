import React, { createContext, useContext, useState, useCallback } from 'react'
import { apiClient } from '@/api/client'

interface AuthUser {
  id: string
  username: string
  email: string
  role: string
  avatar_url: string | null
}

interface AuthContextValue {
  user: AuthUser | null
  token: string | null
  login: (username: string, password: string) => Promise<void>
  logout: () => void
  isAuthenticated: boolean
}

const AuthContext = createContext<AuthContextValue | null>(null)

const TOKEN_KEY = 'ktoto_admin_token'

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_KEY))
  const [user, setUser] = useState<AuthUser | null>(null)

  const login = useCallback(async (username: string, password: string) => {
    const res = await apiClient.post<{ user: AuthUser; accessToken: string }>(
      '/api/auth/login',
      { username, password }
    )

    const { user: loggedUser, accessToken } = res.data

    if (loggedUser.role !== 'admin') {
      throw new Error('Access denied: admin only')
    }

    localStorage.setItem(TOKEN_KEY, accessToken)
    setToken(accessToken)
    setUser(loggedUser)
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY)
    setToken(null)
    setUser(null)
  }, [])

  return (
    <AuthContext.Provider
      value={{ user, token, login, logout, isAuthenticated: !!token }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
