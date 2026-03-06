import { useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useUsers, usePatchUser } from '@/hooks/useAdmin'
import { DataTable } from '@/components/DataTable'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import type { AdminUser } from '@/api/admin'
import { formatDate } from '@/lib/utils'
import { Search, Ban, ShieldCheck, Eye, Crown } from 'lucide-react'

export function UsersPage() {
  const navigate = useNavigate()
  const [page, setPage] = useState(1)
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [confirmDialog, setConfirmDialog] = useState<{
    open: boolean
    title: string
    description: string
    onConfirm: () => void
    danger?: boolean
  }>({ open: false, title: '', description: '', onConfirm: () => {} })

  const debounceTimeout = useCallback(() => {
    let t: ReturnType<typeof setTimeout>
    return (val: string) => {
      clearTimeout(t)
      t = setTimeout(() => setDebouncedSearch(val), 300)
    }
  }, [])()

  const handleSearchChange = (val: string) => {
    setSearch(val)
    setPage(1)
    debounceTimeout(val)
  }

  const { data, isLoading } = useUsers({ page, limit: 20, search: debouncedSearch })
  const patchUser = usePatchUser()

  const confirm = (opts: typeof confirmDialog) => setConfirmDialog(opts)
  const closeConfirm = () => setConfirmDialog((p) => ({ ...p, open: false }))

  const handleBan = (user: AdminUser) => {
    confirm({
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

  const handleUnban = (user: AdminUser) => {
    confirm({
      open: true,
      title: `Unban ${user.username}?`,
      description: 'The user will be able to log in again.',
      onConfirm: () => {
        patchUser.mutate({ id: user.id, body: { action: 'unban' } })
        closeConfirm()
      },
    })
  }

  const handleMakeAdmin = (user: AdminUser) => {
    confirm({
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

  const columns = [
    {
      key: 'user',
      header: 'User',
      render: (u: AdminUser) => (
        <div className="flex items-center gap-3">
          {u.avatar_url ? (
            <img src={u.avatar_url} alt="" className="h-8 w-8 rounded-full object-cover" />
          ) : (
            <div className="h-8 w-8 rounded-full bg-primary/10 flex items-center justify-center">
              <span className="text-xs font-bold text-primary">{u.username[0].toUpperCase()}</span>
            </div>
          )}
          <div>
            <p className="font-medium text-sm">{u.username}</p>
            <p className="text-xs text-muted-foreground">{u.email}</p>
          </div>
        </div>
      ),
    },
    {
      key: 'role',
      header: 'Role',
      render: (u: AdminUser) => (
        <span
          className={`inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full font-medium ${
            u.role === 'admin'
              ? 'bg-primary/10 text-primary'
              : 'bg-secondary text-secondary-foreground'
          }`}
        >
          {u.role === 'admin' && <Crown className="h-3 w-3" />}
          {u.role}
        </span>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      render: (u: AdminUser) =>
        u.banned_at ? (
          <span className="text-xs text-destructive font-medium">Banned</span>
        ) : (
          <span className="text-xs text-green-600 font-medium">Active</span>
        ),
    },
    {
      key: 'created_at',
      header: 'Registered',
      render: (u: AdminUser) => (
        <span className="text-xs text-muted-foreground">{formatDate(u.created_at)}</span>
      ),
    },
    {
      key: 'actions',
      header: '',
      render: (u: AdminUser) => (
        <div className="flex items-center gap-1 justify-end">
          <button
            onClick={() => navigate(`/users/${u.id}`)}
            className="p-1.5 rounded hover:bg-accent transition-colors"
            title="View"
          >
            <Eye className="h-4 w-4 text-muted-foreground" />
          </button>
          {u.banned_at ? (
            <button
              onClick={() => handleUnban(u)}
              className="p-1.5 rounded hover:bg-accent transition-colors"
              title="Unban"
            >
              <ShieldCheck className="h-4 w-4 text-green-600" />
            </button>
          ) : (
            <button
              onClick={() => handleBan(u)}
              className="p-1.5 rounded hover:bg-accent transition-colors"
              title="Ban"
            >
              <Ban className="h-4 w-4 text-destructive" />
            </button>
          )}
          {u.role !== 'admin' && (
            <button
              onClick={() => handleMakeAdmin(u)}
              className="p-1.5 rounded hover:bg-accent transition-colors"
              title="Make Admin"
            >
              <Crown className="h-4 w-4 text-primary" />
            </button>
          )}
        </div>
      ),
      className: 'text-right',
    },
  ]

  return (
    <div className="p-8 space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Users</h1>
        <p className="text-muted-foreground text-sm mt-1">Manage all registered users</p>
      </div>

      {/* Search */}
      <div className="relative max-w-sm">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
        <input
          type="text"
          value={search}
          onChange={(e) => handleSearchChange(e.target.value)}
          placeholder="Search by username or email..."
          className="w-full pl-9 pr-3 py-2 text-sm rounded-md border bg-background focus:outline-none focus:ring-2 focus:ring-ring"
        />
      </div>

      <DataTable
        columns={columns}
        data={data?.users ?? []}
        loading={isLoading}
        total={data?.total ?? 0}
        page={page}
        limit={20}
        onPageChange={setPage}
        emptyMessage="No users found"
      />

      <ConfirmDialog
        {...confirmDialog}
        onCancel={closeConfirm}
        confirmLabel={confirmDialog.danger ? 'Confirm' : 'Yes'}
      />
    </div>
  )
}
