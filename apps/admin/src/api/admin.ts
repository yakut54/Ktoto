import { apiClient } from './client'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface Stats {
  totalUsers: number
  totalConversations: number
  messagesToday: number
  activeUsers24h: number
  messagesChart: { date: string; count: number }[]
  usersChart: { week: string; count: number }[]
}

export interface AdminUser {
  id: string
  username: string
  email: string
  avatar_url: string | null
  role: string
  banned_at: string | null
  status: string
  last_seen_at: string | null
  created_at: string
  stats?: { message_count: number; conversation_count: number }
}

export interface AdminConversation {
  id: string
  name: string | null
  type: 'direct' | 'group'
  avatar_url: string | null
  created_at: string
  updated_at: string
  participant_count: number
  message_count: number
  last_message_at: string | null
}

export interface AdminMessage {
  id: string
  type: string
  content: string | null
  created_at: string
  edited_at: string | null
  deleted_at: string | null
  user_id: string
  username: string | null
  avatar_url: string | null
}

export interface PaginatedResponse<T> {
  total: number
  page: number
  limit: number
  data: T[]
}

// ─── API Functions ─────────────────────────────────────────────────────────────

export const adminApi = {
  getStats: async (): Promise<Stats> => {
    const { data } = await apiClient.get('/api/admin/stats')
    return data
  },

  getUsers: async (params: { page?: number; limit?: number; search?: string }) => {
    const { data } = await apiClient.get<{ users: AdminUser[]; total: number; page: number; limit: number }>(
      '/api/admin/users',
      { params }
    )
    return data
  },

  getUser: async (id: string): Promise<AdminUser> => {
    const { data } = await apiClient.get(`/api/admin/users/${id}`)
    return data
  },

  patchUser: async (id: string, body: { action: 'ban' | 'unban' | 'set_role'; role?: string }) => {
    const { data } = await apiClient.patch(`/api/admin/users/${id}`, body)
    return data
  },

  getConversations: async (params: { page?: number; limit?: number }) => {
    const { data } = await apiClient.get<{
      conversations: AdminConversation[]
      total: number
      page: number
      limit: number
    }>('/api/admin/conversations', { params })
    return data
  },

  getConversationMessages: async (id: string, params: { page?: number; limit?: number }) => {
    const { data } = await apiClient.get<{
      messages: AdminMessage[]
      total: number
      page: number
      limit: number
    }>(`/api/admin/conversations/${id}/messages`, { params })
    return data
  },

  deleteMessage: async (id: string) => {
    const { data } = await apiClient.delete(`/api/admin/messages/${id}`)
    return data
  },
}
