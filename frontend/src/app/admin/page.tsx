'use client';

import { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { Table, type TableColumn } from '@/components/ui/Table';
import { TextField } from '@/components/ui/TextField';
import { AccessState, useHeritageAccess } from '@/components/heritage/HeritageAccess';
import { BranchSelect } from '@/components/admin/BranchSelect';
import {
  adminApi,
  PAGE_SIZE,
  type AdminUser,
  type ContentType,
  type GlobalRole,
  type MemberRole,
  memberRoleLabel,
  type ReportStatus,
  type ReviewAction,
  type UserStatus,
} from '@/services/admin';

const selectClass = 'h-[42px] w-full rounded-control border border-border-strong bg-surface-muted px-3 text-body '
  + 'text-text-primary outline-none focus-visible:outline-2 focus-visible:outline-info';
const errorText = (error: unknown) => (error instanceof Error ? error.message : 'Có lỗi xảy ra, vui lòng thử lại.');
const dateText = (value?: string | null) => (value ? new Date(value).toLocaleString('vi-VN') : 'Chưa có');
const TYPE_LABEL: Record<ContentType, string> = {
  POST: 'Bài đăng', COMMENT: 'Bình luận', EVENT: 'Sự kiện', ANNOUNCEMENT: 'Thông báo',
};
const STATUS_LABEL: Record<ReportStatus, string> = { PENDING: 'Chờ xử lý', HIDDEN: 'Đã ẩn', DISMISSED: 'Đã bỏ qua' };

function Pager({ page, total, onChange }: { page: number; total: number; onChange: (page: number) => void }) {
  if (total <= PAGE_SIZE) return null;
  return (
    <nav className="flex items-center justify-between gap-2" aria-label="Phân trang">
      <Button fullWidth={false} variant="secondary" disabled={page === 0} onClick={() => onChange(page - 1)}>Trang trước</Button>
      <span className="text-caption text-text-secondary">Trang {page + 1}/{Math.ceil(total / PAGE_SIZE)}</span>
      <Button fullWidth={false} variant="secondary" disabled={(page + 1) * PAGE_SIZE >= total}
        onClick={() => onChange(page + 1)}>Trang sau</Button>
    </nav>
  );
}

const lockSchema = z.object({
  lockReason: z.string().trim().min(1, 'Cần nhập lý do khóa').max(255, 'Tối đa 255 ký tự'),
});
type LockValues = z.infer<typeof lockSchema>;

/** UC-AUTH-08 bước 3: khóa bắt buộc có lý do (BR-AUTH-13). */
function LockModal({ user, onClose }: { user: AdminUser; onClose: () => void }) {
  const cache = useQueryClient();
  const { register, handleSubmit, formState: { errors } } = useForm<LockValues>({
    resolver: zodResolver(lockSchema), defaultValues: { lockReason: '' },
  });
  const lock = useMutation({
    mutationFn: (v: LockValues) => adminApi.setStatus(user.id, { status: 'LOCKED', lockReason: v.lockReason }),
    onSuccess: async () => { await cache.invalidateQueries({ queryKey: ['admin-users'] }); onClose(); },
  });
  return (
    <Modal open title={`Khóa tài khoản ${user.fullName}`}
      description="Người này bị đăng xuất ngay và không đăng nhập được cho đến khi được mở khóa." onClose={onClose}>
      <form className="grid gap-4" noValidate onSubmit={handleSubmit((v) => lock.mutate(v))}>
        <TextField label="Lý do khóa" maxLength={255} error={errors.lockReason?.message} {...register('lockReason')} />
        {lock.isError && <p role="alert" className="text-body text-error">{errorText(lock.error)}</p>}
        <Button type="submit" variant="danger" loading={lock.isPending}>Khóa tài khoản</Button>
      </form>
    </Modal>
  );
}

/** UC-AUTH-08, BR-AUTH-14: gán vào dòng họ với vai trò thành viên hoặc trưởng họ/trưởng chi, có hiệu lực ngay. */
function MembershipModal({ user, onClose }: { user: AdminUser; onClose: () => void }) {
  const cache = useQueryClient();
  const [familyId, setFamilyId] = useState(user.membership.familyId ?? '');
  const [role, setRole] = useState<MemberRole>(user.membership.role ?? 'MEMBER');
  const [branchId, setBranchId] = useState(user.membership.branchId ?? '');
  const validFamily = /^[0-9a-f-]{36}$/i.test(familyId.trim());
  const save = useMutation({
    mutationFn: () => adminApi.setMembership(user.id, { familyId: familyId.trim(), role, branchId: branchId || null }),
    onSuccess: async () => { await cache.invalidateQueries({ queryKey: ['admin-users'] }); onClose(); },
  });
  return (
    <Modal open title={`Gán dòng họ cho ${user.fullName}`}
      description="Nếu người này đang thuộc dòng họ khác, bản ghi cũ chuyển sang đã rời và hồ sơ gắn tài khoản được gỡ."
      onClose={onClose}>
      <form className="grid gap-4" onSubmit={(e) => { e.preventDefault(); save.mutate(); }}>
        <TextField label="Mã dòng họ" value={familyId} placeholder="UUID dòng họ"
          error={familyId && !validFamily ? 'Mã dòng họ là UUID' : undefined} onChange={(e) => setFamilyId(e.target.value)} />
        <label className="grid gap-1.5 text-label">Vai trò trong dòng họ
          <select className={selectClass} value={role} onChange={(e) => setRole(e.target.value as MemberRole)}>
            <option value="MEMBER">Thành viên</option><option value="BRANCH_ADMIN">Trưởng họ / Trưởng chi</option>
          </select>
        </label>
        {validFamily && (
          <BranchSelect familyId={familyId.trim()} value={branchId} onChange={setBranchId}
            label={role === 'BRANCH_ADMIN' ? 'Chi quản lý' : 'Chi (không bắt buộc)'} />
        )}
        {save.isError && <p role="alert" className="text-body text-error">{errorText(save.error)}</p>}
        <Button type="submit" loading={save.isPending} disabled={!validFamily}>Lưu</Button>
      </form>
    </Modal>
  );
}

function UsersSection({ myId }: { myId?: string }) {
  const cache = useQueryClient();
  const [search, setSearch] = useState('');
  const [filters, setFilters] = useState<{ q?: string; status?: UserStatus; role?: GlobalRole }>({});
  const [page, setPage] = useState(0);
  const [locking, setLocking] = useState<AdminUser | null>(null);
  const [assigning, setAssigning] = useState<AdminUser | null>(null);
  const list = useQuery({
    queryKey: ['admin-users', filters, page],
    queryFn: () => adminApi.users({ ...filters, page }),
  });
  const refresh = () => cache.invalidateQueries({ queryKey: ['admin-users'] });
  const unlock = useMutation({ mutationFn: (id: string) => adminApi.setStatus(id, { status: 'ACTIVE' }), onSuccess: refresh });
  const roles = useMutation({
    mutationFn: ({ id, admin }: { id: string; admin: boolean }) =>
      adminApi.setRoles(id, admin ? ['MEMBER', 'SYSTEM_ADMIN'] : ['MEMBER']),
    onSuccess: refresh,
  });
  const actionError = unlock.error ?? roles.error;

  const columns: TableColumn<AdminUser>[] = [
    {
      key: 'name', header: 'Người dùng',
      render: (u) => (
        <div className="min-w-0">
          <p className="font-semibold">{u.fullName}</p>
          <p className="break-all text-caption text-text-secondary">{u.email}</p>
        </div>
      ),
    },
    {
      key: 'role', header: 'Vai trò',
      render: (u) => u.roles.includes('SYSTEM_ADMIN') ? 'Quản trị hệ thống'
        : u.membership.status === 'APPROVED'
          ? memberRoleLabel(u.membership.role, u.membership.branchId, u.membership.branchName)
          : 'Chưa xác minh',
    },
    { key: 'family', header: 'Gia đình', render: (u) => u.membership.familyName ?? 'Chưa thuộc gia đình' },
    {
      key: 'status', header: 'Trạng thái',
      render: (u) => u.status === 'LOCKED'
        ? <span className="text-error" title={u.lockReason ?? undefined}>Đã khóa{u.lockReason ? `: ${u.lockReason}` : ''}</span>
        : 'Hoạt động',
    },
    { key: 'login', header: 'Đăng nhập gần nhất', render: (u) => dateText(u.lastLoginAt) },
    {
      key: 'actions', header: 'Thao tác',
      render: (u) => u.id === myId ? <span className="text-caption text-text-secondary">Tài khoản của bạn</span> : (
        <div className="flex flex-wrap gap-2">
          {u.status === 'LOCKED'
            ? <Button fullWidth={false} variant="secondary" loading={unlock.isPending && unlock.variables === u.id}
                onClick={() => unlock.mutate(u.id)}>Mở khóa</Button>
            : <Button fullWidth={false} variant="danger" onClick={() => setLocking(u)}>Khóa</Button>}
          {!u.roles.includes('SYSTEM_ADMIN') && (
            <Button fullWidth={false} variant="secondary" onClick={() => setAssigning(u)}>Gán dòng họ</Button>
          )}
          {/* BR-AUTH-08: người đang thuộc hoặc đang xin vào dòng họ không được làm quản trị hệ thống. */}
          {(u.roles.includes('SYSTEM_ADMIN') || u.membership.status === 'NONE' || u.membership.status === 'REJECTED') && (
            <Button fullWidth={false} variant="secondary"
              loading={roles.isPending && roles.variables?.id === u.id}
              onClick={() => {
                const admin = !u.roles.includes('SYSTEM_ADMIN');
                if (window.confirm(admin ? `Cấp quyền quản trị hệ thống cho ${u.fullName}?`
                  : `Thu quyền quản trị hệ thống của ${u.fullName}?`)) roles.mutate({ id: u.id, admin });
              }}>
              {u.roles.includes('SYSTEM_ADMIN') ? 'Thu quyền quản trị' : 'Cấp quyền quản trị'}
            </Button>
          )}
        </div>
      ),
    },
  ];

  return (
    <section aria-labelledby="nguoi-dung" className="space-y-4 rounded-card border border-border bg-surface p-5">
      <h2 id="nguoi-dung" className="text-section-title">Quản lý người dùng</h2>
      <form className="grid gap-3 md:grid-cols-[1fr_180px_200px_auto] md:items-end" role="search"
        onSubmit={(e) => { e.preventDefault(); setPage(0); setFilters((f) => ({ ...f, q: search.trim() || undefined })); }}>
        <TextField label="Tìm theo họ tên hoặc email" maxLength={100} value={search} onChange={(e) => setSearch(e.target.value)} />
        <label className="grid gap-1.5 text-label">Trạng thái
          <select className={selectClass} value={filters.status ?? ''}
            onChange={(e) => { setPage(0); setFilters((f) => ({ ...f, status: (e.target.value || undefined) as UserStatus | undefined })); }}>
            <option value="">Tất cả</option><option value="ACTIVE">Hoạt động</option><option value="LOCKED">Đã khóa</option>
          </select>
        </label>
        <label className="grid gap-1.5 text-label">Vai trò hệ thống
          <select className={selectClass} value={filters.role ?? ''}
            onChange={(e) => { setPage(0); setFilters((f) => ({ ...f, role: (e.target.value || undefined) as GlobalRole | undefined })); }}>
            <option value="">Tất cả</option><option value="MEMBER">Thành viên</option><option value="SYSTEM_ADMIN">Quản trị hệ thống</option>
          </select>
        </label>
        <Button type="submit" fullWidth={false} variant="secondary">Tìm</Button>
      </form>
      {list.isPending && <p className="text-body text-text-secondary">Đang tải người dùng...</p>}
      {list.isError && <p role="alert" className="text-body text-error">{errorText(list.error)}</p>}
      {actionError && <p role="alert" className="text-body text-error">{errorText(actionError)}</p>}
      {list.isSuccess && (
        <>
          <p className="text-caption text-text-secondary">{list.data.meta.total} người dùng</p>
          <Table caption="Danh sách người dùng" columns={columns} rows={list.data.items} getRowKey={(u) => u.id}
            emptyMessage={filters.q ? 'Không có người dùng phù hợp.' : 'Chưa có người dùng.'} />
          <Pager page={page} total={list.data.meta.total} onChange={setPage} />
        </>
      )}
      {locking && <LockModal user={locking} onClose={() => setLocking(null)} />}
      {assigning && <MembershipModal user={assigning} onClose={() => setAssigning(null)} />}
    </section>
  );
}

/** UC-AUTH-09: hàng đợi báo cáo, ẩn nội dung hoặc bỏ qua. */
function ModerationSection() {
  const cache = useQueryClient();
  const [status, setStatus] = useState<ReportStatus>('PENDING');
  const [targetType, setTargetType] = useState<ContentType | undefined>();
  const [page, setPage] = useState(0);
  const list = useQuery({
    queryKey: ['admin-reports', status, targetType, page],
    queryFn: () => adminApi.reports({ status, targetType, page }),
  });
  const review = useMutation({
    mutationFn: ({ id, action }: { id: string; action: ReviewAction }) => adminApi.review(id, action),
    onSuccess: () => cache.invalidateQueries({ queryKey: ['admin-reports'] }),
  });

  return (
    <section id="kiem-duyet" aria-labelledby="kiem-duyet-title"
      className="scroll-mt-4 space-y-4 rounded-card border border-border bg-surface p-5">
      <h2 id="kiem-duyet-title" className="text-section-title">Kiểm duyệt nội dung</h2>
      <div className="grid gap-3 md:grid-cols-2">
        <label className="grid gap-1.5 text-label">Trạng thái báo cáo
          <select className={selectClass} value={status}
            onChange={(e) => { setPage(0); setStatus(e.target.value as ReportStatus); }}>
            {(Object.keys(STATUS_LABEL) as ReportStatus[]).map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
          </select>
        </label>
        <label className="grid gap-1.5 text-label">Loại nội dung
          <select className={selectClass} value={targetType ?? ''}
            onChange={(e) => { setPage(0); setTargetType((e.target.value || undefined) as ContentType | undefined); }}>
            <option value="">Tất cả</option>
            {(Object.keys(TYPE_LABEL) as ContentType[]).map((t) => <option key={t} value={t}>{TYPE_LABEL[t]}</option>)}
          </select>
        </label>
      </div>
      {list.isPending && <p className="text-body text-text-secondary">Đang tải báo cáo...</p>}
      {list.isError && <p role="alert" className="text-body text-error">{errorText(list.error)}</p>}
      {review.isError && <p role="alert" className="text-body text-error">{errorText(review.error)}</p>}
      {list.isSuccess && list.data.items.length === 0 && (
        <p className="rounded-card bg-surface-muted p-5 text-body text-text-secondary">
          {status === 'PENDING' ? 'Không có báo cáo nào chờ xử lý.' : 'Chưa có báo cáo ở trạng thái này.'}
        </p>
      )}
      <ul className="space-y-3">
        {list.data?.items.map((r) => (
          <li key={r.id} className="space-y-3 rounded-card border border-border p-4">
            <div className="flex flex-wrap items-center gap-2 text-caption text-text-secondary">
              <span className="rounded-full bg-surface-muted px-3 py-1 font-medium text-text-primary">{TYPE_LABEL[r.targetType]}</span>
              <span>{STATUS_LABEL[r.status]}</span>
              <span className={r.reportCount > 1 ? 'font-semibold text-error' : ''}>· {r.reportCount} lượt báo cáo</span>
              <span>· Báo cáo đầu lúc {dateText(r.createdAt)}</span>
            </div>
            <p className="text-caption text-text-secondary">
              Người đăng: <span className="text-text-primary">{r.contentOwnerName ?? 'Tài khoản đã xóa'}</span>
              {' · '}Dòng họ: <span className="text-text-primary">{r.familyName ?? 'Không xác định'}</span>
            </p>
            <blockquote className="whitespace-pre-wrap break-words border-l-4 border-secondary pl-3 text-body">
              {r.contentSnippet || '(Không có nội dung văn bản)'}
            </blockquote>
            <p className="text-body"><span className="font-semibold">Lý do{r.reportCount > 1 ? ' (báo cáo đầu tiên)' : ''}:</span> {r.reason}</p>
            {r.status === 'HIDDEN' && (
              <Button fullWidth={false} variant="secondary"
                loading={review.isPending && review.variables?.id === r.id}
                onClick={() => { if (window.confirm('Khôi phục nội dung này lên bảng tin?')) review.mutate({ id: r.id, action: 'RESTORE_CONTENT' }); }}>
                Khôi phục
              </Button>
            )}
            {r.status === 'PENDING' && (
              <div className="flex flex-wrap gap-2">
                <Button fullWidth={false} variant="danger"
                  loading={review.isPending && review.variables?.id === r.id && review.variables.action === 'HIDE_CONTENT'}
                  onClick={() => { if (window.confirm('Ẩn nội dung này khỏi bảng tin?')) review.mutate({ id: r.id, action: 'HIDE_CONTENT' }); }}>
                  Ẩn nội dung
                </Button>
                <Button fullWidth={false} variant="secondary"
                  loading={review.isPending && review.variables?.id === r.id && review.variables.action === 'DISMISS'}
                  onClick={() => review.mutate({ id: r.id, action: 'DISMISS' })}>
                  Bỏ qua
                </Button>
              </div>
            )}
          </li>
        ))}
      </ul>
      {list.isSuccess && <Pager page={page} total={list.data.meta.total} onChange={setPage} />}
    </section>
  );
}

/** Trang /admin (SCRUM-50): chỉ SYSTEM_ADMIN; backend vẫn kiểm quyền ở mọi API (PERM_001). */
export default function AdminPage() {
  const access = useHeritageAccess();
  const loading = access.profile.isLoading || access.membership.isLoading;
  const error = (access.profile.error as Error | null)?.message;

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <header>
        <h1 className="text-page-title">Quản trị hệ thống</h1>
        <p className="mt-1 text-body text-text-secondary">Quản lý người dùng và xử lý nội dung bị báo cáo.</p>
      </header>
      <AccessState loading={loading} error={error} />
      {access.profile.isSuccess && !access.isSystemAdmin && (
        <p role="alert" className="rounded-card border border-error bg-error-soft p-4 text-body text-error">
          Trang này chỉ dành cho quản trị hệ thống.
        </p>
      )}
      {access.isSystemAdmin && (
        <>
          <UsersSection myId={access.profile.data?.id} />
          <ModerationSection />
        </>
      )}
    </div>
  );
}
