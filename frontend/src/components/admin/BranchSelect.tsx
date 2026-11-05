'use client';

import { useQuery } from '@tanstack/react-query';
import { branchesOf } from '@/services/admin';

const selectClass = 'h-[42px] w-full rounded-control border border-border-strong bg-surface-muted px-3 text-body '
  + 'text-text-primary outline-none focus-visible:outline-2 focus-visible:outline-info';

/** Chọn chi quản lý; "" là cả dòng họ (trưởng họ). */
export function BranchSelect({ familyId, value, onChange, label = 'Chi quản lý', emptyLabel = 'Cả dòng họ (trưởng họ)' }: {
  familyId: string; value: string; onChange: (value: string) => void; label?: string; emptyLabel?: string;
}) {
  const branches = useQuery({
    queryKey: ['branches', familyId],
    enabled: /^[0-9a-f-]{36}$/i.test(familyId),
    queryFn: () => branchesOf(familyId),
  });
  return (
    <label className="grid gap-1.5 text-label">{label}
      <select className={selectClass} value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">{emptyLabel}</option>
        {branches.data?.items.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
      </select>
      {branches.isError && <span className="text-caption text-error">Không tải được danh sách chi.</span>}
    </label>
  );
}
