import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useConversationMessages, useDeleteMessage } from '@/hooks/useAdmin'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import type { AdminMessage } from '@/api/admin'
import { formatDate } from '@/lib/utils'
import { ArrowLeft, Trash2, Image, Mic, Paperclip } from 'lucide-react'

const typeIcons: Record<string, React.ReactNode> = {
  image: <Image className="h-3.5 w-3.5" />,
  voice: <Mic className="h-3.5 w-3.5" />,
  file: <Paperclip className="h-3.5 w-3.5" />,
}

export function ConversationMessagesPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [page, setPage] = useState(1)
  const [deletingId, setDeletingId] = useState<string | null>(null)

  const { data, isLoading } = useConversationMessages(id!, { page, limit: 50 })
  const deleteMessage = useDeleteMessage()

  const totalPages = data ? Math.ceil(data.total / 50) : 1

  const confirmDelete = (msg: AdminMessage) => {
    setDeletingId(msg.id)
  }

  const handleConfirmDelete = () => {
    if (!deletingId) return
    deleteMessage.mutate(deletingId, {
      onSuccess: () => setDeletingId(null),
      onError: () => setDeletingId(null),
    })
  }

  return (
    <div className="p-8 space-y-6">
      <button
        onClick={() => navigate('/conversations')}
        className="flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors"
      >
        <ArrowLeft className="h-4 w-4" />
        Back to conversations
      </button>

      <div>
        <h1 className="text-2xl font-bold">Messages</h1>
        {data && (
          <p className="text-muted-foreground text-sm mt-1">{data.total} total messages</p>
        )}
      </div>

      {isLoading && (
        <div className="space-y-2">
          {[...Array(5)].map((_, i) => (
            <div key={i} className="h-16 bg-muted animate-pulse rounded-lg" />
          ))}
        </div>
      )}

      {!isLoading && data && (
        <div className="space-y-2">
          {data.messages.map((msg) => (
            <div
              key={msg.id}
              className={`flex items-start gap-3 p-4 rounded-lg border bg-card ${
                msg.deleted_at ? 'opacity-50' : ''
              }`}
            >
              {/* Avatar */}
              <div className="h-8 w-8 rounded-full bg-primary/10 flex items-center justify-center flex-shrink-0">
                <span className="text-xs font-bold text-primary">
                  {msg.username?.[0]?.toUpperCase() ?? '?'}
                </span>
              </div>

              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 mb-1">
                  <span className="text-sm font-medium">{msg.username ?? 'Deleted user'}</span>
                  {msg.type !== 'text' && (
                    <span className="flex items-center gap-1 text-xs text-muted-foreground bg-muted px-1.5 py-0.5 rounded">
                      {typeIcons[msg.type]}
                      {msg.type}
                    </span>
                  )}
                  {msg.deleted_at && (
                    <span className="text-xs text-destructive">deleted</span>
                  )}
                  <span className="text-xs text-muted-foreground ml-auto">{formatDate(msg.created_at)}</span>
                </div>
                <p className="text-sm text-muted-foreground truncate">
                  {msg.content ?? <em>no text content</em>}
                </p>
              </div>

              {!msg.deleted_at && (
                <button
                  onClick={() => confirmDelete(msg)}
                  className="p-1.5 rounded hover:bg-destructive/10 transition-colors flex-shrink-0"
                  title="Delete permanently"
                >
                  <Trash2 className="h-4 w-4 text-destructive" />
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">
            Page {page} of {totalPages}
          </p>
          <div className="flex gap-2">
            <button
              disabled={page <= 1}
              onClick={() => setPage(page - 1)}
              className="px-3 py-1.5 text-sm rounded border hover:bg-accent disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              Previous
            </button>
            <button
              disabled={page >= totalPages}
              onClick={() => setPage(page + 1)}
              className="px-3 py-1.5 text-sm rounded border hover:bg-accent disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              Next
            </button>
          </div>
        </div>
      )}

      <ConfirmDialog
        open={!!deletingId}
        title="Delete message?"
        description="This action is permanent and cannot be undone. The message will be removed from the database."
        onConfirm={handleConfirmDelete}
        onCancel={() => setDeletingId(null)}
        confirmLabel="Delete"
        danger
      />
    </div>
  )
}
