'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { TextField } from '@/components/ui/TextField';
import { AccessState, useHeritageAccess } from '@/components/heritage/HeritageAccess';
import { genealogyApi, toPersonInput, type Person } from '@/services/genealogy';

const PAGE_SIZE = 20;
const selectClass = 'h-[42px] w-full rounded-control border border-border-strong bg-surface-muted px-3 text-body '
  + 'text-text-primary outline-none focus-visible:outline-2 focus-visible:outline-info';
const errorText = (error: unknown) => (error instanceof Error ? error.message : 'Có lỗi xảy ra, vui lòng thử lại.');
const yearOf = (p: Person) => p.birthYear ?? (p.birthDate ? Number(p.birthDate.slice(0, 4)) : undefined);

// UC-GEN-03: người còn sống phải có ngày sinh đầy đủ; người đã mất có thể chỉ biết năm sinh (BR-GEN-17).
const personSchema = z.object({
  fullName: z.string().trim().min(1, 'Họ tên không được để trống').max(150, 'Tối đa 150 ký tự'),
  gender: z.enum(['MALE', 'FEMALE', 'OTHER']),
  isDeceased: z.boolean(),
  birthDate: z.string(),
  birthYear: z.string().refine((v) => v === '' || /^\d{4}$/.test(v), 'Năm gồm 4 chữ số'),
  currentProvince: z.string().trim().max(100, 'Tối đa 100 ký tự'),
}).refine((v) => v.isDeceased || v.birthDate !== '', { path: ['birthDate'], message: 'Người còn sống phải có ngày sinh' });
type PersonValues = z.infer<typeof personSchema>;

function PersonFormModal({ familyId, person, onClose }: { familyId: string; person?: Person; onClose: () => void }) {
  const cache = useQueryClient();
  const { register, handleSubmit, watch, formState: { errors } } = useForm<PersonValues>({
    resolver: zodResolver(personSchema),
    defaultValues: {
      fullName: person?.fullName ?? '', gender: person?.gender ?? 'MALE', isDeceased: person?.isDeceased ?? false,
      birthDate: person?.birthDate ?? '', birthYear: person?.birthYear ? String(person.birthYear) : '',
      currentProvince: person?.currentProvince ?? '',
    },
  });
  const save = useMutation({
    mutationFn: (v: PersonValues) => {
      const input = {
        ...(person ? toPersonInput(person) : { isDeceased: false }),
        fullName: v.fullName, gender: v.gender, isDeceased: v.isDeceased,
        birthDate: v.birthDate || null, birthYear: !v.birthDate && v.birthYear ? Number(v.birthYear) : null,
        currentProvince: v.currentProvince || null,
      };
      return person?.id ? genealogyApi.updatePerson(person.id, input) : genealogyApi.createPerson(familyId, input);
    },
    onSuccess: async () => { await cache.invalidateQueries({ queryKey: ['members'] }); onClose(); },
  });
  return (
    <Modal open title={person ? 'Sửa thành viên' : 'Thêm thành viên'} onClose={onClose}>
      <form className="grid gap-4 p-5" noValidate onSubmit={handleSubmit((v) => save.mutate(v))}>
        <TextField label="Họ tên" maxLength={150} error={errors.fullName?.message} {...register('fullName')} />
        <label className="grid gap-1 text-label">Giới tính
          <select className={selectClass} {...register('gender')}>
            <option value="MALE">Nam</option><option value="FEMALE">Nữ</option><option value="OTHER">Khác</option>
          </select>
        </label>
        <label className="flex min-h-11 items-center gap-2 text-body">
          <input type="checkbox" className="h-5 w-5" {...register('isDeceased')} /> Đã mất
        </label>
        <TextField label="Ngày sinh" type="date" error={errors.birthDate?.message} {...register('birthDate')} />
        {watch('isDeceased') && !watch('birthDate') && (
          <TextField label="Năm sinh (nếu không rõ ngày)" inputMode="numeric" maxLength={4}
            error={errors.birthYear?.message} {...register('birthYear')} />
        )}
        <TextField label="Tỉnh/thành đang sống" maxLength={100} error={errors.currentProvince?.message}
          {...register('currentProvince')} />
        {save.isError && <p role="alert" className="text-body text-error">{errorText(save.error)}</p>}
        <Button type="submit" loading={save.isPending}>Lưu</Button>
      </form>
    </Modal>
  );
}

type RelationMode = 'parent' | 'spouse';

/** UC-GEN-04, 05: chọn người còn lại trong gia đình; lỗi nghiệp vụ hiện đúng thông điệp server (GEN_005..011). */
function RelationModal({ familyId, person, mode, onClose }: {
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
  useEffect(() => { if (other?.gender) setRole(other.gender === 'FEMALE' ? 'MOTHER' : 'FATHER'); }, [other]); // BR-GEN-06
  const gap = other && yearOf(other) && yearOf(person) ? (yearOf(person)! - yearOf(other)!) : undefined;
  const needsConfirm = mode === 'parent' && gap !== undefined && gap < 15; // BR-GEN-04: chỉ cảnh báo, server mới chặn
  const save = useMutation({
    mutationFn: () => mode === 'parent'
      ? genealogyApi.addParent(person.id!, { parentId: otherId, parentRole: role, kind })
      : genealogyApi.createMarriage({ person1Id: person.id!, person2Id: otherId, startDate: startDate || null }),
    onSuccess: async () => {
      await Promise.all([cache.invalidateQueries({ queryKey: ['members'] }), cache.invalidateQueries({ queryKey: ['tree'] })]);
      onClose();
    },
  });
  const title = mode === 'parent' ? `Thêm cha/mẹ cho ${person.fullName}` : `Thêm vợ/chồng cho ${person.fullName}`;
  return (
    <Modal open title={title} onClose={onClose}>
      <form className="grid gap-4 p-5" onSubmit={(e) => { e.preventDefault(); save.mutate(); }}>
        {options.isPending && <p className="text-body text-text-secondary">Đang tải danh sách thành viên...</p>}
        {options.isError && <p role="alert" className="text-body text-error">{errorText(options.error)}</p>}
        <label className="grid gap-1 text-label">{mode === 'parent' ? 'Người làm cha/mẹ' : 'Người còn lại'}
          <select className={selectClass} value={otherId} required onChange={(e) => setOtherId(e.target.value)}>
            <option value="">Chọn thành viên</option>
            {others.map((p) => <option key={p.id} value={p.id}>{p.fullName}{yearOf(p) ? ` (${yearOf(p)})` : ''}</option>)}
          </select>
        </label>
        {mode === 'parent' ? <>
          <label className="grid gap-1 text-label">Vai trò
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
            {gap <= 0 ? 'Người được chọn không lớn tuổi hơn con (hệ thống sẽ từ chối nếu sinh sau con).'
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

export default function MembersPage() {
  const access = useHeritageAccess();
  const [adminFamily, setAdminFamily] = useState('');
  const familyId = access.familyId ?? (access.isSystemAdmin ? adminFamily.trim() : '');
  const [search, setSearch] = useState('');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Person | 'new' | null>(null);
  const [relation, setRelation] = useState<{ person: Person; mode: RelationMode } | null>(null);
  const list = useQuery({
    queryKey: ['members', familyId, q, page],
    enabled: access.isReady && Boolean(familyId),
    queryFn: () => genealogyApi.persons(familyId, { q: q || undefined, page, size: PAGE_SIZE, sort: 'fullName,asc' }),
  });
  const total = list.data?.meta.total ?? 0;
  const error = (access.membership.error as Error | undefined)?.message ?? (list.error as Error | undefined)?.message;

  return (
    <main className="mx-auto min-h-screen max-w-4xl space-y-6 px-4 py-8 sm:px-6">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-page-title">Thành viên</h1>
          <p className="mt-1 text-body text-text-secondary">Tìm thành viên, thêm hồ sơ và quan hệ gia đình.</p>
        </div>
        {access.isAdmin && familyId && <Button fullWidth={false} onClick={() => setEditing('new')}>Thêm thành viên</Button>}
      </header>
      <AccessState loading={access.membership.isLoading || access.profile.isLoading} error={error} />
      {access.isSystemAdmin && !access.familyId && (
        <TextField label="Mã gia đình" value={adminFamily} onChange={(e) => setAdminFamily(e.target.value)} placeholder="UUID gia đình" />
      )}
      {familyId && <>
        <form className="flex gap-2" role="search" onSubmit={(e) => { e.preventDefault(); setPage(0); setQ(search.trim()); }}>
          <div className="flex-1"><TextField label="Tìm theo họ tên" value={search} onChange={(e) => setSearch(e.target.value)} /></div>
          <div className="self-end"><Button type="submit" fullWidth={false} variant="secondary">Tìm</Button></div>
        </form>
        {list.isPending && <p className="text-body text-text-secondary">Đang tải danh sách...</p>}
        {list.isSuccess && list.data.items.length === 0 && (
          <p className="rounded-card bg-surface p-5 text-body text-text-secondary">
            {q ? 'Không có thành viên phù hợp.' : 'Gia đình chưa có thành viên.'}
          </p>
        )}
        <ul className="space-y-3">
          {list.data?.items.map((p) => (
            <li key={p.id} className="flex flex-wrap items-center justify-between gap-3 rounded-card border border-border bg-surface p-4">
              <div className="min-w-0">
                <Link href={`/persons/${p.id}`} className="text-body font-semibold text-info underline">{p.fullName}</Link>
                <p className="text-caption text-text-secondary">
                  {[yearOf(p) && `Sinh ${yearOf(p)}`, p.currentProvince, p.isDeceased && 'Đã mất'].filter(Boolean).join(' · ') || 'Chưa có thông tin'}
                </p>
              </div>
              {access.isAdmin && <div className="flex flex-wrap gap-2">
                <Button fullWidth={false} variant="secondary" onClick={() => setEditing(p)}>Sửa</Button>
                <Button fullWidth={false} variant="secondary" onClick={() => setRelation({ person: p, mode: 'parent' })}>Thêm cha/mẹ</Button>
                <Button fullWidth={false} variant="secondary" onClick={() => setRelation({ person: p, mode: 'spouse' })}>Thêm vợ/chồng</Button>
              </div>}
            </li>
          ))}
        </ul>
        {total > PAGE_SIZE && <nav className="flex items-center justify-between" aria-label="Phân trang">
          <Button fullWidth={false} variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Trang trước</Button>
          <span className="text-caption text-text-secondary">Trang {page + 1}/{Math.ceil(total / PAGE_SIZE)}</span>
          <Button fullWidth={false} variant="secondary" disabled={(page + 1) * PAGE_SIZE >= total} onClick={() => setPage(page + 1)}>Trang sau</Button>
        </nav>}
      </>}
      {editing && familyId && <PersonFormModal familyId={familyId} person={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
      {relation && familyId && <RelationModal familyId={familyId} {...relation} onClose={() => setRelation(null)} />}
    </main>
  );
}
