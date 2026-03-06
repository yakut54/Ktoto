import { useParams, useNavigate } from 'react-router-dom'
import { useUser, usePatchUser } from '@/hooks/useAdmin'
import { formatDate, formatNumber } from '@/lib/utils'
import { ArrowLeft, Ban, ShieldCheck, Crown, MessageSquare, Users } from 'lucide-react'
import { useState } from 'react'
import { ConfirmDialog } from '@/components/ConfirmDialog'

export function UserDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { data: user, isLoading } = useUser(id!)
  const patchUser = usePatchUser()
  const [confirmDialog, setConfirmDialog] = useState<{
    open: boolean
    title: string
    description: string
    onConfirm: () => void
    danger?: boolean
  }>({ open: false, title: '', description: '', onConfirm: () => {} })

  const closeConfirm = () => setConfirmDialog((p) => ({ ...p, open: false }))

  if (isLoading) {
    return (
      <div className="p-8">
        <div className="h-32 bg-muted animate-pulse rounded-lg" />
      </div>
    )
  }

  if (!user) {
    return (
      <div className="p-8">
        <p className="text-destructive">User not found</p>
      </div>
    )
  }

  const handleBan = () => {
    setConfirmDialog({
      open: true,
      title: `Ban ${user.username}?`,
      description: 'The user will not be able to log in.',
      danger: true,
      onConfirm: () => {
        patchUser.mutate({ id: user.id, body: { action: 'ban' } })
        closeConfirm()
      },
    })
  }

  const handleUnban = () => {
    setConfirmDialog({
      open: true,
      title: `Unban ${user.username}?`,
      description: 'The user will be able to log in again.',
      onConfirm: () => {
        patchUser.mutate({ id: user.id, body: { action: 'unban' } })
        closeConfirm()
      },
    })
  }

  const handleMakeAdmin = () => {
    setConfirmDialog({
      open: true,
      title: `Make ${user.username} admin?`,
      description: 'This user will have full access to the admin panel.',
      danger: true,
      onConfirm: () => {
        patchUser.mutate({ id: user.id, body: { action: 'set_role', role: 'admin' } })
        closeConfirm()
      },
    })
  }

  const handleRemoveAdmin = () => {
    setConfirmDialog({
      open: true,
      title: `Remove admin from ${user.username}?`,
      description: 'This user will lose admin access.',
      danger: true,
      onConfirm: () => {
        patchUser.mutate({ id: user.id, body: { action: 'set_role', role: 'user' } })
        closeConfirm()
      },
    })
  }

  return (
    <div className="p-8 max-w-2xl space-y-6">
      <button
        onClick={() => navigate('/users')}
        className="flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors"
      >
        <ArrowLeft className="h-4 w-4" />
        Back to users
      </button>

      <div className="rounded-lg border bg-card p-6">
        <div className="flex items-start gap-4">
          {user.avatar_url ? (
            <img src={user.avatar_url} alt="" className="h-16 w-16 rounded-full object-cover" />
          ) : (
            <div className="h-16 w-16 rounded-full bg-primary/10 flex items-center justify-center">
              <span className="text-xl font-bold text-primary">{user.username[0].toUpperCase()}</span>
            </div>
          )}

          <div className="flex-1">
            <div className="flex items-center gap-2">
              <h1 className="text-xl font-bold">{user.username}</h1>
              <span
                className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                  user.role === 'admin'
                    ? 'bg-primary/10 text-primary'
                    : 'bg-secondary text-secondary-foreground'
                }`}
              >
                {user.role}
              </span>
              {user.banned_at && (
                <span className="text-xs px-2 py-0.5 rounded-full bg-destructive/10 text-destructive font-medium">
                  Banned
                </span>
              )}
            </div>
            <p className="text-muted-foreground text-sm">{user.email}</p>
            <p className="text-xs text-muted-foreground mt-1">
              Registered: {formatDate(user.created_at)}
            </p>
            {user.last_seen_at && (
              <p className="text-xs text-muted-foreground">
                Last seen: {formatDate(user.last_seen_at)}
              </p>
            )}
          </div>
        </div>

        {/* Stats */}
        {user.stats && (
          <div className="grid grid-cols-2 gap-4 mt-6 pt-6 border-t">
            <div className="flex items-center gap-3">
              <MessageSquare className="h-5 w-5 text-muted-foreground" />
              <div>
                <p className="text-lg font-bold">{formatNumber(user.stats.message_count)}</p>
                <p className="text-xs text-muted-foreground">Messages</p>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <Users className="h-5 w-5 text-muted-foreground" />
              <div>
                <p className="text-lg font-bold">{formatNumber(user.stats.conversation_count)}</p>
                <p className="text-xs text-muted-foreground">Conversations</p>
              </div>
            </div>
          </div>
        )}

        {/* Actions */}
        <div className="flex gap-2 mt-6 pt-6 border-t flex-wrap">
          {user.banned_at ? (
            <button
              onClick={handleUnban}
              className="flex items-center gap-2 px-4 py-2 text-sm rounded-md bg-green-600 text-white hover:bg-green-700 transition-colors"
            >
              <ShieldCheck className="h-4 w-4" />
              Unban user
            </button>
          ) : (
            <button
              onClick={handleBan}
              className="flex items-center gap-2 px-4 py-2 text-sm rounded-md bg-destructive text-destructive-foreground hover:bg-destructive/90 transition-colors"
            >
              <Ban className="h-4 w-4" />
              Ban user
            </button>
          )}

          {user.role !== 'admin' ? (
            <button
              onClick={handleMakeAdmin}
              className="flex items-center gap-2 px-4 py-2 text-sm rounded-md border hover:bg-accent transition-colors"
            >
              <Crown className="h-4 w-4" />
              Make admin
            </button>
          ) : (
            <button
              onClick={handleRemoveAdmin}
              className="flex items-center gap-2 px-4 py-2 text-sm rounded-md border hover:bg-accent transition-colors text-muted-foreground"
            >
              <Crown className="h-4 w-4" />
              Remove admin
            </button>
          )}
        </div>
      </div>

      <ConfirmDialog
        {...confirmDialog}
        onCancel={closeConfirm}
        confirmLabel="Confirm"
      />
    </div>
  )
}
