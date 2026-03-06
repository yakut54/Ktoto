import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '@/api/admin'

export function useStats() {
  return useQuery({
    queryKey: ['admin', 'stats'],
    queryFn: adminApi.getStats,
    refetchInterval: 30_000,
  })
}

export function useUsers(params: { page?: number; limit?: number; search?: string }) {
  return useQuery({
    queryKey: ['admin', 'users', params],
    queryFn: () => adminApi.getUsers(params),
  })
}

export function useUser(id: string) {
  return useQuery({
    queryKey: ['admin', 'users', id],
    queryFn: () => adminApi.getUser(id),
    enabled: !!id,
  })
}

export function usePatchUser() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: { action: 'ban' | 'unban' | 'set_role'; role?: string } }) =>
      adminApi.patchUser(id, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'users'] })
    },
  })
}

export function useConversations(params: { page?: number; limit?: number }) {
  return useQuery({
    queryKey: ['admin', 'conversations', params],
    queryFn: () => adminApi.getConversations(params),
  })
}

export function useConversationMessages(id: string, params: { page?: number; limit?: number }) {
  return useQuery({
    queryKey: ['admin', 'conversations', id, 'messages', params],
    queryFn: () => adminApi.getConversationMessages(id, params),
    enabled: !!id,
  })
}

export function useDeleteMessage() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => adminApi.deleteMessage(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'conversations'] })
    },
  })
}
