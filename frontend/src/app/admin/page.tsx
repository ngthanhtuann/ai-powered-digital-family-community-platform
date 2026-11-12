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
  type AuditLog,
  type BackupFile,
  type AdminUser,
  type ContentType,
  type GlobalRole,
  type MemberRole,
  memberRoleLabel,
  type ReportStatus,
  type ReviewAction,
  type SystemConfig,
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

/** UC-AUTH-11: sao lưu PostgreSQL + ảnh, khôi phục có xác nhận ghi đè. */
function BackupSection() {
  const cache = useQueryClient();
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<BackupFile | null>(null);
  const [confirmText, setConfirmText] = useState('');
  const [notice, setNotice] = useState('');
  const backups = useQuery({
    queryKey: ['admin-backups', page],
    queryFn: () => adminApi.backups(page),
  });
  const create = useMutation({
    mutationFn: () => adminApi.createBackup(),
    onSuccess: async (file) => {
      setNotice(`Đã tạo bản sao lưu ${file.fileName}.`);
      setPage(0);
      await cache.invalidateQueries({ queryKey: ['admin-backups'] });
    },
  });
  const restore = useMutation({
    mutationFn: ({ id, text }: { id: string; text: 'RESTORE' }) => adminApi.restore(id, text),
    onSuccess: async () => {
      setNotice('Khôi phục hoàn tất. Tất cả refresh token đã bị thu hồi; vui lòng đăng nhập lại.');
      setSelected(null);
      setConfirmText('');
      await cache.invalidateQueries({ queryKey: ['admin-backups'] });
    },
  });
  const busy = create.isPending || restore.isPending;

  return (
    <section id="sao-luu-khoi-phuc" aria-labelledby="backup-title"
      className="scroll-mt-4 space-y-4 rounded-card border border-border bg-surface p-5">
      <h2 id="backup-title" className="text-section-title">Sao lưu &amp; Khôi phục</h2>
      <p className="text-body text-text-secondary">Bản sao lưu gồm PostgreSQL (cả dữ liệu vector) và ảnh/tệp đã tải lên.</p>
      <Button fullWidth={false} disabled={busy} loading={create.isPending} onClick={() => {
        setNotice(''); create.mutate();
      }}>Tạo bản sao lưu</Button>
      {create.isError && <p role="alert" className="text-body text-error">{errorText(create.error)}</p>}
      {notice && <p role="status" className="text-body text-text-primary">{notice}</p>}
      {backups.isPending && <p className="text-body text-text-secondary">Đang tải danh sách...</p>}
      {backups.isError && <p role="alert" className="text-body text-error">{errorText(backups.error)}</p>}
      {backups.isSuccess && backups.data.items.length === 0 && (
        <p className="text-body text-text-secondary">Chưa có bản sao lưu.</p>
      )}
      <ul className="space-y-3">
        {backups.data?.items.map((file) => (
          <li key={file.id} className="flex flex-wrap items-center justify-between gap-3 rounded-card border border-border p-4">
            <div className="min-w-0 text-body">
              <p className="break-all font-semibold">{file.fileName}</p>
              <p className="text-caption text-text-secondary">{dateText(file.createdAt)} · {(file.fileSizeBytes / (1024 * 1024)).toFixed(2)} MB</p>
            </div>
            <Button fullWidth={false} variant="danger" disabled={busy}
              onClick={() => { setSelected(file); setConfirmText(''); setNotice(''); }}>
              Khôi phục
            </Button>
          </li>
        ))}
      </ul>
      {backups.isSuccess && <Pager page={page} total={backups.data.meta.total} onChange={setPage} />}
      {selected && (
        <Modal open title={`Khôi phục ${selected.fileName}`} onClose={() => { if (!busy) setSelected(null); }}
          description="CẢNH BÁO: Dữ liệu cơ sở dữ liệu và toàn bộ ảnh/tệp tải lên hiện tại sẽ bị ghi đè bằng bản sao lưu đã chọn.">
          <form className="grid gap-4" onSubmit={(e) => {
            e.preventDefault();
            if (confirmText === 'RESTORE' && !busy && window.confirm('Xác nhận ghi đè dữ liệu hiện tại?')) {
              setNotice(''); restore.mutate({ id: selected.id, text: confirmText });
            }
          }}>
            <label className="grid gap-1.5 text-label">Nhập chính xác RESTORE để xác nhận
              <input className={selectClass} autoComplete="off" value={confirmText} disabled={busy}
                onChange={(e) => setConfirmText(e.target.value)} placeholder="RESTORE" />
            </label>
            {restore.isError && <p role="alert" className="text-body text-error">{errorText(restore.error)}</p>}
            <div className="flex flex-wrap gap-2">
              <Button fullWidth={false} variant="secondary" disabled={busy} onClick={() => setSelected(null)}>Hủy</Button>
              <Button type="submit" fullWidth={false} variant="danger" loading={restore.isPending}
                disabled={confirmText !== 'RESTORE' || busy}>Ghi đè và khôi phục</Button>
            </div>
          </form>
        </Modal>
      )}
    </section>
  );
}

/** Tên dễ đọc cho các mã hành động hay gặp (auth.md mục 6.4); mã khác hiện nguyên. */
const ACTION_LABEL: Record<string, string> = {
  LOGIN_SUCCESS: 'Đăng nhập', LOGIN_FAILED: 'Đăng nhập sai', LOGIN_BLOCKED: 'Đăng nhập bị chặn', LOGOUT: 'Đăng xuất',
  USER_REGISTERED: 'Đăng ký tài khoản', PASSWORD_CHANGED: 'Đổi mật khẩu', PROFILE_UPDATED: 'Sửa hồ sơ tài khoản',
  JOIN_REQUESTED: 'Xin vào gia đình', JOIN_CANCELLED: 'Hủy xin vào', JOIN_APPROVED: 'Duyệt thành viên',
  JOIN_REJECTED: 'Từ chối thành viên', USER_LOCKED: 'Khóa tài khoản', USER_UNLOCKED: 'Mở khóa tài khoản',
  ROLE_CHANGED: 'Đổi vai trò', MEMBERSHIP_CHANGED: 'Đổi tư cách thành viên', CONTENT_DELETED: 'Gỡ nội dung',
  CONTENT_CREATED: 'Đăng nội dung', CONTENT_UPDATED: 'Sửa nội dung',
  CONTENT_HIDDEN: 'Ẩn nội dung', REPORT_DISMISSED: 'Bỏ qua báo cáo', CONTENT_RESTORED: 'Khôi phục nội dung',
  BACKUP_CREATED: 'Tạo sao lưu', RESTORE_COMPLETED: 'Khôi phục dữ liệu', AI_QUERY: 'Hỏi trợ lý AI',
  CONFIG_CHANGED: 'Đổi cấu hình hệ thống',
  FAMILY_CREATED: 'Tạo gia đình', FAMILY_UPDATED: 'Sửa gia đình', FAMILY_DELETED: 'Xóa gia đình',
  BRANCH_CREATED: 'Tạo chi', BRANCH_UPDATED: 'Sửa chi', BRANCH_DELETED: 'Xóa chi',
  PERSON_CREATED: 'Thêm người', PERSON_UPDATED: 'Sửa người', PERSON_DELETED: 'Xóa người',
  PARENT_CHILD_CREATED: 'Thêm quan hệ cha/mẹ - con', PARENT_CHILD_DELETED: 'Xóa quan hệ cha/mẹ - con',
  MARRIAGE_CREATED: 'Thêm hôn nhân', MARRIAGE_UPDATED: 'Sửa hôn nhân', MARRIAGE_DELETED: 'Xóa hôn nhân',
  MEMBERS_CSV_EXPORTED: 'Xuất CSV thành viên', OUTSTANDING_MEMBER_CREATED: 'Thêm người tiêu biểu',
  OUTSTANDING_MEMBER_UPDATED: 'Sửa người tiêu biểu', OUTSTANDING_MEMBER_DELETED: 'Xóa người tiêu biểu',
};
const ROLE_LABEL: Record<string, string> = { MEMBER: 'Thành viên', BRANCH_ADMIN: 'Trưởng họ/chi', SYSTEM_ADMIN: 'Quản trị' };
const shortId = (id?: string | null) => (id ? id.slice(0, 8) : '');
/** Ngày từ ô date (giờ máy người xem) thành ISO-8601 kèm múi giờ, đầu hoặc cuối ngày. */
const dayBound = (day: string, end: boolean) =>
  (day ? new Date(`${day}T${end ? '23:59:59.999' : '00:00:00'}`).toISOString() : undefined);
const detailText = (detail: AuditLog['detail']) => Object.entries(detail ?? {})
  .map(([k, v]) => `${k}: ${typeof v === 'object' ? JSON.stringify(v) : String(v)}`).join('; ');

/** UC-AUTH-10: nhật ký kiểm toán, chỉ đọc, mới nhất trước, lọc theo ngày, hành động, người thực hiện. */
function AuditSection() {
  const [action, setAction] = useState('');
  const [fromDay, setFromDay] = useState('');
  const [toDay, setToDay] = useState('');
  const [actor, setActor] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const badRange = Boolean(fromDay && toDay && fromDay > toDay);
  const params = {
    action: action || undefined, from: dayBound(fromDay, false), to: dayBound(toDay, true),
    actorUserId: actor ?? undefined, page,
  };
  const list = useQuery({
    queryKey: ['admin-audit-logs', params],
    queryFn: () => adminApi.auditLogs(params),
    enabled: !badRange,
  });
  const filter = (apply: () => void) => { setPage(0); apply(); };

  const columns: TableColumn<AuditLog>[] = [
    { key: 'time', header: 'Thời điểm', render: (r) => <span className="whitespace-nowrap">{dateText(r.createdAt)}</span> },
    {
      key: 'action', header: 'Hành động',
      render: (r) => (
        <div>
          <p className="font-semibold">{ACTION_LABEL[r.action] ?? r.action}</p>
          <p className="text-caption text-text-secondary">{r.action}</p>
        </div>
      ),
    },
    {
      key: 'actor', header: 'Người thực hiện',
      render: (r) => !r.actorUserId ? <span className="text-text-secondary">Chưa xác định</span> : (
        <div className="space-y-1">
          <p>{ROLE_LABEL[r.actorRole ?? ''] ?? 'Không rõ vai trò'} · <span title={r.actorUserId}>{shortId(r.actorUserId)}</span></p>
          {actor !== r.actorUserId && (
            <button type="button" className="text-caption text-info underline"
              onClick={() => filter(() => setActor(r.actorUserId))}>Chỉ xem người này</button>
          )}
        </div>
      ),
    },
    {
      key: 'target', header: 'Đối tượng',
      render: (r) => r.targetType
        ? <span title={r.targetId ?? undefined}>{r.targetType}{r.targetId ? ` · ${shortId(r.targetId)}` : ''}</span>
        : '—',
    },
    { key: 'ip', header: 'IP', render: (r) => r.ipAddress ?? '—' },
    { key: 'detail', header: 'Chi tiết', render: (r) => <span className="break-all text-caption">{detailText(r.detail) || '—'}</span> },
  ];

  return (
    <section id="nhat-ky" aria-labelledby="nhat-ky-title"
      className="scroll-mt-4 space-y-4 rounded-card border border-border bg-surface p-5">
      <h2 id="nhat-ky-title" className="text-section-title">Nhật ký kiểm toán</h2>
      <p className="text-body text-text-secondary">
        Các thao tác nhạy cảm: ai làm, làm gì, lúc nào, từ IP nào. Chỉ xem, không sửa hay xóa được.
      </p>
      <div className="grid gap-3 md:grid-cols-3">
        <label className="grid gap-1.5 text-label">Hành động
          <select className={selectClass} value={action} onChange={(e) => filter(() => setAction(e.target.value))}>
            <option value="">Tất cả</option>
            {Object.entries(ACTION_LABEL).map(([code, label]) => <option key={code} value={code}>{label}</option>)}
          </select>
        </label>
        <label className="grid gap-1.5 text-label">Từ ngày
          <input type="date" className={selectClass} value={fromDay} max={toDay || undefined}
            onChange={(e) => filter(() => setFromDay(e.target.value))} />
        </label>
        <label className="grid gap-1.5 text-label">Đến ngày
          <input type="date" className={selectClass} value={toDay} min={fromDay || undefined}
            onChange={(e) => filter(() => setToDay(e.target.value))} />
        </label>
      </div>
      {actor && (
        <div className="flex flex-wrap items-center gap-2 text-body">
          <span>Đang lọc theo người thực hiện <span className="font-semibold" title={actor}>{shortId(actor)}</span></span>
          <Button fullWidth={false} variant="secondary" onClick={() => filter(() => setActor(null))}>Bỏ lọc người</Button>
        </div>
      )}
      {badRange && <p role="alert" className="text-body text-error">“Từ ngày” phải trước hoặc bằng “Đến ngày”.</p>}
      {!badRange && list.isPending && <p className="text-body text-text-secondary">Đang tải nhật ký...</p>}
      {!badRange && list.isError && <p role="alert" className="text-body text-error">{errorText(list.error)}</p>}
      {!badRange && list.isSuccess && (
        <>
          <p className="text-caption text-text-secondary">{list.data.meta.total} bản ghi</p>
          <Table caption="Nhật ký kiểm toán" columns={columns} rows={list.data.items} getRowKey={(r) => r.id}
            emptyMessage="Không có bản ghi phù hợp." />
          <Pager page={page} total={list.data.meta.total} onChange={setPage} />
        </>
      )}
    </section>
  );
}

/** Ô chọn cho khóa có tập giá trị cố định (auth.md mục 5.3); khóa khác dùng ô nhập. */
const CONFIG_CHOICES: Record<string, { value: string; label: string }[]> = {
  'membership.approval_mode': [
    { value: 'MANUAL', label: 'Thủ công (trưởng họ, trưởng chi duyệt)' },
    { value: 'AUTO', label: 'Tự động duyệt ngay' },
  ],
};
const BOOLEAN_CHOICES = [{ value: 'true', label: 'Bật' }, { value: 'false', label: 'Tắt' }];

const configSchema = z.object({ value: z.string().trim().min(1, 'Không được để trống').max(500, 'Tối đa 500 ký tự') });
type ConfigValues = z.infer<typeof configSchema>;

/** Một tham số: đổi giá trị hoặc khôi phục mặc định; khoảng hợp lệ do backend kiểm tra (400 VALID_001). */
function ConfigRow({ config }: { config: SystemConfig }) {
  const cache = useQueryClient();
  const [saved, setSaved] = useState('');
  const { register, handleSubmit, reset, watch, formState: { errors } } = useForm<ConfigValues>({
    resolver: zodResolver(configSchema), values: { value: config.value },
  });
  const save = useMutation({
    mutationFn: (value: string) => adminApi.updateConfig(config.key, value),
    onSuccess: async (updated) => {
      reset({ value: updated.value });
      setSaved('Đã lưu, có hiệu lực trong vòng 1 phút.');
      await cache.invalidateQueries({ queryKey: ['admin-configs'] });
    },
  });
  const draft = watch('value');
  const choices = config.valueType === 'BOOLEAN' ? BOOLEAN_CHOICES : CONFIG_CHOICES[config.key];
  const fieldId = `config-${config.key}`;
  return (
    <li className="rounded-card border border-border p-4">
      <form className="grid gap-3 md:grid-cols-[1fr_minmax(0,260px)_auto] md:items-end" noValidate
        onSubmit={handleSubmit((v) => { setSaved(''); save.mutate(v.value); })}>
        <div className="min-w-0 text-body">
          {/* Ô nhập chữ đã có nhãn riêng của TextField: mô tả chỉ là nhãn khi đi với ô chọn, tránh hai nhãn một ô. */}
          {choices
            ? <label htmlFor={fieldId} className="font-semibold">{config.description}</label>
            : <p className="font-semibold">{config.description}</p>}
          <p className="break-all text-caption text-text-secondary">
            {config.key} · Mặc định: {config.defaultValue} · Cập nhật: {dateText(config.updatedAt)}
          </p>
        </div>
        {choices ? (
          <select id={fieldId} className={selectClass} disabled={save.isPending} {...register('value')}>
            {choices.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
          </select>
        ) : (
          <TextField id={fieldId} label="Giá trị" inputMode={config.valueType === 'INTEGER' ? 'numeric' : undefined}
            maxLength={500} disabled={save.isPending} error={errors.value?.message} {...register('value')} />
        )}
        <div className="flex flex-wrap gap-2">
          <Button type="submit" fullWidth={false} loading={save.isPending}
            disabled={draft.trim() === config.value}>Lưu</Button>
          <Button fullWidth={false} variant="secondary" disabled={save.isPending || config.value === config.defaultValue}
            onClick={() => { setSaved(''); save.mutate(config.defaultValue); }}>Khôi phục mặc định</Button>
        </div>
      </form>
      {save.isError && <p role="alert" className="mt-2 text-body text-error">{errorText(save.error)}</p>}
      {saved && <p role="status" className="mt-2 text-body text-success">{saved}</p>}
    </li>
  );
}

/** UC-AUTH-12 (SCRUM-94): tham số hệ thống nhóm theo mục. Khóa bí mật chỉ ở biến môi trường, không hiện ở đây. */
function ConfigSection() {
  const configs = useQuery({ queryKey: ['admin-configs'], queryFn: () => adminApi.configs() });
  const groups = new Map<string, SystemConfig[]>();
  configs.data?.items.forEach((c) => groups.set(c.group, [...(groups.get(c.group) ?? []), c]));
  return (
    <section id="cau-hinh" aria-labelledby="cau-hinh-title"
      className="scroll-mt-4 space-y-4 rounded-card border border-border bg-surface p-5">
      <h2 id="cau-hinh-title" className="text-section-title">Cấu hình hệ thống</h2>
      <p className="text-body text-text-secondary">
        Thay đổi áp dụng trong vòng 1 phút, không cần khởi động lại. Mọi thay đổi được ghi vào nhật ký kiểm toán.
      </p>
      {configs.isPending && <p className="text-body text-text-secondary">Đang tải cấu hình...</p>}
      {configs.isError && <p role="alert" className="text-body text-error">{errorText(configs.error)}</p>}
      {configs.isSuccess && configs.data.items.length === 0 && (
        <p className="text-body text-text-secondary">Chưa có tham số cấu hình.</p>
      )}
      {[...groups.entries()].map(([group, items]) => (
        <div key={group} className="space-y-3">
          <h3 className="text-label text-text-secondary">{group}</h3>
          <ul className="space-y-3">
            {items.map((c) => <ConfigRow key={c.key} config={c} />)}
          </ul>
        </div>
      ))}
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
        <p className="mt-1 text-body text-text-secondary">Quản lý người dùng, kiểm duyệt nội dung, sao lưu dữ liệu, cấu hình hệ thống và xem nhật ký kiểm toán.</p>
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
          <BackupSection />
          <ConfigSection />
          <AuditSection />
        </>
      )}
    </div>
  );
}
