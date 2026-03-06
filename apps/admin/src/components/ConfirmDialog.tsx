interface ConfirmDialogProps {
  open: boolean
  title: string
  description: string
  onConfirm: () => void
  onCancel: () => void
  confirmLabel?: string
  danger?: boolean
}

export function ConfirmDialog({
  open,
  title,
  description,
  onConfirm,
  onCancel,
  confirmLabel = 'Confirm',
  danger = false,
}: ConfirmDialogProps) {
  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/50" onClick={onCancel} />
      <div className="relative bg-card rounded-lg border p-6 shadow-lg w-full max-w-sm mx-4">
        <h3 className="font-semibold text-lg mb-2">{title}</h3>
        <p className="text-sm text-muted-foreground mb-6">{description}</p>
        <div className="flex gap-3 justify-end">
          <button
            onClick={onCancel}
            className="px-4 py-2 text-sm rounded-md border hover:bg-accent transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={onConfirm}
            className={`px-4 py-2 text-sm rounded-md font-medium transition-colors ${
              danger
                ? 'bg-destructive text-destructive-foreground hover:bg-destructive/90'
                : 'bg-primary text-primary-foreground hover:bg-primary/90'
            }`}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
