'use client';

import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { TextField } from '@/components/ui/TextField';
import { genealogyApi, type Person } from '@/services/genealogy';

export const selectClass = 'h-[42px] w-full rounded-control border border-border-strong bg-surface-muted px-3 text-body '
  + 'text-text-primary outline-none focus-visible:outline-2 focus-visible:outline-info';
export const errorText = (error: unknown) => (error instanceof Error ? error.message : 'Có lỗi xảy ra, vui lòng thử lại.');
export const yearOf = (p: Person) => p.birthYear ?? (p.birthDate ? Number(p.birthDate.slice(0, 4)) : undefined);

/** parent: thêm cha/mẹ cho person; child: person làm cha/mẹ của người được chọn; spouse: thêm vợ/chồng. */
export type RelationMode = 'parent' | 'child' | 'spouse';

const TITLES: Record<RelationMode, string> = { parent: 'Thêm cha/mẹ cho', child: 'Thêm con cho', spouse: 'Thêm vợ/chồng cho' };
const PICK_LABEL: Record<RelationMode, string> = { parent: 'Người làm cha/mẹ', child: 'Người làm con', spouse: 'Người còn lại' };

/** UC-GEN-04, 05: chọn người còn lại trong gia đình; lỗi nghiệp vụ hiện đúng thông điệp server (GEN_005..011). */
export function RelationModal({ familyId, person, mode, onClose }: {
  familyId: string; person: Person; mode: RelationMode; onClose: () => void;
}) {
  const cache = useQueryClient();
  const options = useQuery({
    queryKey: ['members-options', familyId],
    queryFn: () => genealogyApi.persons(familyId, { page: 0, size: 100, sort: 'fullName,asc' }),
  });
  const [otherId, setOtherId] = useState('');
  const [role, setRole] = useState<'FATHER' | 'MOTHER'>('FATHER');
  const [kind, setKind] = useState<'BIOLOGICAL' | 'ADOPTED' | 'STEP'>('BIOLOGICAL');
  const [startDate, setStartDate] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const others = (options.data?.items ?? []).filter((p) => p.id !== person.id);
  const other = others.find((p) => p.id === otherId);
  // BR-GEN-06: vai trò theo giới tính của người làm cha/mẹ
  const parent = mode === 'child' ? person : other;
  useEffect(() => { if (parent?.gender) setRole(parent.gender === 'FEMALE' ? 'MOTHER' : 'FATHER'); }, [parent]);
  const [older, younger] = mode === 'child' ? [person, other] : [other, person];
  const gap = older && younger && yearOf(older) && yearOf(younger) ? yearOf(younger)! - yearOf(older)! : undefined;
  const needsConfirm = mode !== 'spouse' && gap !== undefined && gap < 15; // BR-GEN-04: chỉ cảnh báo, server mới chặn
  const save = useMutation({
    mutationFn: () => {
      if (mode === 'spouse') {
        return genealogyApi.createMarriage({ person1Id: person.id!, person2Id: otherId, startDate: startDate || null });
      }
      const [childId, parentId] = mode === 'parent' ? [person.id!, otherId] : [otherId, person.id!];
      return genealogyApi.addParent(childId, { parentId, parentRole: role, kind });
    },
    onSuccess: async () => {
      await Promise.all([cache.invalidateQueries({ queryKey: ['members'] }), cache.invalidateQueries({ queryKey: ['tree'] })]);
      onClose();
    },
  });
  return (
    <Modal open title={`${TITLES[mode]} ${person.fullName}`} onClose={onClose}>
      <form className="grid gap-4 p-5" onSubmit={(e) => { e.preventDefault(); save.mutate(); }}>
        {options.isPending && <p className="text-body text-text-secondary">Đang tải danh sách thành viên...</p>}
        {options.isError && <p role="alert" className="text-body text-error">{errorText(options.error)}</p>}
        <label className="grid gap-1 text-label">{PICK_LABEL[mode]}
          <select className={selectClass} value={otherId} required onChange={(e) => setOtherId(e.target.value)}>
            <option value="">Chọn thành viên</option>
            {others.map((p) => <option key={p.id} value={p.id}>{p.fullName}{yearOf(p) ? ` (${yearOf(p)})` : ''}</option>)}
          </select>
        </label>
        {mode !== 'spouse' ? <>
          <label className="grid gap-1 text-label">Vai trò {mode === 'child' ? `của ${person.fullName}` : ''}
            <select className={selectClass} value={role} onChange={(e) => setRole(e.target.value as 'FATHER' | 'MOTHER')}>
              <option value="FATHER">Cha</option><option value="MOTHER">Mẹ</option>
            </select>
          </label>
          <label className="grid gap-1 text-label">Loại quan hệ
            <select className={selectClass} value={kind} onChange={(e) => setKind(e.target.value as typeof kind)}>
              <option value="BIOLOGICAL">Ruột</option><option value="ADOPTED">Nuôi</option><option value="STEP">Kế</option>
            </select>
          </label>
          {needsConfirm && <label className="flex min-h-11 items-center gap-2 rounded-control bg-warning-soft p-3 text-body">
            <input type="checkbox" className="h-5 w-5" checked={confirmed} onChange={(e) => setConfirmed(e.target.checked)} />
            {gap <= 0 ? 'Cha/mẹ không lớn tuổi hơn con (hệ thống sẽ từ chối nếu sinh sau con).'
              : `Cha/mẹ chỉ hơn con ${gap} tuổi.`} Tôi xác nhận thông tin đúng.
          </label>}
        </> : <TextField label="Ngày kết hôn (không bắt buộc)" type="date" value={startDate}
          onChange={(e) => setStartDate(e.target.value)} />}
        {save.isError && <p role="alert" className="text-body text-error">{errorText(save.error)}</p>}
        <Button type="submit" loading={save.isPending} disabled={!otherId || (needsConfirm && !confirmed)}>Lưu</Button>
      </form>
    </Modal>
  );
}
