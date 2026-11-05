'use client';

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { TextField } from '@/components/ui/TextField';
import { AccessState, useHeritageAccess } from '@/components/heritage/HeritageAccess';
import { BranchSelect } from '@/components/admin/BranchSelect';
import { branchesOf } from '@/services/admin';
import { genealogyApi } from '@/services/genealogy';
import { JOIN_PAGE_SIZE, joinRequestsApi, type JoinRequest, type JoinRequestStatus } from '@/services/join-requests';

const selectClass = 'h-[42px] w-full rounded-control border border-border-strong bg-surface-muted px-3 text-body '
  + 'text-text-primary outline-none focus-visible:outline-2 focus-visible:outline-info';
const errorText = (error: unknown) => (error instanceof Error ? error.message : 'Có lỗi xảy ra, vui lòng thử lại.');
const dateText = (value?: string | null) => (value ? new Date(value).toLocaleString('vi-VN') : '');
const STATUS_LABEL: Partial<Record<JoinRequestStatus, string>> = {
  PENDING: 'Chờ duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Đã từ chối', CANCELLED: 'Người xin đã hủy',
};

/** UC-AUTH-07 bước 2: duyệt, có thể gắn với hồ sơ thành viên có sẵn (BR-AUTH-11). */
function ApproveModal({ request, canPickBranch, onClose }: {
  request: JoinRequest; canPickBranch: boolean; onClose: () => void;
}) {
  const cache = useQueryClient();
  const [personId, setPersonId] = useState('');
  const [branchId, setBranchId] = useState(request.branchId ?? '');
  const persons = useQuery({
    queryKey: ['join-persons', request.familyId],
    queryFn: () => genealogyApi.persons(request.familyId, { page: 0, size: 100, sort: 'fullName,asc' }),
  });
  const approve = useMutation({
    mutationFn: () => joinRequestsApi.review(request.id, {
      status: 'APPROVED', personId: personId || null,
      branchId: canPickBranch && branchId !== (request.branchId ?? '') ? branchId || null : null,
    }),
    onSuccess: async () => { await cache.invalidateQueries({ queryKey: ['join-requests'] }); onClose(); },
  });
  return (
    <Modal open title={`Duyệt ${request.applicantName}`} onClose={onClose}
      description="Người này sẽ thành thành viên đã xác minh và xem được dữ liệu gia đình ngay.">
      <form className="grid gap-4" onSubmit={(e) => { e.preventDefault(); approve.mutate(); }}>
        <label className="grid gap-1.5 text-label">Gắn với hồ sơ trong cây gia phả (không bắt buộc)
          <select className={selectClass} value={personId} onChange={(e) => {
            setPersonId(e.target.value);
            const chosen = persons.data?.items.find((p) => p.id === e.target.value);
            if (canPickBranch && chosen?.branchId) setBranchId(chosen.branchId);
          }}>
            <option value="">Không gắn</option>
            {persons.data?.items.map((p) => (
              <option key={p.id} value={p.id}>{p.fullName}{p.birthYear ? ` (${p.birthYear})` : ''}</option>
            ))}
          </select>
        </label>
        {persons.isError && <p className="text-caption text-error">Không tải được danh sách thành viên.</p>}
        {canPickBranch && (
          <BranchSelect familyId={request.familyId} value={branchId} onChange={setBranchId}
            label="Chi của thành viên" emptyLabel="Không thuộc chi nào (cả dòng họ)" />
        )}
        {approve.isError && <p role="alert" className="text-body text-error">{errorText(approve.error)}</p>}
        <Button type="submit" loading={approve.isPending}>Duyệt</Button>
      </form>
    </Modal>
  );
}

function RejectModal({ request, onClose }: { request: JoinRequest; onClose: () => void }) {
  const cache = useQueryClient();
  const [reason, setReason] = useState('');
  const reject = useMutation({
    mutationFn: () => joinRequestsApi.review(request.id, { status: 'REJECTED', rejectReason: reason.trim() || null }),
    onSuccess: async () => { await cache.invalidateQueries({ queryKey: ['join-requests'] }); onClose(); },
  });
  return (
    <Modal open title={`Từ chối ${request.applicantName}`} onClose={onClose}
      description="Người xin sẽ thấy lý do và có thể gửi yêu cầu mới.">
      <form className="grid gap-4" onSubmit={(e) => { e.preventDefault(); reject.mutate(); }}>
        <TextField label="Lý do (không bắt buộc)" maxLength={255} value={reason} onChange={(e) => setReason(e.target.value)} />
        {reject.isError && <p role="alert" className="text-body text-error">{errorText(reject.error)}</p>}
        <Button type="submit" variant="danger" loading={reject.isPending}>Từ chối</Button>
      </form>
    </Modal>
  );
}

function CopyCode({ label, code }: { label: string; code: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <li className="flex flex-wrap items-center justify-between gap-2 border-b border-border py-2 last:border-b-0">
      <div className="min-w-0">
        <p className="text-label">{label}</p>
        <code className="select-all break-all text-caption text-text-secondary">{code}</code>
      </div>
      <Button fullWidth={false} variant="secondary" onClick={() => {
        void navigator.clipboard?.writeText(code).then(() => setCopied(true), () => setCopied(false));
      }}>{copied ? 'Đã sao chép' : 'Sao chép'}</Button>
    </li>
  );
}

/** BR-AUTH-09: mã để mời. Mã chi đưa yêu cầu tới trưởng chi đó; mã gia đình tới trưởng họ. */
function InviteCodes({ familyId, myBranchId }: { familyId: string; myBranchId: string | null }) {
  const branches = useQuery({ queryKey: ['branches', familyId], queryFn: () => branchesOf(familyId) });
  const all = branches.data?.items ?? [];
  // Trưởng chi: chi mình và các chi con (đi theo parentBranchId).
  const visible = myBranchId === null ? all : all.filter((b) => {
    for (let id: string | null | undefined = b.id; id; id = all.find((x) => x.id === id)?.parentBranchId) {
      if (id === myBranchId) return true;
    }
    return false;
  });
  return (
    <section className="rounded-card border border-border bg-surface p-4" aria-labelledby="ma-moi">
      <h2 id="ma-moi" className="text-label">Mã để mời người thân</h2>
      <p className="mt-1 text-caption text-text-secondary">
        Gửi mã chi để yêu cầu tới thẳng trưởng chi đó; mã gia đình thì trưởng họ duyệt.
      </p>
      <ul className="mt-2">
        {myBranchId === null && <CopyCode label="Mã gia đình (cả dòng họ)" code={familyId} />}
        {visible.map((b) => <CopyCode key={b.id} label={`Mã chi ${b.name}`} code={b.id!} />)}
      </ul>
      {branches.isError && <p className="text-caption text-error">Không tải được danh sách chi.</p>}
    </section>
  );
}

/** UC-AUTH-07: trưởng họ, trưởng chi (trong phạm vi chi) và quản trị duyệt yêu cầu tham gia. */
export default function JoinRequestsPage() {
  const access = useHeritageAccess();
  const [status, setStatus] = useState<JoinRequestStatus>('PENDING');
  const [adminFamily, setAdminFamily] = useState('');
  const [page, setPage] = useState(0);
  const [approving, setApproving] = useState<JoinRequest | null>(null);
  const [rejecting, setRejecting] = useState<JoinRequest | null>(null);
  const familyFilter = access.isSystemAdmin && /^[0-9a-f-]{36}$/i.test(adminFamily.trim()) ? adminFamily.trim() : undefined;
  const list = useQuery({
    queryKey: ['join-requests', status, familyFilter, page],
    enabled: access.isReady && access.isAdmin,
    queryFn: () => joinRequestsApi.list({ status, familyId: familyFilter, page }),
  });
  const total = list.data?.meta.total ?? 0;
  const familyId = access.familyId ?? '';

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <header>
        <h1 className="text-page-title">Duyệt yêu cầu tham gia</h1>
        <p className="mt-1 text-body text-text-secondary">
          Người thân xin vào bằng mã gia đình hoặc mã chi. Trưởng chi thấy yêu cầu gửi bằng mã chi mình và chi con.
        </p>
      </header>
      <AccessState loading={access.profile.isLoading || access.membership.isLoading}
        error={(access.membership.error as Error | null)?.message} />
      {access.isReady && !access.isAdmin && (
        <p role="alert" className="rounded-card border border-error bg-error-soft p-4 text-body text-error">
          Chỉ trưởng họ, trưởng chi và quản trị hệ thống mới duyệt được yêu cầu.
        </p>
      )}
      {access.isAdmin && (
        <>
          {familyId && <InviteCodes familyId={familyId} myBranchId={access.membership.data?.branchId ?? null} />}
          <div className="grid gap-3 md:grid-cols-2">
            <label className="grid gap-1.5 text-label">Trạng thái
              <select className={selectClass} value={status}
                onChange={(e) => { setPage(0); setStatus(e.target.value as JoinRequestStatus); }}>
                {Object.entries(STATUS_LABEL).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
            </label>
            {access.isSystemAdmin && (
              <TextField label="Lọc theo mã gia đình (không bắt buộc)" value={adminFamily} placeholder="UUID gia đình"
                onChange={(e) => { setPage(0); setAdminFamily(e.target.value); }} />
            )}
          </div>
          {list.isPending && list.fetchStatus !== 'idle' && <p className="text-body text-text-secondary">Đang tải yêu cầu...</p>}
          {list.isError && <p role="alert" className="text-body text-error">{errorText(list.error)}</p>}
          {list.isSuccess && list.data.items.length === 0 && (
            <p className="rounded-card bg-surface p-5 text-body text-text-secondary">
              {status === 'PENDING' ? 'Không có yêu cầu nào đang chờ duyệt.' : 'Chưa có yêu cầu ở trạng thái này.'}
            </p>
          )}
          <ul className="space-y-3">
            {list.data?.items.map((r) => (
              <li key={r.id} className="space-y-2 rounded-card border border-border bg-surface p-4">
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <p className="text-body font-semibold">{r.applicantName}</p>
                  <span className="text-caption text-text-secondary">{STATUS_LABEL[r.status] ?? r.status} · {dateText(r.createdAt)}</span>
                </div>
                <p className="break-all text-caption text-text-secondary">{r.applicantEmail}</p>
                {r.message && <p className="whitespace-pre-wrap break-words text-body">“{r.message}”</p>}
                {r.status === 'REJECTED' && r.rejectReason && (
                  <p className="text-caption text-text-secondary">Lý do từ chối: {r.rejectReason}</p>
                )}
                {r.status === 'PENDING' && (
                  <div className="flex flex-wrap gap-2 pt-1">
                    <Button fullWidth={false} onClick={() => setApproving(r)}>Duyệt</Button>
                    <Button fullWidth={false} variant="secondary" onClick={() => setRejecting(r)}>Từ chối</Button>
                  </div>
                )}
              </li>
            ))}
          </ul>
          {total > JOIN_PAGE_SIZE && (
            <nav className="flex items-center justify-between" aria-label="Phân trang">
              <Button fullWidth={false} variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Trang trước</Button>
              <span className="text-caption text-text-secondary">Trang {page + 1}/{Math.ceil(total / JOIN_PAGE_SIZE)}</span>
              <Button fullWidth={false} variant="secondary" disabled={(page + 1) * JOIN_PAGE_SIZE >= total}
                onClick={() => setPage(page + 1)}>Trang sau</Button>
            </nav>
          )}
        </>
      )}
      {approving && (
        <ApproveModal request={approving} canPickBranch={!access.membership.data?.branchId}
          onClose={() => setApproving(null)} />
      )}
      {rejecting && <RejectModal request={rejecting} onClose={() => setRejecting(null)} />}
    </div>
  );
}
