import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from '@/lib/auth'
import { ProtectedRoute } from '@/components/ProtectedRoute'
import { AdminLayout } from '@/components/layout/AdminLayout'
import { LoginPage } from '@/pages/Login'
import { DashboardPage } from '@/pages/Dashboard'
import { UsersPage } from '@/pages/Users'
import { UserDetailPage } from '@/pages/UserDetail'
import { ConversationsPage } from '@/pages/Conversations'
import { ConversationMessagesPage } from '@/pages/ConversationMessages'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
    },
  },
})

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<ProtectedRoute />}>
              <Route element={<AdminLayout />}>
                <Route path="/" element={<DashboardPage />} />
                <Route path="/users" element={<UsersPage />} />
                <Route path="/users/:id" element={<UserDetailPage />} />
                <Route path="/conversations" element={<ConversationsPage />} />
                <Route path="/conversations/:id" element={<ConversationMessagesPage />} />
              </Route>
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  )
}
