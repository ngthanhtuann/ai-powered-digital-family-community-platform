'use client';

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { Table, type TableColumn } from '@/components/ui/Table';
import { BranchSelect } from '@/components/admin/BranchSelect';
import { AccessState, useHeritageAccess } from '@/components/heritage/HeritageAccess';
import { TextField } from '@/components/ui/TextField';
import { branchesOf, familyRolesApi, memberRoleLabel, PAGE_SIZE, type FamilyAccount, type MemberRole } from '@/services/admin';
import { genealogyApi, type BranchHead, type Person } from '@/services/genealogy';
import { ResetCodeModal } from '@/components/admin/ResetCodeModal';
import { AdminFamilyPicker, useAdminFamily } from '@/components/admin/AdminFamilyPicker';

const selectClass = 'h-[42px] w-full rounded-control border border-border-strong bg-surface-muted px-3 text-body '
  + 'text-text-primary outline-none focus-visible:outline-2 focus-visible:outline-info';
const errorText = (error: unknown) => (error instanceof Error ? error.message : 'Có lỗi xảy ra, vui lòng thử lại.');

function RoleModal({ familyId, account, onClose }: { familyId: string; account: FamilyAccount; onClose: () => void }) {
  const cache = useQueryClient();
  const [role, setRole] = useState<MemberRole>(account.role);
  const [branchId, setBranchId] = useState(account.branchId ?? '');
  const save = useMutation({
    mutationFn: () => familyRolesApi.setRole(familyId, account.userId, { role, branchId: branchId || null }),
    onSuccess: async () => { await cache.invalidateQueries({ queryKey: ['family-accounts'] }); onClose(); },
  });
  return (
    <Modal open title={`Phân công ${account.fullName}`}
      description="Trưởng chi quản lý chi được chọn và các chi con; chọn cả dòng họ để cùng làm trưởng họ." onClose={onClose}>
      <form className="grid gap-4" onSubmit={(e) => { e.preventDefault(); save.mutate(); }}>
        <label className="grid gap-1.5 text-label">Vai trò
          <select className={selectClass} value={role} onChange={(e) => setRole(e.target.value as MemberRole)}>
            <option value="MEMBER">Thành viên</option>
            <option value="BRANCH_ADMIN">Trưởng họ / Trưởng chi</option>
          </select>
        </label>
        <BranchSelect familyId={familyId} value={branchId} onChange={setBranchId}
          label={role === 'BRANCH_ADMIN' ? 'Chi quản lý' : 'Chi của thành viên (không bắt buộc)'} />
        {save.isError && <p role="alert" className="text-body text-error">{errorText(save.error)}</p>}
        <Button type="submit" loading={save.isPending}>Lưu</Button>
      </form>
    </Modal>
  );
}

type BranchItem = Awaited<ReturnType<typeof branchesOf>>['items'][number];

/** Chi xếp thành cây: chi gốc trước, chi con thụt vào; chi mồ côi (cha đã xóa) xếp cuối ở độ sâu 0. */
function branchRows(branches: BranchItem[]) {
  const rows: { branch: BranchItem; depth: number }[] = [];
  const seen = new Set<string>();
  const visit = (parent: string | null, depth: number) => {
    branches.filter((b) => (b.parentBranchId ?? null) === parent && !seen.has(b.id ?? ''))
      .sort((a, b) => (a.name ?? '').localeCompare(b.name ?? '', 'vi'))
      .forEach((b) => { seen.add(b.id ?? ''); rows.push({ branch: b, depth }); visit(b.id ?? null, depth + 1); });
  };
  visit(null, 0);
  branches.filter((b) => !seen.has(b.id ?? '')).forEach((b) => rows.push({ branch: b, depth: 0 }));
  return rows;
}

const matches = (q: string, ...texts: (string | null | undefined)[]) =>
  !q || texts.some((s) => s?.toLocaleLowerCase('vi').includes(q.toLocaleLowerCase('vi')));

/** Người có thể mời: còn sống, chưa gắn tài khoản, đủ 16 tuổi (BR-GEN-16: dưới 16 không có tài khoản riêng). */
function invitable(p: Person, today: Date) {
  if (p.isDeceased || p.userId || !p.birthDate) return false;
  const born = new Date(p.birthDate);
  born.setFullYear(born.getFullYear() + 16);
  return born <= today;
}

/** UC-GEN-02, BR-GEN-30 (SCRUM-147): thêm chi hoặc sửa tên, chi cha, người lập chi. Người lập chưa có chi thì vào chi. */
function BranchFormModal({ familyId, branch, branches, people, onClose }: {
  familyId: string; branch?: BranchItem; branches: BranchItem[]; people: Person[]; onClose: () => void;
}) {
  const cache = useQueryClient();
  const [name, setName] = useState(branch?.name ?? `Chi ${branches.filter((b) => !b.parentBranchId).length + 1}`);
  const [parentBranchId, setParentBranchId] = useState(branch?.parentBranchId ?? '');
  const [originPersonId, setOriginPersonId] = useState(branch?.originPersonId ?? '');
  // Người chưa thuộc chi nào lên đầu: thường là con trai của người tổ chưa lập chi.
  const founders = people.filter((p) => !p.isDeceased || p.id === originPersonId)
    .sort((a, b) => Number(Boolean(a.branchId)) - Number(Boolean(b.branchId)) || (a.fullName ?? '').localeCompare(b.fullName ?? '', 'vi'));
  const save = useMutation({
    mutationFn: () => {
      const input = { name: name.trim(), parentBranchId: parentBranchId || null, originPersonId: originPersonId || null,
        description: branch?.description ?? null };
      return branch ? genealogyApi.updateBranch(branch.id!, input) : genealogyApi.createBranch(familyId, input);
    },
    onSuccess: async () => {
      await Promise.all(['branches', 'branch-heads', 'members', 'tree'].map((key) => cache.invalidateQueries({ queryKey: [key] })));
      onClose();
    },
  });
  return (
    <Modal open title={branch ? `Sửa ${branch.name}` : 'Thêm chi'}
      description="Chi gốc do con trai của người tổ lập (Chi 1 con cả, Chi 2 con thứ hai...). Người lập chi chưa thuộc chi nào sẽ vào chi này."
      onClose={onClose}>
      <form className="grid gap-4" onSubmit={(e) => { e.preventDefault(); save.mutate(); }}>
        <TextField label="Tên chi" required maxLength={150} value={name} onChange={(e) => setName(e.target.value)} />
        <label className="grid gap-1.5 text-label">Thuộc chi
          <select className={selectClass} value={parentBranchId} onChange={(e) => setParentBranchId(e.target.value)}>
            <option value="">Chi gốc (thuộc cả dòng họ)</option>
            {branches.filter((b) => b.id !== branch?.id).map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
          </select>
        </label>
        <label className="grid gap-1.5 text-label">Người lập chi
          <select className={selectClass} value={originPersonId} onChange={(e) => setOriginPersonId(e.target.value)}>
            <option value="">Chưa ghi</option>
            {founders.map((p) => <option key={p.id} value={p.id}>{p.fullName}{p.branchId ? '' : ' (chưa thuộc chi nào)'}</option>)}
          </select>
        </label>
        {save.isError && <p role="alert" className="text-body text-error">{errorText(save.error)}</p>}
        <Button type="submit" loading={save.isPending} disabled={!name.trim()}>Lưu</Button>
      </form>
    </Modal>
  );
}

/** BR-GEN-29 (SCRUM-146): chọn người trên cây làm trưởng chi, kể cả chưa có tài khoản; hoặc quay về theo dòng. */
function PickHeadPersonModal({ branch, head, people, onClose }: {
  branch: BranchItem; head?: BranchHead; people: Person[]; onClose: () => void;
}) {
  const cache = useQueryClient();
  const [personId, setPersonId] = useState(head?.source === 'MANUAL' ? head.headPersonId ?? '' : '');
  const options = people.filter((p) => !p.isDeceased && p.branchId === branch.id)
    .sort((a, b) => (a.fullName ?? '').localeCompare(b.fullName ?? '', 'vi'));
  const save = useMutation({
    mutationFn: () => genealogyApi.setBranchHead(branch.id!, personId || null),
    onSuccess: async () => {
      await Promise.all(['branch-heads', 'family-accounts', 'tree'].map((key) => cache.invalidateQueries({ queryKey: [key] })));
      onClose();
    },
  });
  return (
    <Modal open title={`Trưởng ${branch.name}`}
      description="Mặc định theo dòng (con trưởng của người lập chi). Có thể chọn người khác trong chi, kể cả người chưa có tài khoản." onClose={onClose}>
      <form className="grid gap-4" onSubmit={(e) => { e.preventDefault(); save.mutate(); }}>
        <label className="grid gap-1.5 text-label">Trưởng chi
          <select className={selectClass} value={personId} onChange={(e) => setPersonId(e.target.value)}>
            <option value="">Theo dòng (tự động)</option>
            {options.map((p) => <option key={p.id} value={p.id}>{p.fullName}{p.userId ? '' : ' (chưa có tài khoản)'}</option>)}
          </select>
        </label>
        {save.isError && <p role="alert" className="text-body text-error">{errorText(save.error)}</p>}
        <Button type="submit" loading={save.isPending}>Lưu</Button>
      </form>
    </Modal>
  );
}

function InviteModal({ familyId, person, branchName, onClose }: {
  familyId: string; person: Person; branchName?: string; onClose: () => void;
}) {
  const [copied, setCopied] = useState(false);
  const code = person.branchId ?? familyId; // mã chi đưa yêu cầu tới trưởng chi đó; mã gia đình tới trưởng họ (BR-AUTH-09)
  const reviewer = person.branchId ? `trưởng chi ${branchName ?? ''}`.trim() : 'trưởng họ';
  const message = `Mời ${person.fullName} tham gia gia phả trên FamilyConnect.\n`
    + '1. Đăng ký tài khoản FamilyConnect.\n'
    + `2. Vào "Tham gia gia đình", dán mã: ${code}\n`
    + `3. Chờ ${reviewer} duyệt; khi duyệt sẽ gắn tài khoản vào hồ sơ ${person.fullName} trên cây.`;
  return (
    <Modal open title={`Mời ${person.fullName}`} description="Gửi lời mời này qua tin nhắn hoặc mạng xã hội." onClose={onClose}>
      <div className="grid gap-4">
        <p className="whitespace-pre-wrap rounded-control bg-surface-muted p-3 text-body">{message}</p>
        <Button onClick={() => { void navigator.clipboard?.writeText(message).then(() => setCopied(true), () => setCopied(false)); }}>
          {copied ? 'Đã sao chép lời mời' : 'Sao chép lời mời'}
        </Button>
      </div>
    </Modal>
  );
}

/** SCRUM-137: xem phân công theo chi; chi chưa có trưởng chi hiện "Chưa có" kèm nút chọn; mời người chưa có tài khoản. */
function BranchOverview({ familyId }: { familyId: string }) {
  const [q, setQ] = useState('');
  const [picking, setPicking] = useState<BranchItem | null>(null);
  const [editing, setEditing] = useState<BranchItem | 'new' | null>(null);
  const [inviting, setInviting] = useState<Person | null>(null);
  // shortcut: lấy tối đa 100 tài khoản, 100 chi, 100 người (giới hạn size của API); dòng họ lớn hơn cần phân trang.
  const accounts = useQuery({ queryKey: ['family-accounts', familyId, 'all'], queryFn: () => familyRolesApi.accounts(familyId, 0, 100) });
  const branches = useQuery({ queryKey: ['branches', familyId], queryFn: () => branchesOf(familyId) });
  const branchHeads = useQuery({ queryKey: ['branch-heads', familyId], queryFn: () => genealogyApi.branchHeads(familyId) });
  const headOf = new Map((branchHeads.data ?? []).map((h) => [h.branchId ?? '', h]));
  const persons = useQuery({
    queryKey: ['members', familyId, 'invite'],
    queryFn: () => genealogyApi.persons(familyId, { page: 0, size: 100, sort: 'fullName,asc' }),
  });
  const all = accounts.data?.items ?? [];
  const heads = all.filter((a) => a.role === 'BRANCH_ADMIN' && !a.branchId);
  const branchList = branches.data?.items ?? [];
  const branchName = (id?: string | null) => branchList.find((b) => b.id === id)?.name;
  const today = new Date();
  const toInvite = (persons.data?.items ?? []).filter((p) => invitable(p, today) && matches(q, p.fullName, branchName(p.branchId)));
  const rows = branchRows(branchList).filter(({ branch }) => matches(q, branch.name, headOf.get(branch.id ?? '')?.headPersonName));
  const failed = accounts.error ?? branches.error ?? persons.error;

  return (
    <section className="space-y-4" aria-labelledby="theo-chi">
      <h2 id="theo-chi" className="text-section-title">Theo chi</h2>
      <TextField label="Tìm theo tên người hoặc tên chi" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Ví dụ: Nguyễn Văn An" />
      {(accounts.isPending || branches.isPending) && <p className="text-body text-text-secondary">Đang tải...</p>}
      {failed && <p role="alert" className="text-body text-error">{errorText(failed)}</p>}

      <div className="rounded-card border border-border bg-surface p-4">
        <p className="text-label">Trưởng họ</p>
        <p className="mt-1 text-body">{heads.length ? heads.map((h) => h.fullName).join(', ') : 'Chưa có'}</p>
      </div>

      <div className="flex justify-end">
        <Button fullWidth={false} onClick={() => setEditing('new')}>Thêm chi</Button>
      </div>
      <ul className="divide-y divide-border rounded-card border border-border bg-surface" aria-label="Các chi">
        {branches.isSuccess && rows.length === 0 && (
          <li className="p-4 text-body text-text-secondary">{q ? 'Không có chi phù hợp.' : 'Dòng họ chưa có chi nào.'}</li>
        )}
        {rows.map(({ branch, depth }) => {
          const head = headOf.get(branch.id ?? '');
          const note = head?.headPersonName && [
            head.familyHead ? 'trưởng họ kiêm' : head.source === 'MANUAL' ? 'chọn tay' : 'theo dòng',
            head.hasAccount ? null : 'chưa có tài khoản',
          ].filter(Boolean).join(', ');
          return (
            <li key={branch.id} className="flex flex-wrap items-center justify-between gap-2 p-3"
              style={{ paddingLeft: 12 + Math.min(depth, 6) * 20 }}>
              <div className="min-w-0">
                <p className="text-label">{depth > 0 ? '└ ' : ''}{branch.name}</p>
                <p className={`text-caption ${head?.headPersonName ? 'text-text-secondary' : 'font-semibold text-warning'}`}>
                  Trưởng chi: {head?.headPersonName ? `${head.headPersonName} (${note})` : 'Chưa có'}
                </p>
              </div>
              <div className="flex flex-wrap gap-2">
                <Button fullWidth={false} variant="secondary" onClick={() => setEditing(branch)}>Sửa chi</Button>
                <Button fullWidth={false} variant="secondary" onClick={() => setPicking(branch)}>
                  {head?.headPersonName ? 'Đổi trưởng chi' : 'Chọn trưởng chi'}
                </Button>
              </div>
            </li>
          );
        })}
      </ul>

      <div className="space-y-2">
        <h3 className="text-label">Người trong gia phả chưa có tài khoản</h3>
        {persons.isSuccess && toInvite.length === 0 && (
          <p className="text-body text-text-secondary">Không có ai cần mời (người còn sống, từ 16 tuổi, chưa có tài khoản).</p>
        )}
        <ul className="divide-y divide-border rounded-card border border-border bg-surface">
          {toInvite.map((p) => (
            <li key={p.id} className="flex flex-wrap items-center justify-between gap-2 p-3">
              <div className="min-w-0">
                <p className="text-label">{p.fullName}</p>
                <p className="text-caption text-text-secondary">{p.branchId ? branchName(p.branchId) ?? '' : 'Chưa thuộc chi nào'}</p>
              </div>
              <Button fullWidth={false} variant="secondary" onClick={() => setInviting(p)}>Mời</Button>
            </li>
          ))}
        </ul>
      </div>

      {editing && <BranchFormModal familyId={familyId} branch={editing === 'new' ? undefined : editing} branches={branchList}
        people={persons.data?.items ?? []} onClose={() => setEditing(null)} />}
      {picking && <PickHeadPersonModal branch={picking} head={headOf.get(picking.id ?? '')} people={persons.data?.items ?? []}
        onClose={() => setPicking(null)} />}
      {inviting && <InviteModal familyId={familyId} person={inviting} branchName={branchName(inviting.branchId)}
        onClose={() => setInviting(null)} />}
    </section>
  );
}

/** UC-AUTH-14: trưởng họ (BRANCH_ADMIN không gán chi) hoặc quản trị bổ nhiệm, thu quyền trưởng chi. */
export default function FamilyRolesPage() {
  const access = useHeritageAccess();
  const adminFamily = useAdminFamily() ?? ''; // gia đình chung chọn ở header (SCRUM-118)
  const membership = access.membership.data;
  const isHead = membership?.status === 'APPROVED' && membership.role === 'BRANCH_ADMIN' && !membership.branchId;
  const familyId = access.isSystemAdmin ? adminFamily.trim() : (isHead ? membership?.familyId ?? '' : '');
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<FamilyAccount | null>(null);
  const [resetting, setResetting] = useState<FamilyAccount | null>(null);
  const list = useQuery({
    queryKey: ['family-accounts', familyId, page],
    enabled: access.isReady && /^[0-9a-f-]{36}$/i.test(familyId),
    queryFn: () => familyRolesApi.accounts(familyId, page),
  });
  const myId = access.profile.data?.id;
  const columns: TableColumn<FamilyAccount>[] = [
    { key: 'name', header: 'Họ tên', render: (a) => <span className="font-semibold">{a.fullName}</span> },
    { key: 'role', header: 'Vai trò', render: (a) => memberRoleLabel(a.role, a.branchId, a.branchName) },
    {
      key: 'actions', header: 'Thao tác',
      render: (a) => a.userId === myId
        ? <span className="text-caption text-text-secondary">Tài khoản của bạn</span>
        : (
          <div className="flex flex-wrap gap-2">
            <Button fullWidth={false} variant="secondary" onClick={() => setEditing(a)}>Phân công</Button>
            {/* BR-AUTH-24: trưởng họ không cấp mã cho trưởng họ khác; quản trị thì được. */}
            {(access.isSystemAdmin || a.role !== 'BRANCH_ADMIN' || a.branchId) && (
              <Button fullWidth={false} variant="secondary" onClick={() => setResetting(a)}>Cấp mã đặt lại mật khẩu</Button>
            )}
          </div>
        ),
    },
  ];
  const total = list.data?.meta.total ?? 0;

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <header>
        <h1 className="text-page-title">Phân công trưởng chi</h1>
        <p className="mt-1 text-body text-text-secondary">
          Trưởng họ xem từng chi đã có trưởng chi chưa, chọn người quản lý, mời người thân chưa có tài khoản,
          hoặc thu quyền về thành viên.
        </p>
      </header>
      <AccessState loading={access.profile.isLoading || access.membership.isLoading}
        error={(access.membership.error as Error | null)?.message} />
      {access.isReady && !access.isSystemAdmin && !isHead && (
        <p role="alert" className="rounded-card border border-error bg-error-soft p-4 text-body text-error">
          Chỉ trưởng họ (người quản lý cả dòng họ) mới phân công được trưởng chi.
        </p>
      )}
      {access.isSystemAdmin && (
        <AdminFamilyPicker />
      )}
      {list.isPending && list.fetchStatus !== 'idle' && <p className="text-body text-text-secondary">Đang tải danh sách...</p>}
      {list.isError && <p role="alert" className="text-body text-error">{errorText(list.error)}</p>}
      {familyId && list.isSuccess && <BranchOverview familyId={familyId} />}
      {list.isSuccess && (
        <>
          <h2 className="text-section-title">Tất cả tài khoản</h2>
          <Table caption="Tài khoản trong dòng họ" columns={columns} rows={list.data.items} getRowKey={(a) => a.userId}
            emptyMessage="Dòng họ chưa có tài khoản nào được duyệt." />
          {total > PAGE_SIZE && (
            <nav className="flex items-center justify-between" aria-label="Phân trang">
              <Button fullWidth={false} variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Trang trước</Button>
              <span className="text-caption text-text-secondary">Trang {page + 1}/{Math.ceil(total / PAGE_SIZE)}</span>
              <Button fullWidth={false} variant="secondary" disabled={(page + 1) * PAGE_SIZE >= total}
                onClick={() => setPage(page + 1)}>Trang sau</Button>
            </nav>
          )}
        </>
      )}
      {editing && <RoleModal familyId={familyId} account={editing} onClose={() => setEditing(null)} />}
      {resetting && <ResetCodeModal userId={resetting.userId} fullName={resetting.fullName} onClose={() => setResetting(null)} />}
    </div>
  );
}
