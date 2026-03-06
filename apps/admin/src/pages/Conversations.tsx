import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useConversations } from '@/hooks/useAdmin'
import { DataTable } from '@/components/DataTable'
import type { AdminConversation } from '@/api/admin'
import { formatDate } from '@/lib/utils'
import { Users, MessageSquare } from 'lucide-react'

export function ConversationsPage() {
  const navigate = useNavigate()
  const [page, setPage] = useState(1)
  const { data, isLoading } = useConversations({ page, limit: 20 })

  const columns = [
    {
      key: 'name',
      header: 'Conversation',
      render: (c: AdminConversation) => (
        <div>
          <p className="font-medium text-sm">{c.name ?? `${c.type === 'direct' ? 'Direct' : 'Group'} chat`}</p>
          <span
            className={`text-xs px-1.5 py-0.5 rounded font-medium ${
              c.type === 'group'
                ? 'bg-primary/10 text-primary'
                : 'bg-secondary text-secondary-foreground'
            }`}
          >
            {c.type}
          </span>
        </div>
      ),
    },
    {
      key: 'participants',
      header: 'Participants',
      render: (c: AdminConversation) => (
        <div className="flex items-center gap-1 text-sm">
          <Users className="h-3.5 w-3.5 text-muted-foreground" />
          {c.participant_count}
        </div>
      ),
    },
    {
      key: 'messages',
      header: 'Messages',
      render: (c: AdminConversation) => (
        <div className="flex items-center gap-1 text-sm">
          <MessageSquare className="h-3.5 w-3.5 text-muted-foreground" />
          {c.message_count}
        </div>
      ),
    },
    {
      key: 'last_active',
      header: 'Last Active',
      render: (c: AdminConversation) => (
        <span className="text-xs text-muted-foreground">
          {formatDate(c.last_message_at ?? c.updated_at)}
        </span>
      ),
    },
    {
      key: 'actions',
      header: '',
      render: (c: AdminConversation) => (
        <button
          onClick={() => navigate(`/conversations/${c.id}`)}
          className="text-xs text-primary hover:underline"
        >
          View messages
        </button>
      ),
      className: 'text-right',
    },
  ]

  return (
    <div className="p-8 space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Conversations</h1>
        <p className="text-muted-foreground text-sm mt-1">All chats in the messenger</p>
      </div>

      <DataTable
        columns={columns}
        data={data?.conversations ?? []}
        loading={isLoading}
        total={data?.total ?? 0}
        page={page}
        limit={20}
        onPageChange={setPage}
        emptyMessage="No conversations yet"
      />
    </div>
  )
}
