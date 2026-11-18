'use client';

import Link from 'next/link';
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/TextField';
import { AccessState, useHeritageAccess } from '@/components/heritage/HeritageAccess';
import { RelationModal, yearOf, type RelationMode } from '@/components/genealogy/RelationModal';
import { Avatar } from '@/components/genealogy/PersonNode';
import { PersonFormModal } from '@/components/genealogy/PersonFormModal';
import { genealogyApi, type Person } from '@/services/genealogy';

const PAGE_SIZE = 20;


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
    <main className="mx-auto min-h-screen max-w-4xl space-y-6 px-4 py-8 md:px-6">
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
        <ul className="grid gap-3 md:grid-cols-2">
          {list.data?.items.map((p) => (
            <li key={p.id} className="space-y-3 rounded-card border border-border bg-surface p-4 shadow-card">
              {/* Bấm cả thẻ để mở trang cá nhân (SCRUM-117). */}
              <Link href={`/persons/${p.id}`} className="flex min-h-11 items-center gap-3 rounded-control hover:bg-surface-muted">
                <Avatar mediaId={p.avatarMediaId} name={p.fullName ?? ''} size="h-12 w-12" />
                <span className="min-w-0">
                  <span className="block truncate text-label">{p.fullName}</span>
                  <span className="block text-caption text-text-secondary">
                    {[yearOf(p) && `Sinh ${yearOf(p)}`, p.currentProvince, p.isDeceased && 'Đã mất'].filter(Boolean).join(' · ') || 'Chưa có thông tin'}
                  </span>
                </span>
              </Link>
              {(p.canEdit || access.isAdmin) && <div className="flex flex-wrap gap-2">
                {/* BR-GEN-20: nút Sửa theo canEdit do backend tính (chính chủ, hoặc quản trị với hồ sơ chưa có tài khoản). */}
                {p.canEdit && <Button fullWidth={false} variant="secondary" onClick={() => setEditing(p)}>Sửa</Button>}
                {access.isAdmin && <Button fullWidth={false} variant="secondary" onClick={() => setRelation({ person: p, mode: 'parent' })}>Thêm cha/mẹ</Button>}
                {access.isAdmin && <Button fullWidth={false} variant="secondary" onClick={() => setRelation({ person: p, mode: 'spouse' })}>Thêm vợ/chồng</Button>}
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
