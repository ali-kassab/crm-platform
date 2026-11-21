import { Users } from 'lucide-react'
import { useDeferredValue, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Field, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Spinner } from '@/components/ui/spinner'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api'
import { formatDate, formatDateTime } from '@/lib/format'
import { useUserAccess, useUsers, type PlatformUser } from './api'
import { Pager, QueryState } from './parts'

const ALL = 'ALL'

/** Registered accounts across companies: account metadata only (no passwords, tokens or phones). */
export function UsersPage() {
  const { t } = useTranslation()
  const [q, setQ] = useState('')
  const [status, setStatus] = useState(ALL)
  const [kind, setKind] = useState(ALL)
  const [page, setPage] = useState(0)
  const query = useDeferredValue(q.trim())
  const users = useUsers({ q: query, status: status === ALL ? '' : status, kind: kind === ALL ? '' : kind, page })
  const data = users.data
  const reset = <T,>(set: (v: T) => void) => (v: T) => {
    set(v)
    setPage(0)
  }

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold">{t('admin.users.title')}</h1>
      <div className="flex flex-wrap gap-2">
        <Input value={q} onChange={(e) => reset(setQ)(e.target.value)} placeholder={t('admin.users.search')} aria-label={t('common.search')} className="max-w-xs bg-background" />
        <Select value={status} onValueChange={reset(setStatus)}>
          <SelectTrigger className="w-40 bg-background" aria-label={t('admin.users.status')}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>{t('admin.users.allStatuses')}</SelectItem>
            <SelectItem value="ACTIVE">{t('admin.users.active')}</SelectItem>
            <SelectItem value="INACTIVE">{t('admin.users.inactive')}</SelectItem>
          </SelectContent>
        </Select>
        <Select value={kind} onValueChange={reset(setKind)}>
          <SelectTrigger className="w-44 bg-background" aria-label={t('admin.users.kind')}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>{t('admin.users.allKinds')}</SelectItem>
            <SelectItem value="TENANT">{t('admin.users.tenant')}</SelectItem>
            <SelectItem value="PLATFORM_ADMIN">{t('admin.users.platformAdmin')}</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <QueryState pending={users.isPending} error={users.error} empty={!!data && data.content.length === 0} emptyIcon={Users} emptyTitle={t('admin.users.empty')} onRetry={() => void users.refetch()}>
        {data ? (
          <>
            <div className="overflow-x-auto rounded-md border bg-background">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('admin.users.user')}</TableHead>
                    <TableHead>{t('admin.companies.title')}</TableHead>
                    <TableHead>{t('admin.users.status')}</TableHead>
                    <TableHead>{t('admin.users.registered')}</TableHead>
                    <TableHead>{t('admin.users.lastLogin')}</TableHead>
                    <TableHead>
                      <span className="sr-only">{t('admin.users.access')}</span>
                    </TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.content.map((u) => (
                    <TableRow key={u.id}>
                      <TableCell>
                        <div className="font-medium">
                          {u.firstName} {u.lastName}
                        </div>
                        <div className="text-xs text-muted-foreground" dir="ltr">
                          {u.email}
                        </div>
                      </TableCell>
                      <TableCell>
                        {u.platformAdmin ? (
                          <Badge>{t('admin.users.platformAdmin')}</Badge>
                        ) : u.companies.length === 0 ? (
                          <span className="text-muted-foreground">—</span>
                        ) : (
                          <div className="flex flex-col gap-1">
                            {u.companies.map((c) => (
                              <span key={c.id} className="flex flex-wrap items-center gap-1">
                                <Link to={`/admin/companies/${c.id}`} className="underline-offset-4 hover:underline">
                                  {c.name}
                                </Link>
                                <Badge variant="outline">{t(`admin.roles.${c.role}`, { defaultValue: c.role })}</Badge>
                                {!c.active ? <Badge variant="outline">{t('admin.users.membershipEnded')}</Badge> : null}
                              </span>
                            ))}
                          </div>
                        )}
                      </TableCell>
                      <TableCell>
                        <Badge variant={u.active ? 'secondary' : 'destructive'}>{t(u.active ? 'admin.users.active' : 'admin.users.inactive')}</Badge>
                      </TableCell>
                      <TableCell>{formatDate(u.createdAt)}</TableCell>
                      <TableCell>{u.lastLoginAt ? formatDateTime(u.lastLoginAt) : t('admin.users.never')}</TableCell>
                      <TableCell className="text-end">
                        <UserAccessButton user={u} />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
            <Pager page={page} pages={data.totalPages} total={data.totalElements} onPage={setPage} />
          </>
        ) : null}
      </QueryState>
    </div>
  )
}

/** Activate / deactivate with a confirmation dialog; deactivating needs a reason (kept in the audit log). */
export function UserAccessButton({ user }: { user: PlatformUser }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const access = useUserAccess(user.id)
  if (user.platformAdmin) return null
  const deactivating = user.active
  const missing = deactivating && !reason.trim()

  function submit() {
    setError(null)
    if (missing) return
    access.mutate(
      { active: !deactivating, reason: reason.trim() || undefined },
      {
        onSuccess: () => {
          toast.success(t(deactivating ? 'admin.users.deactivated' : 'admin.users.activated'))
          setOpen(false)
          setReason('')
        },
        onError: (e) => setError(e instanceof ApiError ? e.message : t('errors.generic')),
      },
    )
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant={deactivating ? 'outline' : 'default'}>
          {t(deactivating ? 'admin.users.deactivate' : 'admin.users.activate')}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t(deactivating ? 'admin.users.deactivateTitle' : 'admin.users.activateTitle', { email: user.email })}</DialogTitle>
          <DialogDescription>{t(deactivating ? 'admin.users.deactivateHint' : 'admin.users.activateHint')}</DialogDescription>
        </DialogHeader>
        <FieldGroup>
          {error ? (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          ) : null}
          <Field data-invalid={missing && !!reason}>
            <FieldLabel htmlFor={`reason-${user.id}`}>{t(deactivating ? 'admin.actions.reason' : 'admin.requests.note')}</FieldLabel>
            <Textarea id={`reason-${user.id}`} rows={2} maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
            {missing && reason ? <FieldError>{t('admin.users.reasonRequired')}</FieldError> : null}
          </Field>
        </FieldGroup>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            {t('common.cancel')}
          </Button>
          <Button variant={deactivating ? 'destructive' : 'default'} onClick={submit} disabled={access.isPending || missing}>
            {access.isPending ? <Spinner /> : null}
            {t(deactivating ? 'admin.users.deactivate' : 'admin.users.activate')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
