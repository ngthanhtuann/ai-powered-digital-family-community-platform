'use client';

import { useEffect, useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
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
type SpouseRef = { id: string; name: string; status: string; deceased: boolean };

export function RelationModal({ familyId, person, mode, spouses = [], onClose }: {
  familyId: string; person: Person; mode: RelationMode; spouses?: SpouseRef[]; onClose: () => void;
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
  // SCRUM-143: mặc định nhập người mới ngay tại đây; người đã có hồ sơ thì chọn trong danh sách.
  const [source, setSource] = useState<'new' | 'existing'>('new');
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
      <div className="grid grid-cols-2 gap-2 px-5 pt-5" role="group" aria-label="Nguồn hồ sơ">
        <Button variant={source === 'new' ? 'primary' : 'secondary'} aria-pressed={source === 'new'} onClick={() => setSource('new')}>
          Người mới
        </Button>
        <Button variant={source === 'existing' ? 'primary' : 'secondary'} aria-pressed={source === 'existing'}
          onClick={() => setSource('existing')}>
          Người đã có hồ sơ
        </Button>
      </div>
      {source === 'new' && <NewRelativeForm person={person} mode={mode} spouses={spouses} onDone={onClose} />}
      {source === 'existing' && (
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
      )}
    </Modal>
  );
}

// SCRUM-143 (BR-GEN-26): người còn sống phải có ngày sinh (BR-GEN-17); người đã mất có thể chỉ biết năm sinh.
const relativeSchema = z.object({
  fullName: z.string().trim().min(1, 'Họ tên không được để trống').max(150, 'Tối đa 150 ký tự'),
  gender: z.enum(['MALE', 'FEMALE', 'OTHER']),
  isDeceased: z.boolean(),
  birthDate: z.string(),
  birthYear: z.string().refine((v) => v === '' || /^\d{4}$/.test(v), 'Năm gồm 4 chữ số'),
  deathDate: z.string(),
  parentRole: z.enum(['FATHER', 'MOTHER']),
  kind: z.enum(['BIOLOGICAL', 'ADOPTED', 'STEP']),
  startDate: z.string(),
}).refine((v) => v.isDeceased || v.birthDate !== '', { path: ['birthDate'], message: 'Người còn sống phải có ngày sinh' });
type RelativeValues = z.infer<typeof relativeSchema>;

const RELATION = { parent: 'PARENT', child: 'CHILD', spouse: 'SPOUSE' } as const;
const NAME_LABEL: Record<RelationMode, string> = { parent: 'Họ tên cha/mẹ', child: 'Họ tên người con', spouse: 'Họ tên vợ/chồng' };

/** Tạo hồ sơ mới và nối quan hệ trong một lần lưu (POST /persons/{id}/relatives); lỗi thì server không lưu gì. */
function NewRelativeForm({ person, mode, spouses, onDone }: {
  person: Person; mode: RelationMode; spouses: SpouseRef[]; onDone: () => void;
}) {
  const cache = useQueryClient();
  // BR-GEN-27: con mới mặc định có cha/mẹ còn lại là vợ/chồng hiện tại; BR-GEN-28: lấy vợ/chồng mới thì hỏi ghi ly hôn người cũ.
  const current = spouses.filter((s) => s.status === 'MARRIED' && !s.deceased);
  const [otherParentId, setOtherParentId] = useState(current[0]?.id ?? '');
  const [divorcePrevious, setDivorcePrevious] = useState(true);
  const defaultGender = mode === 'spouse' ? (person.gender === 'FEMALE' ? 'MALE' : 'FEMALE') : 'MALE';
  const { register, handleSubmit, watch, formState: { errors } } = useForm<RelativeValues>({
    resolver: zodResolver(relativeSchema),
    defaultValues: {
      fullName: '', gender: defaultGender, isDeceased: false, birthDate: '', birthYear: '', deathDate: '',
      parentRole: 'FATHER', kind: 'BIOLOGICAL', startDate: '',
    },
  });
  const gender = watch('gender');
  const deceased = watch('isDeceased');
  // Vai trò cha/mẹ lấy theo giới tính; chỉ hỏi khi người làm cha/mẹ chọn giới tính Khác.
  const needsRole = (mode === 'parent' && gender === 'OTHER') || (mode === 'child' && person.gender === 'OTHER');
  const save = useMutation({
    mutationFn: (v: RelativeValues) => genealogyApi.addRelative(person.id!, {
      relation: RELATION[mode],
      person: {
        fullName: v.fullName.trim(),
        gender: v.gender,
        isDeceased: v.isDeceased,
        birthDate: v.birthDate || null,
        birthYear: v.isDeceased && !v.birthDate && v.birthYear ? Number(v.birthYear) : null,
        deathDate: v.isDeceased && v.deathDate ? v.deathDate : null,
      },
      kind: mode === 'spouse' ? null : v.kind,
      parentRole: needsRole ? v.parentRole : null,
      startDate: mode === 'spouse' && v.startDate ? v.startDate : null,
      otherParentId: mode === 'child' && otherParentId ? otherParentId : null,
      divorcePrevious: mode === 'spouse' && current.length > 0 && divorcePrevious,
    }),
    onSuccess: async () => {
      await Promise.all(['members', 'tree', 'person'].map((key) => cache.invalidateQueries({ queryKey: [key] })));
      onDone();
    },
  });
  return (
    <form className="grid gap-4 p-5" noValidate onSubmit={handleSubmit((v) => save.mutate(v))}>
      <TextField label={NAME_LABEL[mode]} maxLength={150} error={errors.fullName?.message} {...register('fullName')} />
      <label className="grid gap-1 text-label">Giới tính
        <select className={selectClass} {...register('gender')}>
          <option value="MALE">Nam</option><option value="FEMALE">Nữ</option><option value="OTHER">Khác</option>
        </select>
      </label>
      {needsRole && (
        <label className="grid gap-1 text-label">Là cha hay mẹ
          <select className={selectClass} {...register('parentRole')}>
            <option value="FATHER">Cha</option><option value="MOTHER">Mẹ</option>
          </select>
        </label>
      )}
      {mode === 'child' && spouses.length > 0 && (
        <label className="grid gap-1 text-label">{person.gender === 'FEMALE' ? 'Cha' : 'Mẹ'} của người con
          <select className={selectClass} value={otherParentId} onChange={(e) => setOtherParentId(e.target.value)}>
            {spouses.map((s) => <option key={s.id} value={s.id}>{s.name}{s.status === 'MARRIED' ? '' : ' (đã kết thúc hôn nhân)'}</option>)}
            <option value="">Không ghi</option>
          </select>
        </label>
      )}
      {mode === 'spouse' && current.length > 0 && (
        <label className="flex min-h-11 items-center gap-2 rounded-control bg-warning-soft p-3 text-body">
          <input type="checkbox" className="h-5 w-5" checked={divorcePrevious} onChange={(e) => setDivorcePrevious(e.target.checked)} />
          Ghi đã ly hôn với {current.map((s) => s.name).join(', ')}
        </label>
      )}
      <TextField label="Ngày sinh" type="date" error={errors.birthDate?.message} {...register('birthDate')} />
      <label className="flex min-h-11 items-center gap-2 text-body">
        <input type="checkbox" className="h-5 w-5" {...register('isDeceased')} /> Đã mất
      </label>
      {deceased && !watch('birthDate') && (
        <TextField label="Năm sinh (nếu không rõ ngày)" inputMode="numeric" maxLength={4}
          error={errors.birthYear?.message} {...register('birthYear')} />
      )}
      {deceased && <TextField label="Ngày mất (không bắt buộc)" type="date" {...register('deathDate')} />}
      {mode !== 'spouse' ? (
        <label className="grid gap-1 text-label">Loại quan hệ
          <select className={selectClass} {...register('kind')}>
            <option value="BIOLOGICAL">Ruột</option><option value="ADOPTED">Nuôi</option><option value="STEP">Kế</option>
          </select>
        </label>
      ) : (
        <TextField label="Ngày kết hôn (không bắt buộc)" type="date" {...register('startDate')} />
      )}
      <p className="text-caption text-text-secondary">
        Người mới vào cùng chi với {person.fullName}; sửa thêm quê quán, địa chỉ ở trang hồ sơ sau.
      </p>
      {save.isError && <p role="alert" className="text-body text-error">{errorText(save.error)}</p>}
      <Button type="submit" loading={save.isPending}>Lưu và vẽ lên cây</Button>
    </form>
  );
}
