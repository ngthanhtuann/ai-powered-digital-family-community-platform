'use client';

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { Table, type TableColumn } from '@/components/ui/Table';
import { TextField } from '@/components/ui/TextField';
import { BranchSelect } from '@/components/admin/BranchSelect';
import { AccessState, useHeritageAccess } from '@/components/heritage/HeritageAccess';
import { familyRolesApi, memberRoleLabel, PAGE_SIZE, type FamilyAccount, type MemberRole } from '@/services/admin';

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

/** UC-AUTH-14: trưởng họ (BRANCH_ADMIN không gán chi) hoặc quản trị bổ nhiệm, thu quyền trưởng chi. */
export default function FamilyRolesPage() {
  const access = useHeritageAccess();
  const [adminFamily, setAdminFamily] = useState('');
  const membership = access.membership.data;
  const isHead = membership?.status === 'APPROVED' && membership.role === 'BRANCH_ADMIN' && !membership.branchId;
  const familyId = access.isSystemAdmin ? adminFamily.trim() : (isHead ? membership?.familyId ?? '' : '');
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<FamilyAccount | null>(null);
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
        : <Button fullWidth={false} variant="secondary" onClick={() => setEditing(a)}>Phân công</Button>,
    },
  ];
  const total = list.data?.meta.total ?? 0;

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <header>
        <h1 className="text-page-title">Phân công trưởng chi</h1>
        <p className="mt-1 text-body text-text-secondary">
          Trưởng họ bổ nhiệm người quản lý từng chi hoặc thu quyền về thành viên.
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
        <TextField label="Mã dòng họ" value={adminFamily} placeholder="UUID dòng họ" onChange={(e) => setAdminFamily(e.target.value)} />
      )}
      {list.isPending && list.fetchStatus !== 'idle' && <p className="text-body text-text-secondary">Đang tải danh sách...</p>}
      {list.isError && <p role="alert" className="text-body text-error">{errorText(list.error)}</p>}
      {list.isSuccess && (
        <>
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
    </div>
  );
}
